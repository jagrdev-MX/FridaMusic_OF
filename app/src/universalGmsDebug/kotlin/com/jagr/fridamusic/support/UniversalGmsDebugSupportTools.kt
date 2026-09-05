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
        when (scenario) {
            SupportDebugScenario.SUCCESS -> host.simulateDebugPurchaseState(
                SupportPurchaseState.Completed("support_50"),
            )
            SupportDebugScenario.PENDING -> host.simulateDebugPurchaseState(
                SupportPurchaseState.Pending("support_50"),
            )
            SupportDebugScenario.CANCELLED -> host.simulateDebugPurchaseState(
                SupportPurchaseState.Cancelled,
            )
            SupportDebugScenario.ERROR -> host.simulateDebugPurchaseState(
                SupportPurchaseState.Error(SupportBillingIssue.PURCHASE_ERROR),
            )
            SupportDebugScenario.BILLING_UNAVAILABLE -> host.simulateDebugCatalogState(
                SupportCatalogState(
                    status = SupportCatalogStatus.UNAVAILABLE,
                    issue = SupportBillingIssue.BILLING_UNAVAILABLE,
                    isDebugPreview = true,
                ),
            )
            SupportDebugScenario.EMPTY_PRODUCTS -> host.simulateDebugCatalogState(
                SupportCatalogState(
                    status = SupportCatalogStatus.READY,
                    isDebugPreview = true,
                ),
            )
        }
    }

    override fun onMockProductClick(productId: String) {
        host.simulateDebugPurchaseState(SupportPurchaseState.Started(productId))
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
