package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.viewmodels.BandUiState
import com.jagr.fridamusic.viewmodels.EQViewModel
import kotlin.math.roundToInt

private const val GAIN_RANGE = 12f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQScreen(
    onBack: () -> Unit,
    viewModel: EQViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val profiles by viewModel.profiles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.equalizer),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showImportDialog() }) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = stringResource(R.string.eq_import_autoeq))
                    }
                    IconButton(onClick = { viewModel.resetBands() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.eq_reset))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                EQMasterToggle(
                    enabled = state.isEnabled,
                    onToggle = { viewModel.setEnabled(it) },
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }

            item {
                AnimatedVisibility(
                    visible = state.isEnabled,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column {
                        EQBandGraph(
                            bands = state.bands,
                            preamp = state.preamp,
                            onBandGainChange = { index, gain -> viewModel.setBandGain(index, gain) },
                            onPreampChange = { viewModel.setPreamp(it) },
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )

                        EQProfileSection(
                            profiles = profiles,
                            activeProfileId = state.activeProfileId,
                            onLoadProfile = { viewModel.loadProfile(it) },
                            onDeleteProfile = { viewModel.deleteProfile(it) },
                            onSaveNew = { viewModel.showSaveDialog() },
                        )
                    }
                }
            }
        }
    }

    if (state.showSaveDialog) {
        SaveProfileDialog(
            name = state.editingProfileName,
            onNameChange = { viewModel.setProfileName(it) },
            onConfirm = { viewModel.saveCurrentAsProfile() },
            onDismiss = { viewModel.dismissSaveDialog() },
        )
    }

    if (state.showImportDialog) {
        ImportAutoEQDialog(
            text = state.importText,
            error = state.importError,
            onTextChange = { viewModel.setImportText(it) },
            onConfirm = { viewModel.importProfile() },
            onDismiss = { viewModel.dismissImportDialog() },
        )
    }
}

@Composable
private fun EQMasterToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.equalizer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.eq_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun EQBandGraph(
    bands: List<BandUiState>,
    preamp: Double,
    onBandGainChange: (Int, Double) -> Unit,
    onPreampChange: (Double) -> Unit,
) {
    val bandScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.eq_bands),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(bandScrollState)
                .padding(horizontal = 8.dp)
                .height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EQSlider(
                label = "Pre",
                value = preamp.toFloat(),
                onValueChange = { onPreampChange(it.toDouble()) },
                isPreamp = true,
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            )

            bands.forEachIndexed { index, band ->
                EQSlider(
                    label = band.frequency.toInt().let { hz ->
                        if (hz >= 1000) "${hz / 1000}k" else "$hz"
                    },
                    value = band.gain.toFloat(),
                    onValueChange = { onBandGainChange(index, it.toDouble()) },
                    enabled = band.enabled,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.eq_drag_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun EQSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    isPreamp: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    var dragAccumulator by remember(label) { mutableFloatStateOf(value) }

    val clampedValue = value.coerceIn(-GAIN_RANGE, GAIN_RANGE)
    val trackFraction = (clampedValue + GAIN_RANGE) / (2 * GAIN_RANGE)
    val thumbPositionFraction = 1f - trackFraction

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(32.dp),
    ) {
        Text(
            text = if (value >= 0) "+${value.roundToInt()}" else "${value.roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                value > 0f -> primary
                value < 0f -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .width(28.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) surfaceVariant else surfaceVariant.copy(alpha = 0.4f))
                .pointerInput(label) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumulator = value },
                        onVerticalDrag = { _, delta ->
                            val sensitivity = (2 * GAIN_RANGE) / size.height.toFloat()
                            dragAccumulator = (dragAccumulator - delta * sensitivity)
                                .coerceIn(-GAIN_RANGE, GAIN_RANGE)
                            onValueChange(dragAccumulator)
                        },
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            if (enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((160 * (1f - thumbPositionFraction)).dp)
                        .background(
                            color = when {
                                isPreamp -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                value > 0.5f -> primary.copy(alpha = 0.6f)
                                value < -0.5f -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            },
                        )
                        .align(Alignment.BottomCenter),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (160 * thumbPositionFraction - 10).coerceAtLeast(0f).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (enabled) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun EQProfileSection(
    profiles: List<com.jagr.fridamusic.eq.data.SavedEQProfile>,
    activeProfileId: String?,
    onLoadProfile: (com.jagr.fridamusic.eq.data.SavedEQProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveNew: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.eq_saved_profiles),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            FilledTonalIconButton(
                onClick = onSaveNew,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = stringResource(R.string.save), modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.eq_no_profiles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onSaveNew) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.save))
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    EQProfileChip(
                        profile = profile,
                        isActive = profile.id == activeProfileId,
                        onLoad = { onLoadProfile(profile) },
                        onDelete = { onDeleteProfile(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EQProfileChip(
    profile: com.jagr.fridamusic.eq.data.SavedEQProfile,
    isActive: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier
            .height(64.dp)
            .then(
                if (isActive) Modifier.border(1.5.dp, primary, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable { onLoad() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) primary.copy(alpha = 0.12f) else surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isActive) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = profile.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.eq_profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ImportAutoEQDialog(
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_import_autoeq)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.eq_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.eq_import_paste)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.eq_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
