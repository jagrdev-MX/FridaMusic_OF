package com.jagr.fridamusic.discord

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import com.jagr.fridamusic.BuildConfig
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.utils.DiscordImageResolver
import com.jagr.fridamusic.utils.isLocalMediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object DiscordPresenceManager {
    private const val TAG = "DiscordPresenceManager"
    private const val UPDATE_DEBOUNCE_MS = 400L
    private const val SEEK_DEBOUNCE_MS = 750L
    private const val FRIDAMUSIC_URL = "https://github.com/jagrdev-MX/FridaMusic_OF"

    private val started = AtomicBoolean(false)
    private val debugProbeAttempted = AtomicBoolean(false)
    private val updateGeneration = AtomicLong(0L)
    private val nativeMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var scope: CoroutineScope? = null
    private var refreshJob: Job? = null
    private var applicationContext: Context? = null
    private var songProvider: (suspend () -> Song?)? = null
    private var positionProvider: (() -> Long)? = null
    private var durationProvider: (() -> Long)? = null
    private var playbackSpeedProvider: (() -> Float)? = null
    private var pausedProvider: (() -> Boolean)? = null
    private var lastUpdateFailed = false

    @Volatile
    private var debugProbeVisibleUntilMs = 0L

    private val lastRpcStartTimeState = MutableStateFlow<Long?>(null)
    val lastRpcStartTimeFlow = lastRpcStartTimeState.asStateFlow()
    val lastRpcStartTime: Long? get() = lastRpcStartTimeState.value

    private val lastRpcEndTimeState = MutableStateFlow<Long?>(null)
    val lastRpcEndTimeFlow = lastRpcEndTimeState.asStateFlow()
    val lastRpcEndTime: Long? get() = lastRpcEndTimeState.value

    fun start(
        context: Context,
        songProvider: suspend () -> Song?,
        positionProvider: () -> Long,
        durationProvider: () -> Long,
        playbackSpeedProvider: () -> Float,
        isPausedProvider: () -> Boolean,
    ) {
        applicationContext = context.applicationContext
        this.songProvider = songProvider
        this.positionProvider = positionProvider
        this.durationProvider = durationProvider
        this.playbackSpeedProvider = playbackSpeedProvider
        pausedProvider = isPausedProvider

        if (!started.getAndSet(true)) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            debugLog("start")
        }
        requestUpdate("start", immediate = true)
    }

    fun restart(reason: String = "event"): Boolean = requestUpdate(reason, immediate = false)

    fun refreshAfterSeek(): Boolean = requestUpdate("seek", immediate = false, SEEK_DEBOUNCE_MS)

    fun clear() {
        val generation = updateGeneration.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = null
        setLastRpcTimestamps(null, null)
        cleanupScope.launch {
            nativeMutex.withLock {
                if (generation == updateGeneration.get()) DiscordSocialSdkBridge.clearPresence()
            }
        }
    }

    fun stop() {
        started.set(false)
        val generation = updateGeneration.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = null
        scope?.cancel()
        scope = null
        applicationContext = null
        songProvider = null
        positionProvider = null
        durationProvider = null
        playbackSpeedProvider = null
        pausedProvider = null
        lastUpdateFailed = false
        debugProbeAttempted.set(false)
        debugProbeVisibleUntilMs = 0L
        setLastRpcTimestamps(null, null)

        cleanupScope.launch {
            nativeMutex.withLock {
                if (!started.get() && generation == updateGeneration.get()) {
                    DiscordSocialSdkBridge.shutdown()
                }
            }
        }
        debugLog("clear")
    }

    fun isRunning(): Boolean = started.get()

    private fun requestUpdate(
        reason: String,
        immediate: Boolean,
        debounceMs: Long = UPDATE_DEBOUNCE_MS,
    ): Boolean {
        val context = applicationContext ?: return false
        val getSong = songProvider ?: return false
        val getPosition = positionProvider ?: return false
        val getDuration = durationProvider ?: return false
        val getPlaybackSpeed = playbackSpeedProvider ?: return false
        val isPaused = pausedProvider ?: return false
        val activeScope = scope ?: return false

        val generation = updateGeneration.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = activeScope.launch {
            try {
                if (!immediate) delay(debounceMs)
                val playback = withContext(Dispatchers.Main.immediate) {
                    PlaybackSnapshot(
                        positionMs = getPosition().coerceAtLeast(0L),
                        durationMs = getDuration(),
                        playbackSpeed = getPlaybackSpeed().takeIf { it.isFinite() && it > 0f } ?: 1f,
                        paused = isPaused(),
                    )
                }
                val song = getSong()
                if (generation != updateGeneration.get()) return@launch
                if (song == null) {
                    clear()
                    return@launch
                }

                val presence = buildPresence(context, song, playback)
                nativeMutex.withLock {
                    if (generation != updateGeneration.get()) return@withLock
                    if (BuildConfig.DEBUG && debugProbeAttempted.compareAndSet(false, true)) {
                        if (DiscordSocialSdkBridge.publishDebugTestPresence(context)) {
                            debugProbeVisibleUntilMs = SystemClock.elapsedRealtime() + 2_500L
                        } else {
                            debugProbeAttempted.set(false)
                        }
                    }
                    val diagnosticDelayMs = debugProbeVisibleUntilMs - SystemClock.elapsedRealtime()
                    if (diagnosticDelayMs > 0L) delay(diagnosticDelayMs)
                    if (generation != updateGeneration.get()) return@withLock
                    if (lastUpdateFailed) debugLog("reconnect requested")
                    debugLog("Discord Presence publishing track=${presence.title} reason=$reason")
                    val accepted = DiscordSocialSdkBridge.updatePresence(context, presence)
                    lastUpdateFailed = !accepted
                    if (accepted) {
                        updateLastTimestamps(playback)
                    } else {
                        setLastRpcTimestamps(null, null)
                    }
                    debugLog(if (accepted) "update requested ($reason)" else "update failed ($reason)")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastUpdateFailed = true
                debugLog("update failed ($reason)", error)
            }
        }
        return true
    }

    private suspend fun buildPresence(
        context: Context,
        song: Song,
        playback: PlaybackSnapshot,
    ): DiscordRichPresence {
        val title = song.song.title.trim().ifBlank { "FridaMusic" }.take(128)
        val artist = song.artists.joinToString { it.name }.trim().ifBlank { "Unknown artist" }.take(120)
        val album = (song.song.albumName ?: song.album?.title)?.trim()?.takeIf { it.isNotBlank() }?.take(128)
        val artwork = runCatching {
            DiscordImageResolver.resolveImagesForSong(context, song).thumbnailResolvedId
        }.getOrNull()
        val detailsUrl = song.song.id
            .takeUnless { song.song.isLocal || it.isLocalMediaId() }
            ?.let { "https://music.youtube.com/watch?v=$it" }
        val timestamps = calculateTimestamps(song, playback)

        return DiscordRichPresence(
            title = title,
            state = if (playback.paused) "Paused · $artist" else "de $artist",
            album = album,
            artworkUrl = artwork,
            detailsUrl = detailsUrl,
            startTimeSeconds = timestamps.first,
            endTimeSeconds = timestamps.second,
            paused = playback.paused,
            buttonLabel = "FridaMusic",
            buttonUrl = FRIDAMUSIC_URL,
        )
    }

    private fun calculateTimestamps(song: Song, playback: PlaybackSnapshot): Pair<Long?, Long?> {
        if (playback.paused) return null to null
        val durationMs = playback.durationMs
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: song.song.duration.takeIf { it > 0 }?.times(1_000L)
            ?: return null to null
        val nowSeconds = System.currentTimeMillis() / 1_000L
        val elapsedWallSeconds = (playback.positionMs / playback.playbackSpeed / 1_000f).toLong()
        val durationWallSeconds = (durationMs / playback.playbackSpeed / 1_000f).toLong()
        val startSeconds = nowSeconds - elapsedWallSeconds
        return startSeconds to (startSeconds + durationWallSeconds)
    }

    private fun updateLastTimestamps(playback: PlaybackSnapshot) {
        if (playback.paused || playback.durationMs == C.TIME_UNSET || playback.durationMs <= 0L) {
            setLastRpcTimestamps(null, null)
            return
        }
        val startMs = System.currentTimeMillis() - (playback.positionMs / playback.playbackSpeed).toLong()
        val remainingWallMs = ((playback.durationMs - playback.positionMs).coerceAtLeast(0L) / playback.playbackSpeed).toLong()
        setLastRpcTimestamps(startMs, System.currentTimeMillis() + remainingWallMs)
    }

    private fun setLastRpcTimestamps(start: Long?, end: Long?) {
        lastRpcStartTimeState.value = start
        lastRpcEndTimeState.value = end
    }

    private fun debugLog(message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (error == null) Timber.tag(TAG).d(message) else Timber.tag(TAG).d(error, message)
    }

    private data class PlaybackSnapshot(
        val positionMs: Long,
        val durationMs: Long,
        val playbackSpeed: Float,
        val paused: Boolean,
    )
}
