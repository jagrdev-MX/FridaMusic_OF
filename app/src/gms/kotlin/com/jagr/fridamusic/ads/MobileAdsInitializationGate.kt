package com.jagr.fridamusic.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import timber.log.Timber
import kotlin.concurrent.thread

internal object MobileAdsInitializationGate {
    private enum class State { IDLE, INITIALIZING, INITIALIZED }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    private var state = State.IDLE

    fun initialize(context: Context, onFinished: (Boolean) -> Unit) {
        var shouldInitialize = false
        synchronized(this) {
            when (state) {
                State.INITIALIZED -> {
                    mainHandler.post { onFinished(true) }
                    return
                }
                State.INITIALIZING -> callbacks += onFinished
                State.IDLE -> {
                    state = State.INITIALIZING
                    callbacks += onFinished
                    shouldInitialize = true
                }
            }
        }
        if (!shouldInitialize) return

        val applicationContext = context.applicationContext
        try {
            thread(isDaemon = true, name = "MobileAdsInit") {
                try {
                    MobileAds.initialize(applicationContext) { initializationStatus ->
                        Timber.tag(TAG).d(
                            "Mobile Ads adapter initialization completed with %d adapters",
                            initializationStatus.adapterStatusMap.size,
                        )
                    }
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Mobile Ads background initialization failed")
                }
            }
            // The legacy SDK can initialize from the first ad request. Do not hold every
            // placement for up to the adapter initialization callback's 30-second timeout.
            mainHandler.postDelayed({ finish(success = true) }, REQUEST_READY_DELAY_MS)
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Mobile Ads initialization dispatch failed")
            finish(success = false)
        }
    }

    private fun finish(success: Boolean) {
        val pendingCallbacks = synchronized(this) {
            state = if (success) State.INITIALIZED else State.IDLE
            callbacks.toList().also { callbacks.clear() }
        }
        pendingCallbacks.forEach { callback -> mainHandler.post { callback(success) } }
    }

    private const val TAG = "MobileAdsInit"
    private const val REQUEST_READY_DELAY_MS = 250L
}
