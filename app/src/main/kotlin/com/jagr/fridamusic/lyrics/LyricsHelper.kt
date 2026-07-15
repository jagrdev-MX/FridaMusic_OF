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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {

    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (_: Exception) { true }

        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val providers = resolveLyricsProviders()
            .filter { it.isEnabled(context) }

        if (providers.isEmpty()) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        return coroutineScope {
            val resultChannel = Channel<Pair<Int, LyricsWithProvider>>(capacity = providers.size)

            val jobs = providers.map { provider ->
                async {
                    try {
                        val raw = provider.getLyrics(
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        ).getOrNull()

                        if (raw != null) {
                            val entries = LyricsUtils.parseLyrics(raw)
                            val score = when {
                                entries.any { !it.words.isNullOrEmpty() } -> 3
                                entries.isNotEmpty() -> 2
                                raw.isNotBlank() && raw != LYRICS_NOT_FOUND -> 1
                                else -> 0
                            }
                            if (score > 0) {
                                resultChannel.trySend(score to LyricsWithProvider(raw, provider.name))
                            }
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }

            launch {
                jobs.awaitAll()
                resultChannel.close()
            }

            var bestScore = 0
            var bestResult: LyricsWithProvider? = null

            for ((score, result) in resultChannel) {
                if (score > bestScore) {
                    bestScore = score
                    bestResult = result
                }
                if (bestScore == 3) break
            }

            jobs.forEach { it.cancel() }
            resultChannel.close()

            bestResult ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }
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

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (_: Exception) { true }

        if (!isNetworkAvailable) return

        val allResult = mutableListOf<LyricsResult>()
        val providers = resolveLyricsProviders()

        val scope = CoroutineScope(SupervisorJob())
        currentLyricsJob = scope.launch {
            val jobs = providers
                .filter { it.isEnabled(context) }
                .map { provider ->
                    launch {
                        try {
                            provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                                val result = LyricsResult(provider.name, lyrics)
                                synchronized(allResult) { allResult += result }
                                callback(result)
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                }
            jobs.forEach { it.join() }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
        scope.cancel()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)