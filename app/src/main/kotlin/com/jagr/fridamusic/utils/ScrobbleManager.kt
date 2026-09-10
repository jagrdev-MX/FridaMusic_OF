package com.jagr.fridamusic.utils

import android.os.SystemClock
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.utils.lastfm.LastFM
import com.jagr.fridamusic.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class ScrobbleManager(
    private val scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 50
) {
    var useNowPlaying = true
    var enableScrobbling = false
    var useSendLikes = false

    private var currentMetadata: MediaMetadata? = null
    private var duration: Long? = null
    private var isPlaying = false
    
    private var playedDurationMs = 0L
    private var resumeStartedAtMs: Long? = null
    private var scrobbled = false
    private var trackJob: Job? = null
    private var scrobblingJob: Job? = null

    fun destroy() {
        trackJob?.cancel()
        scrobblingJob?.cancel()
    }

    fun onSongStart(metadata: MediaMetadata?, duration: Long? = null) {
        if (metadata?.id == currentMetadata?.id) return
        
        stopTracking()
        
        currentMetadata = metadata
        this.duration = duration
        playedDurationMs = 0L
        resumeStartedAtMs = null
        scrobbled = false

        if (!enableScrobbling || metadata == null || metadata.id.isLocalMediaId()) return
        
        if (useNowPlaying && LastFM.isInitialized()) {
            scrobblingJob?.cancel()
            scrobblingJob = scope.launch {
                try {
                    val artists = metadata.artists.joinToString(", ") { it.name }
                    LastFM.updateNowPlaying(
                        artist = artists.ifEmpty { "Unknown Artist" },
                        track = metadata.title,
                        album = metadata.album?.title,
                        duration = duration?.let { (it / 1000).toInt() }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Last.fm updateNowPlaying failed")
                }
            }
        }
    }

    fun onSongResume(metadata: MediaMetadata) {
        onPlayerStateChanged(true, metadata, duration)
    }

    fun onSongPause() {
        onPlayerStateChanged(false, currentMetadata, duration)
    }

    fun onPlayerStateChanged(isPlaying: Boolean, metadata: MediaMetadata?, duration: Long? = null) {
        this.isPlaying = isPlaying
        
        if (metadata != null && metadata.id != currentMetadata?.id) {
             onSongStart(metadata, duration)
        }
        
        if (isPlaying) {
            startTracking()
        } else {
            stopTracking()
        }
    }

    private fun startTracking() {
        if (scrobbled || currentMetadata == null || !enableScrobbling) return
        if (trackJob?.isActive == true) return

        val dur = duration?.let { it / 1000 } ?: return
        if (dur < minSongDuration && dur > 0) return

        val actualDuration = if (dur > 0) dur else FALLBACK_DURATION_SECONDS
        val percentThreshold = (actualDuration * scrobbleDelayPercent).toInt()
        val thresholdSeconds = minOf(percentThreshold, scrobbleDelaySeconds)
        val remainingMs = (thresholdSeconds * 1_000L - playedDurationMs).coerceAtLeast(0L)

        resumeStartedAtMs = SystemClock.elapsedRealtime()
        trackJob = scope.launch {
            delay(remainingMs)
            accumulatePlayedDuration()
            trackJob = null
            checkScrobbleThreshold()
        }
    }
    
    private fun stopTracking() {
        accumulatePlayedDuration()
        trackJob?.cancel()
        trackJob = null
    }

    fun onSongStop() {
        stopTracking()
    }

    private fun checkScrobbleThreshold() {
        val metadata = currentMetadata ?: return
        val dur = duration?.let { it / 1000 } ?: return

        if (scrobbled) return
        if (dur < minSongDuration && dur > 0) return

        val actualDuration = if (dur > 0) dur else FALLBACK_DURATION_SECONDS
        val percentThreshold = (actualDuration * scrobbleDelayPercent).toInt()
        val thresholdMs = minOf(percentThreshold, scrobbleDelaySeconds) * 1_000L

        if (playedDurationMs >= thresholdMs) {
            scrobbled = true
            doScrobble(metadata, actualDuration)
        } else if (isPlaying) {
            startTracking()
        }
    }

    private fun accumulatePlayedDuration() {
        resumeStartedAtMs?.let { startedAtMs ->
            playedDurationMs += (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            resumeStartedAtMs = null
        }
    }
    
    private fun doScrobble(metadata: MediaMetadata, durationInSeconds: Long) {
        val artists = metadata.artists.joinToString(", ") { it.name }
        if (artists.isEmpty() || metadata.id.isLocalMediaId() || !LastFM.isInitialized()) return
        
        val timestamp = System.currentTimeMillis() / 1000 - playedDurationMs / 1000
        
        scrobblingJob?.cancel()
        scrobblingJob = scope.launch {
            try {
                LastFM.scrobble(
                    artist = artists,
                    track = metadata.title,
                    timestamp = timestamp,
                    album = metadata.album?.title,
                    duration = durationInSeconds.toInt()
                )
                Timber.d("Successfully scrobbled ${metadata.title}")
            } catch (e: Exception) {
                Timber.e(e, "Last.fm scrobble failed")
            }
        }
    }

    private companion object {
        const val FALLBACK_DURATION_SECONDS = 300L
    }
}
