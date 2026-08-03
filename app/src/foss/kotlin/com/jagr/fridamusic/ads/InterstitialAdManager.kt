package com.jagr.fridamusic.ads

import android.app.Activity
import timber.log.Timber

class InterstitialAdManager(activity: Activity?) {
    fun load() = Unit

    fun show() {
        Timber.tag(TAG).d("Interstitial unavailable in FOSS build")
    }

    fun release() = Unit

    private companion object {
        const val TAG = "InterstitialAd"
    }
}
