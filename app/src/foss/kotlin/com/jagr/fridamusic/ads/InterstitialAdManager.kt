package com.jagr.fridamusic.ads

import android.app.Activity
import timber.log.Timber

class InterstitialAdManager(activity: Activity?) {
    fun load() = Unit

    fun show() {
        Timber.tag(TAG).d("Interstitial unavailable in FOSS build")
    }

    fun show(onFinished: () -> Unit) {
        Timber.tag(TAG).d("Interstitial unavailable in FOSS build")
        onFinished()
    }

    fun show(
        onShown: () -> Unit,
        onFinished: () -> Unit,
    ) {
        Timber.tag(TAG).d("Interstitial unavailable in FOSS build")
        onFinished()
    }

    fun release() = Unit

    companion object {
        private const val TAG = "InterstitialAd"

        fun forSongSync(activity: Activity?): InterstitialAdManager =
            InterstitialAdManager(activity)
    }
}
