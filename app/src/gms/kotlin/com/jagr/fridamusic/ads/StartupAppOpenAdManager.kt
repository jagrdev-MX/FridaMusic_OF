package com.jagr.fridamusic.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.jagr.fridamusic.R
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class StartupAppOpenAdManager(activity: Activity) {
    private val activityReference = WeakReference(activity)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.elapsedRealtime()
    private var appOpenAd: AppOpenAd? = null
    private var released = false
    private var expired = false
    private var showing = false

    private val showRunnable = Runnable { tryShow() }
    private val expireRunnable = Runnable {
        expired = true
        appOpenAd = null
        Timber.tag(TAG).d("placement=STARTUP event=SHOW_WINDOW_EXPIRED")
    }

    fun start() {
        if (!STARTED_THIS_PROCESS.compareAndSet(false, true)) return
        val activity = activityReference.get() ?: return
        mainHandler.postDelayed(showRunnable, SHOW_DELAY_MS)
        mainHandler.postDelayed(expireRunnable, SHOW_DEADLINE_MS)

        MobileAdsInitializationGate.initialize(activity.applicationContext) { initialized ->
            if (!initialized || released || expired) return@initialize
            load(activity.applicationContext)
        }
    }

    private fun load(context: Context) {
        Timber.tag(TAG).d("placement=STARTUP event=LOAD_START")
        try {
            AppOpenAd.load(
                context,
                context.getString(R.string.admob_startup_app_open_ad_unit_id),
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        if (released || expired) {
                            Timber.tag(TAG).d(
                                "placement=STARTUP event=LOAD_DISCARDED reason=WINDOW_EXPIRED",
                            )
                            return
                        }
                        appOpenAd = ad
                        Timber.tag(TAG).d("placement=STARTUP event=LOAD_SUCCESS")
                        if (SystemClock.elapsedRealtime() - startedAt >= SHOW_DELAY_MS) tryShow()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        val reason = when (error.code) {
                            AdRequest.ERROR_CODE_NO_FILL -> "ADMOB_NO_FILL"
                            AdRequest.ERROR_CODE_NETWORK_ERROR -> "NETWORK_ERROR"
                            AdRequest.ERROR_CODE_INTERNAL_ERROR -> "INTERNAL_ERROR"
                            else -> "GENERIC_ERROR"
                        }
                        Timber.tag(TAG).w(
                            "placement=STARTUP event=LOAD_FAIL reason=%s code=%d domain=%s message=%s responseInfo=%s",
                            reason,
                            error.code,
                            error.domain,
                            error.message,
                            error.responseInfo,
                        )
                    }
                },
            )
        } catch (error: Exception) {
            Timber.tag(TAG).w(
                error,
                "placement=STARTUP event=LOAD_FAIL reason=EXCEPTION",
            )
        }
    }

    private fun tryShow() {
        if (released || expired || showing) return
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (elapsed < SHOW_DELAY_MS || elapsed > SHOW_DEADLINE_MS) return
        val activity = activityReference.get() ?: return
        val lifecycleReady = (activity as? LifecycleOwner)
            ?.lifecycle
            ?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED) != false
        if (activity.isFinishing || activity.isDestroyed || !lifecycleReady) return
        val ad = appOpenAd ?: return

        appOpenAd = null
        showing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Timber.tag(TAG).d("placement=STARTUP event=SHOW_SUCCESS")
            }

            override fun onAdDismissedFullScreenContent() {
                showing = false
                ad.fullScreenContentCallback = null
                Timber.tag(TAG).d("placement=STARTUP event=DISMISSED")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                ad.fullScreenContentCallback = null
                Timber.tag(TAG).w(
                    "placement=STARTUP event=SHOW_FAIL code=%d domain=%s message=%s",
                    error.code,
                    error.domain,
                    error.message,
                )
            }
        }
        ad.show(activity)
    }

    fun release() {
        released = true
        mainHandler.removeCallbacks(showRunnable)
        mainHandler.removeCallbacks(expireRunnable)
        appOpenAd = null
    }

    private companion object {
        const val TAG = "AdMobAppOpen"
        const val SHOW_DELAY_MS = 3_500L
        const val SHOW_DEADLINE_MS = 4_500L
        val STARTED_THIS_PROCESS = AtomicBoolean(false)
    }
}
