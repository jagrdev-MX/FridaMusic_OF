package com.jagr.fridamusic.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.jagr.fridamusic.R
import timber.log.Timber

class InterstitialAdManager(activity: Activity?) {
    private var activity: Activity? = activity
    private var interstitialAd: InterstitialAd? = null
    private var isInitializing = false
    private var isInitialized = false
    private var isLoading = false
    private var showWhenLoaded = false
    private var isReleased = false

    fun load() {
        if (isReleased || isLoading || interstitialAd != null) return

        val currentActivity = activity
        if (currentActivity == null) {
            Timber.tag(TAG).w("Interstitial load skipped: Activity unavailable")
            return
        }

        if (!isInitialized) {
            initializeAndLoad(currentActivity)
            return
        }

        loadAd(currentActivity)
    }

    fun show() {
        if (isReleased) return

        val currentActivity = activity
        if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
            showWhenLoaded = false
            Timber.tag(TAG).w("Interstitial show skipped: Activity unavailable")
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            showWhenLoaded = true
            Timber.tag(TAG).d("Interstitial unavailable; loading on demand")
            load()
            return
        }

        showWhenLoaded = false
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Timber.tag(TAG).d("Interstitial shown")
            }

            override fun onAdDismissedFullScreenContent() {
                Timber.tag(TAG).d("Interstitial dismissed")
                load()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Timber.tag(TAG).w(
                    "Interstitial failed to show: code=%d domain=%s message=%s",
                    adError.code,
                    adError.domain,
                    adError.message,
                )
                load()
            }
        }

        try {
            ad.show(currentActivity)
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Interstitial show failed")
            load()
        }
    }

    fun release() {
        isReleased = true
        showWhenLoaded = false
        isLoading = false
        interstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        activity = null
    }

    private fun initializeAndLoad(currentActivity: Activity) {
        if (isInitializing) return

        isInitializing = true
        try {
            MobileAds.initialize(currentActivity.applicationContext) { initializationStatus ->
                isInitializing = false
                if (!isReleased) {
                    isInitialized = true
                    Timber.tag(TAG).d(
                        "Mobile Ads initialized with %d adapters",
                        initializationStatus.adapterStatusMap.size,
                    )
                    load()
                }
            }
        } catch (error: Exception) {
            isInitializing = false
            showWhenLoaded = false
            Timber.tag(TAG).w(error, "Mobile Ads initialization failed")
        }
    }

    private fun loadAd(currentActivity: Activity) {
        isLoading = true
        try {
            InterstitialAd.load(
                currentActivity.applicationContext,
                currentActivity.getString(R.string.admob_interstitial_ad_unit_id),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoading = false
                        if (isReleased) return

                        interstitialAd = ad
                        Timber.tag(TAG).d("Interstitial loaded")
                        if (showWhenLoaded) show()
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        isLoading = false
                        interstitialAd = null
                        showWhenLoaded = false
                        Timber.tag(TAG).w(
                            "Interstitial failed to load: code=%d domain=%s message=%s",
                            adError.code,
                            adError.domain,
                            adError.message,
                        )
                    }
                },
            )
        } catch (error: Exception) {
            isLoading = false
            showWhenLoaded = false
            Timber.tag(TAG).w(error, "Interstitial load failed")
        }
    }

    private companion object {
        const val TAG = "InterstitialAd"
    }
}
