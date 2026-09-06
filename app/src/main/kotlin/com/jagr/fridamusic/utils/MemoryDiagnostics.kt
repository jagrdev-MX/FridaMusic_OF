package com.jagr.fridamusic.utils

import android.os.Debug
import com.jagr.fridamusic.BuildConfig
import timber.log.Timber

object MemoryDiagnostics {
    fun log(point: String) {
        if (!BuildConfig.DEBUG) return

        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        Timber.tag(TAG).d(
            "%s | javaUsed=%.1fMiB javaTotal=%.1fMiB javaMax=%.1fMiB native=%.1fMiB",
            point,
            usedBytes.toMiB(),
            runtime.totalMemory().toMiB(),
            runtime.maxMemory().toMiB(),
            Debug.getNativeHeapAllocatedSize().toMiB(),
        )
    }

    private fun Long.toMiB(): Double = this / BYTES_PER_MEBIBYTE

    private const val TAG = "MemoryDiagnostics"
    private const val BYTES_PER_MEBIBYTE = 1024.0 * 1024.0
}
