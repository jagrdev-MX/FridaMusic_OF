package com.jagr.fridamusic.support

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
import com.jagr.fridamusic.BuildConfig
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupportBillingManager(context: Context) :
    SupportBilling,
    SupportBillingDebugHost,
    PurchasesUpdatedListener {

    private data class ProductSelection(
        val details: ProductDetails,
        val offerToken: String?,
        val formattedPrice: String,
        val offerIndex: Int,
        val offerCount: Int,
        val offerType: String,
        val selectionSource: String,
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
    private var activeInstanceRegistered = false
    private var productQueryGeneration = 0
    private val instanceId = nextInstanceId.incrementAndGet()

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
        registerActiveInstanceIfNeeded()
        logInfo(
            "start instance=%d buildType=%s flavor=%s applicationId=%s ready=%s",
            instanceId,
            BuildConfig.BUILD_TYPE,
            BuildConfig.FLAVOR,
            BuildConfig.APPLICATION_ID,
            billingClient.isReady,
        )
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
        logDebug("foreground instance=%d ready=%s", instanceId, billingClient.isReady)
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

        val lifecycleState = activity.lifecycleState()
        logInfo(
            "Purchase requested: instance=%d productId=%s ready=%s activity=%s lifecycle=%s finishing=%s destroyed=%s",
            instanceId,
            product.id,
            billingClient.isReady,
            activity.javaClass.name,
            lifecycleState,
            activity.isFinishing,
            activity.isDestroyed,
        )
        if (!activity.isReadyForBilling()) {
            pendingProductId = null
            _state.value = SupportBillingState.PurchaseError(SupportBillingIssue.PURCHASE_ERROR)
            logWarning("Purchase rejected because the Activity is not foreground and valid")
            return
        }

        if (pendingProductId != null) {
            logWarning(
                "Purchase ignored because another request is active: requested=%s active=%s",
                product.id,
                pendingProductId,
            )
            return
        }

        if (!billingClient.isReady) {
            _state.value = SupportBillingState.BillingUnavailable(
                SupportBillingIssue.SERVICE_DISCONNECTED,
            )
            connect()
            return
        }

        pendingProductId = product.id
        _state.value = SupportBillingState.PurchaseStarted(product.id)
        val activityReference = WeakReference(activity)
        queryProductDetails(
            productIds = listOf(product.id),
            operation = "purchase_refresh",
        ) { billingResult, detailsList ->
            if (closed || pendingProductId != product.id) return@queryProductDetails
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                handleLaunchError(billingResult)
                return@queryProductDetails
            }

            val freshDetails = detailsList.singleOrNull { it.productId == product.id }
            val selection = freshDetails?.let(::selectProductOffer)
            if (selection == null) {
                pendingProductId = null
                _state.value = SupportBillingState.PurchaseError(
                    SupportBillingIssue.PRODUCT_UNAVAILABLE,
                )
                logWarning("No unambiguous eligible offer for productId=%s", product.id)
                return@queryProductDetails
            }

            val currentActivity = activityReference.get()
            if (currentActivity == null || !currentActivity.isReadyForBilling()) {
                pendingProductId = null
                _state.value = SupportBillingState.PurchaseError(SupportBillingIssue.PURCHASE_ERROR)
                logWarning(
                    "Purchase aborted after refresh because Activity is no longer foreground: productId=%s",
                    product.id,
                )
                return@queryProductDetails
            }

            productDetails[product.id] = selection
            launchBillingFlow(currentActivity, product.id, selection)
        }
    }

    private fun launchBillingFlow(
        activity: Activity,
        productId: String,
        selection: ProductSelection,
    ) {
        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(selection.details)
            .apply {
                selection.offerToken?.takeIf(String::isNotBlank)?.let(::setOfferToken)
            }
            .build()
        logInfo(
            "Launching official billing flow: productId=%s activity=%s lifecycle=%s offerIndex=%d offerCount=%d offerType=%s source=%s",
            productId,
            activity.javaClass.name,
            activity.lifecycleState(),
            selection.offerIndex,
            selection.offerCount,
            selection.offerType,
            selection.selectionSource,
        )
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(detailsParams))
                .build(),
        )
        logInfo(
            "launchBillingFlow result: productId=%s code=%d message=%s ready=%s",
            productId,
            result.responseCode,
            result.debugMessage,
            billingClient.isReady,
        )
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            _state.value = SupportBillingState.PurchaseStarted(productId)
        } else {
            handleLaunchError(result)
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?,
    ) {
        logInfo(
            "Purchase update: code=%d message=%s purchaseCount=%d ready=%s",
            billingResult.responseCode,
            billingResult.debugMessage,
            purchases?.size ?: 0,
            billingClient.isReady,
        )
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    pendingProductId = null
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
                pendingProductId = null
                _state.value = SupportBillingState.BillingUnavailable(
                    SupportBillingIssue.SERVICE_DISCONNECTED,
                )
            }

            else -> {
                pendingProductId = null
                _state.value = SupportBillingState.PurchaseError(
                    SupportBillingIssue.PURCHASE_ERROR,
                )
                logWarning(
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
        productQueryGeneration++
        pendingProductId = null
        productDetails.clear()
        consumingTokens.clear()
        billingClient.endConnection()
        unregisterActiveInstanceIfNeeded()
        logInfo("closed instance=%d", instanceId)
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
        logInfo("Connecting instance=%d ready=%s", instanceId, billingClient.isReady)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (closed) return
                logInfo(
                    "Billing setup: instance=%d code=%d message=%s ready=%s",
                    instanceId,
                    billingResult.responseCode,
                    billingResult.debugMessage,
                    billingClient.isReady,
                )
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryOwnedPurchases()
                } else if (!debugPreviewActive) {
                    _state.value = SupportBillingState.BillingUnavailable(
                        SupportBillingIssue.BILLING_UNAVAILABLE,
                    )
                    logWarning(
                        "Billing setup failed: code=%d message=%s",
                        billingResult.responseCode,
                        billingResult.debugMessage,
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                logWarning(
                    "Billing service disconnected: instance=%d ready=%s",
                    instanceId,
                    billingClient.isReady,
                )
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
        val generation = ++productQueryGeneration
        queryProductDetails(
            productIds = SUPPORT_PRODUCT_IDS,
            operation = "catalog",
        ) { billingResult, detailsList ->
            if (closed || generation != productQueryGeneration) return@queryProductDetails
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                if (!debugPreviewActive) {
                    _state.value = SupportBillingState.BillingUnavailable(
                        SupportBillingIssue.BILLING_UNAVAILABLE,
                    )
                }
                return@queryProductDetails
            }

            productDetails.clear()
            detailsList.forEach { details ->
                if (details.productId !in SUPPORT_PRODUCT_IDS) return@forEach
                selectProductOffer(details)?.let { selection ->
                    productDetails[details.productId] = selection
                }
            }
            realProducts = SUPPORT_PRODUCT_IDS.mapNotNull { productId ->
                val selection = productDetails[productId] ?: return@mapNotNull null
                SupportProduct(
                    id = productId,
                    formattedPrice = selection.formattedPrice,
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

    private fun queryProductDetails(
        productIds: List<String>,
        operation: String,
        onResult: (BillingResult, List<ProductDetails>) -> Unit,
    ) {
        val products = productIds.map { productId ->
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
            logInfo(
                "Product details result: operation=%s code=%d message=%s requested=%d fetched=%d unfetched=%d ready=%s",
                operation,
                billingResult.responseCode,
                billingResult.debugMessage,
                productIds.size,
                queryResult.productDetailsList.size,
                queryResult.unfetchedProductList.size,
                billingClient.isReady,
            )
            queryResult.unfetchedProductList.forEach { unfetched ->
                logWarning(
                    "Unfetched product: operation=%s productId=%s type=%s status=%d",
                    operation,
                    unfetched.productId,
                    unfetched.productType,
                    unfetched.statusCode,
                )
            }
            onResult(billingResult, queryResult.productDetailsList)
        }
    }

    private fun selectProductOffer(details: ProductDetails): ProductSelection? {
        val eligibleOffers = details.oneTimePurchaseOfferDetailsList.orEmpty()
        val backwardCompatibleOffer = details.oneTimePurchaseOfferDetails
        val selectedOffer = backwardCompatibleOffer ?: eligibleOffers.singleOrNull()
        if (selectedOffer == null) {
            logWarning(
                "Offer selection is ambiguous: productId=%s eligibleOfferCount=%d backwardCompatible=false",
                details.productId,
                eligibleOffers.size,
            )
            return null
        }

        val selectedIndex = eligibleOffers.indexOfFirst {
            it.offerToken == selectedOffer.offerToken
        }.takeIf { it >= 0 } ?: 0
        val offerCount = eligibleOffers.size.takeIf { it > 0 } ?: 1
        val offerType = when {
            selectedOffer.rentalDetails != null -> "rental"
            selectedOffer.preorderDetails != null -> "preorder"
            selectedOffer.discountDisplayInfo != null -> "discount"
            else -> "base"
        }
        val selectionSource = if (backwardCompatibleOffer != null) {
            "backward_compatible"
        } else {
            "single_eligible"
        }
        logInfo(
            "Offer selected: productId=%s eligibleOfferCount=%d selectedIndex=%d type=%s source=%s currency=%s",
            details.productId,
            offerCount,
            selectedIndex,
            offerType,
            selectionSource,
            selectedOffer.priceCurrencyCode,
        )
        return ProductSelection(
            details = details,
            offerToken = selectedOffer.offerToken,
            formattedPrice = selectedOffer.formattedPrice,
            offerIndex = selectedIndex,
            offerCount = offerCount,
            offerType = offerType,
            selectionSource = selectionSource,
        )
    }

    private fun queryOwnedPurchases() {
        if (closed || !billingClient.isReady) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { billingResult, purchases ->
            if (closed) return@queryPurchasesAsync
            logInfo(
                "Owned purchases result: code=%d message=%s count=%d ready=%s",
                billingResult.responseCode,
                billingResult.debugMessage,
                purchases.size,
                billingClient.isReady,
            )
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
        logInfo(
            "Processing purchase: productId=%s state=%d tokenHash=%s acknowledged=%s",
            supportProductId,
            purchase.purchaseState,
            purchase.purchaseToken.safeHash(),
            purchase.isAcknowledged,
        )
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
        if (!consumingTokens.add(purchaseToken)) {
            logDebug(
                "Consumption already active: productId=%s tokenHash=%s",
                productId,
                purchaseToken.safeHash(),
            )
            return
        }
        logInfo(
            "Consuming purchase: productId=%s tokenHash=%s ready=%s",
            productId,
            purchaseToken.safeHash(),
            billingClient.isReady,
        )
        billingClient.consumeAsync(
            ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build(),
        ) { billingResult, returnedToken ->
            consumingTokens.remove(purchaseToken)
            consumingTokens.remove(returnedToken)
            if (closed) return@consumeAsync
            pendingProductId = null
            logInfo(
                "Consumption result: productId=%s tokenHash=%s code=%d message=%s",
                productId,
                returnedToken.safeHash(),
                billingResult.responseCode,
                billingResult.debugMessage,
            )
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.value = SupportBillingState.PurchaseCompleted(productId)
            } else {
                _state.value = SupportBillingState.PurchaseError(
                    SupportBillingIssue.CONSUMPTION_ERROR,
                )
                logWarning(
                    "Purchase consumption failed: code=%d message=%s",
                    billingResult.responseCode,
                    billingResult.debugMessage,
                )
            }
        }
    }

    private fun handleLaunchError(result: BillingResult) {
        pendingProductId = null
        logWarning(
            "Purchase launch failed: code=%d message=%s ready=%s",
            result.responseCode,
            result.debugMessage,
            billingClient.isReady,
        )
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

    private fun registerActiveInstanceIfNeeded() {
        if (activeInstanceRegistered) return
        activeInstanceRegistered = true
        val count = activeInstances.incrementAndGet()
        if (count > 1) {
            logWarning("Multiple active billing managers detected: count=%d", count)
        }
    }

    private fun unregisterActiveInstanceIfNeeded() {
        if (!activeInstanceRegistered) return
        activeInstanceRegistered = false
        activeInstances.decrementAndGet()
    }

    private fun Activity.lifecycleState(): Lifecycle.State? =
        (this as? LifecycleOwner)?.lifecycle?.currentState

    private fun Activity.isReadyForBilling(): Boolean {
        if (isFinishing || isDestroyed) return false
        val state = lifecycleState()
        return state == null || state.isAtLeast(Lifecycle.State.RESUMED)
    }

    private fun logInfo(message: String, vararg args: Any?) {
        Log.i(TAG, String.format(Locale.US, message, *args))
    }

    private fun logWarning(message: String, vararg args: Any?) {
        Log.w(TAG, String.format(Locale.US, message, *args))
    }

    private fun logDebug(message: String, vararg args: Any?) {
        if (BuildConfig.DEBUG) Log.d(TAG, String.format(Locale.US, message, *args))
    }

    private fun String.safeHash(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(6)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val TAG = "SupportBilling"
        val nextInstanceId = AtomicInteger(0)
        val activeInstances = AtomicInteger(0)
    }
}
