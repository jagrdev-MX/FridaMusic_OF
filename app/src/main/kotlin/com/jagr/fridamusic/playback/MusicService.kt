@file:Suppress("DEPRECATION")

package com.jagr.fridamusic.playback


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.jagr.fridamusic.MainActivity
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.AudioNormalizationKey
import com.jagr.fridamusic.constants.AudioOffload
import com.jagr.fridamusic.constants.AudioQuality
import com.jagr.fridamusic.constants.AudioQualityKey
import com.jagr.fridamusic.constants.effectiveForPlayback
import com.jagr.fridamusic.constants.AutoDownloadOnLikeKey
import com.jagr.fridamusic.constants.AutoLoadMoreKey
import com.jagr.fridamusic.constants.AutoSkipNextOnErrorKey
import com.jagr.fridamusic.constants.CrossfadeDurationKey
import com.jagr.fridamusic.constants.CrossfadeEnabledKey
import com.jagr.fridamusic.constants.CrossfadeGaplessKey
import com.jagr.fridamusic.constants.DisableLoadMoreWhenRepeatAllKey
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.jagr.fridamusic.constants.EnableLastFMScrobblingKey
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.constants.HistoryDuration
import com.jagr.fridamusic.constants.LastFMSessionKey
import com.jagr.fridamusic.constants.LastFMUseNowPlaying
import com.jagr.fridamusic.constants.LastFMUseSendLikes
import com.jagr.fridamusic.constants.MediaSessionConstants.CommandToggleLike
import com.jagr.fridamusic.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.jagr.fridamusic.constants.MediaSessionConstants.CommandToggleShuffle
import com.jagr.fridamusic.constants.MediaSessionConstants.CommandToggleStartRadio
import com.jagr.fridamusic.constants.PauseListenHistoryKey
import com.jagr.fridamusic.constants.PauseOnMute
import com.jagr.fridamusic.constants.PersistentQueueKey
import com.jagr.fridamusic.constants.PersistentShuffleAcrossQueuesKey
import com.jagr.fridamusic.constants.PlayerVolumeKey
import com.jagr.fridamusic.constants.RememberShuffleAndRepeatKey
import com.jagr.fridamusic.constants.RepeatModeKey
import com.jagr.fridamusic.constants.ResumeOnBluetoothConnectKey
import com.jagr.fridamusic.constants.ScrobbleDelayPercentKey
import com.jagr.fridamusic.constants.ScrobbleDelaySecondsKey
import com.jagr.fridamusic.constants.ScrobbleMinSongDurationKey
import com.jagr.fridamusic.constants.ShowLyricsKey
import com.jagr.fridamusic.constants.StopMusicOnTaskClearKey
import com.jagr.fridamusic.constants.ShuffleModeKey
import com.jagr.fridamusic.constants.ShufflePlaylistFirstKey
import com.jagr.fridamusic.constants.PreventDuplicateTracksInQueueKey
import com.jagr.fridamusic.constants.SimilarContent
import com.jagr.fridamusic.constants.SkipSilenceInstantKey
import com.jagr.fridamusic.constants.SkipSilenceKey
import com.jagr.fridamusic.constants.IpVersionKey
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.Locale
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.Event
import com.jagr.fridamusic.db.entities.FormatEntity
import com.jagr.fridamusic.db.entities.LyricsEntity
import com.jagr.fridamusic.db.entities.RelatedSongMap
import com.jagr.fridamusic.di.DownloadCache
import com.jagr.fridamusic.di.PlayerCache
import com.jagr.fridamusic.eq.EqualizerService
import com.jagr.fridamusic.eq.audio.CustomEqualizerAudioProcessor
import com.jagr.fridamusic.eq.data.EQProfileRepository
import com.jagr.fridamusic.extensions.SilentHandler
import com.jagr.fridamusic.extensions.collect
import com.jagr.fridamusic.extensions.collectLatest
import com.jagr.fridamusic.extensions.currentMetadata
import com.jagr.fridamusic.extensions.findNextMediaItemById
import com.jagr.fridamusic.extensions.mediaItems
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.extensions.setOffloadEnabled
import com.jagr.fridamusic.extensions.toEnum
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.extensions.toPersistQueue
import com.jagr.fridamusic.extensions.toQueue
import com.jagr.fridamusic.lyrics.LyricsHelper
import com.jagr.fridamusic.models.PersistPlayerState
import com.jagr.fridamusic.models.PersistQueue
import com.jagr.fridamusic.models.toMediaMetadata
import com.jagr.fridamusic.playback.audio.SilenceDetectorAudioProcessor
import com.jagr.fridamusic.playback.queues.EmptyQueue
import com.jagr.fridamusic.playback.queues.Queue
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.jagr.fridamusic.playback.queues.filterExplicit
import com.jagr.fridamusic.playback.queues.filterVideoSongs
import com.jagr.fridamusic.utils.CoilBitmapLoader
import com.jagr.fridamusic.utils.NetworkConnectivityObserver
import com.jagr.fridamusic.utils.ScrobbleManager
import com.jagr.fridamusic.utils.SyncUtils
import com.jagr.fridamusic.utils.StreamClientUtils
import com.jagr.fridamusic.utils.YTPlayerUtils
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.get
import com.jagr.fridamusic.utils.reportException
import com.jagr.fridamusic.widget.EchoMusicWidgetManager
import com.jagr.fridamusic.widget.MusicWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import com.jagr.fridamusic.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L

private data class AudioOutputRouteKey(
    val transportFamily: String,
    val stableIdentity: String,
    val normalizedName: String,
)

private fun AudioDeviceInfo.audioOutputRouteKey(): AudioOutputRouteKey {
    val normalizedName = productName.toString()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
    val stableBluetoothAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        address.trim().lowercase(Locale.ROOT).takeUnless {
            it.isBlank() || it == "0" || it == "unknown" || it == "00:00:00:00:00:00"
        }
    } else {
        null
    }
    return if (isBluetoothAudioOutput()) {
        AudioOutputRouteKey(
            transportFamily = "bluetooth",
            stableIdentity = stableBluetoothAddress ?: "route:$type:$id",
            normalizedName = normalizedName,
        )
    } else {
        AudioOutputRouteKey(
            transportFamily = audioOutputTransportFamily(),
            stableIdentity = "route:$id",
            normalizedName = normalizedName,
        )
    }
}

private fun AudioDeviceInfo.isBluetoothAudioOutput(): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID -> true
    else -> false
}

private fun AudioDeviceInfo.audioOutputTransportFamily(): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi"
    else -> "other:$type"
}

private fun AudioDeviceInfo.mediaRoutePreference(): Int = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST -> 1
    AudioDeviceInfo.TYPE_HEARING_AID -> 2
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 3
    else -> 0
}

private fun AudioDeviceInfo.isUserSelectableMediaOutput(): Boolean =
    isBluetoothAudioOutput() || when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> true
        else -> false
    }

private fun List<AudioDeviceInfo>.canonicalAudioOutputDevices(): List<AudioDeviceInfo> =
    filter(AudioDeviceInfo::isUserSelectableMediaOutput)
        .groupBy { it.audioOutputRouteKey() }
        .values
        .map { equivalentRoutes ->
            equivalentRoutes.minWithOrNull(
                compareBy<AudioDeviceInfo> { it.mediaRoutePreference() }.thenBy { it.id },
            ) ?: equivalentRoutes.first()
        }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var eqProfileRepository: EQProfileRepository

    @Inject
    lateinit var widgetManager: EchoMusicWidgetManager

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var audioFocusRequestActive = false
    private var reentrantFocusGain = false
    private var audioFocusPausePending = false
    private val audioFocusVolumeMultiplier = MutableStateFlow(1f)
    private var audioFocusVolumeRampJob: Job? = null
    private var audioFocusResumeJob: Job? = null
    private var hasActiveVoicePlayback = false
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    var preferredDeviceId: Int? = null
        private set
    private var preferredAudioOutputRouteKey: AudioOutputRouteKey? = null
    val audioOutputDevices = MutableStateFlow<List<AudioDeviceInfo>>(emptyList())
    val selectedAudioOutputDeviceId = MutableStateFlow<Int?>(null)

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossfadeTriggerJob: Job? = null

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Secondary player error")
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            secondaryPlayer = null
        }
    }

    private var scope = CoroutineScope(Dispatchers.Main) + Job()

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private lateinit var audioQuality: com.jagr.fridamusic.constants.AudioQuality
    private lateinit var ipVersion: IpVersion

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.jagr.fridamusic.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState

        applyEffectivePlayerVolumes()
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted

        applyEffectivePlayerVolumes()
    }

    private fun applyEffectivePlayerVolumes(
        userVolume: Float = playerVolume.value,
        muted: Boolean = isMuted.value,
        focusMultiplier: Float = audioFocusVolumeMultiplier.value,
    ) {
        if (!::player.isInitialized || !::playerVolume.isInitialized) return

        val effectiveVolume = if (muted) {
            0f
        } else {
            userVolume.coerceIn(0f, 1f) * focusMultiplier.coerceIn(0f, 1f)
        }
        player.volume = (effectiveVolume * crossfadeInVolumeMultiplier).coerceIn(0f, 1f)
        fadingPlayer?.volume = (effectiveVolume * crossfadeOutVolumeMultiplier).coerceIn(0f, 1f)
    }

    fun setPreferredAudioDevice(deviceId: Int?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val deviceInfo = devices.find { it.id == deviceId }
            applyPreferredAudioDevice(deviceInfo)
        }
    }

    private fun refreshAudioOutputDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
            val canonicalDevices = devices.canonicalAudioOutputDevices()
            audioOutputDevices.value = canonicalDevices
            val selectedId = selectedAudioOutputDeviceId.value
            if (selectedId != null) {
                val selectedDevice = devices.find { it.id == selectedId }
                val selectedRouteKey = preferredAudioOutputRouteKey
                    ?: selectedDevice?.audioOutputRouteKey()
                val replacement = selectedRouteKey?.let { routeKey ->
                    canonicalDevices.find { it.audioOutputRouteKey() == routeKey }
                }
                when {
                    replacement == null -> applyPreferredAudioDevice(null)
                    replacement.id != selectedId -> applyPreferredAudioDevice(replacement)
                    else -> preferredAudioOutputRouteKey = selectedRouteKey
                }
            }
        }
    }

    private fun applyPreferredAudioDevice(deviceInfo: AudioDeviceInfo?) {
        player.setPreferredAudioDevice(deviceInfo)
        preferredDeviceId = deviceInfo?.id
        preferredAudioOutputRouteKey = deviceInfo?.audioOutputRouteKey()
        selectedAudioOutputDeviceId.value = deviceInfo?.id
    }


    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
        private set
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    val isCrossfading = MutableStateFlow(false)
    private var crossfadeJob: Job? = null
    private var crossfadeInVolumeMultiplier = 1f
    private var crossfadeOutVolumeMultiplier = 0f

    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            hasActiveVoicePlayback = configs.any(::isVoicePlayback)
            applyActiveVoiceDuckDuringFocusLossIfNeeded()
        }
    }

    private lateinit var mediaSession: MediaLibrarySession


    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()


    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()


    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var scrobbleManager: ScrobbleManager? = null

    private var listenBrainzEnabled = false
    private var listenBrainzToken = ""
    private var listenBrainzCurrentStartTs: Long = 0L
    private var listenBrainzCurrentMediaId: String? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())


    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null


    private val songUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val playbackDataCache = ConcurrentHashMap<String, YTPlayerUtils.PlaybackData>()
    private val streamResolutionMutexes = ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var activeQualityMediaId: String? = null

    @Volatile
    private var activeRequestedQuality = AudioQuality.OPUS


    private val bypassCacheForQualityChange = mutableSetOf<String>()


    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L


    private val recentlyFailedSongs = mutableSetOf<String>()
    private var failedSongsClearJob: Job? = null


    var castConnectionHandler: CastConnectionHandler? = null
        private set

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            refreshAudioOutputDevices()
            val hasBluetooth = addedDevices?.any(AudioDeviceInfo::isBluetoothAudioOutput) == true

            if (hasBluetooth) {
                if (dataStore.get(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            refreshAudioOutputDevices()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true


        // Workaround for ForegroundServiceStartNotAllowedException
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Timber.tag(TAG).e("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener")
                reportException(Exception("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener"))
            }
        })

        playerInitialized.value = false

        scrobbleManager = ScrobbleManager(scope)

        scope.launch {
            dataStore.data.map { it[EnableLastFMScrobblingKey] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.enableScrobbling = it
            }
        }
        scope.launch {
            dataStore.data.map { it[LastFMUseNowPlaying] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.useNowPlaying = it
            }
        }
        scope.launch {
            dataStore.data.map { it[LastFMUseSendLikes] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.useSendLikes = it
            }
        }
        scope.launch {
            dataStore.data.map { it[LastFMSessionKey] }.distinctUntilChanged().collect { sessionKey ->
                com.jagr.fridamusic.utils.lastfm.LastFM.sessionKey = sessionKey
            }
        }


        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.music_player),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            val pending = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.music_player))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create foreground notification")
            reportException(e)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.ic_stat_name)
                },
        )
        player = createExoPlayer()
        player.addListener(this@MusicService)
        sleepTimer = SleepTimer(scope, player)
        player.addListener(sleepTimer)
        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        abandonAudioFocus()
        setupAudioFocusRequest()
        audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, null)

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
            onUserPauseOrStop = ::cancelAudioFocusAutoResumeForUserAction
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)


        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            player.shuffleModeEnabled = dataStore.get(ShuffleModeKey, false)
        }


        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        refreshAudioOutputDevices()

        audioQuality = dataStore.get(AudioQualityKey).toEnum(com.jagr.fridamusic.constants.AudioQuality.OPUS)
        ipVersion = dataStore.get(IpVersionKey).toEnum(IpVersion.AUTO)
        playerVolume = MutableStateFlow(
            dataStore.get(PlayerVolumeKey, DEFAULT_PLAYER_VOLUME).audibleVolumeOrDefault(),
        )


        initializeCast()


        scope.launch {
            eqProfileRepository.activeProfile.collect { profile ->
                if (profile != null) {
                    val result = equalizerService.applyProfile(profile)
                    if (result.isSuccess && player.playbackState == Player.STATE_READY && player.isPlaying) {



                        player.seekTo(player.currentPosition)
                    }
                } else {
                    equalizerService.disable()
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        player.seekTo(player.currentPosition)
                    }
                }
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    triggerRetry()
                }

            }
        }


        scope.launch {
            dataStore.data
                .map { it[com.jagr.fridamusic.constants.ListenBrainzEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { listenBrainzEnabled = it }
        }

        scope.launch {
            dataStore.data
                .map { it[com.jagr.fridamusic.constants.ListenBrainzTokenKey] ?: "" }
                .distinctUntilChanged()
                .collect { listenBrainzToken = it }
        }

        var isFirstQualityEmit = true
        scope.launch {
            dataStore.data
                .map { it[AudioQualityKey]?.let { value ->
                    com.jagr.fridamusic.constants.AudioQuality.entries.find { it.name == value }
                } ?: com.jagr.fridamusic.constants.AudioQuality.OPUS }
                .distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQuality
                    audioQuality = newQuality


                    if (isFirstQualityEmit) {
                        isFirstQualityEmit = false
                        Timber.tag("MusicService").i("QUALITY INIT: $newQuality")
                        return@collect
                    }

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality")

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality. Will take effect starting from the next song.")

                    // Quality-specific keys keep existing URLs isolated; prefetch the next songs
                    // under the newly selected quality without clearing unrelated cache entries.
                    preloadUpcomingItems()
                }
        }


        scope.launch {
            dataStore.data
                .map { it[IpVersionKey]?.toEnum(IpVersion.AUTO) ?: IpVersion.AUTO }
                .distinctUntilChanged()
                .collect { newIpVersion ->
                    val oldIpVersion = ipVersion
                    ipVersion = newIpVersion

                    if (isFirstQualityEmit) return@collect

                    Timber.tag("MusicService").i("IP VERSION CHANGED: $oldIpVersion -> $newIpVersion")


                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val currentIndex = player.currentMediaItemIndex
                    val wasPlaying = player.isPlaying


                    val effectiveQuality = audioQuality.effectiveForPlayback()
                    songUrlCache.remove("${mediaId}_${effectiveQuality.name}")
                    playbackDataCache.remove("${mediaId}_${effectiveQuality.name}")


                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        combine(playerVolume, isMuted, audioFocusVolumeMultiplier) { volume, muted, focusMultiplier ->
            Triple(volume, muted, focusMultiplier)
        }.collectLatest(scope) { (volume, muted, focusMultiplier) ->
            applyEffectivePlayerVolumes(volume, muted, focusMultiplier)
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidgetUI(player.isPlaying)
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                val song = database.song(mediaMetadata.id).firstOrNull()?.song
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyricsWithProvider.lyrics,
                            provider = lyricsWithProvider.provider,
                        ),
                    )
                    if (lyricsWithProvider.autoOffsetMs != 0 && song?.lyricsOffset == 0) {
                        update(song.copy(lyricsOffset = lyricsWithProvider.autoOffsetMs))
                    }
                }
            }
        }

        dataStore.data
            .map { (it[SkipSilenceKey] ?: false) to (it[SkipSilenceInstantKey] ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (skipSilence, instantSkip) ->
                player.skipSilenceEnabled = skipSilence
                secondaryPlayer?.skipSilenceEnabled = skipSilence

                val enableInstant = skipSilence && instantSkip
                instantSilenceSkipEnabled.value = enableInstant

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) -> setupLoudnessEnhancer()}

        combine(
            dataStore.data.map { it[AudioOffload] ?: false },
            dataStore.data.map { it[CrossfadeEnabledKey] ?: false }
        ) { offloadPref, crossfadeEnabled ->

            if (crossfadeEnabled) false else offloadPref
        }.distinctUntilChanged()
            .collectLatest(scope) { useOffload ->
                player.setOffloadEnabled(useOffload)
                secondaryPlayer?.setOffloadEnabled(useOffload)
            }



        dataStore.data.map { prefs ->
            Triple(
                prefs[CrossfadeEnabledKey] ?: false,
                prefs[CrossfadeDurationKey] ?: 5f,
                prefs[CrossfadeGaplessKey] ?: true
            )
        }
            .distinctUntilChanged()
            .collect(scope) { (enabled, duration, gapless) ->
                crossfadeEnabled = enabled
                crossfadeDuration = duration * 1000f
                crossfadeGapless = gapless
            }


        if (dataStore.get(PersistentQueueKey, true)) {
            val queueFile = filesDir.resolve(PERSISTENT_QUEUE_FILE)
            if (queueFile.exists()) {
                runCatching {
                    queueFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {

                        val restoredQueue = queue.toQueue()

                        scope.launch {
                            playerInitialized.first { it }
                            if (isActive) {
                                playQueue(
                                    queue = restoredQueue,
                                    playWhenReady = false,
                                )
                            }
                        }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read persisted queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            val automixFile = filesDir.resolve(PERSISTENT_AUTOMIX_FILE)
            if (automixFile.exists()) {
                runCatching {
                    automixFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        automixItems.value = queue.items.map { it.toMediaItem() }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore automix queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read automix queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }


            val playerStateFile = filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE)
            if (playerStateFile.exists()) {
                runCatching {
                    playerStateFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistPlayerState
                        }
                    }
                }.onSuccess { playerState ->

                    scope.launch {
                        delay(1000)



                        // player.volume can be zero temporarily while muted, ducking or fading.
                        // Restore the stable application gain instead of reviving a silent session.
                        playerVolume.value = playerState.volume.audibleVolumeOrDefault()


                        if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                            player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                        }
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read player state, clearing data")
                    clearPersistedQueueFiles()
                }
            }
        }


        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }


        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (dataStore.get(PersistentQueueKey, true) && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun createExoPlayer(): ExoPlayer {
        val eqProcessor = CustomEqualizerAudioProcessor()
        equalizerService.addAudioProcessor(eqProcessor)

        val silenceProcessor = SilenceDetectorAudioProcessor { handleLongSilenceDetected() }


        runBlocking {
            val skipSilence = dataStore.get(SkipSilenceKey, false)
            val instantSkip = dataStore.get(SkipSilenceInstantKey, false)
            silenceProcessor.instantModeEnabled = skipSilence && instantSkip
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory(eqProcessor, silenceProcessor))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Seeking already opens a valid ranged stream; resume as soon as a small
                    // safety buffer is available instead of imposing a visible two-second wait.
                    .setBufferDurationsMs(50_000, 50_000, 750, 250)
                    .build()
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            .build()

        playerSilenceProcessors[player] = silenceProcessor

        player.apply {
            runBlocking {
                val offload = dataStore.get(AudioOffload, false)
                val crossfade = dataStore.get(CrossfadeEnabledKey, false)
                setOffloadEnabled(if (crossfade) false else offload)
                skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
            }
            addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))


        }
        _playerFlow.value = player
        return player
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                if (!audioFocusRequestActive) {
                    lastAudioFocusState = focusChange
                    Timber.tag(AUDIO_FOCUS_TAG).d("focus=GAIN action=ignore_stale")
                    return
                }
                hasAudioFocus = true
                lastAudioFocusState = focusChange

                if (wasPlayingBeforeAudioFocusLoss && !player.playWhenReady && !reentrantFocusGain) {
                    Timber.tag(AUDIO_FOCUS_TAG).d("focus=GAIN action=resume_and_restore")
                    prepareSafeAudioFocusResumeVolume()
                    reentrantFocusGain = true
                    audioFocusResumeJob?.cancel()
                    audioFocusResumeJob = scope.launch {
                        try {
                            delay(AUDIO_FOCUS_RESUME_DELAY_MS)
                            if (hasAudioFocus && wasPlayingBeforeAudioFocusLoss && !player.playWhenReady) {
                                wasPlayingBeforeAudioFocusLoss = false
                                if (castConnectionHandler?.isCasting?.value != true) {
                                    player.play()
                                }
                            }
                            if (hasAudioFocus) {
                                rampAudioFocusVolumeTo(1f, AUDIO_FOCUS_FADE_UP_MS)
                            }
                        } finally {
                            reentrantFocusGain = false
                        }
                    }
                } else if (!reentrantFocusGain) {
                    wasPlayingBeforeAudioFocusLoss = false
                    Timber.tag(AUDIO_FOCUS_TAG).d("focus=GAIN action=restore")
                    rampAudioFocusVolumeTo(1f, AUDIO_FOCUS_FADE_UP_MS)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                cancelAudioFocusTransitions()
                pauseForAudioFocusLoss(resumeOnGain = false)
                setAudioFocusVolumeMultiplier(1f)
                abandonAudioFocus()
                lastAudioFocusState = focusChange
                Timber.tag(AUDIO_FOCUS_TAG).d("focus=LOSS action=pause_no_resume")
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                cancelAudioFocusTransitions()
                pauseForAudioFocusLoss(resumeOnGain = true)
                prepareSafeAudioFocusResumeVolume()
                lastAudioFocusState = focusChange
                Timber.tag(AUDIO_FOCUS_TAG).d("focus=LOSS_TRANSIENT action=pause")
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                audioFocusResumeJob?.cancel()
                reentrantFocusGain = false
                lastAudioFocusState = focusChange
                if (hasActiveVoicePlayback) {
                    applyActiveVoiceDuckDuringFocusLossIfNeeded()
                } else {
                    rampAudioFocusVolumeTo(AUDIO_FOCUS_DUCK_MULTIPLIER, AUDIO_FOCUS_FADE_DOWN_MS)
                    Timber.tag(AUDIO_FOCUS_TAG).d("focus=LOSS_TRANSIENT_CAN_DUCK action=duck")
                }
            }
        }
    }

    private fun isVoicePlayback(config: AudioPlaybackConfiguration): Boolean {
        val attributes = config.audioAttributes
        return attributes.usage == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION ||
            attributes.contentType == android.media.AudioAttributes.CONTENT_TYPE_SPEECH &&
            (attributes.usage == android.media.AudioAttributes.USAGE_MEDIA ||
                attributes.usage == android.media.AudioAttributes.USAGE_UNKNOWN)
    }

    private fun applyActiveVoiceDuckDuringFocusLossIfNeeded() {
        if (
            lastAudioFocusState != AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ||
            !hasActiveVoicePlayback ||
            !player.playWhenReady
        ) {
            return
        }

        audioFocusResumeJob?.cancel()
        reentrantFocusGain = false
        rampAudioFocusVolumeTo(AUDIO_FOCUS_VOICE_DUCK_MULTIPLIER, AUDIO_FOCUS_FADE_DOWN_MS)
        Timber.tag(AUDIO_FOCUS_TAG).d("focus=LOSS_TRANSIENT_CAN_DUCK action=duck_voice")
    }

    private fun rampAudioFocusVolumeTo(targetMultiplier: Float, durationMs: Long) {
        audioFocusVolumeRampJob?.cancel()
        val startMultiplier = audioFocusVolumeMultiplier.value
        val target = targetMultiplier.coerceIn(0f, 1f)
        if (startMultiplier == target || durationMs <= 0L) {
            setAudioFocusVolumeMultiplier(target)
            return
        }

        audioFocusVolumeRampJob = scope.launch {
            val steps = (durationMs / AUDIO_FOCUS_RAMP_FRAME_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                if (!isActive) return@launch
                val progress = step / steps.toFloat()
                val easedProgress = progress * progress * (3f - 2f * progress)
                setAudioFocusVolumeMultiplier(
                    startMultiplier + (target - startMultiplier) * easedProgress,
                )
                if (step < steps) delay(AUDIO_FOCUS_RAMP_FRAME_MS)
            }
            setAudioFocusVolumeMultiplier(target)
        }
    }

    private fun setAudioFocusVolumeMultiplier(multiplier: Float) {
        audioFocusVolumeMultiplier.value = multiplier.coerceIn(0f, 1f)
        applyEffectivePlayerVolumes()
    }

    private fun prepareSafeAudioFocusResumeVolume() {
        audioFocusVolumeRampJob?.cancel()
        setAudioFocusVolumeMultiplier(
            minOf(audioFocusVolumeMultiplier.value, AUDIO_FOCUS_DUCK_MULTIPLIER),
        )
    }

    private fun cancelAudioFocusTransitions() {
        audioFocusVolumeRampJob?.cancel()
        audioFocusResumeJob?.cancel()
        reentrantFocusGain = false
    }

    private fun cancelAudioFocusAutoResumeForUserAction() {
        wasPlayingBeforeAudioFocusLoss = false
        cancelAudioFocusTransitions()
        if (!player.playWhenReady) {
            setAudioFocusVolumeMultiplier(1f)
            abandonAudioFocus()
        }
    }

    private fun pauseForAudioFocusLoss(resumeOnGain: Boolean) {
        if (resumeOnGain && player.playWhenReady) {
            wasPlayingBeforeAudioFocusLoss = true
        } else if (!resumeOnGain) {
            wasPlayingBeforeAudioFocusLoss = false
        }

        if (player.playWhenReady) {
            audioFocusPausePending = true
            player.pause()
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            return when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    hasAudioFocus = true
                    audioFocusRequestActive = true
                    rampAudioFocusVolumeTo(1f, AUDIO_FOCUS_FADE_UP_MS)
                    true
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    hasAudioFocus = false
                    audioFocusRequestActive = true
                    cancelAudioFocusTransitions()
                    pauseForAudioFocusLoss(resumeOnGain = true)
                    prepareSafeAudioFocusResumeVolume()
                    false
                }
                else -> {
                    hasAudioFocus = false
                    audioFocusRequestActive = false
                    cancelAudioFocusTransitions()
                    pauseForAudioFocusLoss(resumeOnGain = false)
                    setAudioFocusVolumeMultiplier(1f)
                    false
                }
            }
        }
        return false
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
        }
        hasAudioFocus = false
        audioFocusRequestActive = false
    }

    private fun clearPersistedQueueFiles() {
        runCatching { filesDir.resolve(PERSISTENT_QUEUE_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete() }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun waitOnNetworkError() {
        if (waitingForNetworkConnection.value) return


        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, stopping playback")
            stopOnError()
            retryCount = 0
            return
        }

        waitingForNetworkConnection.value = true


        retryJob?.cancel()
        retryJob = scope.launch {

            val delayMs = minOf(3000L * (1 shl retryCount), 30000L)
            Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
            delay(delayMs)

            if (isNetworkConnected.value && waitingForNetworkConnection.value) {
                retryCount++
                triggerRetry()
            }
        }
    }

    private fun triggerRetry() {
        waitingForNetworkConnection.value = false
        retryJob?.cancel()

        if (player.currentMediaItem != null) {


            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()


        }
    }

    private fun skipOnError() {

        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()

            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked ==
                                true
                            ) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
            ),
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else {
                var updatedSong = song.song
                if (song.song.duration == -1) {
                    updatedSong = updatedSong.copy(duration = duration)
                }

                if (song.song.isVideo != mediaMetadata.isVideoSong) {
                    updatedSong = updatedSong.copy(isVideo = mediaMetadata.isVideoSong)
                }
                if (updatedSong != song.song) {
                    update(updatedSong)
                }
            }
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()


        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                playQueue(queue, playWhenReady)
            }
            return
        }

        currentQueue = queue
        queueTitle = null
        val persistShuffleAcrossQueues = dataStore.get(PersistentShuffleAcrossQueuesKey, false)
        val previousShuffleEnabled = player.shuffleModeEnabled
        if (!persistShuffleAcrossQueues) {
            player.shuffleModeEnabled = false
        }

        originalQueueSize = 0
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch

            originalQueueSize = initialStatus.items.size
            if (queue.preloadItem != null) {
                val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, safeIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        (safeIndex + 1).coerceAtMost(initialStatus.items.size),
                        initialStatus.items.size
                    )
                )
            } else {
                val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                player.setMediaItems(
                    initialStatus.items,
                    safeIndex,
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }


            if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }

            // onMediaItemTransition can run while only preloadItem is in the player. Start the
            // actual queue prefetch again after the remaining MediaItems have been attached.
            preloadUpcomingItems()
        }
    }

    fun startRadioSeamlessly() {

        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id

        scope.launch(SilentHandler) {

            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(
                    videoId = currentMediaId
                )
            )

            try {
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }

                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }


                val radioItems = initialStatus.items.filter { item ->
                    item.mediaId != currentMediaId
                }

                if (radioItems.isNotEmpty()) {
                    val itemCount = player.mediaItemCount

                    if (itemCount > currentIndex + 1) {
                        player.removeMediaItems(currentIndex + 1, itemCount)
                    }

                    player.addMediaItems(currentIndex + 1, radioItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }

                currentQueue = radioQueue
            } catch (e: Exception) {

                try {
                    val nextResult = withContext(Dispatchers.IO) {
                        YouTube.next(WatchEndpoint(videoId = currentMediaId)).getOrNull()
                    }
                    nextResult?.relatedEndpoint?.let { relatedEndpoint ->
                        val relatedPage = withContext(Dispatchers.IO) {
                            YouTube.related(relatedEndpoint).getOrNull()
                        }
                        relatedPage?.songs?.let { songs ->
                            val radioItems = songs
                                .filter { it.id != currentMediaId }
                                .map { it.toMediaItem() }
                                .filterExplicit(dataStore.get(HideExplicitKey, false))
                                .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))

                            if (radioItems.isNotEmpty()) {
                                val itemCount = player.mediaItemCount
                                if (itemCount > currentIndex + 1) {
                                    player.removeMediaItems(currentIndex + 1, itemCount)
                                }
                                player.addMediaItems(currentIndex + 1, radioItems)
                                if (player.shuffleModeEnabled) {
                                    val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {

                }
            }
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.get(SimilarContent, true) &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)) {
            scope.launch(SilentHandler) {
                try {

                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                        .onSuccess { firstResult ->
                            YouTube.next(WatchEndpoint(playlistId = firstResult.endpoint.playlistId))
                                .onSuccess { secondResult ->
                                    automixItems.value = secondResult.items.map { song ->
                                        song.toMediaItem()
                                    }
                                }
                                .onFailure {

                                    if (firstResult.items.isNotEmpty()) {
                                        automixItems.value = firstResult.items.map { song ->
                                            song.toMediaItem()
                                        }
                                    }
                                }
                        }
                        .onFailure {

                            val currentSong = player.currentMetadata
                            if (currentSong != null) {

                                YouTube.next(WatchEndpoint(
                                    videoId = currentSong.id
                                )).onSuccess { radioResult ->
                                    val filteredItems = radioResult.items
                                        .filter { it.id != currentSong.id }
                                        .map { it.toMediaItem() }
                                    if (filteredItems.isNotEmpty()) {
                                        automixItems.value = filteredItems
                                    }
                                }.onFailure {

                                    YouTube.next(WatchEndpoint(videoId = currentSong.id)).getOrNull()?.relatedEndpoint?.let { relatedEndpoint ->
                                        YouTube.related(relatedEndpoint).onSuccess { relatedPage ->
                                            val relatedItems = relatedPage.songs
                                                .filter { it.id != currentSong.id }
                                                .map { it.toMediaItem() }
                                            if (relatedItems.isNotEmpty()) {
                                                automixItems.value = relatedItems

                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (_: Exception) {

                }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun stopAndClearPlayback() {
        crossfadeTriggerJob?.cancel()
        crossfadeJob?.cancel()
        secondaryPlayer?.runCatching {
            removeListener(secondaryPlayerListener)
            stop()
            clearMediaItems()
            release()
        }
        secondaryPlayer = null
        fadingPlayer?.runCatching {
            stop()
            clearMediaItems()
            release()
        }
        fadingPlayer = null
        isCrossfading.value = false

        retryJob?.cancel()
        waitingForNetworkConnection.value = false
        retryCount = 0
        consecutivePlaybackErr = 0
        currentQueue = EmptyQueue
        queueTitle = null
        clearAutomix()
        currentMediaMetadata.value = null

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        abandonAudioFocus()
        closeAudioEffectSession()
        clearPersistedQueueFiles()
    }

    fun playNext(items: List<MediaItem>) {

        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()

            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }


        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }


            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled


        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {

            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex


                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()


                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse()

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }


                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                existingOrder.forEach { if (pos < size) finalOrder[pos++] = it }


                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
    }

    fun addToQueue(items: List<MediaItem>) {

        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }


            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        player.addMediaItems(items)
        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
        player.prepare()
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val isInLibrary = it.song.inLibrary != null
                val token = if (isInLibrary) it.song.libraryRemoveToken else it.song.libraryAddToken
                val updatedSong = it.song.toggleLibrary(syncToYouTube = false)

                database.query {
                    update(updatedSong)
                }
                currentMediaMetadata.value = player.currentMetadata

                val remoteResult = token?.let { feedbackToken ->
                    YouTube.feedback(listOf(feedbackToken))
                } ?: YouTube.toggleSongLibrary(it.id, addToLibrary = !isInLibrary)

                if (remoteResult.getOrDefault(false)) {
                    syncUtils.syncLibrarySongs()
                }
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val song = it.song.toggleLike(syncToYouTube = false)
                database.query {
                    update(song)
                    syncUtils.likeSong(song)


                    if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {

                        val downloadRequest =
                            androidx.media3.exoplayer.offline.DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                            this@MusicService,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false
                        )
                    }
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Timber.tag(TAG).w("setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
            return
        }


        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                Timber.tag(TAG).d("LoudnessEnhancer created for sessionId=$audioSessionId")
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                return
            }
        }

        scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                }

                if (normalizeAudio && currentMediaId != null) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                    Timber.tag(TAG).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")


                    val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb

                    withContext(Dispatchers.Main) {
                        if (loudness != null) {
                            val loudnessDb = loudness.toFloat()
                            val targetGain = (-loudnessDb * 100).toInt()
                            val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                            Timber.tag(TAG).d("Calculated raw normalization gain: $targetGain mB (from loudness: $loudnessDb)")

                            try {
                                loudnessEnhancer?.setTargetGain(clampedGain)
                                loudnessEnhancer?.enabled = true
                                Timber.tag(TAG).i("LoudnessEnhancer gain applied: $clampedGain mB")
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Failed to apply loudness enhancement")
                                reportException(e)
                                releaseLoudnessEnhancer()
                            }
                        } else {
                            loudnessEnhancer?.enabled = false
                            Timber.tag(TAG).w("Normalization enabled but no loudness data available - no normalization applied")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loudnessEnhancer?.enabled = false
                        Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled or mediaId unavailable")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
            Timber.tag(TAG).d("LoudnessEnhancer released")
        } catch (e: Exception) {
            reportException(e)
            Timber.tag(TAG).e(e, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private var previousMediaItemIndex = C.INDEX_UNSET

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        activeQualityMediaId = mediaItem?.mediaId
        activeRequestedQuality = audioQuality

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            val repeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
            if (repeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        previousMediaItemIndex = player.currentMediaItemIndex

        preloadUpcomingItems()
        setupLoudnessEnhancer()

        scrobbleManager?.onSongStop()
        checkAndSubmitListenBrainzFinished()

        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
            player.currentMediaItem?.mediaId?.let { mediaId ->
                if (listenBrainzCurrentMediaId != mediaId) {
                    listenBrainzCurrentMediaId = mediaId
                    listenBrainzCurrentStartTs = System.currentTimeMillis()
                }
                checkAndSubmitListenBrainzPlayingNow(mediaId)
            }
        }



        if (castConnectionHandler?.isCasting?.value == true &&
            castConnectionHandler?.isSyncingFromCast != true &&
            mediaItem != null) {
            val metadata = mediaItem.metadata
            if (metadata != null) {


                val navigated = castConnectionHandler?.navigateToMediaIfInQueue(metadata.id) ?: false
                if (!navigated) {

                    castConnectionHandler?.loadMedia(metadata)
                }
            }
        }


        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            scope.launch(SilentHandler) {
                val mediaItems = withContext(Dispatchers.IO) {
                    currentQueue.nextPage()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }
                if (player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                    player.addMediaItems(mediaItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }
            }
        }


        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {

        if (playbackState == Player.STATE_ENDED) {
            val repeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
            if (repeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
            }
        }


        if (dataStore.get(PersistentQueueKey, true) && !isSilenceSkipping) {
            saveQueueToDisk()
        }

        if (playbackState == Player.STATE_READY) {
            Log.i(
                "RemotePlayback",
                "player.state=READY mediaId=${player.currentMediaItem?.mediaId} playWhenReady=${player.playWhenReady}",
            )
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()


            player.currentMediaItem?.mediaId?.let { mediaId ->
                resetRetryCount(mediaId)
                Timber.tag(TAG).d("Playback successful for $mediaId, reset retry count")
            }
            scheduleCrossfade()
        }

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            scrobbleManager?.onSongStop()
            checkAndSubmitListenBrainzFinished()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {

        val pausedForAudioFocus = !playWhenReady && audioFocusPausePending
        if (pausedForAudioFocus) {
            audioFocusPausePending = false
        }

        if (playWhenReady && castConnectionHandler?.isCasting?.value == true) {
            player.pause()
            return
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
                wasPlayingBeforeAudioFocusLoss = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }

            if (!playWhenReady && !pausedForAudioFocus) {
                cancelAudioFocusAutoResumeForUserAction()
            }
        }

        if (playWhenReady) {
            setupLoudnessEnhancer()
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            scheduleCrossfade()
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                val focusGranted = requestAudioFocus()
                if (focusGranted) {
                    openAudioEffectSession()
                }
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }


        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            updateWidgetUI(player.isPlaying)
            if (player.isPlaying) {
                startWidgetUpdates()
            } else {
                stopWidgetUpdates()
            }
        }

        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)

            if (player.isPlaying) {
                player.currentMediaItem?.mediaId?.let { mediaId ->
                    if (listenBrainzCurrentMediaId != mediaId) {
                        checkAndSubmitListenBrainzFinished()
                        listenBrainzCurrentMediaId = mediaId
                        listenBrainzCurrentStartTs = System.currentTimeMillis()
                        scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
                    }
                    checkAndSubmitListenBrainzPlayingNow(mediaId)
                }
            }
        }

    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {

            if (player.mediaItemCount == 0) return

            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)
        }


        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            scope.launch {
                dataStore.edit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }


        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }


        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }


    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean
    ) {
        if (totalCount == 0) return

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {

            val originalIndices = (0 until originalQueueSize).filter { it != currentIndex }.toMutableList()
            val addedIndices = (originalQueueSize until totalCount).filter { it != currentIndex }.toMutableList()

            originalIndices.shuffle()
            addedIndices.shuffle()

            val shuffledIndices = IntArray(totalCount)
            var pos = 0
            shuffledIndices[pos++] = currentIndex

            if (currentIndex < originalQueueSize) {
                originalIndices.forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            } else {
                (0 until originalQueueSize).shuffled().forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        } else {
            val shuffledIndices = IntArray(totalCount) { it }
            shuffledIndices.shuffle()

            val currentItemIndexInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentItemIndexInShuffled != -1) {
                val temp = shuffledIndices[0]
                shuffledIndices[0] = shuffledIndices[currentItemIndexInShuffled]
                shuffledIndices[currentItemIndexInShuffled] = temp
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }


    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 403
    }


    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 416
    }


    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""

        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "page must be reloaded",
            "reload",
            "ricaricata"
        )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
                    causeMessage.contains(keyword) ||
                    innerCauseMessage.contains(keyword)
        }
    }

    private fun isNetworkRelatedError(error: PlaybackException): Boolean {

        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) {
            return false
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                error.cause is java.net.ConnectException ||
                error.cause is java.net.UnknownHostException ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }


    private fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
    }

    private fun isCacheOrStreamCorruptionError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)


        if (!playerInitialized.value) {
            Timber.tag(TAG).e(error, "Player error occurred but player not initialized")
            return
        }

        val mediaId = player.currentMediaItem?.mediaId
        Timber.tag(TAG).w(error, "Player error occurred for $mediaId: errorCode=${error.errorCode}, message=${error.message}")
        val httpStatusCode = getHttpResponseCode(error)
        Log.e(
            "RemotePlayback",
            "player.error mediaId=$mediaId errorCode=${error.errorCode} http=${httpStatusCode ?: "none"} message=${error.message}",
        )
        val isFallbackError = error.message?.contains("fallback", ignoreCase = true) == true
        if (!isFallbackError) {
            reportException(error)
        }


        if (mediaId != null && hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId has exceeded retry limit, skipping")
            markSongAsFailed(mediaId)
            handleFinalFailure()
            return
        }


        if (mediaId != null) {
            val failingClient = songUrlCache.entries
                .firstOrNull { it.key.startsWith("${mediaId}_") }
                ?.value
                ?.first
                ?.toHttpUrlOrNull()
                ?.queryParameter("c")
            YTPlayerUtils.markStreamClientFailed(mediaId, failingClient, httpStatusCode)
            performAggressiveCacheClear(mediaId)
        }


        when {
            isAudioRendererError(error) -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId)
                return
            }
            isRangeNotSatisfiableError(error) -> {
                Timber.tag(TAG).d("Range Not Satisfiable (416) detected, performing strict recovery")
                handleRangeNotSatisfiableError(mediaId)
                return
            }
            isCacheOrStreamCorruptionError(error) -> {
                Timber.tag(TAG).d("Cache or stream corruption detected, clearing cache and refreshing URL")
                handleExpiredUrlError(mediaId)
                return
            }
            isPageReloadError(error) -> {
                Timber.tag(TAG).d("Page reload error detected, performing strict recovery")
                handlePageReloadError(mediaId)
                return
            }
            isExpiredUrlError(error) -> {
                Timber.tag(TAG).d("Expired URL (403) detected, refreshing stream URL")
                handleExpiredUrlError(mediaId)
                return
            }

            !isNetworkConnected.value -> {
                Timber.tag(TAG).d("Network disconnected, waiting for connection")
                waitOnNetworkError()
                return
            }
            isNetworkRelatedError(error) -> {
                Timber.tag(TAG).d("Network-related error detected, handling as generic IO error")
                handleGenericIOError(mediaId)
                return
            }
        }


        if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            Timber.tag(TAG).d("IO error detected (${error.errorCode}), attempting recovery")
            handleGenericIOError(mediaId)
            return
        }


        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("Auto-skipping to next track due to unrecoverable error")
            skipOnError()
        } else {
            Timber.tag(TAG).d("Stopping playback due to unrecoverable error")
            stopOnError()
        }
    }


    private fun performAggressiveCacheClear(mediaId: String) {
        Timber.tag(TAG).d("Performing aggressive cache clear for $mediaId")


        songUrlCache.keys.removeIf { it.startsWith("${mediaId}_") }
        playbackDataCache.keys.removeIf { it.startsWith("${mediaId}_") }


        try {
            playerCache.removeResource(mediaId)
            Timber.tag(TAG).d("Cleared player cache for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear player cache for $mediaId")
        }


        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
            Timber.tag(TAG).d("Cleared decryption caches for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches for $mediaId")
        }
    }


    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        return currentRetries >= MAX_RETRY_PER_SONG
    }


    private fun incrementRetryCount(mediaId: String) {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        currentMediaIdRetryCount[mediaId] = currentRetries + 1
        Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
    }


    private fun resetRetryCount(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
        recentlyFailedSongs.remove(mediaId)
    }


    private fun markSongAsFailed(mediaId: String) {
        recentlyFailedSongs.add(mediaId)
        currentMediaIdRetryCount.remove(mediaId)


        failedSongsClearJob?.cancel()
        failedSongsClearJob = scope.launch {
            delay(5 * 60 * 1000L)
            recentlyFailedSongs.clear()
            Timber.tag(TAG).d("Cleared recently failed songs list")
        }
    }


    private fun handleAudioRendererError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            try {

                val wasPlaying = player.playWhenReady
                player.pause()
                Timber.tag(TAG).d("Paused playback due to AudioTrack error")



                delay(RETRY_DELAY_MS * 3)


                if (!playerInitialized.value) {
                    Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                    return@launch
                }

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {

                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()

                    Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")


                    if (wasPlaying) {
                        delay(500)
                        if (hasAudioFocus && playerInitialized.value) {
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                        }
                    }
                } else {
                    Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                    handleFinalFailure()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                handleFinalFailure()
            }
        }
    }


    private fun handleRangeNotSatisfiableError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {

            performAggressiveCacheClear(mediaId)



            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, 0)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (from position 0)")
        }
    }


    private fun handlePageReloadError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            Timber.tag(TAG).d("Handling page reload error for $mediaId")


            performAggressiveCacheClear(mediaId)


            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
        }
    }


    private fun handleExpiredUrlError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)


        songUrlCache.keys.removeIf { it.startsWith("${mediaId}_") }
        playbackDataCache.keys.removeIf { it.startsWith("${mediaId}_") }
        Timber.tag(TAG).d("Cleared cached URL for $mediaId")


        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches")
        }

        retryJob?.cancel()
        retryJob = scope.launch {


            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 403 error")
        }
    }


    private fun handleGenericIOError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            performAggressiveCacheClear(mediaId)


            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
        }
    }


    private fun handleFinalFailure() {
        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
            skipOnError()
        } else {
            Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
            stopOnError()
        }
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.get(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying && castConnectionHandler?.isCasting?.value != true) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    private val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    return when (this@MusicService.ipVersion) {
                        IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                        IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                        IpVersion.AUTO -> addresses
                    }
                }
            })
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                if (!StreamClientUtils.isYouTubeMediaHost(request.url.host)) {
                    return@addInterceptor chain.proceed(request)
                }

                val profile = StreamClientUtils.resolveRequestProfile(request.url)
                val profiledRequest = StreamClientUtils
                    .applyRequestProfile(request.newBuilder(), profile)
                    .build()
                Log.i(
                    "RemotePlayback",
                    "datasource.open host=${request.url.host} client=${profile.clientKey} range=${profiledRequest.header("Range") ?: "none"}",
                )
                chain.proceed(profiledRequest)
            }
            .build()
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(mediaOkHttpClient),
                        )
                    )
                    .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)


    private var isSilenceSkipping = false

    private fun handleLongSilenceDetected() {
        if (!instantSilenceSkipEnabled.value) return
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob = scope.launch {

            delay(200)
            performInstantSilenceSkip()
        }
    }

    private suspend fun performInstantSilenceSkip() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = playerSilenceProcessors[player] ?: return
            while (coroutineContext.isActive && instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                val current = player.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                if (target <= current) break


                silenceProcessor.resetTracking()
                player.seekTo(target)
                hops++

                if (hops >= 80 || target >= duration - 500) break

                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Timber.tag(TAG).d("Silence skip: jumped $hops times")
            }
        } finally {
            isSilenceSkipping = false
        }
    }

    private fun updateListenBrainz(title: String, artistNames: String, releaseName: String, durationMs: Long, isFinished: Boolean, startMs: Long = 0, endMs: Long = 0, positionMs: Long = 0) {
        val cleanToken = listenBrainzToken.trim()
        if (!listenBrainzEnabled || cleanToken.isBlank()) return
        scope.launch {
            if (isFinished) {
                com.jagr.fridamusic.listenbrainz.ListenBrainzManager.submitFinished(
                    context = this@MusicService,
                    token = cleanToken,
                    title = title,
                    artistNames = artistNames,
                    releaseName = releaseName,
                    durationMs = durationMs,
                    startMs = startMs,
                    endMs = endMs
                )
            } else {
                com.jagr.fridamusic.listenbrainz.ListenBrainzManager.submitPlayingNow(
                    context = this@MusicService,
                    token = cleanToken,
                    title = title,
                    artistNames = artistNames,
                    releaseName = releaseName,
                    durationMs = durationMs,
                    positionMs = positionMs
                )
            }
        }
    }

    private data class SharedPlaybackResolution(
        val playbackData: YTPlayerUtils.PlaybackData?,
        val streamUrl: String,
    )

    private fun YTPlayerUtils.PlaybackData.resolvedAudioQuality(): AudioQuality =
        when {
            format.mimeType.contains("flac", ignoreCase = true) -> AudioQuality.LOSSLESS
            isSaavnStream -> AudioQuality.SAAVN
            else -> AudioQuality.OPUS
        }

    private fun FormatEntity.storedAudioQuality(): AudioQuality =
        when {
            mimeType.contains("flac", ignoreCase = true) || codecs.contains("flac", ignoreCase = true) ->
                AudioQuality.LOSSLESS
            itag == 0 && (
                mimeType.contains("mp4", ignoreCase = true) ||
                    mimeType.contains("m4a", ignoreCase = true) ||
                    codecs.contains("mp4a", ignoreCase = true)
                ) -> AudioQuality.SAAVN
            else -> AudioQuality.OPUS
        }

    private suspend fun resolvePlaybackShared(
        mediaId: String,
        selectedQuality: AudioQuality,
        knownArtist: String?,
        knownTitle: String?,
        knownDurationMs: Long?,
    ): Result<SharedPlaybackResolution> = runCatching {
        val effectiveQuality = selectedQuality.effectiveForPlayback()
        val cacheKey = "${mediaId}_${effectiveQuality.name}"
        val mutex = streamResolutionMutexes.computeIfAbsent(cacheKey) { Mutex() }
        mutex.withLock {
            songUrlCache[cacheKey]
                ?.takeIf { (_, expiresAt) -> expiresAt > System.currentTimeMillis() }
                ?.let { (url, _) ->
                    return@withLock SharedPlaybackResolution(
                        playbackData = playbackDataCache[cacheKey],
                        streamUrl = url,
                    )
                }
            songUrlCache.remove(cacheKey)
            playbackDataCache.remove(cacheKey)

            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = mediaId,
                audioQuality = effectiveQuality,
                connectivityManager = connectivityManager,
                context = this@MusicService,
                knownArtist = knownArtist,
                knownTitle = knownTitle,
                knownDurationMs = knownDurationMs,
            ).getOrThrow()
            songUrlCache[cacheKey] = playbackData.streamUrl to
                (System.currentTimeMillis() + playbackData.streamExpiresInSeconds * 1000L)
            playbackDataCache[cacheKey] = playbackData
            SharedPlaybackResolution(
                playbackData = playbackData,
                streamUrl = playbackData.streamUrl,
            )
        }
    }

    private fun resolveRemoteDataSpec(
        dataSpec: DataSpec,
        streamUrl: String,
        contentLength: Long? = null,
    ): DataSpec {
        val resolvedDataSpec = dataSpec.withUri(streamUrl.toUri())
        if (dataSpec.length >= 0) return resolvedDataSpec

        val totalLength = contentLength
            ?.takeIf { it > 0 }
            ?: streamUrl.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 }
        val remainingLength = totalLength
            ?.minus(dataSpec.position)
            ?.takeIf { it > 0 }
            ?: return resolvedDataSpec

        // A bounded request ending at the real EOF keeps YouTube's fast partial-response path
        // without manufacturing intermediate boundaries that interrupt playback.
        return resolvedDataSpec.buildUpon().setLength(remainingLength).build()
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            if (mediaId.isLocalMediaId()) {
                val localUri = android.net.Uri.parse(mediaId)
                try {
                    contentResolver.openFileDescriptor(localUri, "r")?.close()
                } catch (e: java.io.FileNotFoundException) {
                    throw androidx.media3.common.PlaybackException("Local file deleted", e, androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
                }
                return@Factory dataSpec
            }



            var shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)

            val isCurrentlyPlaying = runBlocking(Dispatchers.Main) { player.currentMediaItem?.mediaId == mediaId }
            val dbFormat = runBlocking(Dispatchers.IO) { database.format(mediaId).firstOrNull() }

            val cachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(downloadCache.getContentMetadata(mediaId))
                .takeIf { it != androidx.media3.common.C.LENGTH_UNSET.toLong() } ?: dbFormat?.contentLength ?: -1L
            val isFullyDownloaded = cachedLength > 0 && downloadCache.isCached(mediaId, 0, cachedLength)
            val playerCachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(playerCache.getContentMetadata(mediaId))
                .takeIf { it != androidx.media3.common.C.LENGTH_UNSET.toLong() } ?: dbFormat?.contentLength ?: -1L
            val isFullyPlayerCached = playerCachedLength > 0 && playerCache.isCached(mediaId, 0, playerCachedLength)
            val preferPlayerCache = dataSpec.uri.getQueryParameter("frida_player_cache") == "1"

            if (preferPlayerCache && isFullyPlayerCached) {
                return@Factory dataSpec
            }

            if (isFullyDownloaded) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec
            }

            val lockedQuality = if (isCurrentlyPlaying && activeQualityMediaId == mediaId) {
                activeRequestedQuality
            } else {
                audioQuality
            }
            val effectiveQuality = lockedQuality.effectiveForPlayback()
            val qualityCacheKey = "${mediaId}_${effectiveQuality.name}"
            val cachedResolvedQuality = playbackDataCache[qualityCacheKey]?.resolvedAudioQuality()

            if (!shouldBypassCache && !isFullyDownloaded && dbFormat != null) {
                val cacheMatchesTarget = dbFormat.storedAudioQuality() ==
                    (cachedResolvedQuality ?: effectiveQuality)

                if (!cacheMatchesTarget) {
                    shouldBypassCache = true
                    Timber.tag(TAG).i(
                        "Effective quality changed to $effectiveQuality for $mediaId. Clearing playerCache to prevent container mismatch.",
                    )
                    playerCache.removeResource(mediaId)
                }
            }

            if (!shouldBypassCache) {
                if (downloadCache.isCached(
                        mediaId,
                        dataSpec.position,
                        if (dataSpec.length >= 0) dataSpec.length else 1
                    )
                ) {
                    // Fall through to fetch real URL since it's only partially downloaded
                }

                if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)) {
                    if (songUrlCache[qualityCacheKey]?.second?.let { it > System.currentTimeMillis() } != true) {
                        Timber.tag(TAG).w("Ghost cache entry for $mediaId, re-fetching")
                        playerCache.removeResource(mediaId)
                    }
                }
            } else {
                Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
            }

            Timber.tag("MusicService").i(
                "FETCHING STREAM: $mediaId | selected=$lockedQuality | effective=$effectiveQuality",
            )
            val sharedResolution = runBlocking(Dispatchers.IO) {
                val dbSong = database.song(mediaId).firstOrNull()
                val knownArtist = dbSong?.artists?.joinToString { it.name }?.replace(" - Topic", "")
                val knownTitle = dbSong?.song?.title
                val knownDuration = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null }

                resolvePlaybackShared(
                    mediaId = mediaId,
                    selectedQuality = lockedQuality,
                    knownArtist = knownArtist,
                    knownTitle = knownTitle,
                    knownDurationMs = knownDuration,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            val nonNullPlayback = sharedResolution.playbackData
            if (nonNullPlayback == null) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory resolveRemoteDataSpec(dataSpec, sharedResolution.streamUrl, dbFormat?.contentLength)
            }
            run {
                val format = nonNullPlayback.format
                val resolvedQuality = nonNullPlayback.resolvedAudioQuality()

                val isFinalLossless = resolvedQuality == AudioQuality.LOSSLESS
                val isFinalSaavn = resolvedQuality == AudioQuality.SAAVN

                if (dbFormat != null && !shouldBypassCache) {
                    val storedQuality = dbFormat.storedAudioQuality()
                    val cacheIsLossless = storedQuality == AudioQuality.LOSSLESS
                    val cacheIsSaavn = storedQuality == AudioQuality.SAAVN

                    if (isFinalLossless != cacheIsLossless || isFinalSaavn != cacheIsSaavn) {
                        Timber.tag(TAG).w("Format fallback detected AFTER fetch. Clearing playerCache to prevent mismatch crash.")
                        playerCache.removeResource(mediaId)

                        if (isCurrentlyPlaying) {
                            Timber.tag(TAG).e("Format changed mid-stream for $mediaId. Throwing to force player restart.")
                            runBlocking(Dispatchers.IO) { database.query { deleteFormat(mediaId) } }
                            throw PlaybackException(
                                "Container format changed mid-stream due to fallback",
                                null,
                                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                            )
                        }
                    }
                }

                val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb
                val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb

                Timber.tag(TAG).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
                if (loudnessDb == null && perceptualLoudnessDb == null) {
                    Timber.tag(TAG).w("No loudness data available from YouTube for video: $mediaId")
                }

                database.query {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.split(";")[0],
                            codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = format.contentLength ?: 0L,
                            loudnessDb = loudnessDb,
                            perceptualLoudnessDb = perceptualLoudnessDb,
                            playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        )
                    )
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }


                if (bypassCacheForQualityChange.remove(mediaId)) {
                    Timber.tag("MusicService").d("Cleared bypass cache flag for $mediaId after fresh fetch")
                }

                val streamUrl = nonNullPlayback.streamUrl
                val expiresAt = System.currentTimeMillis() +
                    (nonNullPlayback.streamExpiresInSeconds * 1000L)

                songUrlCache[qualityCacheKey] = streamUrl to expiresAt
                playbackDataCache[qualityCacheKey] = nonNullPlayback

                return@Factory resolveRemoteDataSpec(dataSpec, streamUrl, format.contentLength)
            }
        }
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            androidx.media3.extractor.DefaultExtractorsFactory()
        )

    private fun createRenderersFactory(
        eqProcessor: CustomEqualizerAudioProcessor,
        silenceProcessor: SilenceDetectorAudioProcessor
    ) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(

                        arrayOf(
                            eqProcessor,
                            silenceProcessor,
                        ),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = dataStore[HistoryDuration]?.times(1000f) ?: 30000f

        if (playbackStats.totalPlayTimeMs >= historyDurationMs &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }
        }

        if (playbackStats.totalPlayTimeMs >= historyDurationMs) {
            CoroutineScope(Dispatchers.IO).launch {
                val playbackUrl = database.format(mediaItem.mediaId).first()?.playbackUrl
                    ?: YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                        .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                playbackUrl?.let {
                    YouTube.registerPlayback(null, playbackUrl)
                        .onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.mediaItemCount == 0) {
            Timber.tag(TAG).d("Skipping queue save - no media items")
            return
        }

        try {

            val persistQueue = currentQueue.toPersistQueue(
                title = queueTitle,
                items = player.mediaItems.mapNotNull { it.metadata },
                mediaItemIndex = player.currentMediaItemIndex,
                position = player.currentPosition
            )

            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                )


            val persistPlayerState = PersistPlayerState(
                playWhenReady = player.playWhenReady,
                repeatMode = player.repeatMode,
                shuffleModeEnabled = player.shuffleModeEnabled,
                // Persist the stable gain. player.volume may contain a transient mute/duck/fade.
                volume = playerVolume.value.audibleVolumeOrDefault(),
                currentPosition = player.currentPosition,
                currentMediaItemIndex = player.currentMediaItemIndex,
                playbackState = player.playbackState
            )

            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistQueue)
                    }
                }
                Timber.tag(TAG).d("Queue saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save queue")
                reportException(it)
            }

            runCatching {
                filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistAutomix)
                    }
                }
                Timber.tag(TAG).d("Automix saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save automix")
                reportException(it)
            }

            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistPlayerState)
                    }
                }
                Timber.tag(TAG).d("Player state saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save player state")
                reportException(it)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during queue save operation")
            reportException(e)
        }
    }

    override fun onDestroy() {
        isRunning = false

        cancelAudioFocusTransitions()
        audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        castConnectionHandler?.release()
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        connectivityObserver.unregister()
        abandonAudioFocus()
        releaseLoudnessEnhancer()
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        playerSilenceProcessors.remove(player)



        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (dataStore.get(StopMusicOnTaskClearKey, false)) {
            player.stop()
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }
            MusicWidgetReceiver.ACTION_NEXT -> {
                player.seekToNext()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                player.seekToPrevious()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }


    private fun updateWidgetUI(isPlaying: Boolean) {
        scope.launch {
            try {
                val songData = currentSong.value
                val song = songData?.song
                val songTitle = song?.title ?: getString(R.string.no_song_playing)
                val artistName = songData?.artists?.joinToString(", ") { it.name } ?: getString(R.string.tap_to_open)
                val isLiked = songData?.song?.liked == true

                widgetManager.updateWidgets(
                    title = songTitle,
                    artist = artistName,
                    artworkUri = song?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                    currentPosition = player.currentPosition
                )
            } catch (e: Exception) {

            }
        }
    }

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    updateWidgetUI(true)
                }
                delay(200)
            }
        }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    private fun shareSong() {
        val songData = currentSong.value
        val songId = songData?.song?.id ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://share.echomusic.fun/watch?v=$songId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }


    suspend fun getStreamUrl(mediaId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = audioQuality.effectiveForPlayback(),
                    connectivityManager = connectivityManager,
                ).getOrNull()
                playbackData?.streamUrl
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to get stream URL for Cast")
                null
            }
        }
    }


    private fun initializeCast() {
        if (dataStore.get(com.jagr.fridamusic.constants.EnableGoogleCastKey, true)) {
            try {
                castConnectionHandler = CastConnectionHandler(this, scope, this)
                castConnectionHandler?.initialize()
                timber.log.Timber.d("Google Cast initialized")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to initialize Google Cast")
            }
        }
    }


    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            scheduleCrossfade()
        }
    }

    private fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        if (!crossfadeEnabled || player.duration == C.TIME_UNSET || player.duration <= crossfadeDuration) return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return

        val triggerTime = player.duration - crossfadeDuration.toLong()
        val delayMs = triggerTime - player.currentPosition
        if (delayMs <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId

        crossfadeTriggerJob = scope.launch {
            delay(delayMs)
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && !sleepTimer.pauseWhenSongEnd) {
                startCrossfade()
            }
        }
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem?.mediaMetadata ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        return current.albumTitle != null && current.albumTitle == next.albumTitle
    }

    private fun startCrossfade() {
        if (isCrossfading.value) return



        val savedRepeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
        val savedShuffleEnabled = runBlocking { dataStore.get(ShuffleModeKey, false) }


        val targetIndex = if (savedRepeatMode == REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return

        secondaryPlayer = createExoPlayer()
        val secPlayer = secondaryPlayer!!
        secPlayer.addListener(secondaryPlayerListener)

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()

        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        secPlayer.setMediaItems(items)

        secPlayer.seekTo(targetIndex, 0)
        secPlayer.volume = 0f


        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = savedShuffleEnabled

        secPlayer.prepare()
        secPlayer.playWhenReady = true

        performCrossfadeSwap()


        if (savedShuffleEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    private fun performCrossfadeSwap() {
        isCrossfading.value = true
        val nextPlayer = secondaryPlayer ?: return
        val currentPlayer = player

        fadingPlayer = currentPlayer
        player = nextPlayer
        crossfadeInVolumeMultiplier = 0f
        crossfadeOutVolumeMultiplier = 1f
        applyEffectivePlayerVolumes()
        _playerFlow.value = player
        secondaryPlayer = null

        fadingPlayer?.removeListener(this)
        fadingPlayer?.removeListener(sleepTimer)


        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCrossfading.value && fadingPlayer != null) {
                    try {
                        if (isPlaying) {
                            fadingPlayer?.play()
                        } else {
                            fadingPlayer?.pause()
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error syncing fadingPlayer play state")
                    }
                } else {
                    player.removeListener(this)
                }
            }
        })

        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        nextPlayer.addListener(sleepTimer)

        sleepTimer.player = player

        try {
            (mediaSession as MediaSession).player = player
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        crossfadeJob = scope.launch {
            val duration = crossfadeDuration.toLong()
            val steps = 20
            val stepTime = duration / steps

            try {
                for (i in 0..steps) {
                    if (!isActive) break

                    while (!player.isPlaying && isActive) {
                        delay(100)
                    }

                    val progress = i / steps.toFloat()
                    val fadeIn = 1.0f - (1.0f - progress) * (1.0f - progress)
                    val fadeOut = (1.0f - progress) * (1.0f - progress)

                    try {
                        crossfadeInVolumeMultiplier = fadeIn
                        crossfadeOutVolumeMultiplier = fadeOut
                        applyEffectivePlayerVolumes()
                    } catch (e: Exception) { break }

                    delay(stepTime)
                }
            } finally {
                try {
                    crossfadeInVolumeMultiplier = 1f
                    crossfadeOutVolumeMultiplier = 0f
                    fadingPlayer?.volume = 0f
                    applyEffectivePlayerVolumes()
                } catch (e: Exception) { }
                cleanupCrossfade()
            }
        }
    }

    private fun cleanupCrossfade() {
        fadingPlayer?.stop()
        fadingPlayer?.clearMediaItems()
        fadingPlayer?.release()
        fadingPlayer = null
        isCrossfading.value = false
        sleepTimer.notifySongTransition()
    }

    companion object {
        private const val DEFAULT_PLAYER_VOLUME = 1f
        private const val AUDIO_FOCUS_DUCK_MULTIPLIER = 0.25f
        private const val AUDIO_FOCUS_VOICE_DUCK_MULTIPLIER = 0.0000000000001f
        private const val AUDIO_FOCUS_FADE_DOWN_MS = 150L
        private const val AUDIO_FOCUS_FADE_UP_MS = 350L
        private const val AUDIO_FOCUS_RAMP_FRAME_MS = 16L
        private const val AUDIO_FOCUS_RESUME_DELAY_MS = 150L
        private const val AUDIO_FOCUS_TAG = "AudioFocus"
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val MAX_RETRY_COUNT = 10

        private const val MAX_GAIN_MB = 300
        private const val MIN_GAIN_MB = -1500

        private const val TAG = "MusicService"

        @Volatile
        var isRunning = false
            private set
    }

    private fun Float.audibleVolumeOrDefault(): Float =
        takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0f, 1f)
            ?: DEFAULT_PLAYER_VOLUME

    private var preloadJob: kotlinx.coroutines.Job? = null

    private fun preloadUpcomingItems() {
        preloadJob?.cancel()
        preloadJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val preloadEnabled = dataStore.get(com.jagr.fridamusic.constants.PreloadNextSongEnabledKey, true)
            if (!preloadEnabled) return@launch

            val preloadLimit = dataStore.get(com.jagr.fridamusic.constants.PreloadNextSongLimitKey, 1)
            val preloadLyrics = dataStore.get(com.jagr.fridamusic.constants.PreloadLyricsEnabledKey, true)
            val preloadQuality = audioQuality.effectiveForPlayback()
            val upcomingMediaIds = withContext(Dispatchers.Main) {
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex == androidx.media3.common.C.INDEX_UNSET) {
                    return@withContext emptyList()
                }
                val limit = kotlin.math.min(preloadLimit, player.mediaItemCount - currentIndex - 1)
                if (limit <= 0) {
                    return@withContext emptyList()
                }
                (1..limit).map { offset ->
                    player.getMediaItemAt(currentIndex + offset).mediaId
                }
            }

            for (mediaId in upcomingMediaIds) {

                val isFullyDownloaded = downloadCache.getCachedSpans(mediaId).isNotEmpty()
                val cacheKey = "${mediaId}_${preloadQuality.name}"
                val hasValidCachedUrl = songUrlCache[cacheKey]
                    ?.let { (_, expiresAt) -> expiresAt > System.currentTimeMillis() } == true
                if (!mediaId.isLocalMediaId() && !hasValidCachedUrl && !isFullyDownloaded) {
                    Timber.tag(TAG).d("Preloading stream for $mediaId")
                    kotlin.runCatching {
                        val dbSong = database.song(mediaId).firstOrNull()
                        val knownArtist = dbSong?.artists?.joinToString(separator = ", ") { artist -> artist.name }?.replace(" - Topic", "")

                        resolvePlaybackShared(
                            mediaId = mediaId,
                            selectedQuality = preloadQuality,
                            knownArtist = knownArtist,
                            knownTitle = dbSong?.song?.title,
                            knownDurationMs = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null },
                        ).onSuccess {
                            Timber.tag(TAG).d("Preloaded stream for $mediaId")
                        }
                    }
                }

                if (preloadLyrics) {
                    val dbLyrics = database.lyrics(mediaId).firstOrNull()
                    if (dbLyrics == null) {
                        Timber.tag(TAG).d("Preloading lyrics for $mediaId")
                        val dbSong = database.song(mediaId).firstOrNull()
                        if (dbSong != null) {
                            kotlin.runCatching {
                                val metadata = com.jagr.fridamusic.models.MediaMetadata(
                                    id = dbSong.song.id,
                                    title = dbSong.song.title,
                                    artists = dbSong.artists.map { artist -> com.jagr.fridamusic.models.MediaMetadata.Artist(artist.id, artist.name) },
                                    duration = dbSong.song.duration,
                                    thumbnailUrl = dbSong.thumbnailUrl
                                )
                                val lyricsResult = lyricsHelper.getLyrics(metadata)
                                database.query {
                                    upsert(
                                        com.jagr.fridamusic.db.entities.LyricsEntity(
                                            id = mediaId,
                                            lyrics = lyricsResult.lyrics,
                                            provider = lyricsResult.provider,
                                        ),
                                    )
                                    if (lyricsResult.autoOffsetMs != 0 && dbSong.song.lyricsOffset == 0) {
                                        update(dbSong.song.copy(lyricsOffset = lyricsResult.autoOffsetMs))
                                    }
                                }
                                Timber.tag(TAG).d("Preloaded lyrics for $mediaId")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndSubmitListenBrainzFinished() {
        listenBrainzCurrentMediaId?.let { mediaId ->
            val startTs = listenBrainzCurrentStartTs
            if (startTs > 0) {
                scope.launch {
                    val mediaMetadata = player.mediaItems.find { it.mediaId == mediaId }?.metadata
                    val dbSong = if (mediaMetadata == null) database.song(mediaId).firstOrNull() else null

                    val title = mediaMetadata?.title ?: dbSong?.song?.title ?: return@launch
                    val artistNames = mediaMetadata?.artists?.joinToString(" & ") { it.name }
                        ?: dbSong?.artists?.joinToString(" & ") { it.name } ?: ""
                    val releaseName = mediaMetadata?.album?.title ?: dbSong?.album?.title ?: ""
                    val durationMs = mediaMetadata?.duration?.takeIf { it != -1 }?.times(1000L)
                        ?: dbSong?.song?.duration?.takeIf { it != -1 }?.times(1000L) ?: 0L

                    updateListenBrainz(title, artistNames, releaseName, durationMs, isFinished = true, startMs = startTs, endMs = System.currentTimeMillis())
                }
            }
        }
        listenBrainzCurrentStartTs = 0L
        listenBrainzCurrentMediaId = null
    }

    private fun checkAndSubmitListenBrainzPlayingNow(mediaId: String) {
        scope.launch {
            val mediaMetadata = player.mediaItems.find { it.mediaId == mediaId }?.metadata
            val dbSong = if (mediaMetadata == null) database.song(mediaId).firstOrNull() else null

            val title = mediaMetadata?.title ?: dbSong?.song?.title ?: return@launch
            val artistNames = mediaMetadata?.artists?.joinToString(" & ") { it.name }
                ?: dbSong?.artists?.joinToString(" & ") { it.name } ?: ""
            val releaseName = mediaMetadata?.album?.title ?: dbSong?.album?.title ?: ""
            val durationMs = mediaMetadata?.duration?.takeIf { it != -1 }?.times(1000L)
                ?: dbSong?.song?.duration?.takeIf { it != -1 }?.times(1000L) ?: 0L

            updateListenBrainz(title, artistNames, releaseName, durationMs, isFinished = false)
        }
    }
}
