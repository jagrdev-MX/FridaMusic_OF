package com.jagr.fridamusic.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReporter {
    fun recordNonFatal(throwable: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }
}
