package com.jagr.fridamusic.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportBillingStateTest {
    private val products = listOf(
        SupportProduct("support_5", "MX\$5"),
        SupportProduct("support_10", "MX\$10"),
    )

    @Test
    fun `A cancelling a purchase retains the catalog and releases busy state`() {
        val state = SupportBillingState()
            .catalogReady(products)
            .purchase(SupportPurchaseState.Started("support_5"))
            .purchase(SupportPurchaseState.Cancelled)

        assertEquals(products, state.catalog.products)
        assertFalse(state.purchase.isBusy)
    }

    @Test
    fun `B reopening clears cancellation without clearing products`() {
        val state = SupportBillingState()
            .catalogReady(products)
            .purchase(SupportPurchaseState.Cancelled)
            .clearTransientPurchase()

        assertEquals(SupportPurchaseState.Idle, state.purchase)
        assertEquals(products, state.catalog.products)
    }

    @Test
    fun `C another product can start after cancellation`() {
        val state = SupportBillingState()
            .catalogReady(products)
            .purchase(SupportPurchaseState.Cancelled)
            .purchase(SupportPurchaseState.Started("support_10"))

        assertEquals(SupportPurchaseState.Started("support_10"), state.purchase)
        assertEquals(products, state.catalog.products)
    }

    @Test
    fun `D completed consumable retains catalog and permits another start`() {
        val completed = SupportBillingState()
            .catalogReady(products)
            .purchase(SupportPurchaseState.Completed("support_5"))

        assertEquals(products, completed.catalog.products)
        assertFalse(completed.purchase.isBusy)

        val next = completed.purchase(SupportPurchaseState.Started("support_10"))
        assertTrue(next.purchase.isBusy)
        assertEquals(products, next.catalog.products)
    }

    @Test
    fun `E recoverable purchase error retains catalog`() {
        val state = SupportBillingState()
            .catalogReady(products)
            .purchase(SupportPurchaseState.Error(SupportBillingIssue.PURCHASE_ERROR))

        assertEquals(products, state.catalog.products)
        assertFalse(state.purchase.isBusy)
    }

    @Test
    fun `F unavailable without a catalog exposes retry state and no products`() {
        val state = SupportBillingState()
            .catalogUnavailable(SupportBillingIssue.BILLING_UNAVAILABLE)

        assertEquals(SupportCatalogStatus.UNAVAILABLE, state.catalog.status)
        assertTrue(state.catalog.products.isEmpty())
        assertEquals(SupportBillingIssue.BILLING_UNAVAILABLE, state.catalog.issue)
    }

    @Test
    fun `G unavailable retains a previously valid catalog`() {
        val state = SupportBillingState()
            .catalogReady(products)
            .catalogUnavailable(SupportBillingIssue.SERVICE_DISCONNECTED)

        assertEquals(SupportCatalogStatus.UNAVAILABLE, state.catalog.status)
        assertEquals(products, state.catalog.products)
    }
}
