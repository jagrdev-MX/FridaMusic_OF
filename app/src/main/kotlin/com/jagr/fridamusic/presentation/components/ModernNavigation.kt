package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.constants.FastAnimationSpec

private data class FloatingNavigationItem(
    val destination: RootDestination,
    val label: String,
    val icon: ImageVector,
)

enum class RootDestination(val route: String) {
    HOME("home"),
    SEARCH("search"),
    LIBRARY("library"),
    ;

    companion object {
        fun fromRoute(route: String?): RootDestination? =
            entries.firstOrNull { destination -> destination.route == route }
    }
}

@Composable
fun ModernBottomNav(
    currentRoot: RootDestination,
    onNavigate: (RootDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember {
        listOf(
            FloatingNavigationItem(RootDestination.HOME, "Inicio", Icons.Rounded.Home),
            FloatingNavigationItem(RootDestination.SEARCH, "Buscar", Icons.Rounded.Search),
            FloatingNavigationItem(RootDestination.LIBRARY, "Biblioteca", Icons.Rounded.LibraryMusic),
        )
    }
    val activeIndex = items.indexOfFirst { item -> item.destination == currentRoot }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    InteractiveNavigationItem(
                        item = item,
                        isSelected = index == activeIndex,
                        onClick = { onNavigate(item.destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveNavigationItem(
    item: FloatingNavigationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val activeTextStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
    )
    val itemColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f)
        },
        animationSpec = tween(
            durationMillis = 180,
            easing = FastOutSlowInEasing,
        ),
        label = "navigationItemColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(
            durationMillis = 180,
            easing = FastOutSlowInEasing,
        ),
        label = "navigationContentColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.92f,
        animationSpec = FastAnimationSpec,
        label = "navigationIconScale",
    )

    Row(
        modifier = Modifier
            .height(48.dp)
            .defaultMinSize(minWidth = 48.dp)
            .clip(CircleShape)
            .background(itemColor)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 140,
                    easing = LinearOutSlowInEasing,
                ),
            ) + expandHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                expandFrom = Alignment.Start,
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 120,
                    easing = FastOutLinearInEasing,
                ),
            ) + shrinkHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                shrinkTowards = Alignment.Start,
            ),
        ) {
            Text(
                text = item.label,
                modifier = Modifier.padding(start = 8.dp),
                style = activeTextStyle,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
        }
    }
}
