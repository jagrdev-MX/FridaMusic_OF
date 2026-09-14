package com.jagr.fridamusic.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.jagr.fridamusic.R
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

enum class NativeAdPlacement(@StringRes internal val adUnitIdRes: Int) {
    HOME_1(R.string.admob_native_home_1_ad_unit_id),
    HOME_2(R.string.admob_native_home_2_ad_unit_id),
    HOME_3(R.string.admob_native_home_3_ad_unit_id),
    HOME_4(R.string.admob_native_home_4_ad_unit_id),
    HOME_FILTER_TOP(R.string.admob_native_home_filter_ad_unit_id),
    SEARCH_TOP(R.string.admob_native_search_ad_unit_id),
    LIBRARY_LARGE(R.string.admob_native_library_ad_unit_id),
    NOW_PLAYING_LARGE(R.string.admob_native_now_playing_ad_unit_id),
}

enum class NativeAdStyle {
    INLINE,
    LARGE,
}

@Stable
class NativeAdSlotState internal constructor(
    internal val placement: NativeAdPlacement,
    internal val presentationCycleKey: String,
) {
    internal var nativeAd by mutableStateOf<NativeAd?>(null)
        private set
    internal var changeVersion by mutableIntStateOf(0)
        private set

    private var disposed = false
    private var attached = false
    private var loading = false
    private var initialAttemptDone = false
    private var retryUsed = false
    private var exhausted = false
    private var retryScheduledLogged = false
    private var outcomeDelivered = false
    private var cycleStarted = false
    private var initialFailureElapsedRealtime: Long? = null

    @Synchronized
    internal fun attach(): Boolean {
        if (disposed) return false
        attached = true
        if (!cycleStarted) {
            cycleStarted = true
            logFlow("CYCLE_START")
        }
        return true
    }

    @Synchronized
    internal fun detach() {
        attached = false
    }

    @Synchronized
    internal fun canStartInitialAttempt(): Boolean =
        attached && !disposed && !loading && nativeAd == null && !initialAttemptDone && !exhausted

    @Synchronized
    internal fun canScheduleRetry(): Boolean =
        attached && !disposed && !loading && nativeAd == null &&
            initialAttemptDone && !retryUsed && !exhausted && initialFailureElapsedRealtime != null

    @Synchronized
    internal fun retryDelayRemainingMs(): Long {
        val failedAt = initialFailureElapsedRealtime ?: return NATIVE_AD_RETRY_DELAY_MS
        val elapsed = android.os.SystemClock.elapsedRealtime() - failedAt
        return (NATIVE_AD_RETRY_DELAY_MS - elapsed).coerceAtLeast(0L)
    }

    @Synchronized
    internal fun logRetryScheduledOnce() {
        if (!retryScheduledLogged) {
            retryScheduledLogged = true
            logFlow("RETRY_SCHEDULED", "delayMs=$NATIVE_AD_RETRY_DELAY_MS")
        }
    }

    @Synchronized
    internal fun startLoad(context: Context, attempt: Int): Boolean {
        if (!attached || disposed || loading || nativeAd != null || exhausted) return false
        when (attempt) {
            1 -> {
                if (initialAttemptDone) return false
                initialAttemptDone = true
            }
            2 -> {
                if (!initialAttemptDone || retryUsed || initialFailureElapsedRealtime == null) return false
                retryUsed = true
                logFlow("RETRY_START")
            }
            else -> return false
        }
        loading = true
        logFlow("LOAD_START", "attempt=$attempt")
        MobileAdsInitializationGate.initialize(context) { initialized ->
            if (!initialized) {
                finishFailure(attempt, type = "INTERNAL", detail = "Mobile Ads initialization failed")
                return@initialize
            }
            requestAd(context, attempt)
        }
        return true
    }

    private fun requestAd(context: Context, attempt: Int) {
        val adUnitId = context.getString(placement.adUnitIdRes)
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()
        val nativeAdOptions = NativeAdOptions.Builder()
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .setVideoOptions(videoOptions)
            .build()

        try {
            AdLoader.Builder(context, adUnitId)
                .forNativeAd { loadedAd ->
                    val accepted = synchronized(this) {
                        if (disposed || !attached) {
                            loading = false
                            false
                        } else {
                            nativeAd?.let(::destroyAd)
                            nativeAd = loadedAd
                            loading = false
                            changeVersion += 1
                            true
                        }
                    }
                    if (!accepted) {
                        destroyAd(loadedAd)
                        finishDetachedAttempt(attempt)
                        return@forNativeAd
                    }
                    logFlow("LOAD_SUCCESS", "attempt=$attempt")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        val failureType = when (error.code) {
                            AdRequest.ERROR_CODE_NO_FILL -> "NO_FILL"
                            AdRequest.ERROR_CODE_NETWORK_ERROR -> "NETWORK"
                            AdRequest.ERROR_CODE_INTERNAL_ERROR -> "INTERNAL"
                            else -> "GENERIC"
                        }
                        Timber.tag(FLOW_TAG).w(
                            "placement=%s cycle=%s event=LOAD_FAIL attempt=%d type=%s code=%d " +
                                "domain=%s message=%s responseInfo=%s",
                            placement.name,
                            presentationCycleKey,
                            attempt,
                            failureType,
                            error.code,
                            error.domain,
                            error.message,
                            error.responseInfo?.toString() ?: "unavailable",
                        )
                        finishFailure(attempt, failureType)
                    }
                })
                .withNativeAdOptions(nativeAdOptions)
                .build()
                .loadAd(AdRequest.Builder().build())
        } catch (error: Exception) {
            Timber.tag(FLOW_TAG).w(
                error,
                "placement=%s cycle=%s event=LOAD_FAIL attempt=%d type=GENERIC",
                placement.name,
                presentationCycleKey,
                attempt,
            )
            finishFailure(attempt, type = "GENERIC")
        }
    }

    @Synchronized
    private fun finishFailure(attempt: Int, type: String, detail: String? = null) {
        if (disposed) return
        loading = false
        if (attempt == 1) {
            initialFailureElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        } else {
            exhausted = true
            logFlow("CYCLE_EXHAUSTED", "type=$type${detail?.let { " detail=$it" }.orEmpty()}")
        }
        changeVersion += 1
    }

    @Synchronized
    private fun finishDetachedAttempt(attempt: Int) {
        if (disposed) return
        if (attempt == 1) {
            initialFailureElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        } else {
            exhausted = true
            logFlow("CYCLE_EXHAUSTED", "type=DETACHED")
        }
        changeVersion += 1
    }

    @Synchronized
    internal fun isExhausted(): Boolean = exhausted

    @Synchronized
    internal fun deliverOutcomeOnce(): Boolean {
        if (outcomeDelivered) return false
        outcomeDelivered = true
        return true
    }

    @Synchronized
    internal fun dispose() {
        if (disposed) return
        disposed = true
        attached = false
        loading = false
        nativeAd?.let(::destroyAd)
        nativeAd = null
        logFlow("CYCLE_END")
    }

    private fun destroyAd(ad: NativeAd) {
        ad.destroy()
        logFlow("AD_DESTROY")
    }

    private fun logFlow(event: String, detail: String? = null) {
        Timber.tag(FLOW_TAG).d(
            "placement=%s cycle=%s event=%s%s",
            placement.name,
            presentationCycleKey,
            event,
            detail?.let { " $it" }.orEmpty(),
        )
    }

    private companion object {
        private const val FLOW_TAG = "NativeAdFlow"
    }
}

@Composable
fun rememberNativeAdSlotState(
    placement: NativeAdPlacement,
    presentationCycleKey: String,
): NativeAdSlotState {
    val state = remember(placement, presentationCycleKey) {
        NativeAdSlotState(placement, presentationCycleKey)
    }
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    return state
}

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
) {
    if (!enabled) return

    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val currentOnLoadFinished by rememberUpdatedState(onLoadFinished)
    val changeVersion = state.changeVersion

    DisposableEffect(state) {
        state.attach()
        onDispose { state.detach() }
    }

    LaunchedEffect(state, applicationContext, changeVersion) {
        when {
            state.nativeAd != null -> {
                if (state.deliverOutcomeOnce()) currentOnLoadFinished?.invoke(true)
            }
            state.canStartInitialAttempt() -> state.startLoad(applicationContext, attempt = 1)
            state.canScheduleRetry() -> {
                state.logRetryScheduledOnce()
                delay(state.retryDelayRemainingMs())
                state.startLoad(applicationContext, attempt = 2)
            }
            state.isExhausted() -> {
                if (state.deliverOutcomeOnce()) currentOnLoadFinished?.invoke(false)
            }
        }
    }

    val ad = state.nativeAd ?: return
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val slotHeight = when (style) {
        NativeAdStyle.INLINE -> 148.dp
        NativeAdStyle.LARGE -> (screenHeight * 0.42f).coerceIn(240.dp, 360.dp)
    }
    val viewHolder = remember(ad, style) { NativeAdViewHolder() }

    DisposableEffect(viewHolder) {
        onDispose { viewHolder.view?.destroy() }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(slotHeight),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 1.dp,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { viewContext ->
                createNativeAdView(viewContext, style).also { viewHolder.view = it }
            },
            update = { adView ->
                bindNativeAd(
                    adView = adView,
                    nativeAd = ad,
                    primaryColor = primaryColor.toArgb(),
                    onPrimaryColor = onPrimaryColor.toArgb(),
                    onSurfaceColor = onSurfaceColor.toArgb(),
                    onSurfaceVariantColor = onSurfaceVariantColor.toArgb(),
                )
            },
        )
    }
}

object AdFrequencyGate {
    private var libraryVisitCount = 0
    private var lastMediaId: String? = null
    private var trackTransitionCount = 0
    private var nowPlayingAdPending = false
    private var nowPlayingAdCycle = 0L

    @Synchronized
    fun shouldShowLibraryAdThisVisit(): Boolean {
        libraryVisitCount += 1
        return libraryVisitCount % LIBRARY_VISIT_FREQUENCY == 0
    }

    @Synchronized
    fun onMediaIdObserved(mediaId: String?): Boolean {
        val normalizedId = mediaId?.takeIf { it.isNotBlank() } ?: return nowPlayingAdPending
        val previousId = lastMediaId
        if (previousId == null) {
            lastMediaId = normalizedId
            return nowPlayingAdPending
        }
        if (previousId == normalizedId) return nowPlayingAdPending

        lastMediaId = normalizedId
        if (!nowPlayingAdPending) {
            trackTransitionCount += 1
            if (trackTransitionCount >= TRACK_TRANSITION_FREQUENCY) {
                trackTransitionCount = 0
                nowPlayingAdPending = true
                nowPlayingAdCycle += 1
            }
        }
        return nowPlayingAdPending
    }

    @Synchronized
    fun hasPendingNowPlayingAd(): Boolean = nowPlayingAdPending

    @Synchronized
    fun pendingNowPlayingAdCycle(): Long? = nowPlayingAdCycle.takeIf { nowPlayingAdPending }

    @Synchronized
    fun consumeNowPlayingAd() {
        nowPlayingAdPending = false
    }

    private const val LIBRARY_VISIT_FREQUENCY = 3
    private const val TRACK_TRANSITION_FREQUENCY = 3
}

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

        try {
            MobileAds.initialize(context.applicationContext) { initializationStatus ->
                Timber.tag(TAG).d(
                    "Mobile Ads initialized with %d adapters",
                    initializationStatus.adapterStatusMap.size,
                )
                finish(success = true)
            }
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Mobile Ads initialization failed")
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
}

private const val NATIVE_AD_RETRY_DELAY_MS = 60_000L

private object NativeAdVisitTokens {
    private val value = AtomicLong(0L)

    fun next(): Long = value.incrementAndGet()
}

private class NativeAdViewHolder {
    var view: NativeAdView? = null
}

private data class NativeAssetViews(
    val attribution: TextView,
    val headline: TextView,
    val body: TextView,
    val advertiser: TextView,
    val icon: ImageView,
    val callToAction: Button,
    val media: MediaView?,
    var boundAd: NativeAd? = null,
)

private fun createNativeAdView(context: Context, style: NativeAdStyle): NativeAdView {
    val adView = NativeAdView(context)
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
    }
    adView.addView(
        content,
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )

    val attribution = TextView(context).apply {
        text = "Anuncio"
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
    }
    content.addView(
        attribution,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )

    val mediaView = if (style == NativeAdStyle.LARGE) {
        MediaView(context).also { media ->
            content.addView(
                media,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = context.dp(6)
                    bottomMargin = context.dp(8)
                },
            )
        }
    } else {
        null
    }

    val detailsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    content.addView(
        detailsRow,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = if (style == NativeAdStyle.INLINE) context.dp(6) else 0
        },
    )

    val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    detailsRow.addView(
        icon,
        LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply {
            marginEnd = context.dp(10)
        },
    )

    val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    detailsRow.addView(
        textColumn,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )

    val headline = TextView(context).apply {
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
    }
    val advertiser = TextView(context).apply {
        textSize = 12f
        maxLines = 1
    }
    val body = TextView(context).apply {
        textSize = 12f
        maxLines = if (style == NativeAdStyle.INLINE) 2 else 1
    }
    textColumn.addView(headline)
    textColumn.addView(advertiser)
    textColumn.addView(body)

    val callToAction = Button(context).apply {
        textSize = 12f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = context.dp(44)
        minimumHeight = context.dp(44)
        setPadding(context.dp(12), 0, context.dp(12), 0)
    }
    detailsRow.addView(
        callToAction,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(44)).apply {
            marginStart = context.dp(8)
        },
    )

    adView.headlineView = headline
    adView.bodyView = body
    adView.iconView = icon
    adView.advertiserView = advertiser
    adView.callToActionView = callToAction
    mediaView?.let { adView.mediaView = it }
    adView.tag = NativeAssetViews(attribution, headline, body, advertiser, icon, callToAction, mediaView)
    return adView
}

private fun bindNativeAd(
    adView: NativeAdView,
    nativeAd: NativeAd,
    primaryColor: Int,
    onPrimaryColor: Int,
    onSurfaceColor: Int,
    onSurfaceVariantColor: Int,
) {
    val views = adView.tag as NativeAssetViews
    views.attribution.setTextColor(onSurfaceVariantColor)
    views.headline.setTextColor(onSurfaceColor)
    views.body.setTextColor(onSurfaceVariantColor)
    views.advertiser.setTextColor(onSurfaceVariantColor)
    views.callToAction.setTextColor(onPrimaryColor)
    views.callToAction.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = adView.context.dp(18).toFloat()
        setColor(primaryColor)
    }

    views.headline.text = nativeAd.headline
    views.body.bindOptionalText(nativeAd.body)
    views.advertiser.bindOptionalText(nativeAd.advertiser)
    views.callToAction.bindOptionalText(nativeAd.callToAction)
    views.icon.apply {
        val drawable = nativeAd.icon?.drawable
        visibility = if (drawable == null) View.GONE else View.VISIBLE
        setImageDrawable(drawable)
    }
    views.media?.apply {
        val content = nativeAd.mediaContent
        visibility = if (content == null) View.GONE else View.VISIBLE
        mediaContent = content
    }
    if (views.boundAd !== nativeAd) {
        views.boundAd = nativeAd
        adView.setNativeAd(nativeAd)
    }
}

private fun TextView.bindOptionalText(value: String?) {
    text = value.orEmpty()
    visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
