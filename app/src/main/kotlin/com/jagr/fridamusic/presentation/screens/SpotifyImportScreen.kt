package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.spotifyimport.SpotifyImportProgressUi
import com.jagr.fridamusic.spotifyimport.SpotifyImportSourceType
import com.jagr.fridamusic.spotifyimport.SpotifyImportSourceUi
import com.jagr.fridamusic.spotifyimport.SpotifyImportSummaryUi
import com.jagr.fridamusic.spotifyimport.SpotifyImportUiState
import com.jagr.fridamusic.spotifyimport.SpotifyImportViewModel

private val SpotifyGreen = Color(0xFF1DB954)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    onBack: () -> Unit,
    viewModel: SpotifyImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.spotify_import_title),
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
                    if (state.isAuthenticated && state.progress == null) {
                        IconButton(onClick = { viewModel.loadSources() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.spotify_refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = when {
                state.isLoading && !state.isAuthenticated -> ContentPhase.LOADING
                !state.isAuthenticated -> ContentPhase.LOGIN
                state.progress != null -> ContentPhase.IMPORTING
                else -> ContentPhase.SOURCES
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "spotify_phase",
        ) { phase ->
            when (phase) {
                ContentPhase.LOADING -> LoadingView()
                ContentPhase.LOGIN -> LoginView(
                    isLoading = state.isLoading,
                    error = state.errorMessage,
                    onConnect = { spDc, spKey -> viewModel.connectWithCookies(spDc, spKey) },
                    onDismissError = { viewModel.dismissError() },
                )
                ContentPhase.IMPORTING -> ImportingView(
                    progress = state.progress!!,
                    onCancel = { viewModel.cancelImport() },
                )
                ContentPhase.SOURCES -> SourcesView(
                    state = state,
                    onToggle = { viewModel.toggleSource(it) },
                    onSelectAll = { viewModel.selectAllSources() },
                    onClearSelection = { viewModel.clearSelection() },
                    onAddByUrl = { viewModel.addPlaylistByUrl(it) },
                    onImport = { viewModel.importSelectedSources() },
                    onLogout = { viewModel.logout() },
                    onDismissError = { viewModel.dismissError() },
                    onDismissSummary = { viewModel.dismissSummary() },
                )
            }
        }
    }
}

private enum class ContentPhase { LOADING, LOGIN, IMPORTING, SOURCES }

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = SpotifyGreen)
            Text(
                text = stringResource(R.string.spotify_loading_library),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoginView(
    isLoading: Boolean,
    error: String?,
    onConnect: (String, String) -> Unit,
    onDismissError: () -> Unit,
) {
    var spDc by remember { mutableStateOf("") }
    var spKey by remember { mutableStateOf("") }
    var showCookies by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.spotify_login_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.spotify_import_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = spDc,
                        onValueChange = { spDc = it.trim() },
                        label = { Text("sp_dc cookie") },
                        placeholder = { Text("AQC…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showCookies) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showCookies = !showCookies }) {
                                Text(if (showCookies) stringResource(R.string.token_shown) else stringResource(R.string.token_hidden), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )
                    OutlinedTextField(
                        value = spKey,
                        onValueChange = { spKey = it.trim() },
                        label = { Text("sp_key cookie (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showCookies) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )

                    Text(
                        text = "Open Spotify in your browser → F12 → Application → Cookies → copy the sp_dc value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = { onConnect(spDc, spKey) },
                        enabled = spDc.isNotBlank() && !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.spotify_connect))
                    }
                }
            }
        }

        if (error != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismissError, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportingView(
    progress: SpotifyImportProgressUi,
    onCancel: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CircularProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.size(72.dp),
                strokeWidth = 6.dp,
                color = SpotifyGreen,
            )

            Text(
                text = stringResource(R.string.spotify_import_in_progress),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = progress.sourceTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.spotify_import_progress_step,
                            progress.sourceTitle,
                            progress.completedSources,
                            progress.totalSources,
                            progress.matchedTracks,
                            progress.totalTracks,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SpotifyGreen,
                    )
                    Text(
                        text = "${progress.percent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }

            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SourcesView(
    state: SpotifyImportUiState,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddByUrl: (String) -> Unit,
    onImport: () -> Unit,
    onLogout: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSummary: () -> Unit,
) {
    var showAddUrl by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AccountHeader(
            name = state.accountName,
            avatarUrl = state.accountAvatarUrl,
            sourceCount = state.sources.size,
            onLogout = onLogout,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        AnimatedVisibility(visible = state.errorMessage != null) {
            state.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismissError, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.selectedSourceIds.isNotEmpty())
                    stringResource(R.string.spotify_selected_count, state.selectedSourceIds.size)
                else
                    stringResource(R.string.spotify_available_count, state.sources.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showAddUrl = true }) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.spotify_import_by_link), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = if (state.selectedSourceIds.size == state.sources.size) onClearSelection else onSelectAll) {
                    Text(
                        text = if (state.selectedSourceIds.size == state.sources.size)
                            stringResource(R.string.spotify_clear_selection)
                        else
                            stringResource(R.string.spotify_select_all),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SpotifyGreen)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.sources.isEmpty() && !state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.spotify_no_sources),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.sources, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    isSelected = source.id in state.selectedSourceIds,
                    onClick = { onToggle(source.id) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { showAddUrl = true },
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.spotify_import_by_link), modifier = Modifier.size(20.dp))
            }
            Button(
                onClick = onImport,
                enabled = state.canImport,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (state.selectedSourceIds.isEmpty())
                        stringResource(R.string.spotify_import_selected)
                    else
                        stringResource(R.string.spotify_import_selected) + " (${state.selectedSourceIds.size})",
                )
            }
        }
    }

    if (showAddUrl) {
        AddByUrlDialog(
            isLoading = state.isLoading,
            onAdd = { url -> onAddByUrl(url); showAddUrl = false },
            onDismiss = { showAddUrl = false },
        )
    }

    state.summary?.let { summary ->
        SummaryDialog(summary = summary, onDismiss = onDismissSummary)
    }
}

@Composable
private fun AccountHeader(
    name: String,
    avatarUrl: String?,
    sourceCount: Int,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SpotifyGreen),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.spotify_connected_as, name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.spotify_available_count, sourceCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onLogout) {
            Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SourceRow(
    source: SpotifyImportSourceUi,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(1.5.dp, SpotifyGreen, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                SpotifyGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (source.thumbnailUrl != null) {
                    AsyncImage(
                        model = source.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (source.type == SpotifyImportSourceType.LIKED_SONGS)
                            Icons.Rounded.Favorite else Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (source.subtitle.isNotBlank()) {
                    Text(
                        text = source.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (source.trackCount != null) {
                    Text(
                        text = stringResource(R.string.spotify_track_count, source.trackCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                    )
                }
            }

            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AddByUrlDialog(
    isLoading: Boolean,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spotify_import_by_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.spotify_import_by_link_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text(stringResource(R.string.spotify_import_by_link_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(url) },
                enabled = url.isNotBlank() && !isLoading,
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(stringResource(R.string.spotify_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun SummaryDialog(
    summary: SpotifyImportSummaryUi,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spotify_import_complete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.spotify_import_summary,
                        summary.sourceCount,
                        summary.importedTracks,
                        summary.failedTracks,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                HorizontalDivider()
                summary.sources.forEach { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = source.title,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.spotify_source_summary, source.title, source.importedTracks, source.totalTracks),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (source.failedTracks > 0)
                                MaterialTheme.colorScheme.error
                            else
                                SpotifyGreen,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.close_content_desc)) }
        },
    )
}