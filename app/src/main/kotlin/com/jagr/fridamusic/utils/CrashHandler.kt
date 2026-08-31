

package com.jagr.fridamusic.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.jagr.fridamusic.BuildConfig
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val exceptionType = throwable.javaClass.simpleName.ifBlank {
            throwable.javaClass.name.substringAfterLast('.')
        }

        try {
            PendingCrashStore.save(
                context = applicationContext,
                pendingCrash = PendingCrash(
                    crashLog = buildCrashLog(throwable, exceptionType, timestamp),
                    exceptionType = exceptionType,
                    timestamp = timestamp,
                ),
            )
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Pending crash saved")
            }
        } catch (_: Throwable) {
            try {
                Log.e(TAG, "Failed to persist pending crash")
            } catch (_: Throwable) {
                // Preserve delegation of the original fatal even if logging is unavailable.
            }
        }

        val previousHandler = defaultHandler
        if (previousHandler != null && previousHandler !== this) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Delegating fatal to previous handler")
            }
            previousHandler.uncaughtException(thread, throwable)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }

    private fun buildCrashLog(
        throwable: Throwable,
        exceptionType: String,
        timestamp: Long,
    ): String {
        return buildString {
            appendLine("echomusic Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Crash timestamp: $timestamp")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Exception type: $exceptionType")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            appendPrivacySafeStackTrace(throwable)
        }
    }

    private fun StringBuilder.appendPrivacySafeStackTrace(throwable: Throwable) {
        val visited = mutableSetOf<Throwable>()
        var current: Throwable? = throwable
        var isRoot = true

        while (current != null && visited.add(current)) {
            if (!isRoot) {
                append("Caused by: ")
            }
            appendLine(current.javaClass.name)
            current.stackTrace.forEach { element ->
                appendLine("\tat $element")
            }
            current = current.cause
            isRoot = false
        }
    }

    companion object {
        private const val TAG = "CrashHandler"

        const val EXTRA_CRASH_LOG = "crash_log"
        const val EXTRA_EXCEPTION_TYPE = "exception_type"

        fun install(context: Context) {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (previousHandler is CrashHandler) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CrashHandler already installed")
                }
                return
            }

            val handler = CrashHandler(context.applicationContext, previousHandler)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "CrashHandler installed")
                Log.d(
                    TAG,
                    "Previous uncaught handler: ${previousHandler?.javaClass?.name ?: "none"}",
                )
            }
        }
    }
}

internal data class PendingCrash(
    val crashLog: String,
    val exceptionType: String,
    val timestamp: Long,
)

internal object PendingCrashStore {
    private const val PREFERENCES_NAME = "pending_crash"
    private const val KEY_CRASH_LOG = "crash_log"
    private const val KEY_EXCEPTION_TYPE = "exception_type"
    private const val KEY_TIMESTAMP = "timestamp"

    @Synchronized
    fun save(context: Context, pendingCrash: PendingCrash) {
        val saved = preferences(context)
            .edit()
            .putString(KEY_CRASH_LOG, pendingCrash.crashLog)
            .putString(KEY_EXCEPTION_TYPE, pendingCrash.exceptionType)
            .putLong(KEY_TIMESTAMP, pendingCrash.timestamp)
            .commit()

        check(saved) { "Pending crash could not be persisted" }
    }

    @Synchronized
    fun consume(context: Context): PendingCrash? {
        val preferences = preferences(context)
        val crashLog = preferences.getString(KEY_CRASH_LOG, null) ?: return null
        val exceptionType = preferences.getString(KEY_EXCEPTION_TYPE, null)
            ?.takeIf(String::isNotBlank)
            ?: "UnknownError"
        val timestamp = preferences.getLong(KEY_TIMESTAMP, 0L)

        check(preferences.edit().clear().commit()) {
            "Pending crash could not be cleared"
        }

        return PendingCrash(
            crashLog = crashLog,
            exceptionType = exceptionType,
            timestamp = timestamp,
        )
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
