package com.jagr.fridamusic.lyrics

import android.content.Context
import android.util.LruCache
import com.jagr.fridamusic.constants.LyricsProviderOrderKey
import com.jagr.fridamusic.constants.PreferredLyricsProvider
import com.jagr.fridamusic.constants.PreferredLyricsProviderKey
import com.jagr.fridamusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jagr.fridamusic.extensions.toEnum
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.utils.NetworkConnectivityObserver
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private val selectedCache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)
    private val searchCache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()
        if (orderString.isNotBlank()) return LyricsProviderRegistry.getOrderedProviders(orderString)

        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        return (listOf(preferredName) + defaultOrder.filter { it != preferredName })
            .mapNotNull(LyricsProviderRegistry::getProviderByName)
    }

    suspend fun getLyrics(
        mediaMetadata: MediaMetadata,
        forceRefresh: Boolean = false,
    ): LyricsWithProvider {
        if (forceRefresh) selectedCache.remove(mediaMetadata.id)
        selectedCache.get(mediaMetadata.id)?.let { return it }
        if (!isNetworkAvailable()) return notFound()

        val providers = resolveLyricsProviders().filter(::isProviderEnabled)
        if (providers.isEmpty()) return notFound()

        val preferred = providers.first()
        val primaryProviders = providers.filterNot(LyricsProviderRegistry::isFallbackProvider)
            .let { primary -> (listOf(preferred) + primary).distinct() }
        val candidates = fetchCandidates(primaryProviders, mediaMetadata, preferred.name).toMutableList()
        var best = candidates.maxByOrNull { it.score.total }

        if (best == null || best.score.total < PRIMARY_ACCEPT_SCORE) {
            val fallbackProviders = providers.filterNot(primaryProviders::contains)
            candidates += fetchCandidates(fallbackProviders, mediaMetadata, preferred.name)
            best = candidates.maxByOrNull { it.score.total }
        }

        if (best == null || best.score.total < MIN_VALID_SCORE) return notFound()

        val exactPlaybackReference = candidates
            .filter { it.providerName == YouTubeSubtitleLyricsProvider.name }
            .maxByOrNull { it.score.total }
        val autoOffsetMs = exactPlaybackReference
            ?.takeIf { it !== best }
            ?.let { LyricsEngine.estimateAutoSyncOffsetMs(best.document, it.document) }
            ?: 0
        val result = LyricsWithProvider(
            lyrics = best.lyrics,
            provider = best.providerName,
            syncType = best.document.syncType,
            score = best.score.total,
            autoOffsetMs = autoOffsetMs,
        )
        selectedCache.put(mediaMetadata.id, result)
        return result
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()
        val cacheKey = searchCacheKey(mediaId, songTitle, songArtists)
        searchCache.get(cacheKey)?.let { results ->
            results.forEach(callback)
            return
        }
        if (!isNetworkAvailable()) return

        val allResults = mutableListOf<LyricsResult>()
        val fingerprints = mutableSetOf<String>()
        val providers = resolveLyricsProviders().filter(::isProviderEnabled)
        val searchJob = currentCoroutineContext()[Job]
        currentLyricsJob = searchJob
        try {
            supervisorScope {
                providers.map { provider ->
                    launch(Dispatchers.IO) {
                        withTimeoutOrNull(PROVIDER_SEARCH_TIMEOUT_MS) {
                            try {
                                provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                                    val document = LyricsEngine.normalize(lyrics, duration)
                                    val score = LyricsEngine.score(
                                        document = document,
                                        providerName = provider.name,
                                        songDurationSeconds = duration,
                                        preferredProvider = provider == providers.firstOrNull(),
                                    )
                                    val fingerprint = lyricsFingerprint(lyrics)
                                    if (score.total >= MIN_VALID_SCORE) {
                                        val result = LyricsResult(
                                            providerName = provider.name,
                                            lyrics = lyrics,
                                            syncType = document.syncType,
                                            score = score.total,
                                        )
                                        val shouldPublish = synchronized(allResults) {
                                            fingerprints.add(fingerprint).also { added ->
                                                if (added) allResults += result
                                            }
                                        }
                                        if (shouldPublish) callback(result)
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                reportException(e)
                            }
                        }
                    }
                }.joinAll()
            }
            searchCache.put(cacheKey, synchronized(allResults) { allResults.sortedByDescending { it.score } })
        } finally {
            if (currentLyricsJob === searchJob) currentLyricsJob = null
        }
    }

    fun invalidate(mediaId: String) {
        selectedCache.remove(mediaId)
        searchCache.evictAll()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    private suspend fun fetchCandidates(
        providers: List<LyricsProvider>,
        mediaMetadata: MediaMetadata,
        preferredProviderName: String,
    ): List<ScoredLyricsCandidate> = supervisorScope {
        providers.map { provider ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    try {
                        val raw = provider.getLyrics(
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        ).getOrElse { error ->
                            reportException(error)
                            return@withTimeoutOrNull null
                        }
                        val document = LyricsEngine.normalize(raw, mediaMetadata.duration)
                        val score = LyricsEngine.score(
                            document = document,
                            providerName = provider.name,
                            songDurationSeconds = mediaMetadata.duration,
                            preferredProvider = provider.name == preferredProviderName,
                        )
                        ScoredLyricsCandidate(provider.name, raw, document, score)
                            .takeIf { score.total >= MIN_VALID_SCORE }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun isProviderEnabled(provider: LyricsProvider): Boolean = try {
        provider.isEnabled(context)
    } catch (e: Exception) {
        reportException(e)
        false
    }

    private suspend fun isNetworkAvailable(): Boolean = try {
        networkConnectivity.isCurrentlyConnected()
    } catch (_: Exception) {
        true
    }

    private fun notFound() = LyricsWithProvider(
        lyrics = LYRICS_NOT_FOUND,
        provider = "Unknown",
        syncType = LyricsSyncType.PLAIN,
        score = Int.MIN_VALUE,
    )

    private fun searchCacheKey(mediaId: String, title: String, artists: String): String =
        "$mediaId|$artists|$title".lowercase(Locale.ROOT).replace(" ", "")

    private fun lyricsFingerprint(lyrics: String): String = lyrics
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val MAX_CACHE_SIZE = 6
        private const val PROVIDER_TIMEOUT_MS = 12_000L
        private const val PROVIDER_SEARCH_TIMEOUT_MS = 18_000L
        private const val MIN_VALID_SCORE = 15
        private const val PRIMARY_ACCEPT_SCORE = 45
    }
}

private data class ScoredLyricsCandidate(
    val providerName: String,
    val lyrics: String,
    val document: LyricsDocument,
    val score: LyricsScore,
)

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
    val syncType: LyricsSyncType = LyricsSyncType.PLAIN,
    val score: Int = 0,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
    val syncType: LyricsSyncType = LyricsSyncType.PLAIN,
    val score: Int = 0,
    val autoOffsetMs: Int = 0,
)
