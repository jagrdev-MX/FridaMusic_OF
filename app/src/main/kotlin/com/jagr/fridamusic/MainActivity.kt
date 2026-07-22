package com.jagr.fridamusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Window // <-- Required import for qualifier warning
import android.view.WindowManager
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
import androidx.lifecycle.lifecycleScope
import com.jagr.fridamusic.constants.DisableScreenshotKey
import com.jagr.fridamusic.constants.DarkModeKey
import com.jagr.fridamusic.constants.DynamicThemeKey
import com.jagr.fridamusic.constants.KeepScreenOn
import com.jagr.fridamusic.constants.PureBlackKey
import androidx.compose.foundation.isSystemInDarkTheme
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.playback.MusicService
import com.jagr.fridamusic.playback.MusicService.MusicBinder
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.screens.MainScreen
import com.jagr.fridamusic.presentation.screens.OnboardingScreen
import com.jagr.fridamusic.presentation.theme.FridaMusicTheme
import com.jagr.fridamusic.utils.dataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds // <-- Importation for delay()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_RECOGNITION = "com.jagr.fridamusic.action.RECOGNITION"
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }

    @Inject
    lateinit var database: MusicDatabase

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                try {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    Timber.tag("MainActivity").d("PlayerConnection created successfully")
                } catch (e: Exception) {
                    Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection, retrying in 500ms")
                    lifecycleScope.launch {
                        delay(500.milliseconds)
                        try {
                            playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
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

            val (darkModePref, pureBlack, _) = themePrefs
            val isDark = when (darkModePref) {
                "ON"  -> true
                "OFF" -> false
                else  -> systemDark
            }

            val isFirstRunFlow = remember {
                dataStore.data.map { preferences ->
                    !(preferences[ONBOARDING_COMPLETED_KEY] ?: false)
                }
            }
            val isFirstRun by isFirstRunFlow.collectAsState(initial = null)

            FridaMusicTheme(darkTheme = isDark, pureBlack = pureBlack) {
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
                            MainScreen()
                        }
                    }
                }
            }
        }
    }

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