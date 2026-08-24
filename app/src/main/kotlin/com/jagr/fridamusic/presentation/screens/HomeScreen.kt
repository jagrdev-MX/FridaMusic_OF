package com.jagr.fridamusic.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.ads.InterstitialAdManager
import com.jagr.fridamusic.db.entities.Album as LocalAlbum
import com.jagr.fridamusic.db.entities.Artist as LocalArtist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist as LocalPlaylist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.utils.rememberIsLowEndDevice
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.HomeViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as YTArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage

@Composable
fun HomeScreen(
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onItemClick: (YTItem) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val quickPicks by viewModel.quickPicks.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val echoBrainPlaylists by viewModel.echoBrainPlaylists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()
    val isLowEnd = rememberIsLowEndDevice()
    val quickPickItems = quickPicks.orEmpty()
    val dailyDiscoverItems = dailyDiscover.orEmpty()
    val forgottenFavoriteItems = forgottenFavorites.orEmpty()
    val keepListeningItems = keepListening.orEmpty()
    val similarRecommendationItems = similarRecommendations.orEmpty()
    val accountPlaylistItems = accountPlaylists.orEmpty()
    val newReleaseItems = explorePage?.newReleaseAlbums.orEmpty()
    val communityPlaylistItems = communityPlaylists.orEmpty()
    val echoBrainPlaylistItems = echoBrainPlaylists.orEmpty()
    val quickPicksTitle = stringResource(R.string.quick_picks)
    val quickPicksSubtitle = stringResource(R.string.quick_picks_subtitle)
    val dailyDiscoverTitle = stringResource(R.string.your_daily_discover)
    val keepListeningTitle = stringResource(R.string.keep_listening)
    val forgottenFavoritesTitle = stringResource(R.string.forgotten_favorites)
    val accountPlaylistsTitle = stringResource(R.string.your_youtube_playlists)
    val newReleasesTitle = stringResource(R.string.new_release_albums)
    val similarToTitle = stringResource(R.string.similar_to)
    val communityTitle = stringResource(R.string.from_the_community)
    val hasGeneralContent = quickPickItems.isNotEmpty() ||
            dailyDiscoverItems.isNotEmpty() ||
            forgottenFavoriteItems.isNotEmpty() ||
            keepListeningItems.isNotEmpty() ||
            similarRecommendationItems.isNotEmpty() ||
            accountPlaylistItems.isNotEmpty() ||
            !homePage?.sections.isNullOrEmpty() ||
            newReleaseItems.isNotEmpty() ||
            communityPlaylistItems.isNotEmpty() ||
            echoBrainPlaylistItems.isNotEmpty()
    val hasVisibleContent = if (selectedChip == null) {
        hasGeneralContent
    } else {
        !homePage?.sections.isNullOrEmpty()
    }

    // Estado para controlar la visibilidad del popup de apoyo
    var showSupportDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()
    val interstitialAdManager = remember(activity) { InterstitialAdManager(activity) }

    DisposableEffect(interstitialAdManager) {
        interstitialAdManager.load()
        onDispose { interstitialAdManager.release() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item(key = "header") {
            HomeHeader(
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onSupportClick = { showSupportDialog = true } // Abrimos el diálogo
            )
        }

        item(key = "chips") {
            MoodChipsRow(
                chips = homePage?.chips,
                selectedChip = selectedChip,
                onChipClick = { viewModel.toggleChip(it) },
            )
        }

        if (selectedChip == null) {
            localSongSection(
                key = "quick_picks",
                title = quickPicksTitle,
                subtitle = quickPicksSubtitle,
                songs = quickPickItems,
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
            )

            ytSection(
                key = "daily_discover",
                title = dailyDiscoverTitle,
                items = dailyDiscoverItems.map { it.recommendation },
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
            )

            localItemSection(
                key = "keep_listening",
                title = keepListeningTitle,
                items = keepListeningItems,
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
                onItemClick = onItemClick,
            )

            localSongSection(
                key = "forgotten_favorites",
                title = forgottenFavoritesTitle,
                songs = forgottenFavoriteItems,
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
            )

            echoBrainPlaylistItems.forEachIndexed { index, playlist ->
                ytSection(
                    key = "echo_brain_${index}_${playlist.playlist.id}",
                    title = playlist.playlist.title,
                    items = playlist.songs,
                    isLowEnd = isLowEnd,
                    onItemClick = onItemClick,
                )
            }
        }

        homePage?.sections.orEmpty().forEachIndexed { index, section ->
            ytSection(
                key = "youtube_${index}_${section.title}",
                title = section.title,
                subtitle = section.label,
                items = section.items,
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
            )
        }

        homePage?.continuation?.let { continuation ->
            item(key = "continuation_${selectedChip?.title.orEmpty()}_$continuation") {
                LaunchedEffect(continuation, selectedChip) {
                    viewModel.loadMoreYouTubeItems(continuation)
                }
                Spacer(modifier = Modifier.height(1.dp))
            }
        }

        if (selectedChip == null) {
            ytSection(
                key = "account_playlists",
                title = accountPlaylistsTitle,
                items = accountPlaylistItems,
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
            )

            ytSection(
                key = "new_releases",
                title = newReleasesTitle,
                items = newReleaseItems,
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
            )

            similarRecommendationItems.forEachIndexed { index, recommendation ->
                ytSection(
                    key = "similar_${index}_${recommendation.title.id}",
                    title = "$similarToTitle ${recommendation.title.title}",
                    items = recommendation.items,
                    isLowEnd = isLowEnd,
                    onItemClick = onItemClick,
                )
            }

            ytSection(
                key = "community",
                title = communityTitle,
                items = communityPlaylistItems.map { it.playlist },
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
            )
        }

        if (isLoading && !hasVisibleContent) {
            item(key = "loading") {
                FridaLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
        }

        if (!isLoading && !hasVisibleContent) {
            item(key = "empty") { HomeEmptyState() }
        }
    }

    // Instancia del Diálogo de Apoyo
    if (showSupportDialog) {
        SupportProjectDialog(
            onDismiss = { showSupportDialog = false },
            onWatchAdClick = {
                showSupportDialog = false
                interstitialAdManager.show()
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun HomeHeader(
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSupportClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FridaMusic",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Nuevo ícono agregado al principio para mayor visibilidad
            HeaderIconButton(
                icon = Icons.Rounded.VolunteerActivism,
                description = "Apoyar proyecto",
                onClick = onSupportClick,
                tint = MaterialTheme.colorScheme.primary // Le damos un toque de color para que resalte sutilmente
            )
            HeaderIconButton(Icons.Rounded.History, stringResource(R.string.history), onHistoryClick)
            HeaderIconButton(Icons.Rounded.CalendarMonth, stringResource(R.string.calendar), {})
            HeaderIconButton(Icons.Rounded.NotificationsNone, stringResource(R.string.notifications), {})
            HeaderIconButton(Icons.Rounded.Settings, stringResource(R.string.settings), onSettingsClick)
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

// --- NUEVO COMPONENTE: DIÁLOGO DE APOYO ---
@Composable
private fun SupportProjectDialog(
    onDismiss: () -> Unit,
    onWatchAdClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.VolunteerActivism,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Apoyar FridaMusic",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "FridaMusic es un proyecto independiente desarrollado con mucho esfuerzo. Si disfrutas de la app, puedes apoyarme viendo un pequeño anuncio. ¡Esto me ayuda a cubrir los costos y seguir mejorándola para ti!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onWatchAdClick,
                shape = RoundedCornerShape(50)
            ) {
                Text("Ver anuncio y apoyar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Quizás luego", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp)
    )
}
// ------------------------------------------

@Composable
private fun MoodChipsRow(
    chips: List<HomePage.Chip>?,
    selectedChip: HomePage.Chip?,
    onChipClick: (HomePage.Chip?) -> Unit,
) {
    val displayChips = chips?.ifEmpty { null } ?: listOf(
        HomePage.Chip(stringResource(R.string.podcasts), null, null),
        HomePage.Chip(stringResource(R.string.energy), null, null),
        HomePage.Chip(stringResource(R.string.mood_entrenar), null, null),
        HomePage.Chip(stringResource(R.string.mood_relax), null, null),
        HomePage.Chip(stringResource(R.string.mood_fiesta), null, null),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(displayChips, key = { it.title }) { chip ->
            val isSelected = chip == selectedChip
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(enabled = chip.endpoint != null) {
                    onChipClick(if (isSelected) null else chip)
                },
            ) {
                Text(
                    text = chip.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.localSongSection(
    key: String,
    title: String,
    subtitle: String? = null,
    songs: List<Song>,
    isLowEnd: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
) {
    if (songs.isEmpty()) return

    item(key = "${key}_header") {
        SectionTitle(title = title, subtitle = subtitle)
    }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = songs,
                key = { index, song -> "${key}_${song.id}_$index" },
            ) { _, song ->
                WideCard(
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name },
                    imageUrl = (song.song.thumbnailUrl
                        ?: "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg")
                        .resize(width = if (isLowEnd) 320 else 480),
                    onClick = { onSongClick(song, songs) },
                )
            }
        }
    }
}

private fun LazyListScope.localItemSection(
    key: String,
    title: String,
    items: List<LocalItem>,
    isLowEnd: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onItemClick: (YTItem) -> Unit,
) {
    if (items.isEmpty()) return
    val songQueue = items.filterIsInstance<Song>()

    item(key = "${key}_header") {
        SectionTitle(title = title, subtitle = null)
    }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { index, localItem -> "${key}_${localItem.id}_$index" },
            ) { _, localItem ->
                val subtitle = when (localItem) {
                    is Song -> localItem.artists.joinToString(", ") { it.name }
                    is LocalAlbum -> localItem.artists.joinToString(", ") { it.name }
                    is LocalArtist -> stringResource(R.string.artists)
                    is LocalPlaylist -> stringResource(R.string.playlists)
                }
                WideCard(
                    title = localItem.title,
                    subtitle = subtitle,
                    imageUrl = localItem.thumbnailUrl.orEmpty()
                        .resize(width = if (isLowEnd) 320 else 480),
                    onClick = {
                        when (localItem) {
                            is Song -> onSongClick(localItem, songQueue)
                            else -> localItem.toYTItemOrNull()?.let(onItemClick)
                        }
                    },
                )
            }
        }
    }
}

private fun LazyListScope.ytSection(
    key: String,
    title: String,
    subtitle: String? = null,
    items: List<YTItem>,
    isLowEnd: Boolean,
    onItemClick: (YTItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "${key}_header") {
        SectionTitle(title = title, subtitle = subtitle)
    }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${key}_${item.id}_$index" },
            ) { _, item ->
                YTItemCard(
                    item = item,
                    isLowEnd = isLowEnd,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun YTItemCard(
    item: YTItem,
    isLowEnd: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name }
            ?: stringResource(R.string.album_text)
        is PlaylistItem -> item.author?.name ?: stringResource(R.string.playlists)
        is ArtistItem -> stringResource(R.string.artists)
    }

    if (item is ArtistItem) {
        ArtistCard(
            name = item.title,
            imageUrl = item.thumbnail?.resize(width = if (isLowEnd) 200 else 300).orEmpty(),
            onClick = onClick,
        )
    } else {
        WideCard(
            title = item.title,
            subtitle = subtitle,
            imageUrl = item.thumbnail?.resize(width = if (isLowEnd) 320 else 480).orEmpty(),
            onClick = onClick,
        )
    }
}

private fun LocalItem.toYTItemOrNull(): YTItem? = when (this) {
    is LocalAlbum -> AlbumItem(
        browseId = id,
        playlistId = album.playlistId.orEmpty(),
        title = title,
        artists = artists.map { artist -> YTArtist(name = artist.name, id = artist.id) },
        year = album.year,
        thumbnail = thumbnailUrl.orEmpty(),
    )
    is LocalArtist -> ArtistItem(
        id = id,
        title = title,
        thumbnail = thumbnailUrl,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
    is LocalPlaylist -> null
    is Song -> null
}

@Composable
private fun SectionTitle(title: String, subtitle: String?) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun WideCard(
    title: String,
    subtitle: String?,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f),
                        ),
                        startY = 60f,
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    name: String,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
