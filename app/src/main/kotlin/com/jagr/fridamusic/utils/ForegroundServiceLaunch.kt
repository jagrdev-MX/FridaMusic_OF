package com.jagr.fridamusic.utils

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import timber.log.Timber

/** Keep restriction handling at the actual start boundary, including library callbacks. */
object ForegroundServiceLaunch {
    fun log(source: String, event: String, error: Throwable? = null) {
        val message = "FGS source=$source event=$event sdk=${Build.VERSION.SDK_INT} " +
            "lifecycle=${ProcessLifecycleOwner.get().lifecycle.currentState} " +
            "exception=${error?.javaClass?.simpleName ?: "none"}"
        // Only fixed labels/class names: never Intent extras or exception messages (may contain URLs).
        if (error == null) Timber.i(message) else Timber.w(message)
        CrashReporter.logForegroundService(message)
    }

    fun isStartRestriction(error: IllegalStateException): Boolean =
        Build.VERSION.SDK_INT >= 31 && error is ForegroundServiceStartNotAllowedException

    fun run(source: String, allowBackgroundIllegalState: Boolean = false, block: () -> Unit): Boolean {
        log(source, "attempt")
        return try {
            block()
            true
        } catch (error: SecurityException) {
            log(source, "rejected", error)
            false
        } catch (error: IllegalStateException) {
            if (!isStartRestriction(error) &&
                !(allowBackgroundIllegalState && error.javaClass == IllegalStateException::class.java)) {
                throw error
            }
            log(source, "rejected", error)
            false
        }
    }

    fun start(context: Context, intent: Intent, source: String): Boolean =
        run(source, allowBackgroundIllegalState = true) {
            checkNotNull(context.startForegroundService(intent)) { "Foreground service unavailable" }
        }
}
