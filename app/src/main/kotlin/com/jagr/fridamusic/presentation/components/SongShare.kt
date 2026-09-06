package com.jagr.fridamusic.presentation.components

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCode2
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.ColorFilter
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
import com.jagr.fridamusic.utils.MemoryDiagnostics
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

@Immutable
data class FridaShareSnapshot(
    val song: SongActionContext,
    val positionMs: Long,
    val durationMs: Long,
    val appCtaUrl: String,
)

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
                        icon = Icons.Rounded.AttachFile,
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
    val snapshot = remember(context.mediaId) {
        val durationMs = context.durationMs?.takeIf { it > 0L } ?: 0L
        FridaShareSnapshot(
            song = context,
            positionMs = if (durationMs > 0L) {
                playbackPositionMs.coerceIn(0L, durationMs)
            } else {
                playbackPositionMs.coerceAtLeast(0L)
            },
            durationMs = durationMs,
            appCtaUrl = FridaAppLinks.appCtaUrl,
        )
    }
    FridaShareCardDialogContent(snapshot = snapshot, onDismiss = onDismiss)
}

@Composable
private fun FridaShareCardDialogContent(
    snapshot: FridaShareSnapshot,
    onDismiss: () -> Unit,
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val links = remember(snapshot.song) { SongShareLinkResolver.resolveSongLinks(snapshot.song) }
    val songShareUrl = remember(links) {
        links.firstOrNull { link ->
            val uri = Uri.parse(link.url)
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
                !uri.host.isNullOrBlank()
        }?.url ?: FridaAppLinks.DEEP_LINK_HOME
    }
    val appQrImage = remember(snapshot.appCtaUrl) {
        runCatching { FridaQrCodeGenerator.create(snapshot.appCtaUrl).asImageBitmap() }.getOrNull()
    }
    var selectedTheme by remember(snapshot.song.mediaId) { mutableStateOf(FridaShareCardTheme.ARTWORK) }
    var artworkBitmap by remember(snapshot.song.artworkUrl) { mutableStateOf<Bitmap?>(null) }
    val artworkImage = remember(artworkBitmap) { artworkBitmap?.asImageBitmap() }
    var dominantColor by remember(snapshot.song.artworkUrl) { mutableStateOf(FridaPink) }
    var isBusy by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var pendingLegacySave by remember { mutableStateOf(false) }

    LaunchedEffect(snapshot.song.artworkUrl) {
        val preview = loadArtworkPreview(androidContext, snapshot.song.artworkUrl)
        artworkBitmap = preview.bitmap
        dominantColor = preview.dominantColor
    }
    LaunchedEffect(Unit) { cleanupOldShareCards(androidContext) }

    fun exportCard(action: suspend (Bitmap) -> Unit) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            runCatching {
                val exportArtwork = loadArtworkBitmap(
                    context = androidContext,
                    artworkUrl = snapshot.song.artworkUrl,
                    size = EXPORT_ARTWORK_SIZE_PX,
                ) ?: artworkBitmap
                renderShareCardBitmap(
                    context = androidContext,
                    snapshot = snapshot,
                    theme = selectedTheme,
                    artwork = exportArtwork,
                    qrBitmap = appQrImage,
                    dominantColor = dominantColor,
                    colorScheme = colorScheme,
                )
            }.onSuccess { bitmap ->
                runCatching { action(bitmap) }
                    .onFailure { Toast.makeText(androidContext, R.string.song_share_failed, Toast.LENGTH_SHORT).show() }
                MemoryDiagnostics.log("After share finished")
            }.onFailure {
                Toast.makeText(androidContext, R.string.song_share_failed, Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    fun saveCard() {
        exportCard { bitmap ->
            saveShareCardToGallery(androidContext, bitmap, snapshot.song.title)
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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
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
                    Spacer(Modifier.height(14.dp))
                    FridaNowPlayingShareCard(
                        snapshot = snapshot,
                        theme = selectedTheme,
                        artwork = artworkImage,
                        qrBitmap = appQrImage,
                        dominantColor = dominantColor,
                        modifier = Modifier
                            .fillMaxWidth(0.64f)
                            .widthIn(max = 280.dp)
                            .aspectRatio(9f / 16f),
                    )
                    Spacer(Modifier.height(14.dp))
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
                                dominantColor = dominantColor,
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
                                shareToInstagramStoryOrFallback(androidContext, uri, snapshot.song, links.firstOrNull())
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
                        ShareQuickAction(
                            icon = Icons.Rounded.QrCode2,
                            label = stringResource(R.string.song_share_qr),
                        ) {
                            showQrDialog = true
                        }
                        ShareQuickAction(
                            icon = Icons.Rounded.MoreHoriz,
                            label = stringResource(R.string.song_share_more),
                        ) {
                            exportCard { bitmap ->
                                val uri = saveTemporaryShareCard(androidContext, bitmap)
                                shareCardMore(androidContext, uri, snapshot.song, links.firstOrNull())
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

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text(stringResource(R.string.song_share_qr)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FridaQrCode(
                        url = songShareUrl,
                        size = 232.dp,
                        logoSize = 42.dp,
                    )
                    Text(
                        text = songShareUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { copyShareLink(androidContext, songShareUrl) }) {
                    Text(stringResource(R.string.copy_link))
                }
                TextButton(onClick = { shareText(androidContext, songShareUrl) }) {
                    Text(stringResource(R.string.share))
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
fun FridaNowPlayingShareCard(
    snapshot: FridaShareSnapshot,
    theme: FridaShareCardTheme,
    artwork: ImageBitmap?,
    qrBitmap: ImageBitmap?,
    dominantColor: Color,
    modifier: Modifier = Modifier,
) {
    val material = MaterialTheme.colorScheme
    val palette = when (theme) {
        FridaShareCardTheme.LIGHT -> CardPalette(Color(0xFFF7F5FB), Color(0xFF17131D), Color(0xFF6D4A7E))
        FridaShareCardTheme.DARK -> CardPalette(Color(0xFF0D0B10), Color.White, Color(0xFFDAB8E7))
        FridaShareCardTheme.ARTWORK -> {
            val background = blend(dominantColor, Color.Black, 0.36f)
            CardPalette(background, readableOn(background), dominantColor)
        }
        FridaShareCardTheme.GRADIENT -> {
            val background = blend(dominantColor, Color.Black, 0.58f)
            CardPalette(background, readableOn(background), dominantColor)
        }
        FridaShareCardTheme.MATERIAL_DYNAMIC -> CardPalette(
            material.primaryContainer,
            material.onPrimaryContainer,
            material.primary,
        )
    }
    val progress = if (snapshot.durationMs > 0L) {
        (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(palette.background),
    ) {
        val scale = (maxWidth / 360.dp).coerceIn(0.58f, 1f)
        if (theme == FridaShareCardTheme.GRADIENT) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                blend(dominantColor, Color.Black, 0.18f),
                                palette.background,
                                blend(dominantColor, Color.Black, 0.78f),
                            ),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp * scale, vertical = 28.dp * scale),
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp * scale)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp * scale))
                        .background(palette.content.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FridaLogo(
                        tint = palette.content,
                        modifier = Modifier.size(82.dp * scale),
                    )
                }
            }
            Spacer(Modifier.height(18.dp * scale))
            Text(
                text = snapshot.song.title,
                color = palette.content,
                fontSize = 22.sp * scale,
                lineHeight = 26.sp * scale,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.song.artists.joinToString(", ") { it.name },
                color = palette.content.copy(alpha = 0.76f),
                fontSize = 14.sp * scale,
                lineHeight = 18.sp * scale,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            snapshot.song.album?.title?.takeIf(String::isNotBlank)?.let { album ->
                Text(
                    text = album,
                    color = palette.content.copy(alpha = 0.58f),
                    fontSize = 11.sp * scale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp * scale))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp * scale)
                    .clip(CircleShape)
                    .background(palette.content.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp * scale)
                        .background(palette.accent),
                )
            }
            Spacer(Modifier.height(4.dp * scale))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration(snapshot.positionMs),
                    color = palette.content.copy(alpha = 0.70f),
                    fontSize = 11.sp * scale,
                )
                Text(
                    formatDuration(snapshot.durationMs),
                    color = palette.content.copy(alpha = 0.70f),
                    fontSize = 11.sp * scale,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp * scale),
                ) {
                    FridaLogo(tint = palette.content, modifier = Modifier.size(28.dp * scale))
                    Column {
                        Text(
                            "FridaMusic",
                            color = palette.content,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp * scale,
                        )
                        Text(
                            stringResource(R.string.by_frida_labs),
                            color = palette.content.copy(alpha = 0.62f),
                            fontSize = 9.sp * scale,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FridaQrCode(
                        url = snapshot.appCtaUrl,
                        providedBitmap = qrBitmap,
                        size = 88.dp * scale,
                        logoSize = 22.dp * scale,
                    )
                    Text(
                        text = stringResource(R.string.listen_on_fridamusic),
                        color = palette.content.copy(alpha = 0.78f),
                        fontSize = 9.sp * scale,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(108.dp * scale),
                    )
                }
            }
        }
    }
}

private data class CardPalette(val background: Color, val content: Color, val accent: Color)

@Composable
private fun FridaLogo(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.frida_music_logo_monochrome),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

@Composable
private fun FridaQrCode(
    url: String,
    providedBitmap: ImageBitmap? = null,
    size: androidx.compose.ui.unit.Dp,
    logoSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(url, providedBitmap) {
        providedBitmap ?: runCatching { FridaQrCodeGenerator.create(url).asImageBitmap() }.getOrNull()
    }
    Surface(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "FridaMusic QR" },
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Surface(
                modifier = Modifier.size(logoSize),
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FridaLogo(tint = Color.Black, modifier = Modifier.fillMaxSize().padding(3.dp))
                }
            }
        }
    }
}

@Composable
private fun ShareThemeSwatch(
    theme: FridaShareCardTheme,
    selected: Boolean,
    dominantColor: Color,
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
            FridaShareCardTheme.ARTWORK -> Box(Modifier.fillMaxSize().background(dominantColor))
            FridaShareCardTheme.GRADIENT -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(dominantColor, blend(dominantColor, Color.Black, 0.70f))),
                ),
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

private data class ArtworkPreview(
    val bitmap: Bitmap?,
    val dominantColor: Color,
)

private suspend fun loadArtworkPreview(context: Context, artworkUrl: String?): ArtworkPreview =
    withContext(Dispatchers.IO) {
        val bitmap = loadArtworkBitmap(context, artworkUrl, PREVIEW_ARTWORK_SIZE_PX)
        val dominantColor = bitmap?.let { source ->
            val sample = if (source.width > DOMINANT_COLOR_SAMPLE_SIZE_PX ||
                source.height > DOMINANT_COLOR_SAMPLE_SIZE_PX
            ) {
                Bitmap.createScaledBitmap(
                    source,
                    DOMINANT_COLOR_SAMPLE_SIZE_PX,
                    DOMINANT_COLOR_SAMPLE_SIZE_PX,
                    true,
                )
            } else {
                source
            }
            try {
                sample.asImageBitmap().themeColor(fallback = FridaPink)
            } finally {
                if (sample !== source) sample.recycle()
            }
        } ?: FridaPink
        ArtworkPreview(bitmap, dominantColor)
    }

private suspend fun loadArtworkBitmap(
    context: Context,
    artworkUrl: String?,
    size: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    artworkUrl?.takeIf(String::isNotBlank)?.let { url ->
        runCatching {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(size)
                    .allowHardware(false)
                    .build(),
            ).image?.toBitmap()
        }.getOrNull()
    }
}

private const val PREVIEW_ARTWORK_SIZE_PX = 512
private const val DOMINANT_COLOR_SAMPLE_SIZE_PX = 192
private const val EXPORT_ARTWORK_SIZE_PX = 1080

private suspend fun renderShareCardBitmap(
    context: Context,
    snapshot: FridaShareSnapshot,
    theme: FridaShareCardTheme,
    artwork: Bitmap?,
    qrBitmap: ImageBitmap?,
    dominantColor: Color,
    colorScheme: ColorScheme,
): Bitmap = withContext(Dispatchers.Main) {
    MemoryDiagnostics.log("Before share bitmap")
    val activity = context.findActivity() ?: error("Share card rendering requires an Activity")
    val root = activity.findViewById<ViewGroup>(android.R.id.content)
    val width = 1080
    val height = 1920
    val composeView = ComposeView(activity).apply {
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                FridaNowPlayingShareCard(
                    snapshot = snapshot,
                    theme = theme,
                    artwork = artwork?.asImageBitmap(),
                    qrBitmap = qrBitmap,
                    dominantColor = dominantColor,
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
        createBitmap(width, height).also {
            composeView.draw(AndroidCanvas(it))
            MemoryDiagnostics.log("After share bitmap")
        }
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
        putExtra("content_url", link?.url ?: FridaAppLinks.appCtaUrl)
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

private fun copyShareLink(context: Context, link: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.song_share_qr), link))
    Toast.makeText(context, R.string.song_share_link_copied, Toast.LENGTH_SHORT).show()
}

private fun shareCaption(song: SongActionContext, link: String?): String = buildString {
    append("Escuchando \"")
    append(song.title)
    append("\"")
    song.artists.joinToString(", ") { it.name }.takeIf(String::isNotBlank)?.let {
        append(" — ")
        append(it)
    }
    append("\nen FridaMusic")
    append("\n")
    append(link ?: FridaAppLinks.appCtaUrl)
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}

private fun readableOn(color: Color): Color = if (color.luminance() > 0.48f) Color(0xFF17131D) else Color.White

private fun blend(start: Color, end: Color, amount: Float): Color {
    val fraction = amount.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val TEMP_CARD_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
