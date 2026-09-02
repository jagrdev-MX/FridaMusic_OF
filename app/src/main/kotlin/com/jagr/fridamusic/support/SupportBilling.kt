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
    val googlePlayRating: Boolean,
)

enum class SupportBillingIssue {
    BILLING_UNAVAILABLE,
    SERVICE_DISCONNECTED,
    PRODUCT_UNAVAILABLE,
    PURCHASE_CANCELLED,
    PURCHASE_ERROR,
    CONSUMPTION_ERROR,
}

sealed interface SupportBillingState {
    data object BillingLoading : SupportBillingState

    data class BillingReady(
        val products: List<SupportProduct>,
        val isDebugPreview: Boolean = false,
    ) : SupportBillingState

    data class BillingUnavailable(val issue: SupportBillingIssue) : SupportBillingState
    data class PurchaseStarted(val productId: String) : SupportBillingState
    data class PurchasePending(val productId: String?) : SupportBillingState
    data class PurchaseCompleted(val productId: String?) : SupportBillingState
    data class PurchaseError(val issue: SupportBillingIssue) : SupportBillingState
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
    fun simulateDebugState(state: SupportBillingState)
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
