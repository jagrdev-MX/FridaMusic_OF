package com.jagr.fridamusic.lyrics

import android.content.Context
import android.util.LruCache
import com.jagr.fridamusic.constants.PreferredLyricsProvider
import com.jagr.fridamusic.constants.PreferredLyricsProviderKey
import com.jagr.fridamusic.constants.LyricsProviderOrderKey
import com.jagr.fridamusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jagr.fridamusic.extensions.toEnum
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.utils.NetworkConnectivityObserver
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private val baseProviders =
        listOf(
            YouLyPlusLyricsProvider,
            PaxSenixLyricsProvider,
            UnisonLyricsProvider,
            BetterLyricsProvider,
            SimpMusicLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider,
        )

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    private suspend fun orderedProviders(): List<LyricsProvider> {
        val savedOrder = context.dataStore.data
            .first()[LyricsProviderOrderKey]
            ?.split(",")
            ?.mapNotNull { name -> LyricsProviderRegistry.getProviderByName(name.trim()) }

        if (!savedOrder.isNullOrEmpty()) {
            return savedOrder + baseProviders.filterNot { it in savedOrder }
        }

        val preferred = context.dataStore.data
            .first()[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)

        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferred)
        val preferredProvider = LyricsProviderRegistry.getProviderByName(preferredName)
        return listOfNotNull(preferredProvider) + baseProviders.filterNot { it == preferredProvider }
    }

    suspend fun getLyrics(mediaMetadata: MediaMetadata, preferredProviderOnly: Boolean = false): LyricsWithProvider {
        currentLyricsJob?.cancel()

        cache.get(mediaMetadata.id)
            ?.firstOrNull { isMeaningfulLyrics(it.lyrics) }
            ?.let { return LyricsWithProvider(it.lyrics, it.providerName) }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (_: Exception) {
            true
        }

        if (!isNetworkAvailable) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

        val orderedProviders = orderedProviders()
        val providers = if (preferredProviderOnly) orderedProviders.take(1) else orderedProviders

        return withContext(Dispatchers.IO) {
            for (provider in providers) {
                if (!provider.isEnabled(context)) continue

                try {
                    val result =
                        provider.getLyrics(
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )

                    result.onSuccess { lyrics ->
                        if (isMeaningfulLyrics(lyrics)) {
                            val normalized = lyrics.trim()
                            cache.put(
                                mediaMetadata.id,
                                listOf(LyricsResult(provider.name, normalized)),
                            )
                            return@withContext LyricsWithProvider(normalized, provider.name)
                        }
                    }.onFailure {
                        reportException(it)
                    }
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
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
            results.forEach(callback)
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (_: Exception) {
            true
        }

        if (!isNetworkAvailable) return

        val collected = mutableListOf<LyricsResult>()
        val providers = orderedProviders()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        currentLyricsJob =
            scope.launch {
                for (provider in providers) {
                    if (!provider.isEnabled(context)) continue

                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            if (!isMeaningfulLyrics(lyrics)) return@getAllLyrics
                            val result = LyricsResult(provider.name, lyrics)
                            synchronized(collected) {
                                collected += result
                            }
                            callback(result)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }

                if (collected.isNotEmpty()) {
                    cache.put(cacheKey, collected)
                }
            }

        currentLyricsJob?.join()
        scope.cancel()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    private fun isMeaningfulLyrics(lyrics: String?): Boolean {
        val normalized =
            lyrics
                ?.replace("\uFEFF", "")
                ?.replace(INVISIBLE_CHARS_REGEX, "")
                ?.trim { it.isWhitespace() || it == '\u00A0' }
                .orEmpty()

        if (normalized.isEmpty()) return false
        if (normalized == LYRICS_NOT_FOUND) return false

        val remaining =
            TIMESTAMP_REGEX
                .replace(normalized, "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == '\u00A0' }

        return remaining.any { !it.isWhitespace() && it != '\u00A0' }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
        private val TIMESTAMP_REGEX = Regex("""\[[0-9]{1,2}:[0-9]{2}(?:\.[0-9]{1,3})?]""")
        private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
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
