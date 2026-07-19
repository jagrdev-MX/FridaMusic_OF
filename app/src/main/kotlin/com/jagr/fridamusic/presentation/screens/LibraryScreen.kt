package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.SongFilter
import com.jagr.fridamusic.constants.SongFilterKey
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.LibraryAlbumsViewModel
import com.jagr.fridamusic.viewmodels.LibraryArtistsViewModel
import com.jagr.fridamusic.viewmodels.LibraryPlaylistsViewModel
import com.jagr.fridamusic.viewmodels.LibrarySongsViewModel
import kotlinx.coroutines.launch

private enum class LibraryTab(val labelRes: Int) {
    PLAYLISTS(R.string.playlists),
    SONGS(R.string.songs),
    ALBUMS(R.string.albums),
    ARTISTS(R.string.artists),
}

@Composable
fun LibraryScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onLocalItemClick: (LocalItem) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.filter_library),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        )

        QuickAccessRow(
            onLikedClick = {
                scope.launch {
                    context.dataStore.edit { it[SongFilterKey] = SongFilter.LIKED.name }
                }
                selectedTab = LibraryTab.SONGS
            },
            onDownloadedClick = {
                scope.launch {
                    context.dataStore.edit { it[SongFilterKey] = SongFilter.DOWNLOADED.name }
                }
                selectedTab = LibraryTab.SONGS
            },
            onLibraryClick = {
                scope.launch {
                    context.dataStore.edit { it[SongFilterKey] = SongFilter.LIBRARY.name }
                }
                selectedTab = LibraryTab.SONGS
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.labelRes)) },
                )
            }
        }

        when (selectedTab) {
            LibraryTab.SONGS -> LibrarySongsTab(onSongClick)
            LibraryTab.PLAYLISTS -> LibraryPlaylistsTab(onLocalItemClick)
            LibraryTab.ALBUMS -> LibraryAlbumsTab(onLocalItemClick)
            LibraryTab.ARTISTS -> LibraryArtistsTab(onLocalItemClick)
        }
    }
}

@Composable
private fun QuickAccessRow(
    onLikedClick: () -> Unit,
    onDownloadedClick: () -> Unit,
    onLibraryClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickAccessChip(Icons.Rounded.Favorite, stringResource(R.string.filter_liked), Modifier.weight(1f), onLikedClick)
        QuickAccessChip(Icons.Rounded.CloudDownload, stringResource(R.string.filter_downloaded), Modifier.weight(1f), onDownloadedClick)
        QuickAccessChip(Icons.Rounded.TrendingUp, stringResource(R.string.filter_library), Modifier.weight(1f), onLibraryClick)
    }
}

@Composable
private fun QuickAccessChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibrarySongsTab(
    onSongClick: (Song, List<Song>) -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val songs by viewModel.allSongs.collectAsState()

    if (songs.isEmpty()) {
        EmptyLibraryState(stringResource(R.string.library_song_empty))
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
        items(songs, key = { it.song.id }) { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(song, songs) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(
                    model = song.song.thumbnailUrl?.resize(width = 96),
                    contentDescription = song.song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaylistsTab(
    onItemClick: (LocalItem) -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    if (playlists.isEmpty()) {
        EmptyLibraryState(stringResource(R.string.library_playlist_empty))
        return
    }
    LocalItemGrid(items = playlists, onItemClick = onItemClick, round = false)
}

@Composable
private fun LibraryAlbumsTab(
    onItemClick: (LocalItem) -> Unit,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.allAlbums.collectAsState()
    if (albums.isEmpty()) {
        EmptyLibraryState(stringResource(R.string.library_album_empty))
        return
    }
    LocalItemGrid(items = albums, onItemClick = onItemClick, round = false)
}

@Composable
private fun LibraryArtistsTab(
    onItemClick: (LocalItem) -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val artists by viewModel.allArtists.collectAsState()
    if (artists.isEmpty()) {
        EmptyLibraryState(stringResource(R.string.library_artist_empty))
        return
    }
    LocalItemGrid(items = artists, onItemClick = onItemClick, round = true)
}

@Composable
private fun LocalItemGrid(
    items: List<LocalItem>,
    onItemClick: (LocalItem) -> Unit,
    round: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        gridItems(items, key = { it.id }) { item ->
            Column(
                modifier = Modifier.clickable { onItemClick(item) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
                    model = item.thumbnailUrl?.resize(width = 300),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(if (round) CircleShape else RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}