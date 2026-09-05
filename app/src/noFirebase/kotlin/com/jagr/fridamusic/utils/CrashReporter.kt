package com.jagr.fridamusic.utils

import android.content.Context

object CrashReporter {
    fun logDiagnostics(context: Context) = Unit
    fun recordNonFatal(throwable: Throwable): Boolean = false
}
