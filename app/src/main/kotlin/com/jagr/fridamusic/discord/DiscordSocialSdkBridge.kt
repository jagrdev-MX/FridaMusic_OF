package com.jagr.fridamusic.discord

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import com.jagr.fridamusic.BuildConfig
import timber.log.Timber
import java.lang.ref.WeakReference

data class DiscordRichPresence(
    val title: String,
    val state: String,
    val album: String?,
    val artworkUrl: String?,
    val detailsUrl: String?,
    val startTimeSeconds: Long?,
    val endTimeSeconds: Long?,
    val paused: Boolean,
    val buttonLabel: String?,
    val buttonUrl: String?,
)

/** Thin JNI boundary for Discord Social SDK Direct Rich Presence on Android. */
object DiscordSocialSdkBridge {
    private const val TAG = "DiscordSocialSdk"
    private const val DISCORD_PACKAGE = "com.discord"

    @Volatile
    private var nativeLoadAttempted = false

    @Volatile
    private var nativeLoaded = false

    @Volatile
    private var engineActivityConfigured = false

    private var engineActivityRef: WeakReference<Activity>? = null

    @Synchronized
    fun initializeAndroid(context: Context): Boolean {
        if (!BuildConfig.DISCORD_SOCIAL_SDK_AVAILABLE) {
            debugLog("SDK unavailable; add the official discord_partner_sdk.aar")
            return false
        }
        val activity = context.findActivity() ?: run {
            debugLog("SDK initialization skipped: Activity unavailable")
            return false
        }
        if (!loadNativeLibrary()) return false

        return runCatching {
            if (engineActivityRef?.get() !== activity) {
                val initializer = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit")
                val setEngineActivity = initializer.getMethod("setEngineActivity", Activity::class.java)
                setEngineActivity.invoke(null, activity)
                engineActivityRef = WeakReference(activity)
                engineActivityConfigured = true
                debugLog("DiscordSDK: engine activity configured")
            }
            debugLog("Discord Social SDK Application ID: ${BuildConfig.DISCORD_APPLICATION_ID_LONG}")
            val initialized = nativeInitialize(BuildConfig.DISCORD_APPLICATION_ID_LONG, BuildConfig.DEBUG)
            debugLog(if (initialized) "Discord Presence initializing: native client ready" else "Discord Presence initializing failed")
            initialized
        }.getOrElse { error ->
            debugLog("SDK initialization failed", error)
            false
        }
    }

    fun updatePresence(context: Context, presence: DiscordRichPresence): Boolean {
        if (!BuildConfig.DISCORD_SOCIAL_SDK_AVAILABLE || !engineActivityConfigured || !loadNativeLibrary()) {
            debugLog("update failed: SDK is not initialized")
            return false
        }
        if (!isDiscordInstalled(context)) {
            debugLog("connect failed: Discord is not installed")
            return false
        }

        debugLog("DiscordPresenceManager -> DiscordSocialSdkBridge: update requested")
        return runCatching {
            if (!nativeInitialize(BuildConfig.DISCORD_APPLICATION_ID_LONG, BuildConfig.DEBUG)) {
                return@runCatching false
            }
            nativeUpdatePresence(
                title = presence.title,
                state = presence.state,
                album = presence.album,
                artworkUrl = presence.artworkUrl,
                detailsUrl = presence.detailsUrl,
                startTimeSeconds = presence.startTimeSeconds ?: 0L,
                endTimeSeconds = presence.endTimeSeconds ?: 0L,
                paused = presence.paused,
                buttonLabel = presence.buttonLabel,
                buttonUrl = presence.buttonUrl,
            )
        }.getOrElse { error ->
            debugLog("update failed", error)
            false
        }
    }

    fun publishDebugTestPresence(context: Context): Boolean {
        if (!BuildConfig.DEBUG || !engineActivityConfigured || !loadNativeLibrary()) return false
        if (!isDiscordInstalled(context)) {
            debugLog("minimal update failed: Discord is not installed")
            return false
        }
        return runCatching {
            nativeInitialize(BuildConfig.DISCORD_APPLICATION_ID_LONG, true) &&
                nativeUpdateTestPresence()
        }.onSuccess { queued ->
            debugLog(if (queued) "minimal static Presence requested" else "minimal static Presence request failed")
        }.getOrElse { error ->
            debugLog("minimal static Presence failed", error)
            false
        }
    }

    fun clearPresence() {
        if (!nativeLoaded) return
        runCatching { nativeClearPresence() }
            .onSuccess { debugLog("clear") }
            .onFailure { debugLog("clear failed", it) }
    }

    fun shutdown() {
        if (!nativeLoaded) return
        runCatching { nativeShutdown() }
            .onFailure { debugLog("shutdown failed", it) }
    }

    fun isDiscordInstalled(context: Context): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    DISCORD_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(DISCORD_PACKAGE, 0)
            }
        }.isSuccess

    @Synchronized
    private fun loadNativeLibrary(): Boolean {
        if (nativeLoadAttempted) return nativeLoaded
        nativeLoadAttempted = true
        nativeLoaded = try {
            System.loadLibrary("frida_discord_presence")
            debugLog("native library loaded: frida_discord_presence")
            true
        } catch (error: UnsatisfiedLinkError) {
            debugLog("native library load failed: frida_discord_presence", error)
            false
        } catch (error: SecurityException) {
            debugLog("native library load denied: frida_discord_presence", error)
            false
        }
        return nativeLoaded
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun debugLog(message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (error == null) Timber.tag(TAG).d(message) else Timber.tag(TAG).d(error, message)
    }

    private external fun nativeInitialize(applicationId: Long, debug: Boolean): Boolean

    private external fun nativeUpdateTestPresence(): Boolean

    private external fun nativeUpdatePresence(
        title: String,
        state: String,
        album: String?,
        artworkUrl: String?,
        detailsUrl: String?,
        startTimeSeconds: Long,
        endTimeSeconds: Long,
        paused: Boolean,
        buttonLabel: String?,
        buttonUrl: String?,
    ): Boolean

    private external fun nativeClearPresence()

    private external fun nativeShutdown()
}
