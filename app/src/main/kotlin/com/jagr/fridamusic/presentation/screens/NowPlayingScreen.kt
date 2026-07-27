package com.jagr.fridamusic.presentation.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.presentation.components.KaraokeLyrics
import com.jagr.fridamusic.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val karaokePositionMs by playerConnection.playbackPositionMs.collectAsState()
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val playbackError by playerConnection.error.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentMediaItemIndex by playerConnection.currentMediaItemIndex.collectAsState()
    val sleepTimer = playerConnection.service.sleepTimer

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessageTemplate = stringResource(R.string.error_playing_snackbar)
    LaunchedEffect(playbackError) {
        playbackError?.let {
            snackbarHostState.showSnackbar(errorMessageTemplate.format(it.errorCodeName))
        }
    }

    val song = mediaMetadata ?: run { onBack(); return }

    var positionMs by remember { mutableLongStateOf(playerConnection.player.currentPosition) }
    var durationMs by remember { mutableLongStateOf(playerConnection.player.duration.coerceAtLeast(0L)) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(song.id) {
        while (isActive) {
            if (!isDragging) {
                positionMs = playerConnection.player.currentPosition
                durationMs = playerConnection.player.duration.coerceAtLeast(0L)
            }
            delay(500.milliseconds)
        }
    }

    var showQueuePanel by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val hasLyrics = currentLyrics != null

    val artworkScale by animateFloatAsState(
        targetValue = if (showLyricsView) 0f else 1f,
        animationSpec = tween(350),
        label = "artwork_scale"
    )

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var volumeLevel by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    volumeLevel = currentVol / maxVolume
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- LÓGICA DE GESTO SWIPE-TO-DISMISS ---
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedSwipeOffsetY by animateFloatAsState(
        targetValue = swipeOffsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "swipe_animation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedSwipeOffsetY.roundToInt()) }
            .pointerInput(showQueuePanel, showLyricsView) {
                // Solo activamos el gesto de deslizar cuando los paneles están cerrados
                // para no interferir con el scroll de la Cola o las Letras.
                if (!showQueuePanel && !showLyricsView) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (swipeOffsetY > 300f) { // Umbral para cerrar
                                onBack()
                            } else {
                                swipeOffsetY = 0f // Rebote al lugar original
                            }
                        },
                        onDragCancel = { swipeOffsetY = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        if (swipeOffsetY + dragAmount > 0) { // Solo permite arrastrar hacia abajo
                            swipeOffsetY += dragAmount
                        }
                    }
                }
            }
    ) {
        // Fondo súper desenfocado
        AsyncImage(
            model = song.thumbnailUrl?.resize(width = 400),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
        )

        // Capa oscura para contraste global
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { scaffoldPadding ->
            if (showQueuePanel || showLyricsView) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding).statusBarsPadding().padding(horizontal = 20.dp),
                ) {
                    // --- HEADER DE PANELES MODERNIZADO (Píldora arrastrable visualmente) ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable { // Cierra el panel al tocar el encabezado
                                showQueuePanel = false
                                showLyricsView = false
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .width(48.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                        Text(
                            text = if (showQueuePanel) stringResource(R.string.queue) else stringResource(R.string.lyrics),
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    // -----------------------------------------------------------------------

                    Box(Modifier.weight(1f)) {
                        if (showQueuePanel) {
                            AppleMusicQueueView(song, queueWindows, currentMediaItemIndex, shuffleEnabled,
                                { playerConnection.player.shuffleModeEnabled = !shuffleEnabled }, repeatMode,
                                { playerConnection.player.repeatMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                } }, { playerConnection.player.seekTo(it, 0) })
                        } else {
                            KaraokeLyricsOnly(currentLyrics?.lyrics.orEmpty(), karaokePositionMs,
                                currentSong?.song?.lyricsOffset?.toLong() ?: 0L, { playerConnection.player.seekTo(it) })
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {

                    Box(modifier = Modifier.weight(1.08f).fillMaxWidth()) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()

                                    // Gradiente que actúa como borrador: difumina el 15% inferior de la carátula
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
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().scale(artworkScale),
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

                        // --- INDICADOR DE GESTO SWIPE-TO-DISMISS (Píldora superior) ---
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 16.dp)
                                .width(48.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                        // --------------------------------------------------------------

                        Row(
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(horizontal = 40.dp, vertical = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artists.joinToString(", ") { it.name }, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.88f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box {
                                NowPlayingRoundButton({ showOverflowMenu = true }, Icons.Rounded.MoreVert, stringResource(R.string.more_options))
                                MaterialTheme(
                                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                                ) {
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false },
                                        modifier = Modifier.background(Color(0xFF252525))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (currentSong?.song?.inLibrary != null) stringResource(R.string.remove_from_library_label) else stringResource(R.string.add_to_library_label),
                                                    color = Color.White
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (currentSong?.song?.inLibrary != null) Icons.Rounded.LibraryAddCheck else Icons.Rounded.LibraryAdd,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            },
                                            onClick = {
                                                playerConnection.toggleLibrary()
                                                showOverflowMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            NowPlayingRoundButton({ playerConnection.toggleLike() }, if (currentSong?.song?.liked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.action_like))
                        }
                    }

                    Column(modifier = Modifier.weight(0.92f).fillMaxWidth().padding(horizontal = 36.dp).navigationBarsPadding()) {
                        Slider(
                            value = if (isDragging) dragPosition else positionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f),
                            onValueChange = { isDragging = true; dragPosition = it },
                            onValueChangeFinished = { playerConnection.seekTo((dragPosition * durationMs).toLong()); isDragging = false },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.32f)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth().offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMs(if (isDragging) (dragPosition * durationMs).toLong() else positionMs), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))) {
                                Text(currentFormat?.codecs?.uppercase() ?: "OPUS", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
                            }
                            Text(formatMs(durationMs), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = playerConnection::seekToPrevious, enabled = canSkipPrevious, modifier = Modifier.size(68.dp)) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(48.dp)) }
                            IconButton(onClick = { if (isPlaying) playerConnection.player.pause() else playerConnection.player.play() }, modifier = Modifier.size(78.dp)) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play), tint = Color.White, modifier = Modifier.size(58.dp)) }
                            IconButton(onClick = playerConnection::seekToNext, enabled = canSkipNext, modifier = Modifier.size(68.dp)) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(48.dp)) }
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                volumeLevel = 0f
                            }) { Icon(Icons.Rounded.VolumeOff, null, tint = Color.White.copy(alpha = 0.8f)) }

                            Slider(
                                value = volumeLevel,
                                onValueChange = {
                                    volumeLevel = it
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).roundToInt(), 0)
                                },
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.32f)),
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume.toInt(), 0)
                                volumeLevel = 1f
                            }) { Icon(Icons.Rounded.VolumeUp, null, tint = Color.White.copy(alpha = 0.8f)) }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NowPlayingRoundButton({ showQueuePanel = true }, Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue))
                                NowPlayingRoundButton({ if (hasLyrics) showLyricsView = true }, Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.lyrics), enabled = hasLyrics)
                                NowPlayingRoundButton({ showSleepTimerDialog = true }, Icons.Rounded.Bedtime, stringResource(R.string.sleep_timer), selected = sleepTimer.isActive)
                            }

                            Spacer(Modifier.width(16.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                val intent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                                                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } else {
                                                throw Exception("API antigua")
                                            }
                                        } catch (e: Exception) {
                                            try {
                                                val fallbackIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(fallbackIntent)
                                            } catch (fallbackError: Exception) {
                                                fallbackError.printStackTrace()
                                            }
                                        }
                                    }
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
                                        text = "Speaker",
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
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isActive = sleepTimer.isActive,
            onDismiss = { showSleepTimerDialog = false },
            onSelectMinutes = { minutes -> sleepTimer.start(minutes); showSleepTimerDialog = false },
            onCancel = { sleepTimer.clear(); showSleepTimerDialog = false },
        )
    }
}

@Composable
private fun NowPlayingRoundButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick),
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
private fun KaraokeLyricsOnly(
    lyrics: String,
    positionMs: Long,
    lyricsOffsetMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    KaraokeLyrics(
        lyrics = lyrics,
        positionMs = positionMs,
        offsetMs = lyricsOffsetMs,
        onSeekTo = onSeekTo,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AppleMusicQueueView(
    currentSong: MediaMetadata?,
    windows: List<androidx.media3.common.Timeline.Window>,
    currentIndex: Int,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: Int,
    onToggleRepeat: () -> Unit,
    onItemClick: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = currentSong?.thumbnailUrl?.resize(width = 150),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artists?.joinToString(", ") { it.name } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.more_label), tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QueueActionButton(icon = Icons.Rounded.Shuffle, isActive = shuffleEnabled, onClick = onToggleShuffle, modifier = Modifier.weight(1f))
                QueueActionButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    isActive = repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = onToggleRepeat,
                    modifier = Modifier.weight(1f)
                )
                QueueActionButton(icon = Icons.Rounded.AllInclusive, isActive = true, onClick = {}, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        val upcoming = windows.withIndex().filter { it.index > currentIndex }
        if (upcoming.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.upcoming), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(stringResource(R.string.auto_play), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            items(upcoming.size) { index ->
                val indexedWindow = upcoming[index]
                val window = indexedWindow.value
                val metadata = window.mediaItem.metadata
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(indexedWindow.index) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = metadata?.thumbnailUrl?.resize(width = 96),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = metadata?.title ?: "—", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = metadata?.artists?.joinToString(", ") { it.name } ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(imageVector = Icons.Rounded.Menu, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                }
            }
        } else {
            item {
                Text(stringResource(R.string.no_more_songs_in_queue), color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}

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