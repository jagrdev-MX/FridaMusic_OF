package com.jagr.fridamusic.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.jagr.fridamusic.R
import com.jagr.fridamusic.utils.MemoryDiagnostics
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class InterstitialAdManager(
    activity: Activity?,
    private val adUnitIdRes: Int = R.string.admob_interstitial_ad_unit_id,
) {
    private var activity: Activity? = activity
    private var interstitialAd: InterstitialAd? = null
    private var isInitializing = false
    private var isInitialized = false
    private var isLoading = false
    private var showWhenLoaded = false
    private var pendingOnShown: (() -> Unit)? = null
    private var pendingCompletion: (() -> Unit)? = null
    private var isReleased = false

    fun load() {
        if (isReleased) {
            finishPendingShow()
            return
        }
        if (isLoading || interstitialAd != null) return

        val currentActivity = activity
        if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
            Timber.tag(TAG).w("Interstitial load skipped: Activity unavailable")
            finishPendingShow()
            return
        }

        if (!isInitialized) {
            initializeAndLoad(currentActivity)
            return
        }

        loadAd(currentActivity)
    }

    fun show() {
        show(onFinished = {})
    }

    fun show(onFinished: () -> Unit) {
        show(onShown = {}, onFinished = onFinished)
    }

    fun show(
        onShown: () -> Unit,
        onFinished: () -> Unit,
    ) {
        val shown = once(onShown)
        val completion = once(onFinished)
        if (isReleased) {
            completion()
            return
        }
        if (pendingCompletion != null) {
            Timber.tag(TAG).w("Interstitial show ignored: another show is pending")
            completion()
            return
        }
        pendingOnShown = shown
        pendingCompletion = completion

        val currentActivity = activity
        if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
            Timber.tag(TAG).w("Interstitial show skipped: Activity unavailable")
            finishPendingShow()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            showWhenLoaded = true
            Timber.tag(TAG).d("Interstitial unavailable; loading on demand")
            load()
            return
        }

        showAd(currentActivity, ad)
    }

    private fun showAd(currentActivity: Activity, ad: InterstitialAd) {
        showWhenLoaded = false
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Timber.tag(TAG).d("Interstitial shown")
                notifyShown()
            }

            override fun onAdDismissedFullScreenContent() {
                ad.fullScreenContentCallback = null
                showWhenLoaded = false
                Timber.tag(TAG).d("Interstitial dismissed")
                MemoryDiagnostics.log("After interstitial dismissed")
                finishPendingShow()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                ad.fullScreenContentCallback = null
                showWhenLoaded = false
                Timber.tag(TAG).w(
                    "Interstitial failed to show: code=%d domain=%s message=%s",
                    adError.code,
                    adError.domain,
                    adError.message,
                )
                MemoryDiagnostics.log("After interstitial show failure")
                finishPendingShow()
            }
        }

        try {
            ad.show(currentActivity)
        } catch (error: Exception) {
            ad.fullScreenContentCallback = null
            showWhenLoaded = false
            Timber.tag(TAG).w(error, "Interstitial show failed")
            MemoryDiagnostics.log("After interstitial show exception")
            finishPendingShow()
        }
    }

    fun release() {
        isReleased = true
        showWhenLoaded = false
        isLoading = false
        interstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        activity = null
        finishPendingShow()
    }

    private fun initializeAndLoad(currentActivity: Activity) {
        if (isInitializing) return

        isInitializing = true
        MobileAdsInitializationGate.initialize(currentActivity.applicationContext) { initialized ->
            isInitializing = false
            if (isReleased) return@initialize
            if (initialized) {
                isInitialized = true
                load()
            } else {
                finishPendingShow()
            }
        }
    }

    private fun loadAd(currentActivity: Activity) {
        isLoading = true
        MemoryDiagnostics.log("Before interstitial load")
        try {
            InterstitialAd.load(
                currentActivity.applicationContext,
                currentActivity.getString(adUnitIdRes),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoading = false
                        if (isReleased) return

                        interstitialAd = ad
                        Timber.tag(TAG).d("Interstitial loaded")
                        MemoryDiagnostics.log("After interstitial load")
                        if (showWhenLoaded) {
                            val showActivity = activity
                            if (showActivity == null || showActivity.isFinishing || showActivity.isDestroyed) {
                                finishPendingShow()
                            } else {
                                showAd(showActivity, ad)
                            }
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        isLoading = false
                        interstitialAd = null
                        Timber.tag(TAG).w(
                            "Interstitial failed to load: code=%d domain=%s message=%s",
                            adError.code,
                            adError.domain,
                            adError.message,
                        )
                        MemoryDiagnostics.log("After interstitial load failure")
                        finishPendingShow()
                    }
                },
            )
        } catch (error: Exception) {
            isLoading = false
            Timber.tag(TAG).w(error, "Interstitial load failed")
            MemoryDiagnostics.log("After interstitial load exception")
            finishPendingShow()
        }
    }

    private fun finishPendingShow() {
        showWhenLoaded = false
        pendingOnShown = null
        val completion = pendingCompletion
        pendingCompletion = null
        completion?.invoke()
    }

    private fun notifyShown() {
        val callback = pendingOnShown
        pendingOnShown = null
        callback?.invoke()
    }

    private fun once(callback: () -> Unit): () -> Unit {
        val completed = AtomicBoolean(false)
        return {
            if (completed.compareAndSet(false, true)) callback()
        }
    }

    companion object {
        private const val TAG = "InterstitialAd"

        fun forSongSync(activity: Activity?): InterstitialAdManager =
            InterstitialAdManager(
                activity = activity,
                adUnitIdRes = R.string.admob_song_sync_interstitial_ad_unit_id,
            )
    }
}
