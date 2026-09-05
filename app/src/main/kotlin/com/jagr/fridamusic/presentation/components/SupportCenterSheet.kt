package com.jagr.fridamusic.presentation.components

import androidx.annotation.StringRes
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.support.SupportBilling
import com.jagr.fridamusic.support.SupportBillingDebugController
import com.jagr.fridamusic.support.SupportBillingIssue
import com.jagr.fridamusic.support.SupportBillingState
import com.jagr.fridamusic.support.SupportDebugScenario
import com.jagr.fridamusic.support.SupportLinkResult
import com.jagr.fridamusic.support.SupportProduct
import com.jagr.fridamusic.support.openFridaMusicPlayStore
import com.jagr.fridamusic.support.openSupportPayPal

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
fun SupportCenterSheet(
    billing: SupportBilling,
    onDismiss: () -> Unit,
    onWatchAdClick: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val billingState by billing.state.collectAsState()
    val capabilities = billing.capabilities
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var visibleProducts by remember { mutableStateOf(emptyList<SupportProduct>()) }
    var debugPreview by remember { mutableStateOf(false) }
    var linkMessage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(billingState) {
        if (billingState is SupportBillingState.BillingReady) {
            val ready = billingState as SupportBillingState.BillingReady
            visibleProducts = ready.products
            debugPreview = ready.isDebugPreview
        }
    }

    val activeProductId = when (val current = billingState) {
        is SupportBillingState.PurchaseStarted -> current.productId
        is SupportBillingState.PurchasePending -> current.productId
        else -> null
    }
    val purchaseBusy = billingState is SupportBillingState.PurchaseStarted ||
        billingState is SupportBillingState.PurchasePending

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SupportHeader(onDismiss = onDismiss)
            }

            if (capabilities.googlePlayRating) {
                item {
                    SupportActionCard(
                        icon = Icons.Rounded.StarRate,
                        title = stringResource(R.string.support_rate_title),
                        description = stringResource(R.string.support_rate_description),
                        trailing = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = {
                            linkMessage = supportLinkMessage(openFridaMusicPlayStore(context))
                        },
                    )
                }
            }

            supportStatusContent(
                billingState = billingState,
                debugPreview = debugPreview,
                linkMessage = linkMessage,
                showBillingStatus = capabilities.googlePlayBilling,
                onRetry = billing::refresh,
            )

            if (capabilities.googlePlayBilling) {
                if (visibleProducts.isEmpty() && billingState is SupportBillingState.BillingReady) {
                    item {
                        EmptyProductsCard(onRetry = billing::refresh)
                    }
                } else if (visibleProducts.isNotEmpty()) {
                    val featured = visibleProducts.firstOrNull { it.id == FEATURED_PRODUCT_ID }
                    featured?.let { product ->
                        item(key = "featured_${product.id}") {
                            FeaturedSupportCard(
                                product = product,
                                enabled = !purchaseBusy,
                                loading = activeProductId == product.id,
                                onClick = {
                                    activity?.let { billing.launchPurchase(it, product) }
                                },
                            )
                        }
                    }

                    item {
                        SupportProductsGrid(
                            products = visibleProducts.filterNot { it.id == FEATURED_PRODUCT_ID },
                            enabled = !purchaseBusy,
                            activeProductId = activeProductId,
                            onProductClick = { product ->
                                activity?.let { billing.launchPurchase(it, product) }
                            },
                        )
                    }
                }
            }

            if (capabilities.paypal) {
                item {
                    SupportActionCard(
                        icon = Icons.Rounded.Payments,
                        title = stringResource(R.string.support_paypal_title),
                        description = stringResource(R.string.support_paypal_description),
                        trailing = Icons.AutoMirrored.Rounded.OpenInNew,
                        prominent = !capabilities.googlePlayBilling,
                        onClick = {
                            linkMessage = supportLinkMessage(openSupportPayPal(context))
                        },
                    )
                }
            }

            item {
                SupportActionCard(
                    icon = Icons.Rounded.PlayArrow,
                    title = stringResource(R.string.support_watch_ad_title),
                    description = stringResource(R.string.support_watch_ad_description),
                    onClick = onWatchAdClick,
                )
            }

            billing.debugController?.let { debugController ->
                item {
                    DebugToolsCard(
                        controller = debugController,
                        onLinkResult = { linkMessage = it },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SupportHeader(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.support_center_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.support_center_close),
                )
            }
        }
        Text(
            text = stringResource(R.string.support_center_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.support_center_optional_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.supportStatusContent(
    billingState: SupportBillingState,
    debugPreview: Boolean,
    linkMessage: Int?,
    showBillingStatus: Boolean,
    onRetry: () -> Unit,
) {
    val status = if (showBillingStatus) when (billingState) {
        SupportBillingState.BillingLoading -> R.string.support_billing_loading
        is SupportBillingState.BillingReady -> null
        is SupportBillingState.BillingUnavailable -> billingIssueMessage(billingState.issue)
        is SupportBillingState.PurchaseStarted -> if (debugPreview) {
            R.string.support_debug_purchase_started
        } else {
            R.string.support_purchase_started
        }
        is SupportBillingState.PurchasePending -> R.string.support_purchase_pending
        is SupportBillingState.PurchaseCompleted -> R.string.support_purchase_completed
        is SupportBillingState.PurchaseError -> billingIssueMessage(billingState.issue)
    } else null
    status?.let { message ->
        item(key = "billing_status") {
            SupportMessageCard(
                message = message,
                loading = billingState is SupportBillingState.BillingLoading ||
                    billingState is SupportBillingState.PurchaseStarted,
                onRetry = onRetry.takeIf {
                    billingState is SupportBillingState.BillingUnavailable ||
                        (billingState is SupportBillingState.PurchaseError &&
                            billingState.issue != SupportBillingIssue.PURCHASE_CANCELLED)
                },
            )
        }
    }
    linkMessage?.let { message ->
        item(key = "link_status") {
            SupportMessageCard(message)
        }
    }
}

@Composable
private fun EmptyProductsCard(onRetry: () -> Unit) {
    SupportMessageCard(message = R.string.support_google_play_no_products, onRetry = onRetry)
}

@Composable
private fun FeaturedSupportCard(
    product: SupportProduct,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SupportIconSurface(icon = supportProductIcon(product.id), featured = true)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.support_recommended),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(supportProductTitle(product.id)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    product.formattedPrice,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(max = 128.dp),
                )
            }
        }
    }
}

@Composable
private fun SupportProductsGrid(
    products: List<SupportProduct>,
    enabled: Boolean,
    activeProductId: String?,
    onProductClick: (SupportProduct) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        val columnCount = if (maxWidth >= 280.dp * LocalDensity.current.fontScale) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            products.chunked(columnCount).forEach { rowProducts ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowProducts.forEach { product ->
                        SupportProductCard(
                            product = product,
                            enabled = enabled,
                            loading = activeProductId == product.id,
                            onClick = { onProductClick(product) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportProductCard(
    product: SupportProduct,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SupportIconSurface(icon = supportProductIcon(product.id), featured = false)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(supportProductTitle(product.id)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        product.formattedPrice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportIconSurface(icon: ImageVector, featured: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (featured) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (featured) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Box(
            modifier = Modifier.size(if (featured) 40.dp else 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SupportActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    trailing: ImageVector? = null,
    prominent: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (prominent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (prominent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SupportIconSurface(icon = icon, featured = prominent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (prominent) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DebugToolsCard(
    controller: SupportBillingDebugController,
    onLinkResult: (Int) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.support_debug_tools),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Text(
                    stringResource(R.string.support_debug_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = controller::useMockProducts) {
                        Text(stringResource(R.string.support_debug_use_mocks))
                    }
                    OutlinedButton(onClick = controller::useRealProducts) {
                        Text(stringResource(R.string.support_debug_use_real))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugScenarioButton(R.string.support_debug_success) {
                        controller.simulate(SupportDebugScenario.SUCCESS)
                    }
                    DebugScenarioButton(R.string.support_debug_pending) {
                        controller.simulate(SupportDebugScenario.PENDING)
                    }
                    DebugScenarioButton(R.string.support_debug_cancelled) {
                        controller.simulate(SupportDebugScenario.CANCELLED)
                    }
                    DebugScenarioButton(R.string.support_debug_error) {
                        controller.simulate(SupportDebugScenario.ERROR)
                    }
                    DebugScenarioButton(R.string.support_debug_unavailable) {
                        controller.simulate(SupportDebugScenario.BILLING_UNAVAILABLE)
                    }
                    DebugScenarioButton(R.string.support_debug_empty) {
                        controller.simulate(SupportDebugScenario.EMPTY_PRODUCTS)
                    }
                }
                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugScenarioButton(R.string.support_debug_open_paypal) {
                        onLinkResult(supportLinkMessage(openSupportPayPal(context)))
                    }
                    DebugScenarioButton(R.string.support_debug_paypal_opened) {
                        onLinkResult(R.string.support_paypal_opened)
                    }
                    DebugScenarioButton(R.string.support_debug_paypal_unavailable) {
                        onLinkResult(R.string.support_no_compatible_app)
                    }
                    DebugScenarioButton(R.string.support_debug_test_play_store) {
                        onLinkResult(supportLinkMessage(openFridaMusicPlayStore(context)))
                    }
                    DebugScenarioButton(R.string.support_debug_test_browser) {
                        onLinkResult(supportLinkMessage(openFridaMusicPlayStore(context, forceBrowser = true)))
                    }
                    DebugScenarioButton(R.string.support_debug_play_unavailable) {
                        onLinkResult(R.string.support_no_compatible_app)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugScenarioButton(@StringRes label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(stringResource(label))
    }
}

@Composable
private fun SupportMessageCard(
    @StringRes message: Int,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(
                stringResource(message),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            onRetry?.let { retry ->
                TextButton(onClick = retry) {
                    Text(stringResource(R.string.support_retry))
                }
            }
        }
    }
}

@StringRes
private fun supportProductTitle(productId: String): Int = when (productId) {
    "support_5" -> R.string.support_level_5
    "support_10" -> R.string.support_level_10
    "support_20" -> R.string.support_level_20
    "support_50" -> R.string.support_level_50
    "support_100" -> R.string.support_level_100
    "support_200" -> R.string.support_level_200
    "support_500" -> R.string.support_level_500
    "support_1000" -> R.string.support_level_1000
    else -> R.string.support_project
}

private fun supportProductIcon(productId: String): ImageVector = when (productId) {
    "support_5" -> Icons.Rounded.FavoriteBorder
    "support_10" -> Icons.Rounded.Coffee
    "support_20" -> Icons.Rounded.Coffee
    "support_50" -> Icons.Rounded.Restaurant
    "support_100" -> Icons.Rounded.RocketLaunch
    "support_200" -> Icons.Rounded.VolunteerActivism
    "support_500" -> Icons.Rounded.AutoAwesome
    "support_1000" -> Icons.Rounded.LocalDining
    else -> Icons.Rounded.Favorite
}

@StringRes
private fun billingIssueMessage(issue: SupportBillingIssue): Int = when (issue) {
    SupportBillingIssue.BILLING_UNAVAILABLE -> R.string.support_billing_unavailable
    SupportBillingIssue.SERVICE_DISCONNECTED -> R.string.support_billing_disconnected
    SupportBillingIssue.PRODUCT_UNAVAILABLE -> R.string.support_product_unavailable
    SupportBillingIssue.PURCHASE_CANCELLED -> R.string.support_purchase_cancelled
    SupportBillingIssue.PURCHASE_ERROR -> R.string.support_purchase_error
    SupportBillingIssue.CONSUMPTION_ERROR -> R.string.support_consumption_error
}

@StringRes
private fun supportLinkMessage(result: SupportLinkResult): Int = when (result) {
    SupportLinkResult.PAYPAL_OPENED -> R.string.support_paypal_opened
    SupportLinkResult.PLAY_STORE_OPENED -> R.string.support_play_store_opened
    SupportLinkResult.BROWSER_OPENED -> R.string.support_browser_opened
    SupportLinkResult.UNAVAILABLE -> R.string.support_no_compatible_app
}


private const val FEATURED_PRODUCT_ID = "support_50"
