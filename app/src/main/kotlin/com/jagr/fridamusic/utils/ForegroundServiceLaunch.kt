package com.jagr.fridamusic.utils

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import timber.log.Timber

/** Keep restriction handling at the actual start boundary, including library callbacks. */
object ForegroundServiceLaunch {
    private fun processLifecycleState(): String = try {
        // Lifecycle is diagnostic only and must not affect foreground-service control flow.
        val owner: LifecycleOwner? = ProcessLifecycleOwner.get()
        val lifecycle: Lifecycle? = owner?.lifecycle
        val state: Lifecycle.State? = lifecycle?.currentState
        state?.name ?: "unavailable"
    } catch (_: RuntimeException) {
        "unavailable"
    }

    private fun safeLog(source: String, event: String, error: Throwable? = null) {
        try {
            val message = "FGS source=$source event=$event sdk=${Build.VERSION.SDK_INT} " +
                "lifecycle=${processLifecycleState()} " +
                "exception=${error?.javaClass?.simpleName ?: "none"}"
            // Only fixed labels/class names: never Intent extras or exception messages (may contain URLs).
            if (error == null) Timber.i(message) else Timber.w(message)
            CrashReporter.logForegroundService(message)
        } catch (_: RuntimeException) {
            // FGS telemetry is best-effort and must never alter service control flow.
        }
    }

    fun log(source: String, event: String, error: Throwable? = null) = safeLog(source, event, error)

    fun isStartRestriction(error: IllegalStateException): Boolean =
        Build.VERSION.SDK_INT >= 31 && error is ForegroundServiceStartNotAllowedException

    fun run(source: String, allowBackgroundIllegalState: Boolean = false, block: () -> Unit): Boolean {
        safeLog(source, "attempt")
        return try {
            block()
            true
        } catch (error: SecurityException) {
            safeLog(source, "rejected", error)
            false
        } catch (error: IllegalStateException) {
            if (!isStartRestriction(error) &&
                !(allowBackgroundIllegalState && error.javaClass == IllegalStateException::class.java)) {
                throw error
            }
            safeLog(source, "rejected", error)
            false
        }
    }

    fun start(context: Context, intent: Intent, source: String): Boolean =
        run(source, allowBackgroundIllegalState = true) {
            checkNotNull(context.startForegroundService(intent)) { "Foreground service unavailable" }
        }
}
