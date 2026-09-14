package com.jagr.fridamusic.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.ads.NativeAdPlacement
import com.jagr.fridamusic.ads.NativeAdSlot
import com.jagr.fridamusic.ads.NativeAdStyle
import com.jagr.fridamusic.constants.AutoLoadMoreKey
import com.jagr.fridamusic.db.entities.LyricsEntity
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.presentation.LocalSongActionsNavigation
import com.jagr.fridamusic.presentation.SongActionsNavigation
import com.jagr.fridamusic.presentation.toLyricsProviderDisplayName
import com.jagr.fridamusic.presentation.components.KaraokeLyrics
import com.jagr.fridamusic.presentation.components.AudioOutputSheet
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.MarqueeText
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.universalMediaClickable
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.utils.MemoryDiagnostics
import com.jagr.fridamusic.utils.rememberPreference
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.PlaylistsViewModel
import com.jagr.fridamusic.viewmodels.LyricsMenuViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
@Composable
fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onBack: () -> Unit,
    onNavigateFromPlayer: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    collapseDragModifier: Modifier = Modifier,
    playlistsViewModel: PlaylistsViewModel? = null,
    showNativeAd: Boolean = false,
    nativeAdPresentationCycleKey: String? = null,
    onNativeAdConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        MemoryDiagnostics.log("NowPlaying entered")
        onDispose { }
    }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val playbackError by playerConnection.error.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val isCasting by playerConnection.isCasting.collectAsState()
    val castDeviceName by playerConnection.castDeviceName.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentQueueIndex by playerConnection.currentWindowIndex.collectAsState()
    val sleepTimer = playerConnection.service.sleepTimer
    val audioOutputDevices by playerConnection.service.audioOutputDevices.collectAsState()
    val selectedAudioOutputDeviceId by playerConnection.service.selectedAudioOutputDeviceId.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessageTemplate = stringResource(R.string.error_playing_snackbar)
    LaunchedEffect(playbackError) {
        playbackError?.let {
            snackbarHostState.showSnackbar(errorMessageTemplate.format(it.errorCodeName))
        }
    }

    val song = mediaMetadata ?: run { onBack(); return }
    NowPlayingStatusBarIconContrast(
        mediaId = song.id,
        artworkUrl = song.thumbnailUrl,
    )
    val navigation = LocalSongActionsNavigation.current
    val playerMenuNavigation = remember(navigation, onNavigateFromPlayer) {
        SongActionsNavigation(
            openAlbum = { albumId ->
                onNavigateFromPlayer { navigation.openAlbum(albumId) }
            },
            openArtist = { artistId ->
                onNavigateFromPlayer { navigation.openArtist(artistId) }
            },
            openSearchResult = { query ->
                onNavigateFromPlayer { navigation.openSearchResult(query) }
            },
        )
    }
    val navigableAlbumId = remember(song.album?.id) {
        song.album?.id?.trim()?.takeIf(::isNavigableAlbumId)
    }
    val searchQuery = remember(song.id, song.title, song.artists) {
        listOfNotNull(
            song.title.trim().takeIf(String::isNotEmpty),
            song.artists.firstOrNull()?.name?.trim()?.takeIf(String::isNotEmpty),
        ).joinToString(" ")
    }
    val artistsText = remember(song.id, song.artists) {
        song.artists.joinToString(", ") { it.name }
    }
    val singleArtistId = remember(song.id, song.artists) {
        song.artists.singleOrNull()?.id?.trim()?.takeIf(String::isNotEmpty)
    }
    val titleActionDescription = if (navigableAlbumId != null) {
        stringResource(R.string.now_playing_open_album, song.title)
    } else {
        stringResource(R.string.now_playing_search_song, song.title)
    }
    val artistActionDescription = when {
        song.artists.size > 1 -> stringResource(R.string.now_playing_view_artists)
        singleArtistId != null -> stringResource(
            R.string.now_playing_open_artist,
            song.artists.single().name,
        )
        else -> null
    }
    val likeHaptic = LocalHapticFeedback.current
    val titleInteractionSource = remember(song.id) { MutableInteractionSource() }
    val artistInteractionSource = remember(song.id) { MutableInteractionSource() }
    var showArtistPicker by remember(song.id) { mutableStateOf(false) }

    var positionMs by remember { mutableLongStateOf(playerConnection.player.currentPosition) }
    var durationMs by remember { mutableLongStateOf(playerConnection.player.duration.coerceAtLeast(0L)) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var liveAudioFormat by remember(song.id) { mutableStateOf<Format?>(null) }

    LaunchedEffect(song.id) {
        while (isActive) {
            liveAudioFormat = playerConnection.player.audioFormat
            if (!isDragging) {
                val castHandler = playerConnection.service.castConnectionHandler
                if (castHandler?.isCasting?.value == true) {
                    positionMs = castHandler.castPosition.value
                    durationMs = castHandler.castDuration.value.coerceAtLeast(0L)
                } else {
                    positionMs = playerConnection.player.currentPosition
                    durationMs = playerConnection.player.duration.coerceAtLeast(0L)
                }
            }
            delay(500.milliseconds)
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var queueMenuSelection by remember { mutableStateOf<QueueMenuSelection?>(null) }

    val hasLyrics = currentLyrics != null
    val coroutineScope = rememberCoroutineScope()
    val panelMotion = remember { PlayerPanelMotionState() }
    val panelMotionSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }
    val panelFlingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = panelMotion.draggableState,
        positionalThreshold = { distance -> distance * 0.32f },
        animationSpec = panelMotionSpec,
    )
    val panelNestedScrollConnection = remember(panelMotion.draggableState, panelFlingBehavior) {
        PlayerPanelNestedScrollConnection(
            state = panelMotion.draggableState,
            flingBehavior = panelFlingBehavior,
        )
    }
    BackHandler(enabled = panelMotion.activePanel != null) {
        coroutineScope.launch {
            panelMotion.close(panelMotionSpec)
        }
    }
    val queueInteractionSource = remember { MutableInteractionSource() }
    val lyricsInteractionSource = remember { MutableInteractionSource() }
    val panelHandleInteractionSource = remember { MutableInteractionSource() }
    val panelCloseDragModifier = Modifier.anchoredDraggable(
        state = panelMotion.draggableState,
        orientation = Orientation.Vertical,
        interactionSource = panelHandleInteractionSource,
        flingBehavior = panelFlingBehavior,
    )
    val queueIconDragged by queueInteractionSource.collectIsDraggedAsState()
    val lyricsIconDragged by lyricsInteractionSource.collectIsDraggedAsState()
    val panelHandleDragged by panelHandleInteractionSource.collectIsDraggedAsState()
    val anyPanelDrag = queueIconDragged || lyricsIconDragged || panelHandleDragged

    LaunchedEffect(queueInteractionSource) {
        queueInteractionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                panelMotion.prepare(PlayerPanel.Queue)
            }
        }
    }
    LaunchedEffect(lyricsInteractionSource, hasLyrics) {
        lyricsInteractionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start && hasLyrics) {
                panelMotion.prepare(PlayerPanel.Lyrics)
            }
        }
    }
    LaunchedEffect(panelMotion, anyPanelDrag) {
        snapshotFlow {
            panelMotion.draggableState.offset to panelMotion.draggableState.settledValue
        }.collect {
            panelMotion.clearClosedPanelIfIdle(anyPanelDrag)
        }
    }

    val audioQualityLabel = remember(currentFormat, liveAudioFormat) {
        val codec = sequenceOf(liveAudioFormat?.codecs, currentFormat?.codecs)
            .mapNotNull { value ->
                value
                    ?.substringBefore(',')
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.lowercase()
                    ?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
        val mimeSubtype = sequenceOf(
            liveAudioFormat?.sampleMimeType,
            liveAudioFormat?.containerMimeType,
            currentFormat?.mimeType,
        ).mapNotNull { value ->
            value
                ?.substringBefore(';')
                ?.substringAfter('/', missingDelimiterValue = "")
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() && it != "*" }
        }.firstOrNull()
        val formatName = codec ?: mimeSubtype

        when {
            formatName?.contains("opus") == true -> "OPUS"
            formatName?.contains("flac") == true -> "FLAC"
            formatName?.contains("mp4a") == true || formatName?.contains("aac") == true -> "AAC"
            formatName?.contains("mpeg") == true || formatName?.contains("mp3") == true -> "MP3"
            formatName?.contains("vorbis") == true -> "VORBIS"
            formatName?.contains("alac") == true -> "ALAC"
            formatName?.contains("wav") == true || formatName?.contains("pcm") == true -> "WAV"
            formatName != null -> formatName.uppercase()
            else -> "DESCONOCIDA"
        }
    }
    val audioDetails = remember(currentFormat, liveAudioFormat, audioQualityLabel) {
        val bitrateValue = sequenceOf(
            liveAudioFormat?.averageBitrate,
            liveAudioFormat?.bitrate,
            currentFormat?.bitrate,
        ).filterNotNull().firstOrNull { it > 0 }
        val bitrate = bitrateValue?.let { "${(it / 1000f).roundToInt()} kbps" }
        val fileSize = currentFormat?.contentLength
            ?.takeIf { it > 0L }
            ?.let { "${(it / 1_000_000f).roundToInt()} MB" }

        listOfNotNull(audioQualityLabel, bitrate, fileSize).joinToString("  •  ")
    }
    val speakerLabel = stringResource(R.string.audio_output_speaker)
    val bluetoothLabel = stringResource(R.string.audio_output_bluetooth)
    val castOutputLabel = if (isCasting) {
        stringResource(R.string.casting_to, castDeviceName ?: stringResource(R.string.google_cast))
    } else {
        null
    }
    val audioOutputLabel = remember(
        audioOutputDevices,
        selectedAudioOutputDeviceId,
        speakerLabel,
        bluetoothLabel,
        castOutputLabel,
    ) {
        val selectedDevice = audioOutputDevices.firstOrNull {
            it.id == selectedAudioOutputDeviceId
        }
        when {
            castOutputLabel != null -> castOutputLabel
            selectedDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> speakerLabel
            selectedDevice?.isBluetoothMediaOutput() == true ->
                selectedDevice.productName.toString().trim().ifBlank { bluetoothLabel }
            selectedDevice != null ->
                selectedDevice.productName.toString().trim().ifBlank { speakerLabel }
            audioOutputDevices.any(AudioDeviceInfo::isBluetoothMediaOutput) -> bluetoothLabel
            else -> speakerLabel
        }
    }

    var panelHeightPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                panelHeightPx = size.height.toFloat()
                panelMotion.updateAnchors(panelHeightPx)
            }
    ) {
        AsyncImage(
            model = song.thumbnailUrl?.resize(width = 400),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { scaffoldPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = panelMotion.progress(panelHeightPx)
                            alpha = (1f - progress).coerceIn(0f, 1f)
                            val playerScale = 1f - (progress * 0.035f)
                            scaleX = playerScale
                            scaleY = playerScale
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.14f)
                            .fillMaxWidth()
                            .then(
                                if (panelMotion.activePanel == null) collapseDragModifier
                                else Modifier,
                            ),
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()

                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.85f to Color.Transparent,
                                            1.0f to Color.Black
                                        ),
                                        blendMode = BlendMode.DstOut
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl?.resize(width = 1200), contentDescription = song.title,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )

                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Black.copy(alpha = 0.5f),
                                        0.25f to Color.Transparent,
                                        0.6f to Color.Transparent,
                                        1.0f to Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 16.dp)
                                .width(48.dp)
                                .height(48.dp)
                                .semantics {
                                    contentDescription = context.getString(R.string.mini_player)
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onBack,
                                ),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.4f)),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 40.dp)
                                .offset(y = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                MarqueeText(
                                    text = song.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(
                                            enabled = navigableAlbumId != null || searchQuery.isNotEmpty(),
                                            interactionSource = titleInteractionSource,
                                            role = Role.Button,
                                        ) {
                                            onNavigateFromPlayer {
                                                if (navigableAlbumId != null) {
                                                    navigation.openAlbum(navigableAlbumId)
                                                } else {
                                                    navigation.openSearchResult(searchQuery)
                                                }
                                            }
                                        }
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = titleActionDescription
                                        }
                                        .heightIn(min = 40.dp)
                                        .padding(vertical = 4.dp),
                                )
                                MarqueeText(
                                    text = artistsText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.88f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(
                                            enabled = song.artists.size > 1 || singleArtistId != null,
                                            interactionSource = artistInteractionSource,
                                            role = Role.Button,
                                        ) {
                                            if (song.artists.size > 1) {
                                                showArtistPicker = true
                                            } else if (singleArtistId != null) {
                                                onNavigateFromPlayer {
                                                    navigation.openArtist(singleArtistId)
                                                }
                                            }
                                        }
                                        .then(
                                            artistActionDescription?.let { description ->
                                                Modifier.semantics(mergeDescendants = true) {
                                                    contentDescription = description
                                                }
                                            } ?: Modifier,
                                        )
                                        .heightIn(min = 40.dp)
                                        .padding(vertical = 4.dp),
                                )
                            }
                            NowPlayingRoundButton(
                                onClick = {
                                    playerConnection.player.currentMediaItem?.let { mediaItem ->
                                        queueMenuSelection = QueueMenuSelection(
                                            windowUid = queueWindows.getOrNull(currentQueueIndex)?.uid,
                                            mediaItem = mediaItem,
                                            isCurrent = true,
                                        )
                                    }
                                },
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                enabled = playerConnection.player.currentMediaItem != null,
                            )
                            Spacer(Modifier.width(12.dp))
                            NowPlayingRoundButton(
                                onClick = {
                                    likeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    playerConnection.toggleLike()
                                },
                                icon = if (currentSong?.song?.liked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.action_like),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.86f)
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp),
                    ) {
                        Slider(
                            value = if (isDragging) dragPosition else positionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f),
                            onValueChange = { isDragging = true; dragPosition = it },
                            onValueChangeFinished = { playerConnection.seekTo((dragPosition * durationMs).toLong()); isDragging = false },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.32f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp)
                                .graphicsLayer { scaleY = 0.84f },
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().offset(y = (-6).dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                formatMs(if (isDragging) (dragPosition * durationMs).toLong() else positionMs),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(7.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            ) {
                                Text(audioQualityLabel, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
                            }
                            Text(
                                formatMs(durationMs),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                        Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NowPlayingModeButton(
                                onClick = {
                                    playerConnection.player.shuffleModeEnabled = !shuffleEnabled
                                },
                                icon = Icons.Rounded.Shuffle,
                                contentDescription = stringResource(
                                    if (shuffleEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on
                                ),
                                selected = shuffleEnabled,
                                modifier = Modifier.size(48.dp),
                            )

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                                NowPlayingTransportButton(
                                    onClick = playerConnection::seekToPrevious,
                                    icon = Icons.Rounded.SkipPrevious,
                                    contentDescription = stringResource(R.string.previous),
                                    enabled = canSkipPrevious,
                                )
                                NowPlayingTransportButton(
                                    onClick = { playerConnection.togglePlayPause() },
                                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                    emphasized = true,
                                )
                                NowPlayingTransportButton(
                                    onClick = playerConnection::seekToNext,
                                    icon = Icons.Rounded.SkipNext,
                                    contentDescription = stringResource(R.string.next),
                                    enabled = canSkipNext,
                                )
                            }

                            NowPlayingModeButton(
                                onClick = {
                                    playerConnection.player.repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                },
                                icon = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                    else -> Icons.Rounded.Repeat
                                },
                                contentDescription = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_mode_one)
                                    Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_mode_all)
                                    else -> stringResource(R.string.repeat_mode_off)
                                },
                                selected = repeatMode != Player.REPEAT_MODE_OFF,
                                modifier = Modifier.size(48.dp),
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = audioDetails,
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.weight(0.45f).heightIn(min = 8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NowPlayingRoundButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            panelMotion.open(PlayerPanel.Queue, panelMotionSpec)
                                        }
                                    },
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = stringResource(R.string.queue),
                                    modifier = Modifier.anchoredDraggable(
                                        state = panelMotion.draggableState,
                                        orientation = Orientation.Vertical,
                                        interactionSource = queueInteractionSource,
                                        flingBehavior = panelFlingBehavior,
                                    ),
                                    interactionSource = queueInteractionSource,
                                )
                                NowPlayingRoundButton(
                                    onClick = {
                                        if (hasLyrics) {
                                            coroutineScope.launch {
                                                panelMotion.open(PlayerPanel.Lyrics, panelMotionSpec)
                                            }
                                        }
                                    },
                                    icon = Icons.AutoMirrored.Rounded.Notes,
                                    contentDescription = stringResource(R.string.lyrics),
                                    enabled = hasLyrics,
                                    modifier = Modifier.anchoredDraggable(
                                        state = panelMotion.draggableState,
                                        orientation = Orientation.Vertical,
                                        enabled = hasLyrics,
                                        interactionSource = lyricsInteractionSource,
                                        flingBehavior = panelFlingBehavior,
                                    ),
                                    interactionSource = lyricsInteractionSource,
                                )
                                NowPlayingRoundButton({ showSleepTimerDialog = true }, Icons.Rounded.Bedtime, stringResource(R.string.sleep_timer), selected = sleepTimer.isActive)
                            }

                            Spacer(Modifier.width(16.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable { showAudioOutputSheet = true }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = audioOutputLabel,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                panelMotion.activePanel?.let { activePanel ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = panelMotion.offset(panelHeightPx).roundToInt(),
                                )
                            }
                            .graphicsLayer {
                                val progress = panelMotion.progress(panelHeightPx)
                                alpha = 0.35f + (progress * 0.65f)
                            }
                            .nestedScroll(panelNestedScrollConnection)
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .then(panelCloseDragModifier)
                                .clickable {
                                    coroutineScope.launch {
                                        panelMotion.close(panelMotionSpec)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .width(48.dp)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.4f)),
                            )
                            Text(
                                text = when (activePanel) {
                                    PlayerPanel.Queue -> stringResource(R.string.queue)
                                    PlayerPanel.Lyrics -> stringResource(R.string.lyrics)
                                },
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        Box(Modifier.weight(1f)) {
                            when (activePanel) {
                                PlayerPanel.Queue -> {
                                    AppleMusicQueueView(
                                        currentSong = song,
                                        currentMediaItem = playerConnection.player.currentMediaItem,
                                        windows = queueWindows,
                                        currentQueueIndex = currentQueueIndex,
                                        shuffleEnabled = shuffleEnabled,
                                        onToggleShuffle = {
                                            playerConnection.player.shuffleModeEnabled = !shuffleEnabled
                                        },
                                        repeatMode = repeatMode,
                                        onToggleRepeat = {
                                            playerConnection.player.repeatMode = when (repeatMode) {
                                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                                else -> Player.REPEAT_MODE_OFF
                                            }
                                        },
                                        onItemClick = { playerConnection.player.seekTo(it, 0) },
                                        onMoveItem = playerConnection::moveQueueItem,
                                        onMoreClick = { window, isCurrent ->
                                            queueMenuSelection = QueueMenuSelection(
                                                windowUid = window.uid,
                                                mediaItem = if (isCurrent) {
                                                    playerConnection.player.currentMediaItem ?: window.mediaItem
                                                } else {
                                                    window.mediaItem
                                                },
                                                isCurrent = isCurrent,
                                            )
                                        },
                                        closeDragModifier = panelCloseDragModifier,
                                    )
                                }

                                PlayerPanel.Lyrics -> {
                                    KaraokeLyricsOnly(
                                        playerConnection = playerConnection,
                                        mediaMetadata = song,
                                        lyricsEntity = currentLyrics,
                                        lyrics = currentLyrics?.lyrics.orEmpty(),
                                        durationMs = durationMs,
                                        lyricsOffsetMs = currentSong?.song?.lyricsOffset?.toLong() ?: 0L,
                                        isPlaying = isPlaying,
                                        onSeekTo = { playerConnection.player.seekTo(it) },
                                        onOffsetChange = { newOffset ->
                                            currentSong?.song?.let { songEntity ->
                                                coroutineScope.launch {
                                                    playerConnection.database.query {
                                                        update(songEntity.copy(lyricsOffset = newOffset))
                                                    }
                                                }
                                            }
                                        },
                                        closeDragModifier = panelCloseDragModifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    queueMenuSelection?.let { selection ->
        val selectedWindow = selection.windowUid?.let { selectedUid ->
            queueWindows.firstOrNull { window -> window.uid == selectedUid }
        }
        val currentWindow = queueWindows.getOrNull(currentQueueIndex)
        val selectedIsCurrent = selectedWindow?.let { window ->
            window.uid == currentWindow?.uid
        } ?: selection.isCurrent
        val firstQueueWindow = queueWindows.firstOrNull()
        val lastQueueWindow = queueWindows.lastOrNull()
        CompositionLocalProvider(LocalSongActionsNavigation provides playerMenuNavigation) {
            UniversalSongActionsHost(
                context = selection.mediaItem.toSongActionContext(),
                onDismiss = { queueMenuSelection = null },
                onMoveToQueueStart = selectedWindow?.takeIf { window ->
                    !selectedIsCurrent && firstQueueWindow?.uid != window.uid
                }?.let { window ->
                    { firstQueueWindow?.let { playerConnection.moveQueueItem(window, it) } }
                },
                onMoveToQueueEnd = selectedWindow?.takeIf { window ->
                    !selectedIsCurrent && lastQueueWindow?.uid != window.uid
                }?.let { window ->
                    { lastQueueWindow?.let { playerConnection.moveQueueItem(window, it) } }
                },
                onRemoveFromQueue = if (!selectedIsCurrent) {
                    {
                        val player = playerConnection.player
                        val timeline = player.currentTimeline
                        val reusableWindow = androidx.media3.common.Timeline.Window()
                        val latestIndex = (0 until timeline.windowCount).firstOrNull { index ->
                            timeline.getWindow(index, reusableWindow).uid == selection.windowUid
                        }
                        if (latestIndex != null && latestIndex != player.currentMediaItemIndex) {
                            player.removeMediaItem(latestIndex)
                        }
                    }
                } else null,
            )
        }
    }

    if (showArtistPicker) {
        NowPlayingArtistPickerSheet(
            artists = song.artists,
            onDismiss = { showArtistPicker = false },
            onSelect = { artistId ->
                showArtistPicker = false
                onNavigateFromPlayer {
                    navigation.openArtist(artistId)
                }
            },
        )
    }

    if (showAudioOutputSheet) {
        AudioOutputSheet(
            playerConnection = playerConnection,
            onDismiss = { showAudioOutputSheet = false },
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isActive = sleepTimer.isActive,
            onDismiss = { showSleepTimerDialog = false },
            onSelectMinutes = { minutes -> sleepTimer.start(minutes); showSleepTimerDialog = false },
            onCancel = { sleepTimer.clear(); showSleepTimerDialog = false },
        )
    }

    if (showNativeAd && nativeAdPresentationCycleKey != null) {
        NowPlayingNativeAdDialog(
            presentationCycleKey = nativeAdPresentationCycleKey,
            onConsumed = onNativeAdConsumed,
        )
    }
}

@Composable
private fun NowPlayingNativeAdDialog(
    presentationCycleKey: String,
    onConsumed: () -> Unit,
) {
    var adLoaded by remember(presentationCycleKey) { mutableStateOf(false) }
    val blockerInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = if (adLoaded) {
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = blockerInteractionSource,
                    indication = null,
                    onClick = {},
                )
        } else {
            Modifier
        },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = if (adLoaded) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            } else {
                Modifier
            },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (adLoaded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Anuncio",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = onConsumed) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                }
                NativeAdSlot(
                    placement = NativeAdPlacement.NOW_PLAYING_LARGE,
                    presentationCycleKey = presentationCycleKey,
                    style = NativeAdStyle.LARGE,
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = if (adLoaded) 12.dp else 0.dp,
                    ),
                    onLoadFinished = { loaded ->
                        if (loaded) adLoaded = true else onConsumed()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingArtistPickerSheet(
    artists: List<MediaMetadata.Artist>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.artists),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            artists.forEachIndexed { index, artist ->
                val artistId = artist.id?.trim()?.takeIf(String::isNotEmpty)
                val actionDescription = stringResource(
                    R.string.now_playing_open_artist,
                    artist.name,
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = artist.name,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = artistId != null,
                            role = Role.Button,
                        ) {
                            artistId?.let(onSelect)
                        }
                        .then(
                            if (artistId != null) {
                                Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = actionDescription
                                }
                            } else {
                                Modifier.graphicsLayer { alpha = 0.45f }
                            },
                        ),
                )
                if (index < artists.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

private fun isNavigableAlbumId(id: String): Boolean =
    id.startsWith("MPREb_") || id.startsWith("LOCAL_ALBUM_")

private enum class PlayerPanel {
    Queue,
    Lyrics,
}

private enum class PlayerPanelAnchor {
    Open,
    Closed,
}

@Stable
private class PlayerPanelMotionState {
    val draggableState = AnchoredDraggableState(PlayerPanelAnchor.Closed)

    var activePanel by mutableStateOf<PlayerPanel?>(null)
        private set

    var isProgrammaticTransition by mutableStateOf(false)
        private set

    private var closedAnchorPx by mutableFloatStateOf(0f)

    fun updateAnchors(heightPx: Float) {
        if (heightPx <= 0f) return
        closedAnchorPx = heightPx
        draggableState.updateAnchors(
            newAnchors = DraggableAnchors {
                PlayerPanelAnchor.Open at 0f
                PlayerPanelAnchor.Closed at heightPx
            },
            newTarget = if (activePanel == null) {
                PlayerPanelAnchor.Closed
            } else {
                draggableState.targetValue
            },
        )
    }

    fun prepare(panel: PlayerPanel) {
        if (activePanel == null || draggableState.settledValue == PlayerPanelAnchor.Closed) {
            activePanel = panel
        }
    }

    suspend fun open(panel: PlayerPanel, animationSpec: AnimationSpec<Float>) {
        isProgrammaticTransition = true
        activePanel = panel
        try {
            draggableState.animateTo(PlayerPanelAnchor.Open, animationSpec)
        } finally {
            isProgrammaticTransition = false
        }
    }

    suspend fun close(animationSpec: AnimationSpec<Float>) {
        isProgrammaticTransition = true
        try {
            draggableState.animateTo(PlayerPanelAnchor.Closed, animationSpec)
        } finally {
            if (isAtClosedAnchor()) activePanel = null
            isProgrammaticTransition = false
        }
    }

    fun clearClosedPanelIfIdle(isDragging: Boolean) {
        if (!isDragging && !isProgrammaticTransition &&
            !draggableState.isAnimationRunning && isAtClosedAnchor()
        ) {
            activePanel = null
        }
    }

    fun offset(fallbackHeightPx: Float): Float =
        draggableState.offset.takeUnless(Float::isNaN) ?: fallbackHeightPx

    fun progress(fallbackHeightPx: Float): Float {
        val height = closedAnchorPx.takeIf { it > 0f } ?: fallbackHeightPx
        if (height <= 0f) return 0f
        return (1f - (offset(height) / height)).coerceIn(0f, 1f)
    }

    private fun isAtClosedAnchor(): Boolean {
        val currentOffset = draggableState.offset
        return closedAnchorPx > 0f && !currentOffset.isNaN() &&
            currentOffset >= closedAnchorPx - 0.5f &&
            draggableState.settledValue == PlayerPanelAnchor.Closed
    }
}

private class PlayerPanelNestedScrollConnection(
    private val state: AnchoredDraggableState<PlayerPanelAnchor>,
    private val flingBehavior: FlingBehavior,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0f && source == NestedScrollSource.UserInput) {
            Offset(x = 0f, y = state.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (source == NestedScrollSource.UserInput) {
        Offset(x = 0f, y = state.dispatchRawDelta(available.y))
    } else {
        Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val currentOffset = state.offset
        val minAnchor = state.anchors.minPosition()
        return if (available.y < 0f && !currentOffset.isNaN() && currentOffset > minAnchor) {
            state.performPanelFling(flingBehavior, available.y)
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val remainingVelocity = state.performPanelFling(flingBehavior, available.y)
        return Velocity(x = consumed.x, y = remainingVelocity)
    }
}

private suspend fun <T> AnchoredDraggableState<T>.performPanelFling(
    flingBehavior: FlingBehavior,
    initialVelocity: Float,
): Float {
    var remainingVelocity = 0f
    anchoredDrag { latestAnchors ->
        val scrollScope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                val currentOffset = offset
                val newOffset = (currentOffset + pixels).coerceIn(
                    latestAnchors.minPosition(),
                    latestAnchors.maxPosition(),
                )
                val consumed = newOffset - currentOffset
                dragTo(newOffset)
                return consumed
            }
        }
        remainingVelocity = with(flingBehavior) {
            scrollScope.performFling(initialVelocity)
        }
    }
    return remainingVelocity
}

@Composable
private fun NowPlayingRoundButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val isDragged by resolvedInteractionSource.collectIsDraggedAsState()
    val feedbackScale by animateFloatAsState(
        targetValue = when {
            isDragged -> 0.88f
            isPressed -> 0.94f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "now_playing_button_feedback",
    )
    val backgroundAlpha = when {
        selected -> 0.22f
        isDragged -> 0.24f
        isPressed -> 0.18f
        else -> 0.12f
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = feedbackScale
                scaleY = feedbackScale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .clickable(
                interactionSource = resolvedInteractionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NowPlayingModeButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isPressed -> 0.92f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "now_playing_mode_scale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.35f
            selected -> 1f
            else -> 0.62f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "now_playing_mode_alpha",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = iconAlpha),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
    }

}

@Composable
private fun NowPlayingTransportButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val feedbackScale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isDragged -> 0.92f
            isPressed -> 0.95f
            emphasized -> 1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "now_playing_transport_feedback",
    )
    val size = if (emphasized) 68.dp else 52.dp
    val iconSize = if (emphasized) 28.dp else 22.dp
    val backgroundAlpha = when {
        !enabled -> 0.08f
        emphasized -> 0.24f
        isDragged -> 0.22f
        isPressed -> 0.18f
        else -> 0.12f
    }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = feedbackScale
                scaleY = feedbackScale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun KaraokeLyricsOnly(
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata,
    lyricsEntity: LyricsEntity?,
    lyrics: String,
    durationMs: Long,
    lyricsOffsetMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onOffsetChange: (Int) -> Unit,
    closeDragModifier: Modifier,
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val positionState = playerConnection.playbackPositionMs.collectAsState()
    val positionProvider = remember(positionState) { { positionState.value } }
    var showSources by remember(mediaMetadata.id) { mutableStateOf(false) }
    var lyricsUnavailable by remember(mediaMetadata.id, lyrics, durationMs) {
        mutableStateOf(lyrics.isBlank())
    }

    Box(Modifier.fillMaxSize()) {
        KaraokeLyrics(
            mediaId = mediaMetadata.id,
            lyrics = lyrics,
            positionProvider = positionProvider,
            durationMs = durationMs,
            offsetMs = lyricsOffsetMs,
            isPlaying = isPlaying,
            onSeekTo = onSeekTo,
            modifier = Modifier.fillMaxSize(),
            onEmptyStateChange = { lyricsUnavailable = it },
        )

        if (lyricsUnavailable) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(closeDragModifier),
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(20.dp)
                    .fillMaxHeight()
                    .then(closeDragModifier),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(20.dp)
                    .fillMaxHeight()
                    .then(closeDragModifier),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.28f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onOffsetChange((lyricsOffsetMs - 250L).coerceAtLeast(-15_000L).toInt()) }) {
                Text("−250", color = Color.White)
            }
            TextButton(onClick = { onOffsetChange(0) }) {
                Text(
                    text = if (lyricsOffsetMs >= 0L) "+${lyricsOffsetMs} ms" else "${lyricsOffsetMs} ms",
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            TextButton(onClick = { onOffsetChange((lyricsOffsetMs + 250L).coerceAtMost(15_000L).toInt()) }) {
                Text("+250", color = Color.White)
            }
            IconButton(onClick = { showSources = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.lyrics_providers_label),
                    tint = Color.White,
                )
            }
        }
    }

    if (showSources) {
        LyricsSourceSheet(
            mediaMetadata = mediaMetadata,
            lyricsEntity = lyricsEntity,
            onDismiss = { showSources = false },
        )
    }
}

@Composable
private fun NowPlayingStatusBarIconContrast(
    mediaId: String,
    artworkUrl: String?,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(view) { (view.context as? Activity)?.window }
    val previousLightStatusBars = remember(window, view) {
        window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars }
    }
    var useDarkStatusBarIcons by remember(mediaId, artworkUrl) { mutableStateOf(false) }

    LaunchedEffect(mediaId, artworkUrl) {
        useDarkStatusBarIcons = false
        val url = artworkUrl?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        useDarkStatusBarIcons = withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(96)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).image
                    ?.toBitmap()
                    ?.let(::artworkTopSupportsDarkStatusBarIcons)
                    ?: false
            }.getOrDefault(false)
        }
    }

    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars =
                useDarkStatusBarIcons
        }
    }

    DisposableEffect(window, view, previousLightStatusBars) {
        onDispose {
            if (window != null && previousLightStatusBars != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    previousLightStatusBars
            }
        }
    }
}

private fun artworkTopSupportsDarkStatusBarIcons(bitmap: android.graphics.Bitmap): Boolean {
    val sampleHeight = (bitmap.height / 4).coerceAtLeast(1)
    var totalLuminance = 0.0
    var sampleCount = 0

    for (y in 0 until sampleHeight) {
        for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val red = overlayAdjustedLinearChannel(android.graphics.Color.red(pixel))
            val green = overlayAdjustedLinearChannel(android.graphics.Color.green(pixel))
            val blue = overlayAdjustedLinearChannel(android.graphics.Color.blue(pixel))
            totalLuminance += 0.2126 * red + 0.7152 * green + 0.0722 * blue
            sampleCount++
        }
    }

    return sampleCount > 0 && totalLuminance / sampleCount > 0.179
}

private fun overlayAdjustedLinearChannel(channel: Int): Double {
    val srgb = (channel / 255.0) * 0.5
    return if (srgb <= 0.04045) {
        srgb / 12.92
    } else {
        ((srgb + 0.055) / 1.055).pow(2.4)
    }
}

private fun AudioDeviceInfo.isBluetoothMediaOutput(): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID -> true
    else -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSourceSheet(
    mediaMetadata: MediaMetadata,
    lyricsEntity: LyricsEntity?,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(mediaMetadata.id) {
        viewModel.search(
            mediaId = mediaMetadata.id,
            title = mediaMetadata.title,
            artist = mediaMetadata.artists.joinToString { it.name },
            duration = mediaMetadata.duration,
            album = mediaMetadata.album?.title,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lyrics_providers_label),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = lyricsEntity?.provider.orEmpty().toLyricsProviderDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.refetchLyrics(mediaMetadata, lyricsEntity)
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.refetch))
                }
            }

            when {
                isLoading && results.isEmpty() -> FridaLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp),
                )

                results.isEmpty() -> Text(
                    text = stringResource(R.string.lyrics_not_found),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                ) {
                    items(
                        items = results,
                        key = { result -> "${result.providerName}:${result.lyrics.hashCode()}" },
                    ) { result ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    result.providerName.toLyricsProviderDisplayName(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            supportingContent = {
                                Text("${result.syncType.name.replace('_', ' ')} · ${result.score}")
                            },
                            modifier = Modifier.clickable {
                                viewModel.selectLyrics(mediaMetadata.id, result)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleMusicQueueView(
    currentSong: MediaMetadata?,
    currentMediaItem: MediaItem?,
    windows: List<androidx.media3.common.Timeline.Window>,
    currentQueueIndex: Int,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: Int,
    onToggleRepeat: () -> Unit,
    onItemClick: (Int) -> Unit,
    onMoveItem: (androidx.media3.common.Timeline.Window, androidx.media3.common.Timeline.Window) -> Boolean,
    onMoreClick: (androidx.media3.common.Timeline.Window, Boolean) -> Unit,
    closeDragModifier: Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var initialScrollPending by remember { mutableStateOf(true) }
    var autoLoadMoreEnabled by rememberPreference(AutoLoadMoreKey, true)

    LaunchedEffect(windows, currentQueueIndex) {
        if (initialScrollPending && currentQueueIndex in windows.indices) {
            lazyListState.scrollToItem((currentQueueIndex - 1).coerceAtLeast(0))
            initialScrollPending = false
        }
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromWindow = windows.firstOrNull { it.saveableQueueKey() == from.key }
        val toWindow = windows.firstOrNull { it.saveableQueueKey() == to.key }
        if (fromWindow != null && toWindow != null && onMoveItem(fromWindow, toWindow)) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val currentWindow = windows.getOrNull(currentQueueIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = closeDragModifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .universalMediaClickable(
                        onClick = {
                            if (currentQueueIndex in windows.indices) {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(currentQueueIndex)
                                }
                            }
                        },
                        onLongClick = currentWindow?.let { window ->
                            { onMoreClick(window, true) }
                        },
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = currentSong?.thumbnailUrl?.resize(width = 150),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = currentSong?.title ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = currentSong?.artists?.joinToString(", ") { it.name } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SongOptionsButton(
                    onClick = { currentWindow?.let { onMoreClick(it, true) } },
                    enabled = currentMediaItem != null && currentWindow != null,
                    iconColor = Color.White,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QueueActionButton(
                    icon = Icons.Rounded.Shuffle,
                    isActive = shuffleEnabled,
                    onClick = onToggleShuffle,
                    modifier = Modifier.weight(1f),
                )
                QueueActionButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    isActive = repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = onToggleRepeat,
                    modifier = Modifier.weight(1f),
                )
                QueueActionButton(
                    icon = Icons.Rounded.AllInclusive,
                    isActive = autoLoadMoreEnabled,
                    onClick = { autoLoadMoreEnabled = !autoLoadMoreEnabled },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = lazyListState,
        ) {
            if (windows.isNotEmpty()) {
                itemsIndexed(windows, key = { _, window -> window.saveableQueueKey() }) { index, window ->
                    ReorderableItem(
                        state = reorderableState,
                        key = window.saveableQueueKey(),
                    ) {
                        val isCurrent = index == currentQueueIndex
                        val mediaItem = if (isCurrent) currentMediaItem ?: window.mediaItem else window.mediaItem
                        val metadata = if (isCurrent) currentSong ?: mediaItem.metadata else mediaItem.metadata
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                )
                                .universalMediaClickable(
                                    onClick = {
                                        if (!isCurrent) onItemClick(window.firstPeriodIndex)
                                    },
                                    onLongClick = { onMoreClick(window, isCurrent) },
                                )
                                .padding(
                                    horizontal = if (isCurrent) 12.dp else 0.dp,
                                    vertical = 12.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = metadata?.thumbnailUrl?.resize(width = 96),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(if (isCurrent) 56.dp else 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                MarqueeText(
                                    text = metadata?.title ?: stringResource(R.string.unknown),
                                    style = if (isCurrent) {
                                        MaterialTheme.typography.titleMedium
                                    } else {
                                        MaterialTheme.typography.bodyLarge
                                    },
                                    color = Color.White,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                )
                                Text(
                                    text = metadata?.artists?.joinToString(", ") { it.name } ?: "",
                                    style = if (isCurrent) {
                                        MaterialTheme.typography.bodyMedium
                                    } else {
                                        MaterialTheme.typography.bodySmall
                                    },
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            SongOptionsButton(
                                onClick = { onMoreClick(window, isCurrent) },
                                iconColor = if (isCurrent) Color.White else Color.White.copy(alpha = 0.8f),
                            )
                            IconButton(
                                onClick = {},
                                enabled = windows.size > 1,
                                modifier = Modifier
                                    .size(48.dp)
                                    .draggableHandle(
                                        enabled = windows.size > 1,
                                        onDragStarted = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.drag_handle),
                                    contentDescription = stringResource(R.string.reorder_queue),
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.no_more_songs_in_queue),
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

private fun androidx.media3.common.Timeline.Window.saveableQueueKey(): String =
    "${mediaItem.mediaId}:${uid.hashCode()}"

private data class QueueMenuSelection(
    val windowUid: Any?,
    val mediaItem: MediaItem,
    val isCurrent: Boolean,
)

@Composable
private fun QueueActionButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isActive) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (isActive) Color.Black else Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun SleepTimerDialog(isActive: Boolean, onDismiss: () -> Unit, onSelectMinutes: (Int) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer_title)) },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = { onSelectMinutes(minutes) }) { Text(stringResource(R.string.minutes_suffix, minutes)) }
                }
                TextButton(onClick = { onSelectMinutes(-1) }) { Text(stringResource(R.string.at_end_of_song)) }
            }
        },
        confirmButton = {
            if (isActive) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
