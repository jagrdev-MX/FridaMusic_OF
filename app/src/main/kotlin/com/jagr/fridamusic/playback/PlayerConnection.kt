

package com.jagr.fridamusic.playback

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.extensions.currentMetadata
import com.jagr.fridamusic.extensions.getCurrentQueueIndex
import com.jagr.fridamusic.extensions.getQueueWindows
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.extensions.togglePlayPause
import com.jagr.fridamusic.playback.MusicService.MusicBinder
import com.jagr.fridamusic.playback.queues.Queue
import com.jagr.fridamusic.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import com.jagr.fridamusic.constants.SponsorBlockEnabledKey
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.data.SponsorBlockRepository
import com.jagr.fridamusic.models.SponsorBlockSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    val context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    val scope: CoroutineScope,
) : Player.Listener {
    private companion object {
        private const val TAG = "PlayerConnection"
        private const val PLAYER_INIT_TIMEOUT_MS = 5000L 
    }

    val service = binder.service
    private val playerReadinessFlow = service.isPlayerReady
    val isCasting = service.castConnectionHandler?.isCasting ?: MutableStateFlow(false)
    val castDeviceName = service.castConnectionHandler?.castDeviceName ?: MutableStateFlow(null)
    
    
    private fun getPlayerSafe(): ExoPlayer {
        return try {
            if (!playerReadinessFlow.value) {
                Timber.tag(TAG).w("Player accessed before service initialization complete; returning best-effort reference")
            }
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            Timber.tag(TAG).e(e, "Fatal: player property accessed but not initialized")
            throw IllegalStateException("MusicService.player not initialized; possible race condition in service startup", e)
        }
    }

    
    val player: ExoPlayer
        get() = getPlayerSafe()

    
    private val isPlayerInitialized = MutableStateFlow(service.isPlayerReady.value)

    val playbackState: MutableStateFlow<Int>
    private val playWhenReady: MutableStateFlow<Boolean>
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>
    
    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")
        
        
        val initialState = try {
            val initialPlayer = getPlayerSafe()
            Triple(
                initialPlayer.playbackState,
                initialPlayer.playWhenReady,
                initialPlayer.playWhenReady && initialPlayer.playbackState == Player.STATE_READY,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection initialization, using defaults")
            Triple(Player.STATE_IDLE, false, false)
        }
        
        playbackState = MutableStateFlow(initialState.first)
        playWhenReady = MutableStateFlow(initialState.second)
        isPlaying = combine(playbackState, playWhenReady) { state, ready ->
            ready && state == Player.STATE_READY
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            initialState.third
        )
        
        
        scope.launch {
            playerReadinessFlow.collect { ready ->
                isPlayerInitialized.value = ready
                if (ready) {
                    Timber.tag(TAG).d("Service player initialization detected by PlayerConnection")
                }
            }
        }
        
        Timber.tag(TAG).d("PlayerConnection state flows initialized successfully")
    }
    
    
    val isEffectivelyPlaying = combine(
        isPlaying,
        isCasting,
        service.castConnectionHandler?.castIsPlaying ?: MutableStateFlow(false)
    ) { localPlaying, isCasting, castPlaying ->
        if (isCasting) castPlaying else localPlaying
    }.stateIn(
        scope,
        SharingStarted.Lazily,
        player.playbackState != STATE_ENDED && player.playWhenReady
    )
    
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    /** Playback clock for consumers that need sub-second synchronization, such as karaoke lyrics. */
    val playbackPositionMs = MutableStateFlow(player.currentPosition)
    val playbackDurationMs = MutableStateFlow(player.validDurationMs())
    val liveAudioFormat = MutableStateFlow<Format?>(player.audioFormat)
    val isMuted = service.isMuted

    val waitingForNetworkConnection = service.waitingForNetworkConnection
    val isCrossfading: kotlinx.coroutines.flow.StateFlow<Boolean> = service.isCrossfading

    
    var shouldBlockPlaybackChanges: (() -> Boolean)? = null
    
    
    @Volatile
    var allowInternalSync: Boolean = false

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null
    
    private val sponsorBlockRepository = SponsorBlockRepository()
    private var sponsorBlockJob: Job? = null
    private val sponsorBlockSegments = MutableStateFlow<List<SponsorBlockSegment>>(emptyList())

    init {
        try {
            scope.launch {
                while (isActive) {
                    val currentPlayer = player
                    updatePlaybackClock(currentPlayer)
                    val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                    val delayMs = when {
                        isForeground && currentPlayer.isPlaying -> 50L
                        isForeground -> 250L
                        currentPlayer.isPlaying -> 1_000L
                        else -> 2_000L
                    }
                    delay(delayMs)
                }
            }

            
            scope.launch {
                service.playerFlow.collect { newPlayer ->
                    if (newPlayer != null && newPlayer != attachedPlayer) {
                        updateAttachedPlayer(newPlayer)
                    }
                }
            }
            
            
            if (attachedPlayer == null && service.isPlayerReady.value) {
                 updateAttachedPlayer(player)
            }

            Timber.tag(TAG).d("PlayerConnection flow observer registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize PlayerConnection listener or state")
            
            throw e
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)
        
        
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        mediaMetadata.value = newPlayer.currentMetadata
        updatePlaybackClock(newPlayer)
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        liveAudioFormat.value = (newPlayer as? ExoPlayer)?.audioFormat
        
        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
        
        startSponsorBlockPolling()
    }

    fun playQueue(queue: Queue) {
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        try {
            service.playQueue(queue)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    fun startRadioSeamlessly() {
        
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("startRadioSeamlessly blocked - Listen Together guest")
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("playNext blocked - Listen Together guest")
            return
        }
        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("addToQueue blocked - Listen Together guest")
            return
        }
        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
    }

    @UnstableApi
    fun moveQueueItem(
        fromWindow: Timeline.Window,
        toWindow: Timeline.Window,
    ): Boolean {
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag(TAG).d("moveQueueItem blocked - Listen Together guest")
            return false
        }

        val queuePlayer = player
        if (!queuePlayer.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return false

        val timeline = queuePlayer.currentTimeline
        if (timeline.isEmpty || timeline.windowCount != queuePlayer.mediaItemCount) return false

        fun windowIndexOf(uid: Any): Int {
            val window = Timeline.Window()
            for (index in 0 until timeline.windowCount) {
                if (timeline.getWindow(index, window).uid == uid) return index
            }
            return C.INDEX_UNSET
        }

        val fromIndex = windowIndexOf(fromWindow.uid)
        val toIndex = windowIndexOf(toWindow.uid)
        if (fromIndex == C.INDEX_UNSET || toIndex == C.INDEX_UNSET || fromIndex == toIndex) {
            return false
        }

        if (!queuePlayer.shuffleModeEnabled) {
            queuePlayer.moveMediaItem(fromIndex, toIndex)
            return true
        }

        val playbackOrder = queuePlayer.getQueueWindows().toMutableList()
        val fromOrderIndex = playbackOrder.indexOfFirst { it.uid == fromWindow.uid }
        val toOrderIndex = playbackOrder.indexOfFirst { it.uid == toWindow.uid }
        if (fromOrderIndex == -1 || toOrderIndex == -1 || fromOrderIndex == toOrderIndex) {
            return false
        }

        playbackOrder.add(toOrderIndex, playbackOrder.removeAt(fromOrderIndex))
        val reorderedWindowIndices = IntArray(playbackOrder.size)
        val seenWindowIndices = BooleanArray(timeline.windowCount)
        playbackOrder.forEachIndexed { orderIndex, window ->
            val windowIndex = windowIndexOf(window.uid)
            if (windowIndex == C.INDEX_UNSET || seenWindowIndices[windowIndex]) return false
            seenWindowIndices[windowIndex] = true
            reorderedWindowIndices[orderIndex] = windowIndex
        }
        if (reorderedWindowIndices.size != timeline.windowCount) return false

        queuePlayer.setShuffleOrder(
            DefaultShuffleOrder(reorderedWindowIndices, System.currentTimeMillis()),
        )
        return true
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    
    fun togglePlayPause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                if (castHandler.castIsPlaying.value) {
                    castHandler.pause()
                } else {
                    castHandler.play()
                }
            } else {
                player.togglePlayPause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }
    
    
    fun play() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.play()
            } else {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }
    
    
    fun pause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.pause()
            } else {
                player.playWhenReady = false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    
    fun seekTo(position: Long) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.seekTo(position)
            } else {
                player.seekTo(position)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    fun seekToNext() {
        try {
            
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToNext()
                return
            }
            player.seekToNext()
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        try {
            
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }

            
            
            if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onRestartSong?.invoke()
            } else {
                
                player.seekToPreviousMediaItem()
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onSkipPrevious?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        updatePlaybackClock(player)
        if (state == Player.STATE_READY) {
            liveAudioFormat.value = player.audioFormat
        }
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        liveAudioFormat.value = null
        updatePlaybackClock(player)
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        updatePlaybackClock(player)
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTracksChanged(tracks: Tracks) {
        liveAudioFormat.value = player.audioFormat
    }

    private fun updatePlaybackClock(currentPlayer: Player) {
        val castHandler = service.castConnectionHandler
        if (castHandler?.isCasting?.value == true) {
            playbackPositionMs.value = castHandler.castPosition.value
            playbackDurationMs.value = castHandler.castDuration.value
                .takeIf { it > 0L } ?: C.TIME_UNSET
        } else {
            playbackPositionMs.value = currentPlayer.currentPosition
            playbackDurationMs.value = currentPlayer.validDurationMs()
        }
    }

    private fun Player.validDurationMs(): Long =
        duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: C.TIME_UNSET

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty && player.currentMediaItemIndex != androidx.media3.common.C.INDEX_UNSET && player.currentMediaItemIndex >= 0 && player.currentMediaItemIndex < player.currentTimeline.windowCount) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    private fun startSponsorBlockPolling() {
        sponsorBlockJob?.cancel()
        sponsorBlockJob = scope.launch {
            val sponsorBlockEnabledFlow = context.dataStore.data.map { it[SponsorBlockEnabledKey] ?: false }

            launch {
                combine(mediaMetadata, sponsorBlockEnabledFlow) { metadata, enabled ->
                    Pair(metadata, enabled)
                }.collect { (metadata, enabled) ->
                    if (enabled && metadata != null) {
                        val videoId = metadata.id
                        sponsorBlockSegments.value = sponsorBlockRepository.getSkipSegments(videoId)
                    } else {
                        sponsorBlockSegments.value = emptyList()
                    }
                }
            }

            launch {
                while (true) {
                    val currentSegments = sponsorBlockSegments.value
                    if (player.isPlaying) {
                        if (currentSegments.isNotEmpty()) {
                            val currentPosSec = player.currentPosition / 1000f
                            for (segment in currentSegments) {
                                if (currentPosSec >= segment.segment[0] && currentPosSec < segment.segment[1]) {
                                    Timber.d("SponsorBlock: skipping segment ${segment.category} from ${segment.segment[0]} to ${segment.segment[1]}")
                                    player.seekTo((segment.segment[1] * 1000).toLong())
                                    break
                                }
                            }
                        }
                    }
                    val delayMs = if (!player.isPlaying) {
                        1_500L
                    } else {
                        val currentPosSec = player.currentPosition / 1000f
                        val secondsUntilNextSegment = currentSegments
                            .asSequence()
                            .map { it.segment[0] - currentPosSec }
                            .filter { it >= 0f }
                            .minOrNull()
                        if (secondsUntilNextSegment != null && secondsUntilNextSegment <= 5f) 500L else 1_000L
                    }
                    delay(delayMs)
                }
            }
        }
    }

    fun dispose() {
        try {
            sponsorBlockJob?.cancel()
            attachedPlayer?.removeListener(this)
            attachedPlayer = null
            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}
