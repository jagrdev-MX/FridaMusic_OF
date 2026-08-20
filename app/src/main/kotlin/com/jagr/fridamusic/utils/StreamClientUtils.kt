/*
 * Adapted from OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed under GPL-3.0 | see git history for contributors
 */

package com.jagr.fridamusic.utils

import com.music.innertube.models.YouTubeClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.Locale

/** Keeps stream probes and Media3 requests on the client profile that issued the URL. */
object StreamClientUtils {
    data class StreamRequestProfile(
        val requestedClientName: String,
        val requestedClientVersion: String,
        val resolvedClientFamily: String,
        val resolvedClientVersion: String,
        val userAgent: String,
        val origin: String?,
        val referer: String?,
        val requiresPlaybackProbeRanges: Boolean,
    ) {
        val clientKey: String
            get() = normalizeClientKey("$resolvedClientFamily@$resolvedClientVersion")
    }

    fun isYouTubeMediaHost(host: String): Boolean =
        host.endsWith("googlevideo.com") ||
            host.endsWith("googleusercontent.com") ||
            host.endsWith("youtube.com") ||
            host.endsWith("youtube-nocookie.com") ||
            host.endsWith("ytimg.com")

    fun resolveRequestProfile(url: String): StreamRequestProfile = resolveRequestProfile(url.toHttpUrlOrNull())

    fun resolveRequestProfile(url: HttpUrl?): StreamRequestProfile =
        resolveRequestProfile(
            clientName = url?.queryParameter("c"),
            clientVersion = url?.queryParameter("cver"),
        )

    fun resolveRequestProfile(
        clientName: String?,
        clientVersion: String? = null,
    ): StreamRequestProfile {
        val requestedName = clientName.normalizedOrEmpty().uppercase(Locale.US)
        val requestedVersion = clientVersion.normalizedOrEmpty()
        val client = resolveClient(requestedName, requestedVersion)
        val isTv = isTvClient(client)
        val isWeb = isWebMusicClient(client)

        return StreamRequestProfile(
            requestedClientName = requestedName.ifEmpty { client.clientName },
            requestedClientVersion = requestedVersion.ifEmpty { client.clientVersion },
            resolvedClientFamily = client.clientName,
            resolvedClientVersion = client.clientVersion,
            userAgent = client.userAgent,
            origin = when {
                isTv -> YouTubeClient.ORIGIN_YOUTUBE
                isWeb -> YouTubeClient.ORIGIN_YOUTUBE_MUSIC
                else -> null
            },
            referer = when {
                isTv -> YouTubeClient.REFERER_YOUTUBE_TV
                isWeb -> YouTubeClient.REFERER_YOUTUBE_MUSIC
                else -> null
            },
            requiresPlaybackProbeRanges = isTv || isWeb,
        )
    }

    fun applyRequestProfile(builder: Request.Builder, url: HttpUrl): Request.Builder =
        applyRequestProfile(builder, resolveRequestProfile(url))

    fun applyRequestProfile(
        builder: Request.Builder,
        profile: StreamRequestProfile,
    ): Request.Builder {
        builder.header("User-Agent", profile.userAgent)
        profile.origin?.let { builder.header("Origin", it) } ?: builder.removeHeader("Origin")
        profile.referer?.let { builder.header("Referer", it) } ?: builder.removeHeader("Referer")
        return builder
    }

    fun patchClientVersion(url: String, clientVersion: String): String {
        if (!url.contains("cver=")) return url
        return url.replace(Regex("cver=[^&]+"), "cver=$clientVersion")
    }

    fun appendPoToken(url: String, poToken: String): String {
        if (url.contains("pot=")) return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}pot=$poToken"
    }

    fun buildClientKey(client: YouTubeClient): String =
        normalizeClientKey("${client.clientName}@${client.clientVersion}")

    fun normalizeClientKey(clientKey: String?): String =
        clientKey?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.US).orEmpty()

    private fun resolveClient(clientName: String, clientVersion: String): YouTubeClient =
        when {
            clientName == "WEB_REMIX" -> YouTubeClient.WEB_REMIX
            clientName == "WEB" -> YouTubeClient.WEB
            clientName == "WEB_CREATOR" -> YouTubeClient.WEB_CREATOR
            clientName == "MWEB" -> YouTubeClient.MWEB
            clientName == "WEB_EMBEDDED_PLAYER" || clientName == "WEB_EMBEDDED" -> YouTubeClient.WEB_EMBEDDED
            clientName == "TVHTML5" -> YouTubeClient.TVHTML5
            clientName == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" || clientName == "TVHTML5_SIMPLY" ->
                YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER
            clientName == "IOS_MUSIC" -> YouTubeClient.IOS_MUSIC
            clientName.startsWith("IOS") ->
                if (clientVersion == YouTubeClient.IPADOS.clientVersion) YouTubeClient.IPADOS else YouTubeClient.IOS
            clientName == "ANDROID_MUSIC" -> YouTubeClient.ANDROID_MUSIC
            clientName == "ANDROID_TESTSUITE" -> YouTubeClient.ANDROID_TESTSUITE
            clientName == "ANDROID_UNPLUGGED" -> YouTubeClient.ANDROID_UNPLUGGED
            clientName.startsWith("ANDROID_CREATOR") -> YouTubeClient.ANDROID_CREATOR
            clientName.startsWith("ANDROID_VR") ->
                when (clientVersion) {
                    YouTubeClient.ANDROID_VR_1_61_48.clientVersion -> YouTubeClient.ANDROID_VR_1_61_48
                    YouTubeClient.ANDROID_VR_1_43_32.clientVersion -> YouTubeClient.ANDROID_VR_1_43_32
                    else -> YouTubeClient.ANDROID_VR_NO_AUTH
                }
            clientName.startsWith("ANDROID") -> YouTubeClient.MOBILE
            clientName.startsWith("VISIONOS") -> YouTubeClient.VISIONOS
            else -> YouTubeClient.ANDROID_VR_NO_AUTH
        }

    private fun isTvClient(client: YouTubeClient): Boolean =
        client.clientName.uppercase(Locale.US) in
            setOf("TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY")

    private fun isWebMusicClient(client: YouTubeClient): Boolean =
        client.clientName.uppercase(Locale.US) in
            setOf("WEB", "WEB_REMIX", "WEB_CREATOR", "MWEB", "WEB_EMBEDDED_PLAYER")

    private fun String?.normalizedOrEmpty(): String = this?.trim().orEmpty()
}
