package com.jagr.fridamusic.presentation.screens

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.jagr.fridamusic.extensions.metadata
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.presentation.components.KaraokeLyrics
import com.jagr.fridamusic.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

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
    LaunchedEffect(playbackError) {
        playbackError?.let {
            snackbarHostState.showSnackbar("No se pudo reproducir: ${it.errorCodeName}")
        }
    }

    val song = mediaMetadata ?: run { onBack(); return }

    var backgroundColor by remember { mutableStateOf(Color(0xFF1A1A2E)) }
    val animatedBackground by animateColorAsState(backgroundColor, animationSpec = tween(600), label = "bg")

    LaunchedEffect(song.thumbnailUrl) {
        val url = song.thumbnailUrl ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap: Bitmap? = result.image?.toBitmap()
            if (bitmap != null) {
                val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                val swatch = palette.darkMutedSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
                swatch?.let { backgroundColor = Color(it.rgb) }
            }
        }
    }

    var positionMs by remember { mutableStateOf(playerConnection.player.currentPosition) }
    var durationMs by remember { mutableStateOf(playerConnection.player.duration.coerceAtLeast(0L)) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = song.thumbnailUrl?.resize(width = 400),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedBackground.copy(alpha = 0.55f),
                            animatedBackground.copy(alpha = 0.85f),
                            animatedBackground,
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Reproduciendo ahora",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Más opciones",
                                tint = Color.White,
                            )
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (currentSong?.song?.inLibrary != null) "Quitar de biblioteca" else "Agregar a biblioteca") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (currentSong?.song?.inLibrary != null) Icons.Rounded.LibraryAddCheck else Icons.Rounded.LibraryAdd,
                                        contentDescription = null,
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

                AnimatedVisibility(
                    visible = showLyricsView,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(150)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl?.resize(width = 120),
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { playerConnection.toggleLike() }) {
                            Icon(
                                imageVector = if (currentSong?.song?.liked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Me gusta",
                                tint = Color.White,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        showQueuePanel -> {
                            AppleMusicQueueView(
                                currentSong = song,
                                windows = queueWindows,
                                currentIndex = currentMediaItemIndex,
                                shuffleEnabled = shuffleEnabled,
                                onToggleShuffle = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
                                repeatMode = repeatMode,
                                onToggleRepeat = {
                                    playerConnection.player.repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                },
                                onItemClick = { index -> playerConnection.player.seekTo(index, 0) }
                            )
                        }
                        showLyricsView -> {
                            KaraokeLyricsOnly(
                                lyrics = currentLyrics?.lyrics ?: "",
                                positionMs = karaokePositionMs,
                                lyricsOffsetMs = currentSong?.song?.lyricsOffset?.toLong() ?: 0L,
                                onSeekTo = { playerConnection.player.seekTo(it) },
                            )
                        }
                        else -> {
                            AsyncImage(
                                model = song.thumbnailUrl?.resize(width = 800),
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .scale(artworkScale.coerceAtLeast(0.01f))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.1f)),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .navigationBarsPadding()
                ) {
                    AnimatedVisibility(
                        visible = !showLyricsView && !showQueuePanel,
                        enter = fadeIn(tween(250)),
                        exit = fadeOut(tween(200)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                currentFormat?.let { format ->
                                    Text(
                                        text = formatQualityLabel(format.codecs, format.bitrate, format.sampleRate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.4f),
                                    )
                                }
                            }
                            IconButton(onClick = { playerConnection.toggleLike() }) {
                                Icon(
                                    imageVector = if (currentSong?.song?.liked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Me gusta",
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (showLyricsView || showQueuePanel) 4.dp else 16.dp))

                    Slider(
                        value = if (isDragging) dragPosition else (positionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)),
                        onValueChange = { isDragging = true; dragPosition = it },
                        onValueChangeFinished = {
                            playerConnection.seekTo((dragPosition * durationMs).toLong())
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )


                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .offset(y = (-4).dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatMs(if (isDragging) (dragPosition * durationMs).toLong() else positionMs),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "-${formatMs(durationMs - (if (isDragging) (dragPosition * durationMs).toLong() else positionMs))}",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { playerConnection.seekToPrevious() },
                            enabled = canSkipPrevious,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    if (isPlaying) playerConnection.player.pause()
                                    else playerConnection.player.play()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = animatedBackground,
                                modifier = Modifier.size(42.dp),
                            )
                        }

                        IconButton(
                            onClick = { playerConnection.seekToNext() },
                            enabled = canSkipNext,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedVisibility(
                        visible = !showQueuePanel,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled }) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Aleatorio",
                                    tint = if (shuffleEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                                )
                            }
                            IconButton(onClick = {
                                showLyricsView = !showLyricsView
                                if (showQueuePanel) showQueuePanel = false
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                                    contentDescription = "Letra",
                                    tint = if (showLyricsView) Color.White else if (hasLyrics) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                                )
                            }
                            IconButton(onClick = { showSleepTimerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = "Temporizador",
                                    tint = if (sleepTimer.isActive) Color.White else Color.White.copy(alpha = 0.45f),
                                )
                            }
                            IconButton(onClick = {
                                showQueuePanel = !showQueuePanel
                                if (showLyricsView) showLyricsView = false
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = "Cola",
                                    tint = Color.White.copy(alpha = 0.45f),
                                )
                            }
                            IconButton(onClick = {
                                playerConnection.player.repeatMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }) {
                                Icon(
                                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Repetir",
                                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.45f) else Color.White,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showQueuePanel,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            IconButton(onClick = { showQueuePanel = false }) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = "Cerrar cola",
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
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
                        text = currentSong?.title ?: "Desconocido",
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
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "Más", tint = Color.White)
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
                    Text("A continuación", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Reproducción automática", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
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
                    Icon(imageVector = Icons.Rounded.Menu, contentDescription = "Reordenar", tint = Color.White.copy(alpha = 0.5f))
                }
            }
        } else {
            item {
                Text("No hay más canciones en la cola", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 24.dp))
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
        title = { Text("Temporizador para dormir") },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = { onSelectMinutes(minutes) }) { Text("$minutes minutos") }
                }
                TextButton(onClick = { onSelectMinutes(-1) }) { Text("Al terminar la canción") }
            }
        },
        confirmButton = {
            if (isActive) TextButton(onClick = onCancel) { Text("Cancelar temporizador") }
            else TextButton(onClick = onDismiss) { Text("Cerrar") }
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

private fun formatQualityLabel(codecs: String, bitrate: Int, sampleRate: Int?): String {
    val codecLabel = when {
        codecs.contains("flac", ignoreCase = true) -> "FLAC"
        codecs.contains("opus", ignoreCase = true) -> "Opus"
        codecs.contains("mp4a", ignoreCase = true) -> "AAC"
        else -> codecs.uppercase()
    }
    val kbps = bitrate / 1000
    val sampleLabel = sampleRate?.let { " · ${it / 1000}kHz" } ?: ""
    return "$codecLabel · ${kbps}kbps$sampleLabel"
}

private enum class DragState { Anchored, Oculto }