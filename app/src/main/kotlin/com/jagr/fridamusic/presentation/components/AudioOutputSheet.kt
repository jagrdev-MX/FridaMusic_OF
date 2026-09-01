package com.jagr.fridamusic.presentation.components

import android.content.Intent
import android.media.AudioDeviceInfo
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.playback.PlayerConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSheet(
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val service = playerConnection.service
    val devices by service.audioOutputDevices.collectAsState()
    val selectedDeviceId by service.selectedAudioOutputDeviceId.collectAsState()
    val playerVolume by service.playerVolume.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.audio_output_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            devices.forEach { device ->
                AudioOutputRow(
                    icon = audioDeviceIcon(device.type),
                    name = device.productName.toString().trim().ifBlank {
                        audioDeviceTypeLabel(device.type)
                    },
                    type = audioDeviceTypeLabel(device.type),
                    selected = selectedDeviceId == device.id,
                    onClick = { service.setPreferredAudioDevice(device.id) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            AudioOutputRow(
                icon = Icons.Rounded.Settings,
                name = stringResource(R.string.audio_output_system_option),
                type = stringResource(R.string.audio_output_system_desc),
                selected = false,
                onClick = {
                    service.setPreferredAudioDevice(null)
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(
                                Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                                },
                            )
                        } else {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                    }.onFailure {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                    }
                },
            )

            Text(
                text = stringResource(R.string.player_volume),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Slider(
                value = playerVolume.coerceIn(0f, 1f),
                onValueChange = { service.playerVolume.value = it.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun AudioOutputRow(
    icon: ImageVector,
    name: String,
    type: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(type, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun audioDeviceIcon(type: Int): ImageVector = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Icons.Rounded.Headphones
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID -> Icons.Rounded.BluetoothAudio
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Rounded.Usb
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI_EARC -> Icons.Rounded.Tv
    else -> Icons.Rounded.VolumeUp
}

@Composable
private fun audioDeviceTypeLabel(type: Int): String = stringResource(
    when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.audio_output_speaker
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> R.string.audio_output_wired
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_HEARING_AID -> R.string.audio_output_bluetooth
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.audio_output_usb
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> R.string.audio_output_hdmi
        else -> R.string.audio_output_other
    },
)
