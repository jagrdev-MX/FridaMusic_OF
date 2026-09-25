package com.jagr.fridamusic.support

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupportBillingManager(context: Context) : SupportBilling {
    private val _state = MutableStateFlow<SupportBillingState>(
        SupportBillingState().catalogReady(emptyList()),
    )

    override val state: StateFlow<SupportBillingState> = _state.asStateFlow()
    override val capabilities = SupportCapabilities(
        googlePlayBilling = false,
        paypal = true,
        kofi = false,
        googlePlayRating = false,
    )
    override val debugController: SupportBillingDebugController? = null

    override fun start() = Unit
    override fun refresh() = Unit
    override fun onResume() = Unit
    override fun launchPurchase(activity: Activity, product: SupportProduct) = Unit
    override fun clearTransientPurchaseState() = Unit
    override fun close() = Unit
}
