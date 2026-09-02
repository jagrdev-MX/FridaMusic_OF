package com.jagr.fridamusic.support

class UniversalGmsDebugSupportTools(
    private val host: SupportBillingDebugHost,
) : SupportBillingDebugController {

    override fun useMockProducts() {
        host.showDebugProducts(MOCK_PRODUCTS)
    }

    override fun useRealProducts() {
        host.restoreRealProducts()
    }

    override fun simulate(scenario: SupportDebugScenario) {
        val state = when (scenario) {
            SupportDebugScenario.SUCCESS -> SupportBillingState.PurchaseCompleted("support_50")
            SupportDebugScenario.PENDING -> SupportBillingState.PurchasePending("support_50")
            SupportDebugScenario.CANCELLED -> SupportBillingState.PurchaseError(
                SupportBillingIssue.PURCHASE_CANCELLED,
            )
            SupportDebugScenario.ERROR -> SupportBillingState.PurchaseError(
                SupportBillingIssue.PURCHASE_ERROR,
            )
            SupportDebugScenario.BILLING_UNAVAILABLE -> SupportBillingState.BillingUnavailable(
                SupportBillingIssue.BILLING_UNAVAILABLE,
            )
            SupportDebugScenario.EMPTY_PRODUCTS -> SupportBillingState.BillingReady(
                products = emptyList(),
                isDebugPreview = true,
            )
        }
        host.simulateDebugState(state)
    }

    override fun onMockProductClick(productId: String) {
        host.simulateDebugState(SupportBillingState.PurchaseStarted(productId))
    }

    private companion object {
        val MOCK_PRODUCTS = listOf(
            SupportProduct("support_5", "MX\$5.00 · DEBUG", isDebugPreview = true),
            SupportProduct("support_10", "MX\$10.00 · DEBUG", isDebugPreview = true),
            SupportProduct("support_20", "MX\$20.00 · DEBUG", isDebugPreview = true),
            SupportProduct("support_50", "MX\$50.00 · DEBUG", isDebugPreview = true),
            SupportProduct("support_100", "MX\$100.00 · DEBUG", isDebugPreview = true),
            SupportProduct("support_200", "MX\$200.00 · DEBUG", isDebugPreview = true),
            SupportProduct("support_500", "MX\$500.00 · DEBUG", isDebugPreview = true),
            SupportProduct("support_1000", "MX\$1,000.00 · DEBUG", isDebugPreview = true),
        )
    }
}
