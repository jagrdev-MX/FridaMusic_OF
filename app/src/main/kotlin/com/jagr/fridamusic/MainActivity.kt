package com.jagr.fridamusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.Window // <-- Required import for qualifier warning
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.jagr.fridamusic.constants.DisableScreenshotKey
import com.jagr.fridamusic.constants.DarkModeKey
import com.jagr.fridamusic.constants.DynamicThemeKey
import com.jagr.fridamusic.constants.KeepScreenOn
import com.jagr.fridamusic.constants.LastAppOpenAtKey
import com.jagr.fridamusic.constants.PureBlackKey
import androidx.compose.foundation.isSystemInDarkTheme
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.localmedia.SupportedLocalAudio
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.playback.MusicService
import com.jagr.fridamusic.playback.MusicService.MusicBinder
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.playback.queues.ListQueue
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.screens.MainScreen
import com.jagr.fridamusic.presentation.screens.OnboardingScreen
import com.jagr.fridamusic.presentation.theme.FridaMusicTheme
import com.jagr.fridamusic.utils.dataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds // <-- Importation for delay()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private data class ExternalAudioRequest(
        val uri: Uri,
        val mimeType: String?,
    )

    companion object {
        const val ACTION_RECOGNITION = "com.jagr.fridamusic.action.RECOGNITION"
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }

    @Inject
    lateinit var database: MusicDatabase

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var pendingExternalAudio: ExternalAudioRequest? = null
    private var externalAudioJob: Job? = null
    private var pendingDeepLink by mutableStateOf<Uri?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                try {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playPendingExternalAudio()
                    Timber.tag("MainActivity").d("PlayerConnection created successfully")
                } catch (e: Exception) {
                    Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection, retrying in 500ms")
                    lifecycleScope.launch {
                        delay(500.milliseconds)
                        try {
                            playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                            playPendingExternalAudio()
                            Timber.tag("MainActivity").d("PlayerConnection created on retry")
                        } catch (e2: Exception) {
                            Timber.tag("MainActivity").e(e2, "Failed retry of PlayerConnection")
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeWindowPreferences()
        enqueueExternalAudio(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val themePrefs by remember {
                dataStore.data.map { prefs ->
                    Triple(
                        prefs[DarkModeKey] ?: "SYSTEM_DEFAULT",
                        prefs[PureBlackKey] ?: false,
                        prefs[DynamicThemeKey] ?: true,
                    )
                }
            }.collectAsState(initial = Triple("SYSTEM_DEFAULT", false, true))

            val (darkModePref, pureBlack, dynamicTheme) = themePrefs
            val isDark = when (darkModePref) {
                "ON"  -> true
                "OFF" -> false
                else  -> systemDark
            }
            val artworkUrlFlow = remember(playerConnection) {
                playerConnection?.mediaMetadata?.map { it?.thumbnailUrl } ?: flowOf(null)
            }
            val artworkUrl by artworkUrlFlow.collectAsState(initial = null)

            val isFirstRunFlow = remember {
                dataStore.data.map { preferences ->
                    !(preferences[ONBOARDING_COMPLETED_KEY] ?: false)
                }
            }
            val isFirstRun by isFirstRunFlow.collectAsState(initial = null)

            FridaMusicTheme(
                darkTheme = isDark,
                pureBlack = pureBlack,
                dynamicTheme = dynamicTheme,
                artworkUrl = artworkUrl,
            ) {
                CompositionLocalProvider(LocalPlayerConnection provides playerConnection) {
                    when (isFirstRun) {
                        null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }
                        true -> {
                            OnboardingScreen(onFinish = {})
                        }
                        false -> {
                            MainScreen(
                                pendingDeepLink = pendingDeepLink,
                                onDeepLinkConsumed = { pendingDeepLink = null },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueExternalAudio(intent)
        pendingDeepLink = intent.data
            ?.takeIf { uri -> uri.scheme.equals("fridamusic", ignoreCase = true) }
    }

    private fun enqueueExternalAudio(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme != "content" && uri.scheme != "file") return

        pendingExternalAudio = ExternalAudioRequest(uri, intent.type)
        playPendingExternalAudio()
    }

    private fun playPendingExternalAudio() {
        val request = pendingExternalAudio ?: return
        val connection = playerConnection ?: return
        pendingExternalAudio = null
        externalAudioJob?.cancel()
        externalAudioJob = lifecycleScope.launch {
            val mediaItem = withContext(Dispatchers.IO) {
                buildExternalAudioMediaItem(request)
            }
            if (mediaItem == null) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_file_open, request.uri.lastPathSegment.orEmpty()),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            connection.playQueue(ListQueue(items = listOf(mediaItem)))
        }
    }

    private fun buildExternalAudioMediaItem(request: ExternalAudioRequest): androidx.media3.common.MediaItem? {
        val displayName = queryDisplayName(request.uri) ?: request.uri.lastPathSegment
        val resolvedMimeType = runCatching { contentResolver.getType(request.uri) }.getOrNull()
            ?: request.mimeType
        if (!SupportedLocalAudio.isSupported(displayName, resolvedMimeType) &&
            !SupportedLocalAudio.isSupportedMimeType(request.mimeType)
        ) {
            return null
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationSeconds = -1
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(this, request.uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationSeconds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?.div(1_000L)
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?: -1
        }.onFailure {
            Timber.tag("MainActivity").w(it, "Could not read external audio metadata")
        }
        runCatching { retriever.release() }

        val fallbackTitle = displayName
            ?.substringBeforeLast('.')
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.unknown)
        val resolvedArtist = artist?.trim()?.takeIf(String::isNotBlank)
            ?: getString(R.string.unknown_artist)
        val resolvedAlbum = album?.trim()?.takeIf(String::isNotBlank)

        return MediaMetadata(
            id = request.uri.toString(),
            title = title?.trim()?.takeIf(String::isNotBlank) ?: fallbackTitle,
            artists = listOf(MediaMetadata.Artist(id = null, name = resolvedArtist)),
            duration = durationSeconds,
            album = resolvedAlbum?.let {
                MediaMetadata.Album(id = "${request.uri}#album", title = it)
            },
        ).toMediaItem()
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun observeWindowPreferences() {
        lifecycleScope.launch {
            dataStore.data
                .map { preferences ->
                    (preferences[KeepScreenOn] ?: false) to
                            (preferences[DisableScreenshotKey] ?: false)
                }
                .distinctUntilChanged()
                .collect { (keepScreenOn, disableScreenshots) ->
                    window.setFlagsOrClear(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        keepScreenOn,
                    )
                    window.setFlagsOrClear(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        disableScreenshots,
                    )
                }
        }
    }

    private fun Window.setFlagsOrClear(flag: Int, enabled: Boolean) {
        if (enabled) setFlags(flag, flag) else clearFlags(flag)
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.edit { settings ->
                settings[LastAppOpenAtKey] = System.currentTimeMillis()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }
}
