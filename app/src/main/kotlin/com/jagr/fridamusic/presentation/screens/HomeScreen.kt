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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.ads.InterstitialAdManager
import com.jagr.fridamusic.db.entities.Album as LocalAlbum
import com.jagr.fridamusic.db.entities.Artist as LocalArtist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist as LocalPlaylist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.HomeContentType
import com.jagr.fridamusic.presentation.components.HomeMediaCard
import com.jagr.fridamusic.presentation.components.HomeSectionHeader
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.YTContentCard
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.utils.rememberIsLowEndDevice
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.HomeViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as YTArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onItemClick: (YTItem) -> Unit = {},
    onPlayItem: (YTItem) -> Unit = {},
    onBrowseClick: (BrowseEndpoint, String) -> Unit = { _, _ -> },
    onNewReleasesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    reselectToken: Int = 0,
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
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val currentMediaMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value
    val currentIsPlaying = playerConnection?.isPlaying?.collectAsState()?.value == true
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
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }
    var remoteMenuItem by remember { mutableStateOf<YTItem?>(null) }
    val activity = LocalContext.current.findActivity()
    val interstitialAdManager = remember(activity) { InterstitialAdManager(activity) }
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val pullRefreshHaptic = LocalHapticFeedback.current
    var handledReselectToken by remember { mutableIntStateOf(reselectToken) }

    DisposableEffect(interstitialAdManager) {
        interstitialAdManager.load()
        onDispose { interstitialAdManager.release() }
    }

    LaunchedEffect(reselectToken) {
        val shouldScroll = reselectToken != handledReselectToken
        handledReselectToken = reselectToken
        if (
            shouldScroll &&
            (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)
        ) {
            listState.animateReselectScrollToTop(directAnimationItemLimit = 8)
        }
    }

    LaunchedEffect(pullToRefreshState) {
        snapshotFlow { pullToRefreshState.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { activated ->
                if (activated) {
                    pullRefreshHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
    }

    PullToRefreshBox(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            pullRefreshHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.refresh()
        },
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
                currentMediaId = currentMediaMetadata?.id,
                isPlaying = currentIsPlaying,
                onSongMore = { menuContext = it.toSongActionContext() },
            )

            ytSection(
                key = "daily_discover",
                title = dailyDiscoverTitle,
                items = dailyDiscoverItems.map { it.recommendation },
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                isPlaying = currentIsPlaying,
                onItemMore = { remoteMenuItem = it },
            )

            localItemSection(
                key = "keep_listening",
                title = keepListeningTitle,
                items = keepListeningItems,
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                isPlaying = currentIsPlaying,
                onSongMore = { menuContext = it.toSongActionContext() },
                onItemMore = { remoteMenuItem = it },
            )

            localSongSection(
                key = "forgotten_favorites",
                title = forgottenFavoritesTitle,
                songs = forgottenFavoriteItems,
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
                currentMediaId = currentMediaMetadata?.id,
                isPlaying = currentIsPlaying,
                onSongMore = { menuContext = it.toSongActionContext() },
            )

            echoBrainPlaylistItems.forEachIndexed { index, playlist ->
                ytSection(
                    key = "echo_brain_${index}_${playlist.playlist.id}",
                    title = playlist.playlist.title,
                    items = playlist.songs,
                    isLowEnd = isLowEnd,
                    onItemClick = onItemClick,
                    onPlayItem = onPlayItem,
                    currentMediaId = currentMediaMetadata?.id,
                    currentAlbumId = currentMediaMetadata?.album?.id,
                    isPlaying = currentIsPlaying,
                    onItemMore = { remoteMenuItem = it },
                )
            }
        }

        homePage?.sections.orEmpty().forEachIndexed { index, section ->
            ytSection(
                key = "youtube_${index}_${section.title}",
                title = section.title,
                subtitle = section.label,
                thumbnail = section.thumbnail,
                circularThumbnail = section.endpoint?.isArtistEndpoint == true,
                items = section.items,
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                isPlaying = currentIsPlaying,
                onSeeAll = section.endpoint
                    ?.takeIf { endpoint -> endpoint.browseId.isNotBlank() }
                    ?.let { endpoint -> { onBrowseClick(endpoint, section.title) } },
                onItemMore = { remoteMenuItem = it },
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
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                isPlaying = currentIsPlaying,
                onItemMore = { remoteMenuItem = it },
            )

            ytSection(
                key = "new_releases",
                title = newReleasesTitle,
                items = newReleaseItems,
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                isPlaying = currentIsPlaying,
                onSeeAll = onNewReleasesClick,
                onItemMore = { remoteMenuItem = it },
            )

            similarRecommendationItems.forEachIndexed { index, recommendation ->
                ytSection(
                    key = "similar_${index}_${recommendation.title.id}",
                    title = "$similarToTitle ${recommendation.title.title}",
                    items = recommendation.items,
                    isLowEnd = isLowEnd,
                    onItemClick = onItemClick,
                    onPlayItem = onPlayItem,
                    currentMediaId = currentMediaMetadata?.id,
                    currentAlbumId = currentMediaMetadata?.album?.id,
                    isPlaying = currentIsPlaying,
                    onItemMore = { remoteMenuItem = it },
                )
            }

            ytSection(
                key = "community",
                title = communityTitle,
                items = communityPlaylistItems.map { it.playlist },
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                isPlaying = currentIsPlaying,
                onItemMore = { remoteMenuItem = it },
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
    UniversalSongActionsHost(
        context = menuContext,
        onDismiss = { menuContext = null },
    )
    UniversalYTItemActionsHost(
        item = remoteMenuItem,
        onDismiss = { remoteMenuItem = null },
        onOpen = { remoteMenuItem?.let(onItemClick) },
        onPlay = remoteMenuItem?.let { item -> { onPlayItem(item) } },
    )
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
    currentMediaId: String?,
    isPlaying: Boolean,
    onSongMore: (Song) -> Unit,
) {
    if (songs.isEmpty()) return

    item(key = "${key}_header") {
        HomeSectionHeader(title = title, subtitle = subtitle)
    }
    item(key = "${key}_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = songs,
                key = { index, song -> "${key}_${song.id}_$index" },
            ) { _, song ->
                HomeMediaCard(
                    type = HomeContentType.SONG,
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name },
                    imageUrl = (song.song.thumbnailUrl
                        ?: "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg")
                        .resize(width = if (isLowEnd) 320 else 480),
                    isActive = song.id == currentMediaId,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song, songs) },
                    onPlay = { onSongClick(song, songs) },
                    onMoreClick = { onSongMore(song) },
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
    onPlayItem: (YTItem) -> Unit,
    currentMediaId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onSongMore: (Song) -> Unit,
    onItemMore: (YTItem) -> Unit,
) {
    if (items.isEmpty()) return
    val songQueue = items.filterIsInstance<Song>()

    item(key = "${key}_header") {
        HomeSectionHeader(title = title)
    }
    item(key = "${key}_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
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
                val type = when (localItem) {
                    is Song -> HomeContentType.SONG
                    is LocalAlbum -> HomeContentType.ALBUM
                    is LocalArtist -> HomeContentType.ARTIST
                    is LocalPlaylist -> HomeContentType.PLAYLIST
                }
                val remoteItem = localItem.toYTItemOrNull()
                HomeMediaCard(
                    type = type,
                    title = localItem.title,
                    subtitle = subtitle,
                    imageUrl = localItem.thumbnailUrl.orEmpty()
                        .resize(width = if (isLowEnd) 320 else 480),
                    isActive = when (localItem) {
                        is Song -> localItem.id == currentMediaId
                        is LocalAlbum -> localItem.id == currentAlbumId
                        else -> false
                    },
                    isPlaying = isPlaying,
                    onClick = {
                        when (localItem) {
                            is Song -> onSongClick(localItem, songQueue)
                            else -> remoteItem?.let(onItemClick)
                        }
                    },
                    onPlay = when {
                        localItem is Song -> ({ onSongClick(localItem, songQueue) })
                        remoteItem is AlbumItem && remoteItem.playlistId.isNotBlank() ->
                            ({ onPlayItem(remoteItem) })
                        else -> null
                    },
                    onMoreClick = when {
                        localItem is Song -> ({ onSongMore(localItem) })
                        remoteItem != null -> ({ onItemMore(remoteItem) })
                        else -> null
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
    thumbnail: String? = null,
    circularThumbnail: Boolean = false,
    items: List<YTItem>,
    isLowEnd: Boolean,
    onItemClick: (YTItem) -> Unit,
    onPlayItem: (YTItem) -> Unit,
    currentMediaId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onSeeAll: (() -> Unit)? = null,
    onItemMore: (YTItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "${key}_header") {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle,
            thumbnail = thumbnail,
            circularThumbnail = circularThumbnail,
            onSeeAll = onSeeAll,
        )
    }
    item(key = "${key}_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${key}_${item.id}_$index" },
            ) { _, item ->
                YTContentCard(
                    item = item,
                    currentMediaId = currentMediaId,
                    currentAlbumId = currentAlbumId,
                    isPlaying = isPlaying,
                    onClick = { onItemClick(item) },
                    onPlay = when (item) {
                        is SongItem -> ({ onPlayItem(item) })
                        is AlbumItem -> item.playlistId
                            .takeIf(String::isNotBlank)
                            ?.let { { onPlayItem(item) } }
                        is ArtistItem,
                        is PlaylistItem -> null
                    },
                    onMore = onItemMore,
                    thumbnailWidth = if (isLowEnd) 320 else 480,
                )
            }
        }
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
        channelId = artist.channelId,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
    is LocalPlaylist -> null
    is Song -> null
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
