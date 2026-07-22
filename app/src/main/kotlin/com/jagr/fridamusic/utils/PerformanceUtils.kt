package com.jagr.fridamusic.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

fun Context.isLowEndDevice(): Boolean {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    if (am.isLowRamDevice) return true
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    val totalRamMb = info.totalMem / (1024 * 1024)
    return totalRamMb <= 3072
}

@Composable
fun rememberIsLowEndDevice(): Boolean {
    val context = LocalContext.current
    return remember { context.isLowEndDevice() }
}