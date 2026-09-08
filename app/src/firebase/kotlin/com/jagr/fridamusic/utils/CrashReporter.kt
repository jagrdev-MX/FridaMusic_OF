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
            FirebaseCrashlytics.getInstance().log(message)
        } catch (_: IllegalStateException) {
            Log.w(TAG, "FGS breadcrumb unavailable: Firebase not initialized")
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
