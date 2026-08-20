/*
 * Adapted from OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed under GPL-3.0 | see git history for contributors
 */

package com.jagr.fridamusic.utils

import android.util.Log
import androidx.media3.common.PlaybackException
import com.jagr.fridamusic.utils.potoken.PoTokenGenerator
import com.jagr.fridamusic.utils.potoken.PoTokenResult
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_TESTSUITE
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_UNPLUGGED
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IOS_MUSIC
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Active YouTube stream resolver. Frida-specific source fallbacks remain in [YTPlayerUtils]. */
object RemoteStreamResolver {
    private const val TAG = "RemotePlayback"
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    private const val URL_EXPIRY_SAFETY_MS = 30_000L

    private val preferredClient = ANDROID_VR_NO_AUTH
    private val fallbackClients = arrayOf(
        IOS,
        MOBILE,
        ANDROID_MUSIC,
        IOS_MUSIC,
        ANDROID_VR_NO_AUTH,
        ANDROID_VR_1_61_48,
        ANDROID_VR_1_43_32,
        ANDROID_CREATOR,
        ANDROID_TESTSUITE,
        ANDROID_UNPLUGGED,
        IPADOS,
        VISIONOS,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        WEB,
        WEB_CREATOR,
        WEB_REMIX,
    )

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClientKey: String,
    )

    private data class CachedStreamUrl(val url: String, val expiresAtMs: Long)
    private data class GateFailure(val clientName: String, val status: String, val reason: String?)

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()
    private val poTokenGenerator = PoTokenGenerator()
    @Volatile
    private var lastSuccessfulClientKey: String? = null

    private val streamHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    return when (YouTube.ipVersion) {
                        IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                        IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                        IpVersion.AUTO -> addresses
                    }
                }
            })
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> = listOf(YouTube.proxy ?: Proxy.NO_PROXY)

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                    Timber.tag(TAG).w(ioe, "Stream proxy connection failed")
                }
            })
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder().header("Proxy-Authorization", auth).build()
                } ?: response.request
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun invalidateCachedStreamUrls(videoId: String) {
        val prefix = "$videoId:"
        streamUrlCache.keys.removeIf { it.startsWith(prefix) }
    }

    fun markStreamClientFailed(videoId: String, clientKey: String?, httpStatusCode: Int?) {
        if (httpStatusCode != 403) return
        val normalized = StreamClientUtils.normalizeClientKey(clientKey)
        if (normalized.isEmpty()) return
        failedStreamClientsUntil["$videoId:$normalized"] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
    }

    suspend fun resolve(
        videoId: String,
        playlistId: String? = null,
        persistedClientKey: String? = null,
    ): Result<PlaybackData> = runCatching {
        Log.i(TAG, "resolve videoId=$videoId resolver=OpenTuneAdapted")
        var signatureTimestamp: Int? = null
        var signatureTimestampResolved = false
        fun signatureTimestampFor(client: YouTubeClient): Int? {
            if (!client.useSignatureTimestamp) return null
            if (!signatureTimestampResolved) {
                signatureTimestamp = NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                signatureTimestampResolved = true
            }
            return signatureTimestamp
        }
        val isLoggedIn = !YouTube.cookie.isNullOrBlank()
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData

        val orderedFallbacks =
            if (isLoggedIn) {
                fallbackClients.filter { it.loginSupported } + fallbackClients.filterNot { it.loginSupported }
            } else {
                fallbackClients.toList()
            }
        val availableClients = buildList {
            add(preferredClient)
            addAll(orderedFallbacks)
            add(WEB_REMIX)
        }.distinct()
            .filterNot { isClientTemporarilyBlocked(videoId, it) }
        val rememberedClientKey = lastSuccessfulClientKey
            ?: StreamClientUtils.normalizeClientKey(persistedClientKey).takeIf { it.isNotEmpty() }
        val initialClient = availableClients.firstOrNull { client ->
            (!client.loginRequired || isLoggedIn) &&
                StreamClientUtils.buildClientKey(client) == rememberedClientKey
        } ?: availableClients.firstOrNull { !it.loginRequired || isLoggedIn } ?: preferredClient
        val streamClients = buildList {
            add(initialClient)
            addAll(availableClients)
        }.distinct()

        val initialPoToken = tokenForClient(initialClient, videoId, sessionId)
        val metadataResponse =
            YouTube.player(
                videoId = videoId,
                playlistId = playlistId,
                client = initialClient,
                signatureTimestamp = signatureTimestampFor(initialClient),
                poToken = initialPoToken?.playerRequestPoToken,
            ).getOrThrow()
        val audioConfig = metadataResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse.videoDetails
        val playbackTracking = metadataResponse.playbackTracking
        val expectedDurationMs = videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L)

        var gateFailure: GateFailure? = null
        val botDetectedClients = mutableSetOf<String>()

        for (client in streamClients) {
            if (client.loginRequired && !isLoggedIn) continue

            val poToken = if (client == initialClient) initialPoToken else tokenForClient(client, videoId, sessionId)
            val response =
                if (client == initialClient) {
                    metadataResponse
                } else {
                    YouTube.player(
                        videoId = videoId,
                        playlistId = playlistId,
                        client = client,
                        signatureTimestamp = signatureTimestampFor(client),
                        poToken = poToken?.playerRequestPoToken,
                    ).getOrNull()
                } ?: continue

            if (response.playabilityStatus.status != "OK") {
                val reason = response.playabilityStatus.reason.orEmpty()
                if (isLoginRecoveryError(reason)) {
                    gateFailure = GateFailure(client.clientName, response.playabilityStatus.status, reason)
                } else if (isBotDetectionError(reason)) {
                    botDetectedClients += client.clientName
                }
                continue
            }

            val expiresInSeconds = response.streamingData?.expiresInSeconds ?: continue
            val candidates = selectAudioFormatCandidates(response)
            for (candidate in candidates.asSequence().take(6)) {
                if (expectedDurationMs != null && isLikelyPreview(candidate, expectedDurationMs)) continue

                val cacheKey = buildCacheKey(videoId, client, candidate.itag)
                val cached = streamUrlCache[cacheKey]
                var candidateUrl =
                    cached?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.url
                        ?: findUrlOrNull(candidate, videoId, client)
                        ?: continue

                poToken?.streamingDataPoToken?.takeIf { client.useWebPoTokens }?.let {
                    candidateUrl = StreamClientUtils.appendPoToken(candidateUrl, it)
                }

                if (!validateStatus(candidateUrl)) {
                    streamUrlCache.remove(cacheKey)
                    continue
                }

                val expiresAt =
                    System.currentTimeMillis() + expiresInSeconds * 1000L - URL_EXPIRY_SAFETY_MS
                streamUrlCache[cacheKey] = CachedStreamUrl(candidateUrl, expiresAt)
                val codec = extractCodec(candidate.mimeType).orEmpty()
                val clientKey = StreamClientUtils.buildClientKey(client)
                lastSuccessfulClientKey = clientKey
                Log.i(
                    TAG,
                    "resolved videoId=$videoId client=$clientKey itag=${candidate.itag} codec=$codec bitrate=${candidate.bitrate}",
                )
                return@runCatching PlaybackData(
                    audioConfig = audioConfig,
                    videoDetails = videoDetails ?: response.videoDetails,
                    playbackTracking = playbackTracking ?: response.playbackTracking,
                    format = candidate,
                    streamUrl = candidateUrl,
                    streamExpiresInSeconds = expiresInSeconds,
                    streamClientKey = clientKey,
                )
            }
        }

        gateFailure?.let {
            throw PlaybackException(
                it.reason ?: "YouTube login required (${it.clientName}/${it.status})",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR,
            )
        }
        if (botDetectedClients.isNotEmpty()) {
            throw PlaybackException(
                "Sign in to confirm you're not a bot",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR,
            )
        }
        throw IllegalStateException("No valid YouTube audio stream after ${streamClients.size} clients")
    }.onFailure { error ->
        Log.e(TAG, "resolve failed videoId=$videoId type=${error::class.simpleName} message=${error.message}")
    }

    private suspend fun tokenForClient(
        client: YouTubeClient,
        videoId: String,
        sessionId: String?,
    ): PoTokenResult? {
        if (!client.useWebPoTokens || sessionId.isNullOrBlank()) return null
        return runCatching { poTokenGenerator.getWebClientPoToken(videoId, sessionId) }
            .onFailure { Timber.tag(TAG).w(it, "PoToken generation failed for ${client.clientName}") }
            .getOrNull()
    }

    private fun selectAudioFormatCandidates(
        response: PlayerResponse,
    ): List<PlayerResponse.StreamingData.Format> {
        val directFirst =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        return response.streamingData?.adaptiveFormats
            ?.asSequence()
            ?.filter { it.isAudio && it.bitrate > 0 }
            ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
            ?.sortedWith(directFirst)
            ?.toList()
            .orEmpty()
    }

    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient,
    ): String? {
        val url = NewPipeExtractor.getStreamUrl(format, videoId) ?: format.url ?: return null
        return StreamClientUtils.patchClientVersion(url, client.clientVersion)
    }

    private fun validateStatus(url: String): Boolean {
        val profile = StreamClientUtils.resolveRequestProfile(url)
        val ranges =
            if (profile.requiresPlaybackProbeRanges) {
                listOf("bytes=0-0", "bytes=262144-262145", "bytes=1048576-1048577")
            } else {
                listOf("bytes=0-0")
            }

        return try {
            ranges.all { range ->
                val builder = Request.Builder().get().url(url).header("Range", range)
                val request = StreamClientUtils.applyRequestProfile(builder, profile).build()
                streamHttpClient.newCall(request).execute().use { response ->
                    response.code in 200..399 || response.code == 416
                }
            }
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Stream validation failed")
            false
        }
    }

    private fun isClientTemporarilyBlocked(videoId: String, client: YouTubeClient): Boolean {
        val keys = listOf(client.clientName, StreamClientUtils.buildClientKey(client))
        return keys.any { clientKey ->
            val key = "$videoId:${StreamClientUtils.normalizeClientKey(clientKey)}"
            val until = failedStreamClientsUntil[key] ?: return@any false
            if (until <= System.currentTimeMillis()) {
                failedStreamClientsUntil.remove(key)
                false
            } else {
                true
            }
        }
    }

    private fun buildCacheKey(videoId: String, client: YouTubeClient, itag: Int): String =
        "$videoId:${StreamClientUtils.buildClientKey(client)}:$itag"

    private fun extractCodec(mimeType: String): String? =
        Regex("""codecs="([^"]+)"""")
            .find(mimeType)
            ?.groupValues
            ?.getOrNull(1)
            ?.substringBefore(',')
            ?.trim()

    private fun codecRank(codec: String?): Int =
        when {
            codec.isNullOrBlank() -> 0
            codec.contains("opus", ignoreCase = true) -> 3
            codec.contains("mp4a", ignoreCase = true) -> 2
            else -> 1
        }

    private fun isLikelyPreview(
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long,
    ): Boolean {
        val approximateDuration = format.approxDurationMs?.toLongOrNull() ?: return false
        if (expectedDurationMs < 90_000L) return false
        return approximateDuration in 1L..minOf(90_000L, expectedDurationMs * 9L / 10L)
    }

    private fun isBotDetectionError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "bot" in lower ||
            "unusual traffic" in lower ||
            "automated" in lower ||
            ("confirm" in lower && "not a" in lower) ||
            ("verify" in lower && "human" in lower)
    }

    private fun isLoginRecoveryError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "confirm your age" in lower ||
            "age-restricted" in lower ||
            "age restricted" in lower ||
            "mature audiences" in lower ||
            ("adult" in lower && "sign in" in lower)
    }
}
