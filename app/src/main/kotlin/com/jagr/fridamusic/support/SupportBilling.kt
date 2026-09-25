package com.jagr.fridamusic.support

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

val SUPPORT_PRODUCT_IDS = listOf(
    "support_5",
    "support_10",
    "support_20",
    "support_50",
    "support_100",
    "support_200",
    "support_500",
    "support_1000",
)

data class SupportProduct(
    val id: String,
    val formattedPrice: String,
    val isDebugPreview: Boolean = false,
)

data class SupportCapabilities(
    val googlePlayBilling: Boolean,
    val paypal: Boolean,
    val kofi: Boolean,
    val googlePlayRating: Boolean,
)

enum class SupportBillingIssue {
    BILLING_UNAVAILABLE,
    SERVICE_DISCONNECTED,
    PRODUCT_UNAVAILABLE,
    PURCHASE_ERROR,
    CONSUMPTION_ERROR,
}

enum class SupportCatalogStatus {
    LOADING,
    READY,
    UNAVAILABLE,
}

data class SupportCatalogState(
    val status: SupportCatalogStatus = SupportCatalogStatus.LOADING,
    val products: List<SupportProduct> = emptyList(),
    val issue: SupportBillingIssue? = null,
    val isDebugPreview: Boolean = false,
)

sealed interface SupportPurchaseState {
    data object Idle : SupportPurchaseState
    data class Started(val productId: String) : SupportPurchaseState
    data class Pending(val productId: String?) : SupportPurchaseState
    data class Completed(val productId: String?) : SupportPurchaseState
    data object Cancelled : SupportPurchaseState
    data class Error(val issue: SupportBillingIssue) : SupportPurchaseState
}

val SupportPurchaseState.isBusy: Boolean
    get() = this is SupportPurchaseState.Started || this is SupportPurchaseState.Pending

val SupportPurchaseState.isTransientTerminal: Boolean
    get() = this == SupportPurchaseState.Cancelled ||
        this is SupportPurchaseState.Completed ||
        this is SupportPurchaseState.Error

data class SupportBillingState(
    val catalog: SupportCatalogState = SupportCatalogState(),
    val purchase: SupportPurchaseState = SupportPurchaseState.Idle,
) {
    fun catalogLoading(): SupportBillingState = copy(
        catalog = catalog.copy(
            status = SupportCatalogStatus.LOADING,
            issue = null,
        ),
    )

    fun catalogReady(
        products: List<SupportProduct>,
        isDebugPreview: Boolean = false,
    ): SupportBillingState = copy(
        catalog = SupportCatalogState(
            status = SupportCatalogStatus.READY,
            products = products,
            isDebugPreview = isDebugPreview,
        ),
    )

    fun catalogUnavailable(issue: SupportBillingIssue): SupportBillingState = copy(
        catalog = catalog.copy(
            status = SupportCatalogStatus.UNAVAILABLE,
            issue = issue,
        ),
    )

    fun purchase(state: SupportPurchaseState): SupportBillingState = copy(purchase = state)

    fun clearTransientPurchase(): SupportBillingState = when (purchase) {
        SupportPurchaseState.Cancelled,
        is SupportPurchaseState.Completed,
        is SupportPurchaseState.Error,
        -> copy(purchase = SupportPurchaseState.Idle)

        else -> this
    }
}

enum class SupportDebugScenario {
    SUCCESS,
    PENDING,
    CANCELLED,
    ERROR,
    BILLING_UNAVAILABLE,
    EMPTY_PRODUCTS,
}

interface SupportBillingDebugHost {
    fun showDebugProducts(products: List<SupportProduct>)
    fun restoreRealProducts()
    fun simulateDebugPurchaseState(state: SupportPurchaseState)
    fun simulateDebugCatalogState(state: SupportCatalogState)
}

interface SupportBillingDebugController {
    fun useMockProducts()
    fun useRealProducts()
    fun simulate(scenario: SupportDebugScenario)
    fun onMockProductClick(productId: String)
}

interface SupportBilling {
    val state: StateFlow<SupportBillingState>
    val capabilities: SupportCapabilities
    val debugController: SupportBillingDebugController?

    fun start()
    fun refresh()
    fun onResume()
    fun launchPurchase(activity: Activity, product: SupportProduct)
    fun clearTransientPurchaseState()
    fun close()
}

internal fun loadSupportBillingDebugController(
    host: SupportBillingDebugHost,
): SupportBillingDebugController? = runCatching {
    val debugClass = Class.forName(
        "com.jagr.fridamusic.support.UniversalGmsDebugSupportTools",
    )
    val constructor = debugClass.getDeclaredConstructor(SupportBillingDebugHost::class.java)
    constructor.newInstance(host) as SupportBillingDebugController
}.getOrNull()
