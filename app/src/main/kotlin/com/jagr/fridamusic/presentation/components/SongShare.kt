package com.jagr.fridamusic.presentation.components

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.theme.FridaPink
import com.jagr.fridamusic.utils.shareLocalAudio
import com.materialkolor.ktx.themeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

enum class FridaShareCardTheme {
    LIGHT,
    DARK,
    ARTWORK,
    GRADIENT,
    MATERIAL_DYNAMIC,
}

@Composable
fun SongShareOptionsDialog(
    context: SongActionContext,
    onDismiss: () -> Unit,
    onOpenShareCard: () -> Unit,
) {
    val androidContext = LocalContext.current
    val links = remember(context) { SongShareLinkResolver.resolveSongLinks(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { link ->
                    ShareDialogRow(
                        icon = Icons.Rounded.Link,
                        title = stringResource(R.string.song_share_link, link.label),
                        onClick = { shareText(androidContext, shareCaption(context, link.url)) },
                    )
                }
                context.localFile?.takeIf(SongLocalFile::canShare)?.let { localFile ->
                    ShareDialogRow(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.song_share_file),
                        onClick = {
                            if (!shareLocalAudio(androidContext, localFile.contentUri, localFile.mimeType)) {
                                Toast.makeText(androidContext, R.string.local_song_share_unavailable, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
                ShareDialogRow(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.share_now_playing),
                    onClick = onOpenShareCard,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun ShareDialogRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FridaShareCardDialog(
    context: SongActionContext,
    playbackPositionMs: Long,
    onDismiss: () -> Unit,
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val durationMs = context.durationMs?.takeIf { it > 0L } ?: 0L
    val capturedPosition = playbackPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val links = remember(context) { SongShareLinkResolver.resolveSongLinks(context) }
    var selectedTheme by remember { mutableStateOf(FridaShareCardTheme.ARTWORK) }
    var artworkBitmap by remember(context.artworkUrl) { mutableStateOf<Bitmap?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var showLinkPicker by remember { mutableStateOf(false) }
    var pendingLegacySave by remember { mutableStateOf(false) }

    LaunchedEffect(context.artworkUrl) {
        artworkBitmap = loadArtworkBitmap(androidContext, context.artworkUrl)
    }
    LaunchedEffect(Unit) { cleanupOldShareCards(androidContext) }

    fun exportCard(action: suspend (Bitmap) -> Unit) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            runCatching {
                renderShareCardBitmap(
                    context = androidContext,
                    song = context,
                    theme = selectedTheme,
                    artwork = artworkBitmap,
                    playbackPositionMs = capturedPosition,
                    colorScheme = colorScheme,
                )
            }.onSuccess { bitmap ->
                runCatching { action(bitmap) }
                    .onFailure { Toast.makeText(androidContext, R.string.song_share_failed, Toast.LENGTH_SHORT).show() }
            }.onFailure {
                Toast.makeText(androidContext, R.string.song_share_failed, Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    fun saveCard() {
        exportCard { bitmap ->
            saveShareCardToGallery(androidContext, bitmap, context.title)
            Toast.makeText(androidContext, R.string.song_share_card_saved, Toast.LENGTH_SHORT).show()
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingLegacySave) saveCard()
        if (!granted) Toast.makeText(androidContext, R.string.song_share_failed, Toast.LENGTH_SHORT).show()
        pendingLegacySave = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.share_now_playing),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = context.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    FridaNowPlayingShareCard(
                        context = context,
                        theme = selectedTheme,
                        artwork = artworkBitmap?.asImageBitmap(),
                        playbackPositionMs = capturedPosition,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(9f / 16f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.song_share_card_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(FridaShareCardTheme.entries, key = { it.name }) { theme ->
                            ShareThemeSwatch(
                                theme = theme,
                                selected = theme == selectedTheme,
                                artwork = artworkBitmap?.asImageBitmap(),
                                onClick = { selectedTheme = theme },
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top,
                    ) {
                        ShareQuickAction(
                            icon = Icons.Rounded.Share,
                            label = stringResource(R.string.song_share_instagram),
                        ) {
                            exportCard { bitmap ->
                                val uri = saveTemporaryShareCard(androidContext, bitmap)
                                shareToInstagramStoryOrFallback(androidContext, uri, context, links.firstOrNull())
                            }
                        }
                        ShareQuickAction(
                            icon = Icons.Rounded.Download,
                            label = stringResource(R.string.song_share_save_card),
                        ) {
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                ContextCompat.checkSelfPermission(
                                    androidContext,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingLegacySave = true
                                legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                saveCard()
                            }
                        }
                        if (links.isNotEmpty()) {
                            ShareQuickAction(
                                icon = Icons.Rounded.Link,
                                label = links.singleOrNull()?.label ?: stringResource(R.string.song_share_song_link),
                            ) {
                                if (links.size == 1) shareText(androidContext, shareCaption(context, links.first().url))
                                else showLinkPicker = true
                            }
                        }
                        ShareQuickAction(
                            icon = Icons.Rounded.MoreHoriz,
                            label = stringResource(R.string.song_share_more),
                        ) {
                            exportCard { bitmap ->
                                val uri = saveTemporaryShareCard(androidContext, bitmap)
                                shareCardMore(androidContext, uri, context, links.firstOrNull())
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                if (isBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (showLinkPicker) {
        AlertDialog(
            onDismissRequest = { showLinkPicker = false },
            title = { Text(stringResource(R.string.song_share_song_link)) },
            text = {
                Column {
                    links.forEach { link ->
                        ShareDialogRow(
                            icon = Icons.Rounded.Link,
                            title = link.label,
                            onClick = {
                                showLinkPicker = false
                                shareText(androidContext, shareCaption(context, link.url))
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLinkPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun FridaNowPlayingShareCard(
    context: SongActionContext,
    theme: FridaShareCardTheme,
    artwork: ImageBitmap?,
    playbackPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val dominant = remember(artwork) { artwork?.themeColor(fallback = FridaPink) ?: FridaPink }
    val material = MaterialTheme.colorScheme
    val palette = when (theme) {
        FridaShareCardTheme.LIGHT -> CardPalette(Color(0xFFF7F5FB), Color(0xFF17131D), Color(0xFFE9E2F1))
        FridaShareCardTheme.DARK -> CardPalette(Color(0xFF0D0B10), Color.White, Color(0xFF242028))
        FridaShareCardTheme.ARTWORK -> CardPalette(dominant, readableOn(dominant), dominant.copy(alpha = 0.72f))
        FridaShareCardTheme.GRADIENT -> CardPalette(dominant, readableOn(dominant), dominant.copy(alpha = 0.64f))
        FridaShareCardTheme.MATERIAL_DYNAMIC -> CardPalette(
            material.primaryContainer,
            material.onPrimaryContainer,
            material.secondaryContainer,
        )
    }
    val durationMs = context.durationMs?.takeIf { it > 0L } ?: 0L
    val progress = if (durationMs > 0L) (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(palette.background),
    ) {
        when (theme) {
            FridaShareCardTheme.ARTWORK -> {
                artwork?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)))
                }
            }
            FridaShareCardTheme.GRADIENT -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(dominant, dominant.copy(alpha = 0.72f), Color(0xFF100D14))),
                ),
            )
            else -> Unit
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.65f))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = palette.panel.copy(alpha = if (theme == FridaShareCardTheme.ARTWORK) 0.92f else 1f),
                shadowElevation = 10.dp,
            ) {
                Column {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = context.title,
                            color = palette.content,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = context.artists.joinToString(", ") { it.name },
                            color = palette.content.copy(alpha = 0.72f),
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        context.album?.title?.takeIf(String::isNotBlank)?.let { album ->
                            Text(
                                text = album,
                                color = palette.content.copy(alpha = 0.56f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(palette.content.copy(alpha = 0.18f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(5.dp)
                                    .background(palette.content),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(playbackPositionMs), color = palette.content.copy(alpha = 0.68f), fontSize = 11.sp)
                            Text(formatDuration(durationMs), color = palette.content.copy(alpha = 0.68f), fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.30f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Column {
                        Text("FridaMusic", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.by_frida_labs), color = Color.White.copy(alpha = 0.68f), fontSize = 9.sp)
                    }
                    Text(
                        text = stringResource(R.string.open_listen),
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(Modifier.weight(0.35f))
        }
    }
}

private data class CardPalette(val background: Color, val content: Color, val panel: Color)

@Composable
private fun ShareThemeSwatch(
    theme: FridaShareCardTheme,
    selected: Boolean,
    artwork: ImageBitmap?,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(3.dp, border, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = theme.name.lowercase().replace('_', ' ')
                this.selected = selected
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (theme) {
            FridaShareCardTheme.LIGHT -> Box(Modifier.fillMaxSize().background(Color(0xFFF7F5FB)))
            FridaShareCardTheme.DARK -> Box(Modifier.fillMaxSize().background(Color(0xFF0D0B10)))
            FridaShareCardTheme.ARTWORK -> artwork?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } ?: Box(Modifier.fillMaxSize().background(FridaPink))
            FridaShareCardTheme.GRADIENT -> Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(FridaPink, Color(0xFF342245)))),
            )
            FridaShareCardTheme.MATERIAL_DYNAMIC -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
            )
        }
        if (selected) Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape))
    }
}

@Composable
private fun ShareQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(74.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private suspend fun loadArtworkBitmap(context: Context, artworkUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
    artworkUrl?.takeIf(String::isNotBlank)?.let { url ->
        runCatching {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(1080)
                    .allowHardware(false)
                    .build(),
            ).image?.toBitmap()
        }.getOrNull()
    }
}

private suspend fun renderShareCardBitmap(
    context: Context,
    song: SongActionContext,
    theme: FridaShareCardTheme,
    artwork: Bitmap?,
    playbackPositionMs: Long,
    colorScheme: ColorScheme,
): Bitmap = withContext(Dispatchers.Main) {
    val activity = context.findActivity() ?: error("Share card rendering requires an Activity")
    val root = activity.findViewById<ViewGroup>(android.R.id.content)
    val width = 1080
    val height = 1920
    val composeView = ComposeView(activity).apply {
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                FridaNowPlayingShareCard(
                    context = song,
                    theme = theme,
                    artwork = artwork?.asImageBitmap(),
                    playbackPositionMs = playbackPositionMs,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    val holder = FrameLayout(activity).apply {
        translationX = -(width * 2).toFloat()
        translationY = -(height * 2).toFloat()
        addView(composeView, FrameLayout.LayoutParams(width, height))
    }
    root.addView(holder, ViewGroup.LayoutParams(width, height))
    try {
        awaitPostedFrame(composeView)
        awaitPostedFrame(composeView)
        composeView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)
        createBitmap(width, height).also { composeView.draw(AndroidCanvas(it)) }
    } finally {
        composeView.disposeComposition()
        root.removeView(holder)
    }
}

private suspend fun awaitPostedFrame(view: android.view.View) = suspendCancellableCoroutine { continuation ->
    view.post { if (continuation.isActive) continuation.resume(Unit) }
}

private suspend fun saveTemporaryShareCard(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "share_cards").also(File::mkdirs)
    val file = File(directory, "FridaMusic_share_${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private suspend fun saveShareCardToGallery(context: Context, bitmap: Bitmap, title: String): Uri =
    withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT))
        val cleanTitle = title.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(48).ifBlank { "Song" }
        val displayName = "FridaMusic_${cleanTitle}_$timestamp.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FridaMusic")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "FridaMusic",
                ).also(File::mkdirs)
                put(MediaStore.Images.Media.DATA, File(directory, displayName).absolutePath)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create MediaStore image")
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } ?: error("Unable to open MediaStore image")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
        }.onFailure { context.contentResolver.delete(uri, null, null) }.getOrThrow()
        uri
    }

private suspend fun cleanupOldShareCards(context: Context) = withContext(Dispatchers.IO) {
    val cutoff = System.currentTimeMillis() - TEMP_CARD_MAX_AGE_MS
    File(context.cacheDir, "share_cards").listFiles()?.forEach { file ->
        if (file.isFile && file.lastModified() < cutoff) runCatching(file::delete)
    }
}

private fun shareToInstagramStoryOrFallback(
    context: Context,
    imageUri: Uri,
    song: SongActionContext,
    link: SongShareLink?,
) {
    val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
        setDataAndType(imageUri, "image/png")
        putExtra("interactive_asset_uri", imageUri)
        putExtra("content_url", link?.url ?: FridaAppLinks.shareCardCtaUrl)
        clipData = ClipData.newUri(context.contentResolver, null, imageUri)
        `package` = INSTAGRAM_PACKAGE
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val directAvailable = context.packageManager.resolveActivity(storyIntent, PackageManager.MATCH_DEFAULT_ONLY) != null
    if (directAvailable && runCatching { context.startActivity(storyIntent) }.isSuccess) return
    shareCardMore(context, imageUri, song, link)
}

private fun shareCardMore(context: Context, imageUri: Uri, song: SongActionContext, link: SongShareLink?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, shareCaption(song, link?.url))
        clipData = ClipData.newUri(context.contentResolver, null, imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}

private fun shareCaption(song: SongActionContext, link: String?): String = buildString {
    append("Escuchando \"")
    append(song.title)
    append("\"")
    song.artists.joinToString(", ") { it.name }.takeIf(String::isNotBlank)?.let {
        append(" — ")
        append(it)
    }
    append("\nen FridaMusic 🎵")
    append("\n")
    append(link ?: FridaAppLinks.shareCardCtaUrl)
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}

private fun readableOn(color: Color): Color = if (color.luminance() > 0.48f) Color(0xFF17131D) else Color.White

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val TEMP_CARD_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
