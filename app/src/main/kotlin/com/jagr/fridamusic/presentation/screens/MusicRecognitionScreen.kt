package com.jagr.fridamusic.presentation.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.recognition.MusicRecognitionService
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicRecognitionScreen(
    onBack: () -> Unit,
    onSearchResult: (String) -> Unit,
    onPlayResult: (RecognitionResult) -> Unit,
    autoStart: Boolean = true,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var state by remember { mutableStateOf<RecognitionUiState>(RecognitionUiState.Ready) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    fun cancelRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        MusicRecognitionService.reset()
        state = RecognitionUiState.Ready
    }

    fun startRecognition() {
        recognitionJob?.cancel()
        MusicRecognitionService.reset()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        recognitionJob = scope.launch {
            val statusJob = launch {
                MusicRecognitionService.recognitionStatus.collectLatest { status ->
                    state = status.toUiState()
                }
            }
            try {
                val result = withTimeout(RECOGNITION_TIMEOUT_MS) {
                    MusicRecognitionService.recognize(context.applicationContext)
                }
                state = result.toUiState()
            } catch (_: TimeoutCancellationException) {
                state = RecognitionUiState.Error(context.getString(R.string.music_recognition_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = RecognitionUiState.Error(
                    error.message ?: context.getString(R.string.music_recognition_recognition_failed),
                )
            } finally {
                statusJob.cancel()
            }
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startRecognition()
            } else {
                val permanentlyDenied = permissionRequested &&
                    activity?.let {
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            it,
                            Manifest.permission.RECORD_AUDIO,
                        )
                    } == true
                state = RecognitionUiState.PermissionRequired(permanentlyDenied)
            }
        }

    fun startOrRequestPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecognition()
        } else {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recognitionJob?.isActive == true) {
                cancelRecognition()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recognitionJob?.cancel()
            MusicRecognitionService.reset()
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart) startOrRequestPermission()
    }

    val backAction = {
        cancelRecognition()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognize_music)) },
                navigationIcon = {
                    IconButton(onClick = backAction) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val background = Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                Color.Transparent,
            ),
            center = Offset(620f, 260f),
            radius = 950f,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(140))) },
                label = "recognition_state",
            ) { current ->
                when (current) {
                    is RecognitionUiState.Success -> RecognitionResultCard(
                        result = current.result,
                        onListenAgain = ::startOrRequestPermission,
                        onSearch = {
                            onSearchResult("${current.result.title} ${current.result.artist}".trim())
                        },
                        onPlay = { onPlayResult(current.result) },
                    )

                    else -> RecognitionListeningContent(
                        state = current,
                        onStart = ::startOrRequestPermission,
                        onCancel = ::cancelRecognition,
                        onPermissionAction = {
                            if (current is RecognitionUiState.PermissionRequired &&
                                current.permanentlyDenied
                            ) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            } else {
                                permissionRequested = true
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.music_recognition_powered_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecognitionListeningContent(
    state: RecognitionUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onPermissionAction: () -> Unit,
) {
    val isListening = state is RecognitionUiState.Listening
    val isProcessing = state is RecognitionUiState.Processing
    val isBusy = isListening || isProcessing

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.music_recognition_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.music_recognition_tap_to_listen),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        ListeningOrb(
            active = isListening,
            processing = isProcessing,
            enabled = !isBusy,
            onClick = onStart,
        )
        Spacer(Modifier.height(24.dp))

        when (state) {
            RecognitionUiState.Ready -> StatusPill(stringResource(R.string.music_recognition_tap_to_listen))
            RecognitionUiState.Listening -> StatusPill(stringResource(R.string.music_recognition_listening))
            RecognitionUiState.Processing -> StatusPill(stringResource(R.string.music_recognition_processing))
            is RecognitionUiState.PermissionRequired -> MessageCard(
                title = stringResource(R.string.music_recognition_permission_title),
                message = if (state.permanentlyDenied) {
                    stringResource(R.string.music_recognition_permission_permanent)
                } else {
                    stringResource(R.string.music_recognition_permission_desc)
                },
                action = if (state.permanentlyDenied) {
                    stringResource(R.string.music_recognition_open_settings)
                } else {
                    stringResource(R.string.music_recognition_permission_action)
                },
                onAction = onPermissionAction,
            )
            is RecognitionUiState.NoMatch -> MessageCard(
                title = stringResource(R.string.music_recognition_no_match),
                message = state.message,
                action = stringResource(R.string.music_recognition_listen_again),
                onAction = onStart,
            )
            is RecognitionUiState.Error -> MessageCard(
                title = stringResource(R.string.music_recognition_error),
                message = state.message,
                action = stringResource(R.string.music_recognition_listen_again),
                onAction = onStart,
            )
            is RecognitionUiState.Success -> Unit
        }

        AnimatedVisibility(visible = isBusy) {
            LoadingIndicator(modifier = Modifier.padding(top = 20.dp).size(36.dp))
        }
        AnimatedVisibility(visible = isListening) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 18.dp).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun ListeningOrb(
    active: Boolean,
    processing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "recognition_orb")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "recognition_pulse",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.04f else 1f,
        animationSpec = tween(220),
        label = "recognition_scale",
    )
    val color = if (processing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val brush = Brush.radialGradient(
        listOf(
            color.copy(alpha = 0.42f),
            color.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )

    Box(
        modifier = Modifier
            .size(248.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (active) {
                val radius = size.minDimension * (0.34f + pulse * 0.18f)
                drawCircle(
                    color = color.copy(alpha = (1f - pulse) * 0.6f),
                    radius = radius,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Icon(
            imageVector = if (processing) Icons.Rounded.MusicNote else Icons.Rounded.Mic,
            contentDescription = null,
            modifier = Modifier.size(58.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RecognitionResultCard(
    result: RecognitionResult,
    onListenAgain: () -> Unit,
    onSearch: () -> Unit,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val cover = result.coverArtHqUrl ?: result.coverArtUrl
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        if (cover.isNullOrBlank()) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MusicNote, null, Modifier.size(40.dp))
                            }
                        } else {
                            AsyncImage(
                                model = cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = result.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val metadata = listOfNotNull(result.album, result.genre, result.releaseDate)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (metadata.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                result.label?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!result.shazamUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.shazamUrl)))
                        },
                    ) {
                        Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.music_recognition_open_shazam))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onListenAgain,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.music_recognition_listen_again))
            }
            Button(
                onClick = if (result.youtubeVideoId.isNullOrBlank()) onSearch else onPlay,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Icon(
                    imageVector = if (result.youtubeVideoId.isNullOrBlank()) {
                        Icons.Rounded.Search
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        if (result.youtubeVideoId.isNullOrBlank()) R.string.search
                        else R.string.music_recognition_play,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onAction) {
                Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(action)
            }
        }
    }
}

private sealed interface RecognitionUiState {
    data object Ready : RecognitionUiState
    data object Listening : RecognitionUiState
    data object Processing : RecognitionUiState
    data class PermissionRequired(val permanentlyDenied: Boolean) : RecognitionUiState
    data class Success(val result: RecognitionResult) : RecognitionUiState
    data class NoMatch(val message: String) : RecognitionUiState
    data class Error(val message: String) : RecognitionUiState
}

private fun RecognitionStatus.toUiState(): RecognitionUiState = when (this) {
    RecognitionStatus.Ready -> RecognitionUiState.Ready
    RecognitionStatus.Listening -> RecognitionUiState.Listening
    RecognitionStatus.Processing -> RecognitionUiState.Processing
    is RecognitionStatus.Success -> RecognitionUiState.Success(result)
    is RecognitionStatus.NoMatch -> RecognitionUiState.NoMatch(message)
    is RecognitionStatus.Error -> RecognitionUiState.Error(message)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val RECOGNITION_TIMEOUT_MS = 30_000L
