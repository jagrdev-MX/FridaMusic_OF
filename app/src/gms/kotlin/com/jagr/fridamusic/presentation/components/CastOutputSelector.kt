package com.jagr.fridamusic.presentation.components

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.jagr.fridamusic.R
import com.jagr.fridamusic.playback.PlayerConnection

private fun createMediaRouteButton(context: Context) = MediaRouteButton(context).also { button ->
    CastButtonFactory.setUpMediaRouteButton(button.context, button)
}

@Composable
fun CastRouteButton(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    if (playerConnection.service.castConnectionHandler == null) return

    val contentDescription = stringResource(R.string.cast_select_device)
    AndroidView(
        factory = { context ->
            val themedContext = ContextThemeWrapper(
                context,
                R.style.ThemeOverlay_FridaMusic_CastRouteButton,
            )
            createMediaRouteButton(themedContext).also { button ->
                button.contentDescription = contentDescription
            }
        },
        update = { button ->
            button.contentDescription = contentDescription
        },
        modifier = modifier,
    )
}

@Composable
fun CastOutputSelector(playerConnection: PlayerConnection) {
    val handler = playerConnection.service.castConnectionHandler ?: return
    val hasDevices by handler.hasCastDevices.collectAsState()
    val isCasting by handler.isCasting.collectAsState()
    val isConnecting by handler.isConnecting.collectAsState()
    val deviceName by handler.castDeviceName.collectAsState()
    val error by handler.castError.collectAsState()

    if (!hasDevices && !isCasting && !isConnecting && error == null) return

    val context = LocalContext.current
    val routeButton = remember(context) {
        createMediaRouteButton(context).apply {
            alpha = 0f
        }
    }
    val detail = when {
        isCasting -> stringResource(
            R.string.casting_to,
            deviceName ?: stringResource(R.string.google_cast),
        )
        isConnecting -> stringResource(R.string.cast_connecting)
        error != null -> error.orEmpty()
        else -> stringResource(R.string.cast_select_device)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDevices || isCasting) { routeButton.performClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AndroidView(
            factory = { routeButton },
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.google_cast),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null && !isCasting) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CastDrawerItem(
    playerConnection: PlayerConnection?,
    content: @Composable (onClick: () -> Unit) -> Unit,
) {
    if (playerConnection?.service?.castConnectionHandler == null) return

    val context = LocalContext.current
    val contentDescription = stringResource(R.string.cast_select_device)
    val routeButton = remember(context) {
        createMediaRouteButton(context)
    }

    content { routeButton.performClick() }
    AndroidView(
        factory = { routeButton },
        update = { button ->
            button.alpha = 0f
            button.contentDescription = contentDescription
        },
        modifier = Modifier.size(0.dp),
    )
}
