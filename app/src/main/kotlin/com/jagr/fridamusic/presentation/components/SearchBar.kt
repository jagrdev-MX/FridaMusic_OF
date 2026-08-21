package com.jagr.fridamusic.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.jagr.fridamusic.R
import kotlin.math.max
import kotlin.math.roundToInt

private val InputFieldHeight = 52.dp
private val SearchBarCornerRadius = InputFieldHeight / 2
private val SearchBarVerticalPadding = 8.dp
private val SearchBarHorizontalPadding = 16.dp

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val animationProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "searchBarAnimation",
    )

    val windowInsets = WindowInsets.statusBars
    val topInset = windowInsets.asPaddingValues().calculateTopPadding()
    val startInset = windowInsets.asPaddingValues()
        .calculateStartPadding(LocalLayoutDirection.current)
    val endInset = windowInsets.asPaddingValues()
        .calculateEndPadding(LocalLayoutDirection.current)

    val topPadding = SearchBarVerticalPadding + topInset
    val animatedSurfaceTopPadding = lerp(topPadding, 0.dp, animationProgress)

    val animatedShape by remember(animationProgress) {
        derivedStateOf {
            val animatedRadius = SearchBarCornerRadius * (1 - animationProgress)
            RoundedCornerShape(CornerSize(animatedRadius))
        }
    }

    BoxWithConstraints(
        modifier = modifier.offset { IntOffset(x = 0, y = 0) },
        propagateMinConstraints = true,
    ) {
        val density = LocalDensity.current
        val startPadding: Float
        val endPadding: Float
        val height: Float
        val width: Float

        with(density) {
            val startHeight = max(
                constraints.minHeight,
                InputFieldHeight.roundToPx()
            ).coerceAtMost(constraints.maxHeight).toFloat()
            val endHeight = constraints.maxHeight.toFloat()

            height = lerp(startHeight, endHeight, animationProgress)
            width = constraints.maxWidth.toFloat()
            startPadding = lerp(
                (SearchBarHorizontalPadding + startInset).roundToPx().toFloat(),
                0f,
                animationProgress,
            )
            endPadding = lerp(
                (SearchBarHorizontalPadding + endInset).roundToPx().toFloat(),
                0f,
                animationProgress,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + InputFieldHeight + SearchBarVerticalPadding * 2)
                .background(MaterialTheme.colorScheme.surface),
        )

        Surface(
            shape = animatedShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(
                    top = animatedSurfaceTopPadding,
                    start = with(density) { startPadding.toDp() },
                    end = with(density) { endPadding.toDp() },
                )
                .size(
                    width = with(density) { width.toDp() },
                    height = with(density) { height.toDp() },
                ),
        ) {
            Column {
                SearchInputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    active = active,
                    onActiveChange = onActiveChange,
                    focusRequester = focusRequester,
                )

                if (animationProgress > 0f) {
                    Column(Modifier.alpha(animationProgress)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        content()
                    }
                }
            }
        }
    }

    BackHandler(enabled = active) {
        onQueryChange("")
        onActiveChange(false)
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(InputFieldHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))

        BasicTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                if (!active) onActiveChange(true)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                TextStyle(color = MaterialTheme.colorScheme.onSurface)
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.search),
                            style = LocalTextStyle.current,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}