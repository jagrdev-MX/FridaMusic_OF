package com.jagr.fridamusic.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.jagr.fridamusic.BuildConfig
import timber.log.Timber

enum class FixedBannerPlacement {
    HOME_FIXED,
}

@Composable
fun FixedBannerAd(
    placement: FixedBannerPlacement,
    modifier: Modifier = Modifier,
    onVisibleHeightChanged: (Dp) -> Unit = {},
) {
    val context = LocalContext.current
    val adUnitIdResource = remember(context.applicationContext, placement) {
        when (placement) {
            FixedBannerPlacement.HOME_FIXED -> context.resources.getIdentifier(
                HOME_BANNER_RESOURCE_NAME,
                "string",
                context.packageName,
            )
        }
    }
    if (adUnitIdResource == 0) {
        SideEffect { onVisibleHeightChanged(0.dp) }
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val availableWidthDp = maxWidth.value.toInt()
        if (availableWidthDp <= 0) {
            SideEffect { onVisibleHeightChanged(0.dp) }
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val adSize = remember(context, availableWidthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, availableWidthDp)
        }
        val adView = remember(context, adUnitIdResource, adSize) {
            AdView(context).apply {
                adUnitId = context.getString(adUnitIdResource)
                setAdSize(adSize)
            }
        }
        var loaded by remember(adView) { mutableStateOf(false) }

        DisposableEffect(adView, lifecycleOwner, placement) {
            var active = true
            var requestStarted = false
            var terminalFailure = false
            debug(placement, "CREATE")

            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (!active || terminalFailure) return
                    loaded = true
                    val height = with(density) { adSize.getHeightInPixels(context).toDp() }
                    onVisibleHeightChanged(height)
                    debug(placement, "LOAD_SUCCESS")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (!active || terminalFailure) return
                    terminalFailure = true
                    loaded = false
                    onVisibleHeightChanged(0.dp)
                    val reason = when (error.code) {
                        AdRequest.ERROR_CODE_NO_FILL -> "NO_FILL"
                        AdRequest.ERROR_CODE_NETWORK_ERROR -> "NETWORK_ERROR"
                        AdRequest.ERROR_CODE_INTERNAL_ERROR -> "INTERNAL_ERROR"
                        else -> null
                    }
                    debug(
                        placement,
                        "LOAD_FAIL code=%d domain=%s message=%s",
                        error.code,
                        error.domain,
                        error.message,
                    )
                    reason?.let { debug(placement, it) }
                    adView.destroy()
                }

                override fun onAdImpression() = debug(placement, "IMPRESSION")
                override fun onAdOpened() = debug(placement, "OPENED")
                override fun onAdClosed() = debug(placement, "CLOSED")
            }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                if (terminalFailure) return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

            MobileAdsInitializationGate.initialize(context.applicationContext) { initialized ->
                if (!active || terminalFailure || requestStarted) return@initialize
                if (!initialized) {
                    terminalFailure = true
                    onVisibleHeightChanged(0.dp)
                    debug(placement, "INTERNAL_ERROR reason=INITIALIZATION")
                    adView.destroy()
                    return@initialize
                }
                requestStarted = true
                debug(placement, "LOAD_START")
                adView.loadAd(AdRequest.Builder().build())
            }

            onDispose {
                active = false
                loaded = false
                onVisibleHeightChanged(0.dp)
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                adView.adListener = object : AdListener() {}
                adView.destroy()
                debug(placement, "DESTROY")
            }
        }

        if (loaded) {
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun debug(placement: FixedBannerPlacement, event: String, vararg args: Any?) {
    if (BuildConfig.DEBUG) {
        Timber.tag(BANNER_TAG).d("placement=%s event=$event", placement.name, *args)
    }
}

private const val BANNER_TAG = "AdMobBanner"
private const val HOME_BANNER_RESOURCE_NAME = "admob_home_banner_ad_unit_id"
