package com.jagr.fridamusic.support

import android.app.Activity
import android.content.Context
import com.jagr.fridamusic.BuildConfig
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class SupportBillingManager(context: Context) :
    SupportBilling,
    SupportBillingDebugHost,
    PurchasesUpdatedListener {

    private data class ProductSelection(
        val details: ProductDetails,
        val offerToken: String?,
    )

    private val _state = MutableStateFlow<SupportBillingState>(SupportBillingState.BillingLoading)
    override val state: StateFlow<SupportBillingState> = _state.asStateFlow()
    override val capabilities = SupportCapabilities(
        googlePlayBilling = BuildConfig.SUPPORT_GOOGLE_PLAY_ENABLED,
        paypal = BuildConfig.SUPPORT_PAYPAL_ENABLED,
        googlePlayRating = BuildConfig.SUPPORT_GOOGLE_PLAY_ENABLED,
    )

    private val productDetails = mutableMapOf<String, ProductSelection>()
    private val consumingTokens = mutableSetOf<String>()
    private var realProducts: List<SupportProduct> = emptyList()
    private var pendingProductId: String? = null
    private var connecting = false
    private var closed = false
    private var debugPreviewActive = false

    private val billingClient by lazy(LazyThreadSafetyMode.NONE) {
        BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .enableAutoServiceReconnection()
            .build()
    }

    override val debugController: SupportBillingDebugController? =
        loadSupportBillingDebugController(this)

    override fun start() {
        if (!capabilities.googlePlayBilling || closed) return
        if (billingClient.isReady) {
            queryProducts()
            queryOwnedPurchases()
        } else {
            connect()
        }
    }

    override fun refresh() {
        if (!capabilities.googlePlayBilling || closed) return
        debugPreviewActive = false
        _state.value = SupportBillingState.BillingLoading
        start()
    }

    override fun onResume() {
        if (!capabilities.googlePlayBilling || closed) return
        if (billingClient.isReady) {
            queryOwnedPurchases()
        } else {
            connect()
        }
    }

    override fun launchPurchase(activity: Activity, product: SupportProduct) {
        if (!capabilities.googlePlayBilling || closed) return
        if (product.isDebugPreview) {
            debugController?.onMockProductClick(product.id)
            return
        }

        if (!billingClient.isReady) {
            _state.value = SupportBillingState.BillingUnavailable(
                SupportBillingIssue.SERVICE_DISCONNECTED,
            )
            connect()
            return
        }

        val selection = productDetails[product.id]
        if (selection == null) {
            _state.value = SupportBillingState.PurchaseError(
                SupportBillingIssue.PRODUCT_UNAVAILABLE,
            )
            return
        }

        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(selection.details)
            .apply {
                selection.offerToken?.takeIf(String::isNotBlank)?.let(::setOfferToken)
            }
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(detailsParams))
                .build(),
        )

        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            pendingProductId = product.id
            _state.value = SupportBillingState.PurchaseStarted(product.id)
        } else {
            handleLaunchError(result)
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    _state.value = SupportBillingState.PurchaseError(
                        SupportBillingIssue.PURCHASE_ERROR,
                    )
                } else {
                    purchases.forEach(::processPurchase)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingProductId = null
                _state.value = SupportBillingState.PurchaseError(
                    SupportBillingIssue.PURCHASE_CANCELLED,
                )
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwnedPurchases()

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            -> {
                _state.value = SupportBillingState.BillingUnavailable(
                    SupportBillingIssue.SERVICE_DISCONNECTED,
                )
            }

            else -> {
                pendingProductId = null
                _state.value = SupportBillingState.PurchaseError(
                    SupportBillingIssue.PURCHASE_ERROR,
                )
                Timber.tag(TAG).w(
                    "Purchase update failed: code=%d message=%s",
                    billingResult.responseCode,
                    billingResult.debugMessage,
                )
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (!capabilities.googlePlayBilling) return
        connecting = false
        productDetails.clear()
        consumingTokens.clear()
        billingClient.endConnection()
    }

    override fun showDebugProducts(products: List<SupportProduct>) {
        if (closed) return
        debugPreviewActive = true
        _state.value = SupportBillingState.BillingReady(
            products = products,
            isDebugPreview = true,
        )
    }

    override fun restoreRealProducts() {
        if (closed) return
        debugPreviewActive = false
        _state.value = SupportBillingState.BillingLoading
        if (billingClient.isReady) {
            queryProducts()
        } else {
            _state.value = SupportBillingState.BillingLoading
            connect()
        }
    }

    override fun simulateDebugState(state: SupportBillingState) {
        if (!closed) _state.value = state
    }

    private fun connect() {
        if (closed || connecting || billingClient.isReady) return
        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (closed) return
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryOwnedPurchases()
                } else if (!debugPreviewActive) {
                    _state.value = SupportBillingState.BillingUnavailable(
                        SupportBillingIssue.BILLING_UNAVAILABLE,
                    )
                    Timber.tag(TAG).w(
                        "Billing setup failed: code=%d message=%s",
                        billingResult.responseCode,
                        billingResult.debugMessage,
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                if (!closed && !debugPreviewActive) {
                    _state.value = SupportBillingState.BillingUnavailable(
                        SupportBillingIssue.SERVICE_DISCONNECTED,
                    )
                }
            }
        })
    }

    private fun queryProducts() {
        if (closed || !billingClient.isReady) return
        val products = SUPPORT_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build(),
        ) { billingResult, queryResult ->
            if (closed) return@queryProductDetailsAsync
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                if (!debugPreviewActive) {
                    _state.value = SupportBillingState.BillingUnavailable(
                        SupportBillingIssue.BILLING_UNAVAILABLE,
                    )
                }
                return@queryProductDetailsAsync
            }

            productDetails.clear()
            queryResult.productDetailsList.forEach { details ->
                if (details.productId !in SUPPORT_PRODUCT_IDS) return@forEach
                val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    ?: details.oneTimePurchaseOfferDetails
                    ?: return@forEach
                productDetails[details.productId] = ProductSelection(
                    details = details,
                    offerToken = offer.offerToken,
                )
            }
            realProducts = SUPPORT_PRODUCT_IDS.mapNotNull { productId ->
                val selection = productDetails[productId] ?: return@mapNotNull null
                val offer = selection.details.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    ?: selection.details.oneTimePurchaseOfferDetails
                    ?: return@mapNotNull null
                SupportProduct(
                    id = productId,
                    formattedPrice = offer.formattedPrice,
                )
            }
            if (!debugPreviewActive && _state.value !is SupportBillingState.PurchaseStarted &&
                _state.value !is SupportBillingState.PurchasePending &&
                _state.value !is SupportBillingState.PurchaseCompleted &&
                _state.value !is SupportBillingState.PurchaseError
            ) {
                _state.value = SupportBillingState.BillingReady(realProducts)
            }
        }
    }

    private fun queryOwnedPurchases() {
        if (closed || !billingClient.isReady) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { billingResult, purchases ->
            if (closed) return@queryPurchasesAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::processPurchase)
            } else if (!debugPreviewActive) {
                _state.value = SupportBillingState.BillingUnavailable(
                    SupportBillingIssue.SERVICE_DISCONNECTED,
                )
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        val supportProductId = purchase.products.firstOrNull { it in SUPPORT_PRODUCT_IDS } ?: return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                pendingProductId = supportProductId
                _state.value = SupportBillingState.PurchasePending(supportProductId)
            }

            Purchase.PurchaseState.PURCHASED -> consumePurchase(
                purchaseToken = purchase.purchaseToken,
                productId = supportProductId,
            )
        }
    }

    private fun consumePurchase(purchaseToken: String, productId: String) {
        if (!consumingTokens.add(purchaseToken)) return
        billingClient.consumeAsync(
            ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build(),
        ) { billingResult, returnedToken ->
            consumingTokens.remove(returnedToken)
            pendingProductId = null
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.value = SupportBillingState.PurchaseCompleted(productId)
            } else {
                _state.value = SupportBillingState.PurchaseError(
                    SupportBillingIssue.CONSUMPTION_ERROR,
                )
                Timber.tag(TAG).w(
                    "Purchase consumption failed: code=%d message=%s",
                    billingResult.responseCode,
                    billingResult.debugMessage,
                )
            }
        }
    }

    private fun handleLaunchError(result: BillingResult) {
        pendingProductId = null
        val issue = when (result.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED ->
                SupportBillingIssue.PURCHASE_CANCELLED

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            -> SupportBillingIssue.SERVICE_DISCONNECTED

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                SupportBillingIssue.PRODUCT_UNAVAILABLE

            else -> SupportBillingIssue.PURCHASE_ERROR
        }
        _state.value = if (issue == SupportBillingIssue.SERVICE_DISCONNECTED) {
            SupportBillingState.BillingUnavailable(issue)
        } else {
            SupportBillingState.PurchaseError(issue)
        }
    }

    private companion object {
        const val TAG = "SupportBilling"
    }
}
