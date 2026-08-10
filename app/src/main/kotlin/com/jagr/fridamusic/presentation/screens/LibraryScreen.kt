package com.jagr.fridamusic.presentation.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.MiniPlayerBottomSpacing
import com.jagr.fridamusic.constants.MiniPlayerHeight
import com.jagr.fridamusic.constants.NavigationBarHeight
import com.jagr.fridamusic.constants.PlaylistSortType
import com.jagr.fridamusic.constants.SongSortType
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.localmedia.LocalMediaStoreActionResult
import com.jagr.fridamusic.localmedia.LocalSongMetadataUpdate
import com.jagr.fridamusic.localmedia.LocalSongSortMetadata
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.DeleteLocalSongDialog
import com.jagr.fridamusic.presentation.components.DeletePlaylistDialog
import com.jagr.fridamusic.presentation.components.EmptyPlaylistsState
import com.jagr.fridamusic.presentation.components.LocalPlaylistPickerDialog
import com.jagr.fridamusic.presentation.components.LocalSongActionsSheet
import com.jagr.fridamusic.presentation.components.LocalSongDetailsDialog
import com.jagr.fridamusic.presentation.components.LocalSongGridItem
import com.jagr.fridamusic.presentation.components.LocalSongListItem
import com.jagr.fridamusic.presentation.components.LocalSongLyricsEditorDialog
import com.jagr.fridamusic.presentation.components.LocalSongMetadataEditorDialog
import com.jagr.fridamusic.presentation.components.PlaylistLibraryActionsSheet
import com.jagr.fridamusic.presentation.components.PlaylistLibraryControls
import com.jagr.fridamusic.presentation.components.PlaylistLibraryGridItem
import com.jagr.fridamusic.presentation.components.PlaylistLibraryListItem
import com.jagr.fridamusic.presentation.components.PlaylistLibrarySortSheet
import com.jagr.fridamusic.presentation.components.PlaylistNameDialog
import com.jagr.fridamusic.presentation.components.LibraryViewModeToggle
import com.jagr.fridamusic.playback.queues.ListQueue
import com.jagr.fridamusic.utils.openLocalAudioFolder
import com.jagr.fridamusic.utils.shareLocalAudio
import com.jagr.fridamusic.utils.SyncErrorKind
import com.jagr.fridamusic.utils.SyncStatus
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.CachePlaylistViewModel
import com.jagr.fridamusic.viewmodels.LibraryAlbumsViewModel
import com.jagr.fridamusic.viewmodels.LibraryArtistsViewModel
import com.jagr.fridamusic.viewmodels.LibraryMixViewModel
import com.jagr.fridamusic.viewmodels.LibraryPlaylistsViewModel
import com.jagr.fridamusic.viewmodels.LibrarySongsViewModel
import com.jagr.fridamusic.viewmodels.LocalSongsViewModel
import com.jagr.fridamusic.viewmodels.PlaylistsViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.ZoneId
import java.util.Locale

private enum class LibraryFilter(val label: String, val icon: ImageVector) {
    LIBRARY("Biblioteca", Icons.Rounded.LibraryMusic),
    PLAYLISTS("Listas", Icons.AutoMirrored.Rounded.QueueMusic),
    SONGS("Canciones", Icons.Rounded.MusicNote),
    LOCAL("Local", Icons.Rounded.Folder),
    ARTISTS("Artistas", Icons.Rounded.Person),
    ALBUMS("Álbumes", Icons.Rounded.Album),
}

private enum class LibrarySongMode(
    val title: String,
    val emptyTitle: String,
    val emptyDescription: String,
    val icon: ImageVector,
) {
    FAVORITES(
        "Canciones favoritas",
        "No hay canciones favoritas",
        "Las canciones que marques como favoritas aparecerán aquí.",
        Icons.Rounded.Favorite,
    ),
    OFFLINE(
        "Sin conexión",
        "No hay canciones sin conexión",
        "Descarga una canción completa para escucharla sin Internet.",
        Icons.Rounded.CloudDownload,
    ),
    CACHED(
        "En caché",
        "No hay canciones completas en caché",
        "Las canciones reproducibles completamente desde la caché aparecerán aquí.",
        Icons.Rounded.Cached,
    ),
}

private enum class LocalSongSortType(val labelRes: Int) {
    NAME(R.string.sort_by_name),
    ALBUM(R.string.sort_by_album),
    ARTIST(R.string.sort_by_artist),
    ALBUM_ARTIST(R.string.sort_by_album_artist),
    GENRE(R.string.sort_by_genre),
    TRACK_NUMBER(R.string.sort_by_track_number),
    DURATION(R.string.sort_by_length),
    YEAR(R.string.sort_by_year),
    COMPOSER(R.string.sort_by_composer),
    DATE_MODIFIED(R.string.sort_by_date_modified),
    DATE_ADDED(R.string.sort_by_date_added),
}

private val SongSortType.labelRes: Int
    get() = when (this) {
        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
        SongSortType.NAME -> R.string.sort_by_name
        SongSortType.ARTIST -> R.string.sort_by_artist
        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
    }

private val LibrarySnackbarBottomPadding =
    MiniPlayerHeight + NavigationBarHeight + MiniPlayerBottomSpacing + 12.dp
private val LibraryContentTopPadding = 12.dp

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onCachedSongClick: (Song, List<Song>) -> Unit,
    onLocalItemClick: (LocalItem) -> Unit,
    onStatsClick: () -> Unit,
    onExternalPlaylistClick: () -> Unit,
    mixViewModel: LibraryMixViewModel = hiltViewModel(),
    playlistsViewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val filters = LibraryFilter.entries
    val pagerState = rememberPagerState(initialPage = 0) { filters.size }
    val scope = rememberCoroutineScope()
    val tabListState = rememberLazyListState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val currentFilter = filters[pagerState.currentPage]
    var songMode by rememberSaveable { mutableStateOf(LibrarySongMode.FAVORITES) }
    var showLocalBlacklist by rememberSaveable { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf(emptySet<String>()) }
    var selectionMenuExpanded by remember { mutableStateOf(false) }
    var showSelectionPlaylistPicker by remember { mutableStateOf(false) }
    var pendingSelectionExport by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer
    val syncState by mixViewModel.syncState.collectAsState()
    val isRefreshing by mixViewModel.isRefreshing.collectAsState()
    val syncError = syncState.overallStatus as? SyncStatus.Error
    var previousSyncError by remember { mutableStateOf(syncError) }
    val snackbarHostState = remember { SnackbarHostState() }
    val playlists by playlistsViewModel.allPlaylists.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    val emptyPlaylistMessage = stringResource(R.string.playlist_is_empty)
    val actionFailedMessage = stringResource(R.string.playlist_action_failed)
    val addedToQueueMessage = stringResource(R.string.added_to_queue)
    val addedToPlayNextMessage = stringResource(R.string.added_to_play_next)
    val exportSuccessfulMessage = stringResource(R.string.export_successful)

    fun showLibraryMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val selectionExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        val playlistsToExport = pendingSelectionExport
        pendingSelectionExport = emptyList()
        if (uri != null && playlistsToExport.isNotEmpty()) {
            playlistsViewModel.exportPlaylists(playlistsToExport, uri) { success ->
                showLibraryMessage(if (success) exportSuccessfulMessage else actionFailedMessage)
            }
        }
    }
    val syncErrorMessage = when (syncError?.kind) {
        SyncErrorKind.OFFLINE -> stringResource(R.string.library_sync_offline)
        SyncErrorKind.SESSION_EXPIRED -> stringResource(R.string.library_sync_session_expired)
        SyncErrorKind.TEMPORARY -> stringResource(R.string.library_sync_failed)
        null -> null
    }

    LaunchedEffect(syncError) {
        val isNewError = syncError != null && syncError != previousSyncError
        previousSyncError = syncError
        if (isNewError) syncErrorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val targetPage = pagerState.currentPage
        val screenWidth = configuration.screenWidthDp.dp
        val tabWidth = 140.dp
        val targetOffsetDp = (screenWidth - tabWidth) / 2
        val targetOffsetPx = with(density) { targetOffsetDp.roundToPx() }
        tabListState.animateScrollToItem(targetPage, scrollOffset = -targetOffsetPx)
    }

    LaunchedEffect(currentFilter) {
        if (currentFilter != LibraryFilter.PLAYLISTS) selectedPlaylistIds = emptySet()
    }

    LaunchedEffect(playlists) {
        selectedPlaylistIds = selectedPlaylistIds.intersect(playlists.mapTo(mutableSetOf()) { it.id })
    }

    BackHandler(enabled = selectedPlaylistIds.isNotEmpty()) {
        selectedPlaylistIds = emptySet()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to tonalStart.copy(alpha = 0.30f),
                        0.5f to tonalMiddle.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (currentFilter == LibraryFilter.PLAYLISTS && selectedPlaylistIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    IconButton(onClick = { selectedPlaylistIds = emptySet() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                    }
                    Text(
                        text = stringResource(R.string.playlist_selected_count, selectedPlaylistIds.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = {
                            playlistsViewModel.loadPlaylistsSongs(selectedPlaylistIds) { songs ->
                                if (playerConnection != null && songs.isNotEmpty()) {
                                    playerConnection.playNext(songs.map { it.toMediaItem() })
                                    showLibraryMessage(addedToPlayNextMessage)
                                } else showLibraryMessage(emptyPlaylistMessage)
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = stringResource(R.string.play_next))
                    }
                    IconButton(
                        onClick = {
                            playlistsViewModel.loadPlaylistsSongs(selectedPlaylistIds) { songs ->
                                if (playerConnection != null && songs.isNotEmpty()) {
                                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                    showLibraryMessage(addedToQueueMessage)
                                } else showLibraryMessage(emptyPlaylistMessage)
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.add_to_queue))
                    }
                    Box {
                        IconButton(onClick = { selectionMenuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = selectionMenuExpanded,
                            onDismissRequest = { selectionMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_to_playlist)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    selectionMenuExpanded = false
                                    showSelectionPlaylistPicker = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_save_file)) },
                                leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                onClick = {
                                    selectionMenuExpanded = false
                                    pendingSelectionExport = selectedPlaylists
                                    selectionExportLauncher.launch("playlists.m3u")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.spotify_select_all)) },
                                leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null) },
                                onClick = {
                                    selectionMenuExpanded = false
                                    selectedPlaylistIds = playlists.mapTo(mutableSetOf()) { it.id }
                                },
                            )
                        }
                    }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Text(
                        text = "Biblioteca",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        enabled = !isRefreshing,
                        onClick = mixViewModel::refresh,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.library_refresh),
                            )
                        }
                    }
                    }
                }

                LazyRow(
                    state = tabListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(filters, key = { it.name }) { filter ->
                        ExpressiveTabChip(
                            label = filter.label,
                            icon = filter.icon,
                            selected = currentFilter == filter,
                            onClick = {
                                if (filter == LibraryFilter.LOCAL) showLocalBlacklist = false
                                scope.launch {
                                    pagerState.animateScrollToPage(filters.indexOf(filter))
                                }
                            },
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (filters[page]) {
                    LibraryFilter.LIBRARY -> LibraryMixTab(
                        onLocalItemClick = onLocalItemClick,
                        onStatsClick = onStatsClick,
                        onTabSelected = { filter ->
                            if (filter == LibraryFilter.LOCAL) showLocalBlacklist = false
                            scope.launch {
                                pagerState.animateScrollToPage(filters.indexOf(filter))
                            }
                        },
                        onBlacklistSelected = {
                            showLocalBlacklist = true
                            scope.launch {
                                pagerState.animateScrollToPage(filters.indexOf(LibraryFilter.LOCAL))
                            }
                        },
                        onSongCollectionSelected = { mode ->
                            songMode = mode
                            scope.launch {
                                pagerState.animateScrollToPage(filters.indexOf(LibraryFilter.SONGS))
                            }
                        },
                        playlistsViewModel = playlistsViewModel,
                    )
                    LibraryFilter.PLAYLISTS -> PlaylistsTab(
                        onLocalItemClick = onLocalItemClick,
                        onExternalPlaylistClick = onExternalPlaylistClick,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onSelectedPlaylistIdsChange = { selectedPlaylistIds = it },
                        viewModel = playlistsViewModel,
                    )
                    LibraryFilter.SONGS -> SongsTab(
                        mode = songMode,
                        onModeSelected = { songMode = it },
                        onSongClick = onSongClick,
                        onCachedSongClick = onCachedSongClick,
                        onLocalItemClick = onLocalItemClick,
                    )
                    LibraryFilter.LOCAL -> LocalSongsTab(
                        onSongClick = onSongClick,
                        onLocalItemClick = onLocalItemClick,
                        showBlacklist = showLocalBlacklist,
                        onCloseBlacklist = { showLocalBlacklist = false },
                    )
                    LibraryFilter.ARTISTS -> ArtistsTab(onLocalItemClick = onLocalItemClick)
                    LibraryFilter.ALBUMS -> AlbumsTab(onLocalItemClick = onLocalItemClick)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = LibrarySnackbarBottomPadding,
                ),
        )
    }
    if (showSelectionPlaylistPicker) {
        LocalPlaylistPickerDialog(
            playlists = playlists.filter {
                it.id !in selectedPlaylistIds && it.playlist.isEditable
            },
            onDismiss = { showSelectionPlaylistPicker = false },
            onSelect = { target ->
                showSelectionPlaylistPicker = false
                playlistsViewModel.addPlaylistsToPlaylist(selectedPlaylists, target) { success ->
                    showLibraryMessage(
                        if (success) resources.getString(R.string.added_to_playlist, target.playlist.name)
                        else actionFailedMessage
                    )
                }
            },
        )
    }
}

@Composable
private fun ExpressiveTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (selected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "chip_scale",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chip_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chip_content",
    )

    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label,
            tint = contentColor, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
            color = contentColor,
        )
    }
}

@Composable
private fun LibraryMixTab(
    onLocalItemClick: (LocalItem) -> Unit,
    onStatsClick: () -> Unit,
    onTabSelected: (LibraryFilter) -> Unit,
    onBlacklistSelected: () -> Unit,
    onSongCollectionSelected: (LibrarySongMode) -> Unit,
    playlistsViewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    artistsViewModel: LibraryArtistsViewModel = hiltViewModel(),
    songsViewModel: LibrarySongsViewModel = hiltViewModel(),
    cacheViewModel: CachePlaylistViewModel = hiltViewModel(),
    localSongsViewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val playlists by playlistsViewModel.allPlaylists.collectAsState()
    val artists by artistsViewModel.allArtists.collectAsState()
    val favoriteSongs by songsViewModel.favoriteSongs.collectAsState()
    val downloadedSongs by cacheViewModel.downloadedSongs.collectAsState()
    val cachedSongs by cacheViewModel.cachedSongs.collectAsState()
    val blacklistedSongs by localSongsViewModel.blacklistedSongs.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = LibraryContentTopPadding, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "shortcuts") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ShortcutCard(
                        title = "Canciones favoritas",
                        countText = "${favoriteSongs.size} pistas",
                        icon = Icons.Rounded.Favorite,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        iconColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = { onSongCollectionSelected(LibrarySongMode.FAVORITES) },
                    )
                    ShortcutCard(
                        title = "Sin conexión",
                        countText = "${downloadedSongs.size} pistas",
                        icon = Icons.Rounded.CloudDownload,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { onSongCollectionSelected(LibrarySongMode.OFFLINE) },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ShortcutCard(
                        title = "En caché",
                        countText = "${cachedSongs.size} pistas",
                        icon = Icons.Rounded.Cached,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = { onSongCollectionSelected(LibrarySongMode.CACHED) },
                    )
                    ShortcutCard(
                        title = "Archivos locales",
                        countText = "En el dispositivo",
                        icon = Icons.Rounded.Folder,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        iconColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(LibraryFilter.LOCAL) },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ShortcutCard(
                        title = "Mi top 50",
                        countText = "Todo el tiempo",
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        iconColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onStatsClick,
                    )
                    ShortcutCard(
                        title = stringResource(R.string.local_song_blacklist),
                        countText = stringResource(
                            R.string.local_blacklist_song_count,
                            blacklistedSongs.size,
                        ),
                        icon = Icons.Rounded.Block,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        iconColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = onBlacklistSelected,
                    )
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "playlists_header") {
                LibrarySectionHeader(
                    title = "Tus listas",
                    onSeeAll = { onTabSelected(LibraryFilter.PLAYLISTS) },
                )
            }
            item(key = "playlists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(playlists.take(8), key = { "playlist_${it.id}" }) { playlist ->
                        PlaylistCompactCard(
                            playlist = playlist,
                            onClick = { onLocalItemClick(playlist) },
                        )
                    }
                    item {
                        SeeMoreCard(
                            onClick = { onTabSelected(LibraryFilter.PLAYLISTS) },
                        )
                    }
                }
            }
        }

        if (artists.isNotEmpty()) {
            item(key = "artists_header") {
                LibrarySectionHeader(
                    title = "Tus artistas",
                    onSeeAll = { onTabSelected(LibraryFilter.ARTISTS) },
                )
            }
            item(key = "artists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(artists.take(10), key = { "artist_${it.artist.id}" }) { artistWithSongs ->
                        ArtistCircleCard(
                            artist = artistWithSongs,
                            onClick = { onLocalItemClick(artistWithSongs) },
                        )
                    }
                    item {
                        ArtistMoreCard(
                            onClick = { onTabSelected(LibraryFilter.ARTISTS) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    countText: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "shortcut_scale",
    )

    Box(
        modifier = modifier
            .aspectRatio(1.45f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(26.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null,
                    tint = iconColor, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = countText, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LibrarySectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = "Ver todo",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onSeeAll)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PlaylistCompactCard(playlist: Playlist, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "playlist_scale",
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            playlist.thumbnails.firstOrNull()?.let { url ->
                AsyncImage(
                    model = url.resize(width = 240),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = playlist.playlist.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground)
        Text(text = "${playlist.songCount} canciones",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun SeeMoreCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Rounded.ExpandMore, contentDescription = "Más",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Más", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun ArtistCircleCard(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(80.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.artist.thumbnailUrl?.resize(width = 160),
            contentDescription = artist.artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = artist.artist.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun ArtistMoreCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(80.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = "Más",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Más",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PlaylistsTab(
    onLocalItemClick: (LocalItem) -> Unit,
    onExternalPlaylistClick: () -> Unit,
    selectedPlaylistIds: Set<String>,
    onSelectedPlaylistIdsChange: (Set<String>) -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val playlists by viewModel.allPlaylists.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val queueTitleFlow = remember(playerConnection) { playerConnection?.queueTitle ?: flowOf(null) }
    val isPlayingFlow = remember(playerConnection) { playerConnection?.isEffectivelyPlaying ?: flowOf(false) }
    val hasMiniPlayerFlow = remember(playerConnection) {
        playerConnection?.mediaMetadata?.map { it != null } ?: flowOf(false)
    }
    val queueTitle by queueTitleFlow.collectAsState(initial = null)
    val isPlaying by isPlayingFlow.collectAsState(initial = false)
    val hasMiniPlayer by hasMiniPlayerFlow.collectAsState(initial = false)
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var createMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var menuPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var renamePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var addToPlaylistSource by remember { mutableStateOf<Playlist?>(null) }
    var pendingCoverPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var pendingExportPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var activePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val emptyPlaylistMessage = stringResource(R.string.playlist_is_empty)
    val addedToQueueMessage = stringResource(R.string.added_to_queue)
    val addedToPlayNextMessage = stringResource(R.string.added_to_play_next)
    val createdMessage = stringResource(R.string.playlist_created)
    val renamedMessage = stringResource(R.string.playlist_renamed)
    val coverUpdatedMessage = stringResource(R.string.playlist_cover_updated)
    val deletedMessage = stringResource(R.string.playlist_deleted)
    val actionFailedMessage = stringResource(R.string.playlist_action_failed)
    val exportSuccessfulMessage = stringResource(R.string.export_successful)
    val importedMessage = stringResource(R.string.playlist_imported)

    fun toggleSelection(playlistId: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSelectedPlaylistIdsChange(
            if (playlistId in selectedPlaylistIds) selectedPlaylistIds - playlistId
            else selectedPlaylistIds + playlistId
        )
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun playPlaylist(playlist: Playlist) {
        viewModel.loadPlaylistSongs(playlist.id) { songs ->
            if (songs.isEmpty()) {
                showMessage(emptyPlaylistMessage)
                return@loadPlaylistSongs
            }
            val connection = playerConnection
            if (connection == null) {
                showMessage(actionFailedMessage)
                return@loadPlaylistSongs
            }
            activePlaylistId = playlist.id
            connection.playQueue(
                ListQueue(
                    title = playlist.playlist.name,
                    items = songs.map { it.toMediaItem() },
                )
            )
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val playlist = pendingCoverPlaylist
        pendingCoverPlaylist = null
        if (uri != null && playlist != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setPlaylistCover(playlist, uri) { success ->
                showMessage(if (success) coverUpdatedMessage else actionFailedMessage)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        val playlist = pendingExportPlaylist
        pendingExportPlaylist = null
        if (uri != null && playlist != null) {
            viewModel.exportPlaylist(playlist, uri) { success ->
                showMessage(if (success) exportSuccessfulMessage else actionFailedMessage)
            }
        }
    }

    val importM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importM3u(uri) { success ->
                showMessage(if (success) importedMessage else actionFailedMessage)
            }
        }
    }

    LaunchedEffect(queueTitle) {
        val activePlaylist = playlists.firstOrNull { it.id == activePlaylistId }
        if (activePlaylist != null && queueTitle != activePlaylist.playlist.name) {
            activePlaylistId = null
        }
    }

    val sortLabel = stringResource(
        when (preferences.sortType) {
            PlaylistSortType.NAME -> R.string.sort_by_name
            PlaylistSortType.SONG_COUNT -> R.string.playlist_sort_song_count
            PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
            PlaylistSortType.CREATE_DATE -> R.string.playlist_sort_date_added
        }
    )
    val createFabRotation by animateFloatAsState(
        targetValue = if (createMenuExpanded) 405f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "playlistCreateFabRotation",
    )
    val createFabBottomPadding by animateDpAsState(
        targetValue = if (hasMiniPlayer) {
            LibrarySnackbarBottomPadding + 8.dp
        } else {
            NavigationBarHeight + MiniPlayerBottomSpacing + 12.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "playlistCreateFabBottomPadding",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (preferences.gridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = LibraryContentTopPadding,
                    bottom = LibrarySnackbarBottomPadding + 84.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "playlist_controls") {
                    PlaylistLibraryControls(
                        sortLabel = sortLabel,
                        descending = preferences.descending,
                        gridView = true,
                        onSortClick = { showSortSheet = true },
                        onGridViewChanged = viewModel::setGridView,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "playlists_empty") {
                        EmptyPlaylistsState()
                    }
                }
                gridItems(playlists, key = { it.id }) { playlist ->
                    val isCurrent = queueTitle == playlist.playlist.name &&
                        (activePlaylistId == null || activePlaylistId == playlist.id)
                    PlaylistLibraryGridItem(
                        playlist = playlist,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        selected = playlist.id in selectedPlaylistIds,
                        selectionMode = selectedPlaylistIds.isNotEmpty(),
                        onClick = {
                            if (selectedPlaylistIds.isNotEmpty()) toggleSelection(playlist.id)
                            else onLocalItemClick(playlist)
                        },
                        onLongClick = { toggleSelection(playlist.id) },
                        onPlay = { playPlaylist(playlist) },
                        onMoreClick = { menuPlaylist = playlist },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = LibraryContentTopPadding,
                    bottom = LibrarySnackbarBottomPadding + 84.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "playlist_controls") {
                    PlaylistLibraryControls(
                        sortLabel = sortLabel,
                        descending = preferences.descending,
                        gridView = false,
                        onSortClick = { showSortSheet = true },
                        onGridViewChanged = viewModel::setGridView,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (playlists.isEmpty()) {
                    item(key = "playlists_empty") { EmptyPlaylistsState() }
                }
                items(playlists, key = { it.id }) { playlist ->
                    val isCurrent = queueTitle == playlist.playlist.name &&
                        (activePlaylistId == null || activePlaylistId == playlist.id)
                    PlaylistLibraryListItem(
                        playlist = playlist,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        selected = playlist.id in selectedPlaylistIds,
                        selectionMode = selectedPlaylistIds.isNotEmpty(),
                        onClick = {
                            if (selectedPlaylistIds.isNotEmpty()) toggleSelection(playlist.id)
                            else onLocalItemClick(playlist)
                        },
                        onLongClick = { toggleSelection(playlist.id) },
                        onPlay = { playPlaylist(playlist) },
                        onMoreClick = { menuPlaylist = playlist },
                    )
                }
            }
        }

        if (selectedPlaylistIds.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = createFabBottomPadding),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(visible = createMenuExpanded) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PlaylistCreateAction(
                            label = stringResource(R.string.playlist_external),
                            icon = Icons.Rounded.Public,
                            onClick = {
                                createMenuExpanded = false
                                onExternalPlaylistClick()
                            },
                        )
                        PlaylistCreateAction(
                            label = stringResource(R.string.playlist_import),
                            icon = Icons.AutoMirrored.Rounded.Input,
                            onClick = {
                                createMenuExpanded = false
                                importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "text/plain"))
                            },
                        )
                        PlaylistCreateAction(
                            label = stringResource(R.string.create_playlist),
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            onClick = {
                                createMenuExpanded = false
                                showCreateDialog = true
                            },
                        )
                    }
                }
                FloatingActionButton(onClick = { createMenuExpanded = !createMenuExpanded }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(if (createMenuExpanded) R.string.close else R.string.create_playlist),
                        modifier = Modifier.graphicsLayer { rotationZ = createFabRotation },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = LibrarySnackbarBottomPadding,
                ),
        )
    }

    if (showSortSheet) {
        PlaylistLibrarySortSheet(
            selectedSort = preferences.sortType,
            descending = preferences.descending,
            onSortSelected = {
                viewModel.setSortType(it)
                showSortSheet = false
            },
            onDescendingChanged = viewModel::setSortDescending,
            onDismiss = { showSortSheet = false },
        )
    }

    menuPlaylist?.let { playlist ->
        val hasSongs = playlist.songCount > 0
        PlaylistLibraryActionsSheet(
            playlist = playlist,
            canEditMetadata = true,
            onDismiss = { menuPlaylist = null },
            onPlayNext = if (hasSongs) {
                {
                    menuPlaylist = null
                    viewModel.loadPlaylistSongs(playlist.id) { songs ->
                        if (playerConnection != null && songs.isNotEmpty()) {
                            playerConnection.playNext(songs.map { it.toMediaItem() })
                            showMessage(addedToPlayNextMessage)
                        } else {
                            showMessage(actionFailedMessage)
                        }
                    }
                }
            } else null,
            onPlay = if (hasSongs) {
                { menuPlaylist = null; playPlaylist(playlist) }
            } else null,
            onAddToQueue = if (hasSongs) {
                {
                    menuPlaylist = null
                    viewModel.loadPlaylistSongs(playlist.id) { songs ->
                        if (playerConnection != null && songs.isNotEmpty()) {
                            playerConnection.addToQueue(songs.map { it.toMediaItem() })
                            showMessage(addedToQueueMessage)
                        } else {
                            showMessage(actionFailedMessage)
                        }
                    }
                }
            } else null,
            onAddToPlaylist = if (hasSongs) {
                { menuPlaylist = null; addToPlaylistSource = playlist }
            } else null,
            onTogglePinned = {
                viewModel.togglePinned(playlist)
                menuPlaylist = null
            },
            onChooseCover = {
                pendingCoverPlaylist = playlist
                menuPlaylist = null
                coverPicker.launch(arrayOf("image/*"))
            },
            onRename = { renamePlaylist = playlist; menuPlaylist = null },
            onExport = {
                pendingExportPlaylist = playlist
                menuPlaylist = null
                val safeName = playlist.playlist.name
                    .replace(Regex("[\\/:*?\"<>|]"), "_")
                    .ifBlank { "playlist" }
                exportLauncher.launch("$safeName.m3u")
            },
            onDelete = {
                deletePlaylist = playlist
                menuPlaylist = null
            },
        )
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.create_playlist),
            confirmLabel = stringResource(R.string.create_playlist),
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createPlaylist(name) { success ->
                    showMessage(if (success) createdMessage else actionFailedMessage)
                }
            },
        )
    }

    renamePlaylist?.let { playlist ->
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_rename),
            initialName = playlist.playlist.name,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { renamePlaylist = null },
            onConfirm = { name ->
                renamePlaylist = null
                viewModel.renamePlaylist(playlist, name) { success ->
                    showMessage(if (success) renamedMessage else actionFailedMessage)
                }
            },
        )
    }

    addToPlaylistSource?.let { source ->
        LocalPlaylistPickerDialog(
            playlists = playlists.filter {
                it.id != source.id && it.playlist.isEditable
            },
            onDismiss = { addToPlaylistSource = null },
            onSelect = { target ->
                addToPlaylistSource = null
                viewModel.addPlaylistToPlaylist(source, target) { success ->
                    showMessage(
                        if (success) {
                            resources.getString(R.string.added_to_playlist, target.playlist.name)
                        } else {
                            actionFailedMessage
                        }
                    )
                }
            },
        )
    }

    deletePlaylist?.let { playlist ->
        DeletePlaylistDialog(
            playlistName = playlist.playlist.name,
            onDismiss = { deletePlaylist = null },
            onConfirm = {
                deletePlaylist = null
                viewModel.deletePlaylist(playlist) { success ->
                    showMessage(if (success) deletedMessage else actionFailedMessage)
                }
            },
        )
    }

}

@Composable
private fun PlaylistCreateAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun SongsTab(
    mode: LibrarySongMode,
    onModeSelected: (LibrarySongMode) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onCachedSongClick: (Song, List<Song>) -> Unit,
    onLocalItemClick: (LocalItem) -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
    cacheViewModel: CachePlaylistViewModel = hiltViewModel(),
    localSongsViewModel: LocalSongsViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val downloadedSongs by cacheViewModel.downloadedSongs.collectAsState()
    val cachedSongs by cacheViewModel.cachedSongs.collectAsState()
    val availabilityLoading by cacheViewModel.isLoading.collectAsState()
    val availabilityError by cacheViewModel.hasError.collectAsState()
    val pinnedSongIds by localSongsViewModel.pinnedSongIds.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val playlists by playlistsViewModel.allPlaylists.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val currentSongFlow = remember(playerConnection) { playerConnection?.currentSong ?: flowOf(null) }
    val isPlayingFlow = remember(playerConnection) { playerConnection?.isEffectivelyPlaying ?: flowOf(false) }
    val currentSong by currentSongFlow.collectAsState(initial = null)
    val isPlaying by isPlayingFlow.collectAsState(initial = false)
    val songs = when (mode) {
        LibrarySongMode.FAVORITES -> favoriteSongs
        LibrarySongMode.OFFLINE -> downloadedSongs
        LibrarySongMode.CACHED -> cachedSongs
    }
    val sortAscending = !preferences.descending
    val sortedSongs = remember(songs, pinnedSongIds, preferences, mode, locale) {
        sortLibrarySongs(
            songs = songs,
            pinnedSongIds = pinnedSongIds,
            sortType = preferences.sortType,
            descending = preferences.descending,
            mode = mode,
            locale = locale,
        )
    }
    val isAvailabilityMode = mode == LibrarySongMode.OFFLINE || mode == LibrarySongMode.CACHED
    val isLoading = isAvailabilityMode && availabilityLoading
    val hasError = isAvailabilityMode && availabilityError
    val playSong = if (mode == LibrarySongMode.CACHED) onCachedSongClick else onSongClick
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var gridView by rememberSaveable { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    var lyricsEditorSong by remember { mutableStateOf<Song?>(null) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    val shareUnavailableMessage = stringResource(R.string.local_song_share_unavailable)

    LazyVerticalGrid(
        columns = if (gridView) GridCells.Adaptive(minSize = 156.dp) else GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = LibraryContentTopPadding, bottom = 140.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (gridView) 12.dp else 8.dp),
    ) {
        item(
            key = "song_mode_header_${mode.name}",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${songs.size} pistas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LibrarySongFilterMenu(
                    selectedMode = mode,
                    onModeSelected = onModeSelected,
                )
            }
        }

        if (!isLoading && !hasError && sortedSongs.isNotEmpty()) {
            item(key = "song_controls", span = { GridItemSpan(maxLineSpan) }) {
                LocalSongSortControls(
                    sortLabel = stringResource(preferences.sortType.labelRes),
                    ascending = sortAscending,
                    gridView = gridView,
                    onSortClick = { showSortSheet = true },
                    onGridViewChanged = { gridView = it },
                    onShuffleClick = {
                        val shuffledSongs = sortedSongs.shuffled()
                        shuffledSongs.firstOrNull()?.let { firstSong ->
                            playSong(firstSong, shuffledSongs)
                        }
                    },
                )
            }
        }

        when {
            isLoading -> item(key = "song_mode_loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
            hasError -> item(key = "song_mode_error", span = { GridItemSpan(maxLineSpan) }) {
                LibrarySongsEmptyState(
                    title = "No se pudo comprobar el almacenamiento",
                    description = "Vuelve a abrir esta sección para intentarlo de nuevo.",
                    isError = true,
                )
            }
            sortedSongs.isEmpty() -> item(
                key = "song_mode_empty_${mode.name}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                LibrarySongsEmptyState(
                    title = mode.emptyTitle,
                    description = mode.emptyDescription,
                )
            }
        }

        if (!isLoading && !hasError) {
            gridItems(sortedSongs, key = { "${mode.name}_${it.song.id}" }) { song ->
                if (gridView) {
                    LocalSongGridItem(
                        song = song,
                        isCurrent = currentSong?.song?.id == song.song.id,
                        isPlaying = isPlaying,
                        onClick = { playSong(song, sortedSongs) },
                        onMoreClick = { menuSong = song },
                    )
                } else {
                    LocalSongListItem(
                        song = song,
                        isCurrent = currentSong?.song?.id == song.song.id,
                        isPlaying = isPlaying,
                        onClick = { playSong(song, sortedSongs) },
                        onMoreClick = { menuSong = song },
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        SongSortSheet(
            options = SongSortType.entries,
            selectedSort = preferences.sortType,
            ascending = sortAscending,
            onSortSelected = {
                viewModel.setSortType(it)
                showSortSheet = false
            },
            onAscendingChanged = { viewModel.setSortDescending(!it) },
            onDismiss = { showSortSheet = false },
            key = { it.name },
            labelRes = { it.labelRes },
        )
    }

    menuSong?.let { selectedSong ->
        val albumTarget = selectedSong.album?.let { album ->
            Album(album = album, artists = selectedSong.artists)
        }
        val artistTarget = selectedSong.artists.firstOrNull()?.let { artist ->
            Artist(artist = artist, songCount = 0)
        }
        val albumArtistTarget = artistTarget
        LocalSongActionsSheet(
            song = selectedSong,
            onDismiss = { menuSong = null },
            isPinned = selectedSong.song.id in pinnedSongIds,
            onToggleFavorite = {
                viewModel.toggleFavorite(selectedSong)
                menuSong = null
            },
            onPlayNext = {
                playerConnection?.playNext(selectedSong.toMediaItem())
                menuSong = null
            },
            onAddToQueue = {
                playerConnection?.addToQueue(selectedSong.toMediaItem())
                menuSong = null
            },
            onAddToPlaylist = {
                playlistSong = selectedSong
                menuSong = null
            },
            onTogglePinned = {
                localSongsViewModel.togglePinnedSong(selectedSong.song.id)
                menuSong = null
            },
            onGoToAlbum = albumTarget?.let { target ->
                { onLocalItemClick(target); menuSong = null }
            },
            onGoToArtist = artistTarget?.let { target ->
                { onLocalItemClick(target); menuSong = null }
            },
            onGoToAlbumArtist = albumArtistTarget?.let { target ->
                { onLocalItemClick(target); menuSong = null }
            },
            onEditLyrics = {
                lyricsEditorSong = selectedSong
                menuSong = null
            },
            onDetails = {
                detailsSong = selectedSong
                menuSong = null
            },
            onShare = {
                if (!shareLibrarySong(context, selectedSong)) {
                    Toast.makeText(context, shareUnavailableMessage, Toast.LENGTH_SHORT).show()
                }
                menuSong = null
            },
        )
    }

    playlistSong?.let { selectedSong ->
        LocalPlaylistPickerDialog(
            playlists = playlists,
            onDismiss = { playlistSong = null },
            onSelect = { playlist ->
                playlistsViewModel.addSongToPlaylist(playlist, selectedSong)
                playlistSong = null
            },
        )
    }

    lyricsEditorSong?.let { selectedSong ->
        val lyricsEntity by remember(selectedSong.song.id) {
            localSongsViewModel.lyrics(selectedSong.song.id)
        }.collectAsState(initial = null)
        LocalSongLyricsEditorDialog(
            songId = selectedSong.song.id,
            initialLyrics = lyricsEntity?.lyrics.orEmpty(),
            onDismiss = { lyricsEditorSong = null },
            onSave = { lyrics ->
                localSongsViewModel.saveLyrics(selectedSong.song.id, lyrics)
                lyricsEditorSong = null
            },
        )
    }

    detailsSong?.let { selectedSong ->
        LocalSongDetailsDialog(
            song = selectedSong,
            metadata = null,
            locale = locale,
            onDismiss = { detailsSong = null },
        )
    }
}

@Composable
private fun LibrarySongFilterMenu(
    selectedMode: LibrarySongMode,
    onModeSelected: (LibrarySongMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = "Filtrar canciones",
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
        ) {
            LibrarySongMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(imageVector = mode.icon, contentDescription = null)
                    },
                    trailingIcon = {
                        if (mode == selectedMode) {
                            Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibrarySongsEmptyState(
    title: String,
    description: String,
    isError: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalSongsTab(
    onSongClick: (Song, List<Song>) -> Unit,
    onLocalItemClick: (LocalItem) -> Unit,
    showBlacklist: Boolean,
    onCloseBlacklist: () -> Unit,
    viewModel: LocalSongsViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerConnection = LocalPlayerConnection.current
    val currentSongFlow = remember(playerConnection) { playerConnection?.currentSong ?: flowOf(null) }
    val isPlayingFlow = remember(playerConnection) { playerConnection?.isEffectivelyPlaying ?: flowOf(false) }
    val currentSong by currentSongFlow.collectAsState(initial = null)
    val isPlaying by isPlayingFlow.collectAsState(initial = false)
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val songs by viewModel.songs.collectAsState()
    val blacklistedSongs by viewModel.blacklistedSongs.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val sortMetadata by viewModel.sortMetadata.collectAsState()
    val pinnedSongIds by viewModel.pinnedSongIds.collectAsState()
    val sortPreference by viewModel.sortPreference.collectAsState()
    val playlists by playlistsViewModel.allPlaylists.collectAsState()
    val sortType = remember(sortPreference.typeName) {
        runCatching { LocalSongSortType.valueOf(sortPreference.typeName) }
            .getOrDefault(LocalSongSortType.DATE_ADDED)
    }
    val sortAscending = !sortPreference.descending
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var gridView by rememberSaveable { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    var metadataEditorSong by remember { mutableStateOf<Song?>(null) }
    var lyricsEditorSong by remember { mutableStateOf<Song?>(null) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    var deleteSong by remember { mutableStateOf<Song?>(null) }
    var pendingMetadataUpdate by remember { mutableStateOf<LocalSongMetadataUpdate?>(null) }
    var pendingDeleteSongId by rememberSaveable { mutableStateOf<String?>(null) }
    val sortedSongs = remember(songs, sortMetadata, pinnedSongIds, sortType, sortAscending) {
        sortLocalSongs(
            songs = songs,
            metadata = sortMetadata,
            pinnedSongIds = pinnedSongIds,
            sortType = sortType,
            ascending = sortAscending,
        )
    }
    val sortedBlacklistedSongs = remember(
        blacklistedSongs,
        sortMetadata,
        pinnedSongIds,
        sortType,
        sortAscending,
    ) {
        sortLocalSongs(
            songs = blacklistedSongs,
            metadata = sortMetadata,
            pinnedSongIds = pinnedSongIds,
            sortType = sortType,
            ascending = sortAscending,
        )
    }
    val actionFailedMessage = stringResource(R.string.local_song_action_failed)
    val metadataSavedMessage = stringResource(R.string.local_song_information_saved)
    val deletedMessage = stringResource(R.string.local_song_deleted)
    val folderUnavailableMessage = stringResource(R.string.local_song_folder_unavailable)
    val sharedUnavailableMessage = stringResource(R.string.local_song_share_unavailable)
    val blacklistedMessage = stringResource(R.string.local_song_blacklisted)
    val restoredMessage = stringResource(R.string.local_song_restored_from_blacklist)
    val undoLabel = stringResource(R.string.undo)

    BackHandler(enabled = showBlacklist, onBack = onCloseBlacklist)

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun restoreFromBlacklist(songId: String) {
        viewModel.setSongBlacklisted(songId, blacklisted = false)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = restoredMessage,
                actionLabel = undoLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.setSongBlacklisted(songId, blacklisted = true)
            }
        }
    }

    fun completeMetadataUpdate() {
        val update = pendingMetadataUpdate ?: return
        viewModel.updateSongMetadata(update, requestPermission = false) { result ->
            when (result) {
                LocalMediaStoreActionResult.Success -> showMessage(metadataSavedMessage)
                is LocalMediaStoreActionResult.Failure,
                is LocalMediaStoreActionResult.PermissionRequired -> showMessage(actionFailedMessage)
            }
            pendingMetadataUpdate = null
        }
    }

    fun completeLegacyDelete() {
        val songId = pendingDeleteSongId ?: return
        viewModel.deleteSongFromDevice(songId, requestPermission = false) { result ->
            when (result) {
                LocalMediaStoreActionResult.Success -> showMessage(deletedMessage)
                is LocalMediaStoreActionResult.Failure,
                is LocalMediaStoreActionResult.PermissionRequired -> showMessage(actionFailedMessage)
            }
            pendingDeleteSongId = null
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when {
                pendingMetadataUpdate != null -> completeMetadataUpdate()
                pendingDeleteSongId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    viewModel.refreshAfterExternalMediaAction()
                    showMessage(deletedMessage)
                    pendingDeleteSongId = null
                }
                pendingDeleteSongId != null -> completeLegacyDelete()
            }
        } else {
            pendingMetadataUpdate = null
            pendingDeleteSongId = null
        }
    }

    val legacyWritePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (pendingMetadataUpdate != null) completeMetadataUpdate() else completeLegacyDelete()
        } else {
            showMessage(actionFailedMessage)
            pendingMetadataUpdate = null
            pendingDeleteSongId = null
        }
    }

    fun launchMediaPermission(result: LocalMediaStoreActionResult.PermissionRequired) {
        runCatching {
            mediaPermissionLauncher.launch(
                IntentSenderRequest.Builder(result.pendingIntent.intentSender).build(),
            )
        }.onFailure {
            showMessage(actionFailedMessage)
            pendingMetadataUpdate = null
            pendingDeleteSongId = null
        }
    }

    fun startMetadataUpdate(update: LocalSongMetadataUpdate) {
        pendingDeleteSongId = null
        pendingMetadataUpdate = update
        val needsLegacyWritePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyWritePermission) {
            legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewModel.updateSongMetadata(update, requestPermission = true) { result ->
            when (result) {
                LocalMediaStoreActionResult.Success -> {
                    showMessage(metadataSavedMessage)
                    pendingMetadataUpdate = null
                }
                is LocalMediaStoreActionResult.PermissionRequired -> launchMediaPermission(result)
                is LocalMediaStoreActionResult.Failure -> {
                    showMessage(actionFailedMessage)
                    pendingMetadataUpdate = null
                }
            }
        }
    }

    fun startDelete(songId: String) {
        pendingMetadataUpdate = null
        pendingDeleteSongId = songId
        val needsLegacyWritePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyWritePermission) {
            legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewModel.deleteSongFromDevice(songId, requestPermission = true) { result ->
            when (result) {
                LocalMediaStoreActionResult.Success -> {
                    showMessage(deletedMessage)
                    pendingDeleteSongId = null
                }
                is LocalMediaStoreActionResult.PermissionRequired -> launchMediaPermission(result)
                is LocalMediaStoreActionResult.Failure -> {
                    showMessage(actionFailedMessage)
                    pendingDeleteSongId = null
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.refreshLibrary()
            viewModel.refreshSortMetadata()
        }
    }

    if (showSortSheet) {
        SongSortSheet(
            options = LocalSongSortType.entries,
            selectedSort = sortType,
            ascending = sortAscending,
            onSortSelected = {
                viewModel.setSortType(it.name)
                showSortSheet = false
            },
            onAscendingChanged = { viewModel.setSortDescending(!it) },
            onDismiss = { showSortSheet = false },
            key = { it.name },
            labelRes = { it.labelRes },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showBlacklist) {
            LocalBlacklistContent(
                songs = sortedBlacklistedSongs,
                currentSongId = currentSong?.song?.id,
                isPlaying = isPlaying,
                onClose = onCloseBlacklist,
                onSongClick = { song -> onSongClick(song, sortedBlacklistedSongs) },
                onRestore = { song -> restoreFromBlacklist(song.song.id) },
            )
        } else {
            LazyVerticalGrid(
                columns = if (gridView) GridCells.Adaptive(minSize = 156.dp) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = LibraryContentTopPadding,
                    bottom = 140.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (gridView) 12.dp else 8.dp),
            ) {
            item(key = "local_scan", span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.local_songs_scan_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (hasPermission) {
                                stringResource(R.string.local_songs_scan_subtitle)
                            } else {
                                stringResource(R.string.local_songs_permission_body)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            enabled = !scanState.isScanning,
                            onClick = {
                                if (hasPermission) {
                                    viewModel.scanDevice()
                                } else {
                                    permissionLauncher.launch(requiredPermission)
                                }
                            },
                        ) {
                            if (scanState.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = when {
                                    scanState.isScanning -> stringResource(R.string.scanning_device)
                                    hasPermission -> stringResource(R.string.scan_device)
                                    else -> stringResource(R.string.allow)
                                },
                            )
                        }
                        scanState.lastSummary?.let { summary ->
                            Text(
                                text = stringResource(
                                    R.string.local_songs_scan_summary,
                                    summary.scannedSongs,
                                    summary.removedSongs,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (scanState.errorMessage != null) {
                            Text(
                                text = stringResource(R.string.local_songs_scan_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (hasPermission && songs.isNotEmpty()) {
                item(key = "local_sort_controls", span = { GridItemSpan(maxLineSpan) }) {
                    LocalSongSortControls(
                        sortLabel = stringResource(sortType.labelRes),
                        ascending = sortAscending,
                        gridView = gridView,
                        onSortClick = { showSortSheet = true },
                        onGridViewChanged = { gridView = it },
                        onShuffleClick = {
                            val shuffledSongs = sortedSongs.shuffled()
                            shuffledSongs.firstOrNull()?.let { firstSong ->
                                onSongClick(firstSong, shuffledSongs)
                            }
                        },
                    )
                }
            }

            if (!hasPermission) {
                item(key = "local_permission", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.permission_storage_desc),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (songs.isEmpty() && !scanState.isScanning) {
                item(key = "local_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.local_songs_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.local_songs_ready_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            gridItems(sortedSongs, key = { "local_${it.song.id}" }) { song ->
                if (gridView) {
                    LocalSongGridItem(
                        song = song,
                        isCurrent = currentSong?.song?.id == song.song.id,
                        isPlaying = isPlaying,
                        onClick = { onSongClick(song, sortedSongs) },
                        onMoreClick = { menuSong = song },
                    )
                } else {
                    LocalSongListItem(
                        song = song,
                        isCurrent = currentSong?.song?.id == song.song.id,
                        isPlaying = isPlaying,
                        onClick = { onSongClick(song, sortedSongs) },
                        onMoreClick = { menuSong = song },
                    )
                }
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = LibrarySnackbarBottomPadding,
                ),
        )
    }

    menuSong?.let { selectedSong ->
        val metadata = sortMetadata[selectedSong.song.id]
        val albumTarget = selectedSong.album?.let { album ->
            Album(album = album, artists = selectedSong.artists)
        }
        val artistTarget = selectedSong.artists.firstOrNull()?.let { artist ->
            Artist(artist = artist, songCount = 0)
        }
        val albumArtistTarget = selectedSong.artists.firstOrNull { artist ->
            artist.name.equals(metadata?.albumArtist, ignoreCase = true)
        }?.let { artist -> Artist(artist = artist, songCount = 0) } ?: artistTarget
        val canOpenFolder = !metadata?.relativePath.isNullOrBlank() || !metadata?.absolutePath.isNullOrBlank()

        LocalSongActionsSheet(
            song = selectedSong,
            metadata = metadata,
            isPinned = selectedSong.song.id in pinnedSongIds,
            onDismiss = { menuSong = null },
            onToggleFavorite = {
                viewModel.toggleFavorite(selectedSong)
                menuSong = null
            },
            onPlayNext = {
                playerConnection?.playNext(selectedSong.toMediaItem())
                menuSong = null
            },
            onAddToQueue = {
                playerConnection?.addToQueue(selectedSong.toMediaItem())
                menuSong = null
            },
            onAddToPlaylist = {
                playlistSong = selectedSong
                menuSong = null
            },
            onTogglePinned = {
                viewModel.togglePinnedSong(selectedSong.song.id)
                menuSong = null
            },
            onGoToAlbum = albumTarget?.let { target ->
                { onLocalItemClick(target); menuSong = null }
            },
            onGoToArtist = artistTarget?.let { target ->
                { onLocalItemClick(target); menuSong = null }
            },
            onGoToAlbumArtist = albumArtistTarget?.let { target ->
                { onLocalItemClick(target); menuSong = null }
            },
            onGoToFolder = if (canOpenFolder) {
                {
                    val opened = openLocalAudioFolder(context, metadata.relativePath, metadata.absolutePath)
                    if (!opened) showMessage(folderUnavailableMessage)
                    menuSong = null
                }
            } else {
                null
            },
            onEditMetadata = {
                metadataEditorSong = selectedSong
                menuSong = null
            },
            onEditLyrics = {
                lyricsEditorSong = selectedSong
                menuSong = null
            },
            onBlacklist = {
                viewModel.setSongBlacklisted(selectedSong.song.id, blacklisted = true)
                menuSong = null
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = blacklistedMessage,
                        actionLabel = undoLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.setSongBlacklisted(selectedSong.song.id, blacklisted = false)
                    }
                }
            },
            onDetails = {
                detailsSong = selectedSong
                menuSong = null
            },
            onShare = {
                val shared = shareLocalAudio(context, selectedSong.song.id, metadata?.mimeType)
                if (!shared) showMessage(sharedUnavailableMessage)
                menuSong = null
            },
            onDelete = {
                deleteSong = selectedSong
                menuSong = null
            },
        )
    }

    playlistSong?.let { selectedSong ->
        LocalPlaylistPickerDialog(
            playlists = playlists,
            onDismiss = { playlistSong = null },
            onSelect = { playlist ->
                playlistsViewModel.addSongToPlaylist(playlist, selectedSong)
                playlistSong = null
            },
        )
    }

    metadataEditorSong?.let { selectedSong ->
        LocalSongMetadataEditorDialog(
            song = selectedSong,
            metadata = sortMetadata[selectedSong.song.id],
            onDismiss = { metadataEditorSong = null },
            onSave = { title, artist, album ->
                metadataEditorSong = null
                startMetadataUpdate(
                    LocalSongMetadataUpdate(
                        mediaId = selectedSong.song.id,
                        title = title,
                        artist = artist,
                        album = album,
                    ),
                )
            },
        )
    }

    lyricsEditorSong?.let { selectedSong ->
        val lyricsEntity by remember(selectedSong.song.id) {
            viewModel.lyrics(selectedSong.song.id)
        }.collectAsState(initial = null)
        LocalSongLyricsEditorDialog(
            songId = selectedSong.song.id,
            initialLyrics = lyricsEntity?.lyrics.orEmpty(),
            onDismiss = { lyricsEditorSong = null },
            onSave = { lyrics ->
                viewModel.saveLyrics(selectedSong.song.id, lyrics)
                lyricsEditorSong = null
            },
        )
    }

    detailsSong?.let { selectedSong ->
        LocalSongDetailsDialog(
            song = selectedSong,
            metadata = sortMetadata[selectedSong.song.id],
            locale = locale,
            onDismiss = { detailsSong = null },
        )
    }

    deleteSong?.let { selectedSong ->
        DeleteLocalSongDialog(
            title = selectedSong.song.title,
            onDismiss = { deleteSong = null },
            onConfirm = {
                deleteSong = null
                startDelete(selectedSong.song.id)
            },
        )
    }
}

@Composable
private fun LocalBlacklistContent(
    songs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRestore: (Song) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = LibraryContentTopPadding,
            bottom = 140.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "local_blacklist_header") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalIconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.local_song_blacklist),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.local_blacklist_song_count, songs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (songs.isEmpty()) {
            item(key = "local_blacklist_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.local_blacklist_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.local_blacklist_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        items(songs, key = { "blacklisted_${it.song.id}" }) { song ->
            LocalSongListItem(
                song = song,
                isCurrent = currentSongId == song.song.id,
                isPlaying = isPlaying,
                onClick = { onSongClick(song) },
                onMoreClick = { onRestore(song) },
                trailingContent = {
                    IconButton(
                        onClick = { onRestore(song) },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.local_song_remove_from_blacklist),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun LocalSongSortControls(
    sortLabel: String,
    ascending: Boolean,
    gridView: Boolean,
    onSortClick: () -> Unit,
    onGridViewChanged: (Boolean) -> Unit,
    onShuffleClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onSortClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = sortLabel.uppercase(locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        FilledTonalIconButton(
            onClick = onShuffleClick,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.shuffle_content_desc),
            )
        }
        LibraryViewModeToggle(
            gridView = gridView,
            onGridViewChanged = onGridViewChanged,
        )
    }
}

private fun sortLibrarySongs(
    songs: List<Song>,
    pinnedSongIds: Set<String>,
    sortType: SongSortType,
    descending: Boolean,
    mode: LibrarySongMode,
    locale: Locale,
): List<Song> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val comparator = when (sortType) {
        SongSortType.CREATE_DATE -> Comparator<Song> { first, second ->
            val firstDate = when (mode) {
                LibrarySongMode.FAVORITES -> first.song.likedDate
                LibrarySongMode.OFFLINE -> first.song.dateDownload
                LibrarySongMode.CACHED -> first.song.dateModified ?: first.song.date ?:
                    first.song.likedDate ?: first.song.inLibrary
            }
            val secondDate = when (mode) {
                LibrarySongMode.FAVORITES -> second.song.likedDate
                LibrarySongMode.OFFLINE -> second.song.dateDownload
                LibrarySongMode.CACHED -> second.song.dateModified ?: second.song.date ?:
                    second.song.likedDate ?: second.song.inLibrary
            }
            compareValues(firstDate, secondDate)
        }
        SongSortType.NAME -> Comparator { first, second ->
            collator.compare(first.song.title, second.song.title)
        }
        SongSortType.ARTIST -> Comparator { first, second ->
            collator.compare(
                first.artists.joinToString("") { it.name },
                second.artists.joinToString("") { it.name },
            )
        }
        SongSortType.PLAY_TIME -> compareBy { it.song.totalPlayTime }
    }
    val ordered = songs.sortedWith(comparator).let { if (descending) it.asReversed() else it }
    return ordered.sortedByDescending { it.song.id in pinnedSongIds }
}

private fun shareLibrarySong(context: Context, song: Song): Boolean {
    val songId = song.song.id.trim()
    if (song.song.isLocal || songId.startsWith("content://") || songId.startsWith("file://")) {
        return shareLocalAudio(context, songId, song.format?.mimeType)
    }
    if (songId.isEmpty()) return false

    val shareUrl = when {
        songId.startsWith("spotify:track:", ignoreCase = true) ->
            "https://open.spotify.com/track/${Uri.encode(songId.substringAfterLast(':'))}"
        songId.startsWith("http://", ignoreCase = true) ||
            songId.startsWith("https://", ignoreCase = true) -> songId
        else -> "https://music.youtube.com/watch?v=${Uri.encode(songId)}"
    }
    return runCatching {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareUrl)
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
    }.isSuccess
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SongSortSheet(
    options: List<T>,
    selectedSort: T,
    ascending: Boolean,
    onSortSelected: (T) -> Unit,
    onAscendingChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    key: (T) -> Any,
    labelRes: (T) -> Int,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.songs).uppercase(locale),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.sort_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LocalSortDirectionOption(
                        selected = ascending,
                        icon = Icons.Rounded.ArrowUpward,
                        label = stringResource(R.string.sort_ascending_short),
                        onClick = { onAscendingChanged(true) },
                    )
                    LocalSortDirectionOption(
                        selected = !ascending,
                        icon = Icons.Rounded.ArrowDownward,
                        label = stringResource(R.string.sort_descending_short),
                        onClick = { onAscendingChanged(false) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(options, key = key) { sortType ->
                    val selected = sortType == selectedSort
                    Surface(
                        onClick = { onSortSelected(sortType) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 17.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(labelRes(sortType)),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalSortDirectionOption(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun sortLocalSongs(
    songs: List<Song>,
    metadata: Map<String, LocalSongSortMetadata>,
    pinnedSongIds: Set<String>,
    sortType: LocalSongSortType,
    ascending: Boolean,
): List<Song> {
    val zoneId = ZoneId.systemDefault()
    return songs.sortedWith { first, second ->
        val firstPinned = first.song.id in pinnedSongIds
        val secondPinned = second.song.id in pinnedSongIds
        if (firstPinned != secondPinned) {
            return@sortedWith if (firstPinned) -1 else 1
        }
        val firstMetadata = metadata[first.song.id]
        val secondMetadata = metadata[second.song.id]
        val primaryComparison = when (sortType) {
            LocalSongSortType.NAME -> compareLocalSortText(
                firstMetadata?.title ?: first.song.title,
                secondMetadata?.title ?: second.song.title,
                ascending,
            )
            LocalSongSortType.ALBUM -> compareLocalSortText(
                firstMetadata?.album ?: first.song.albumName ?: first.album?.title,
                secondMetadata?.album ?: second.song.albumName ?: second.album?.title,
                ascending,
            )
            LocalSongSortType.ARTIST -> compareLocalSortText(
                firstMetadata?.artist ?: first.artists.joinToString(", ") { it.name },
                secondMetadata?.artist ?: second.artists.joinToString(", ") { it.name },
                ascending,
            )
            LocalSongSortType.ALBUM_ARTIST -> compareLocalSortText(
                firstMetadata?.albumArtist ?: first.artists.joinToString(", ") { it.name },
                secondMetadata?.albumArtist ?: second.artists.joinToString(", ") { it.name },
                ascending,
            )
            LocalSongSortType.GENRE -> compareLocalSortText(
                firstMetadata?.genre,
                secondMetadata?.genre,
                ascending,
            )
            LocalSongSortType.TRACK_NUMBER -> compareLocalSortValues(
                firstMetadata?.trackNumber,
                secondMetadata?.trackNumber,
                ascending,
            )
            LocalSongSortType.DURATION -> compareLocalSortValues(
                firstMetadata?.durationMs ?: first.song.duration.takeIf { it >= 0 }?.times(1000L),
                secondMetadata?.durationMs ?: second.song.duration.takeIf { it >= 0 }?.times(1000L),
                ascending,
            )
            LocalSongSortType.YEAR -> compareLocalSortValues(
                firstMetadata?.year ?: first.song.year,
                secondMetadata?.year ?: second.song.year,
                ascending,
            )
            LocalSongSortType.COMPOSER -> compareLocalSortText(
                firstMetadata?.composer,
                secondMetadata?.composer,
                ascending,
            )
            LocalSongSortType.DATE_MODIFIED -> compareLocalSortValues(
                firstMetadata?.dateModifiedSeconds ?: first.song.dateModified?.atZone(zoneId)?.toEpochSecond(),
                secondMetadata?.dateModifiedSeconds ?: second.song.dateModified?.atZone(zoneId)?.toEpochSecond(),
                ascending,
            )
            LocalSongSortType.DATE_ADDED -> compareLocalSortValues(
                firstMetadata?.dateAddedSeconds ?: first.song.date?.atZone(zoneId)?.toEpochSecond(),
                secondMetadata?.dateAddedSeconds ?: second.song.date?.atZone(zoneId)?.toEpochSecond(),
                ascending,
            )
        }

        if (primaryComparison != 0) {
            primaryComparison
        } else {
            val titleComparison = compareLocalSortText(
                first.song.title,
                second.song.title,
                ascending = true,
            )
            if (titleComparison != 0) titleComparison else first.song.id.compareTo(second.song.id)
        }
    }
}

private fun compareLocalSortText(first: String?, second: String?, ascending: Boolean): Int =
    compareLocalSortValues(
        first.normalizedLocalSortText(),
        second.normalizedLocalSortText(),
        ascending,
    )

private fun String?.normalizedLocalSortText(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.getDefault())

private fun <T : Comparable<T>> compareLocalSortValues(
    first: T?,
    second: T?,
    ascending: Boolean,
): Int = when {
    first == null && second == null -> 0
    first == null -> 1
    second == null -> -1
    ascending -> first.compareTo(second)
    else -> second.compareTo(first)
}

@Composable
private fun ArtistsTab(
    onLocalItemClick: (LocalItem) -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val artists by viewModel.allArtists.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = LibraryContentTopPadding, bottom = 140.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(artists, key = { it.artist.id }) { artistWithSongs ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLocalItemClick(artistWithSongs) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = artistWithSongs.artist.thumbnailUrl?.resize(width = 96),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = artistWithSongs.artist.name,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(text = "${artistWithSongs.songCount} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    onLocalItemClick: (LocalItem) -> Unit,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.allAlbums.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = LibraryContentTopPadding, bottom = 140.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onLocalItemClick(album) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = album.album.thumbnailUrl?.resize(width = 120),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = album.album.title,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(text = album.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
