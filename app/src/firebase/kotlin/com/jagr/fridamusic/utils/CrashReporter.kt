package com.jagr.fridamusic.utils

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jagr.fridamusic.BuildConfig

object CrashReporter {
    private const val TAG = "CrashlyticsInit"

    fun logForegroundService(message: String) {
        try {
            val crashlytics: FirebaseCrashlytics? = FirebaseCrashlytics.getInstance()
            if (crashlytics == null) {
                Log.w(TAG, "FGS breadcrumb unavailable: Crashlytics instance unavailable")
                return
            }
            crashlytics.log(message)
        } catch (_: RuntimeException) {
            // Crashlytics breadcrumbs are best-effort and must not escape into MusicService.
        }
    }
    fun logDiagnostics(context: Context) {
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        // SDK 19.0.1 has no public Crashlytics collection getter. Its own debug
        // logs report the effective state, including a persisted runtime override.
        Log.i(TAG, "package=${BuildConfig.APPLICATION_ID} type=${BuildConfig.BUILD_TYPE} " +
            "flavor=${BuildConfig.FLAVOR} initialized=${app != null} " +
            "appSuffix=${app?.options?.applicationId?.takeLast(8)} firebaseDefaultCollection=${app?.isDataCollectionDefaultEnabled} crashlyticsCollection=seeSDKLogs")
    }

    fun recordNonFatal(throwable: Throwable): Boolean = runCatching {
        val crashlytics = FirebaseCrashlytics.getInstance()
        // recordException queues locally; the SDK itself respects collection/consent.
        // Do not force collection or call sendUnsentReports to bypass an opt-out.
        crashlytics.recordException(throwable)
        Log.i(TAG, "Nonfatal dispatched to SDK; remote receipt unverified")
        true
    }.getOrElse {
        Log.e(TAG, "Nonfatal unavailable: Firebase initialization failed")
        false
    }
}
