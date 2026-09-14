package com.jagr.fridamusic.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicLong

enum class NativeAdPlacement {
    HOME_1,
    HOME_2,
    HOME_3,
    HOME_4,
    HOME_FILTER_TOP,
    SEARCH_TOP,
    LIBRARY_LARGE,
    NOW_PLAYING_LARGE,
}

enum class NativeAdStyle {
    INLINE,
    LARGE,
}

class NativeAdSlotState internal constructor()

@Composable
fun rememberNativeAdSlotState(
    placement: NativeAdPlacement,
    presentationCycleKey: String,
): NativeAdSlotState = remember(placement, presentationCycleKey) { NativeAdSlotState() }

@Composable
fun rememberNativeAdVisitToken(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var token by remember(lifecycleOwner) {
        mutableLongStateOf(NativeAdVisitTokens.next())
    }
    DisposableEffect(lifecycleOwner) {
        var wasStopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_START -> if (wasStopped) {
                    token = NativeAdVisitTokens.next()
                    wasStopped = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return token
}

@Composable
fun NativeAdSlot(
    placement: NativeAdPlacement,
    presentationCycleKey: String,
    style: NativeAdStyle = NativeAdStyle.INLINE,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onLoadFinished: ((Boolean) -> Unit)? = null,
    state: NativeAdSlotState = rememberNativeAdSlotState(placement, presentationCycleKey),
) = Unit

object AdFrequencyGate {
    fun shouldShowLibraryAdThisVisit(): Boolean = false
    fun onMediaIdObserved(mediaId: String?): Boolean = false
    fun hasPendingNowPlayingAd(): Boolean = false
    fun pendingNowPlayingAdCycle(): Long? = null
    fun consumeNowPlayingAd() = Unit
}

private object NativeAdVisitTokens {
    private val value = AtomicLong(0L)

    fun next(): Long = value.incrementAndGet()
}
