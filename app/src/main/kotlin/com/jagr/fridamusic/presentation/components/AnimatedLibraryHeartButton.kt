package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun AnimatedLibraryHeartButton(
    isSaved: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var previousSaved by remember { mutableStateOf<Boolean?>(null) }
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isSaved, enabled) {
        if (!enabled) return@LaunchedEffect
        val previous = previousSaved
        previousSaved = isSaved
        if (previous == null || previous == isSaved) return@LaunchedEffect

        if (isSaved) {
            scale.snapTo(0.65f)
            rotation.snapTo(-6f)
            coroutineScope {
                launch {
                    scale.animateTo(
                        targetValue = 1.22f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                    scale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                }
                launch {
                    rotation.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessLow))
                }
            }
        } else {
            scale.snapTo(0.82f)
            rotation.snapTo(-8f)
            coroutineScope {
                launch {
                    scale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                }
                launch {
                    rotation.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 300
                            -8f at 0
                            9f at 100
                            -6f at 200
                            0f at 300
                        },
                    )
                }
            }
        }
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = null,
            tint = if (isSaved) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White
            },
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = rotation.value
                }
                .alpha(if (enabled) 1f else 0.45f)
                .size(28.dp),
        )
    }
}
