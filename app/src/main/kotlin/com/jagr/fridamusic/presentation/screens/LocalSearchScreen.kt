package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.CONTENT_TYPE_LIST
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.extensions.togglePlayPause
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.LocalSongListItem
import com.jagr.fridamusic.presentation.components.SearchBar
import com.jagr.fridamusic.playback.queues.ListQueue
import com.jagr.fridamusic.viewmodels.LocalFilter
import com.jagr.fridamusic.viewmodels.LocalSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSearchScreen(
    onBack: () -> Unit,
    onToggleToOnlineSearch: () -> Unit,
    onLocalItemClick: (LocalItem) -> Unit,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val result by viewModel.result.collectAsState()
    val query by viewModel.query.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()
    val queueSearchedSongs = stringResource(R.string.queue_searched_songs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        }

        SearchBar(
            query = query,
            onQueryChange = { viewModel.query.value = it },
            onSearch = { viewModel.query.value = it },
            active = query.isNotBlank(),
            onActiveChange = { if (!it) viewModel.query.value = "" },
            onToggleMode = onToggleToOnlineSearch,
            toggleContentDescription = stringResource(R.string.search_online),
            toggleIcon = Icons.Rounded.LibraryMusic,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                item(key = "filters") {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchFilterChip(LocalFilter.ALL, currentFilter) { viewModel.filter.value = it }
                        SearchFilterChip(LocalFilter.SONG, currentFilter) { viewModel.filter.value = it }
                        SearchFilterChip(LocalFilter.ALBUM, currentFilter) { viewModel.filter.value = it }
                        SearchFilterChip(LocalFilter.ARTIST, currentFilter) { viewModel.filter.value = it }
                        SearchFilterChip(LocalFilter.PLAYLIST, currentFilter) { viewModel.filter.value = it }
                    }
                }

                if (query.isBlank()) {
                    item {
                        EmptySearchState()
                    }
                    return@LazyColumn
                }

                if (result.map.isEmpty()) {
                    item { EmptySearchState() }
                    return@LazyColumn
                }

                result.map.forEach { (filter, items) ->
                    if (result.filter == LocalFilter.ALL) {
                        item(key = "header_${filter.name}") {
                            SectionHeader(title = filterLabel(filter))
                        }
                    }

                    items(items.distinctBy { it.id }, key = { it.id }, contentType = { CONTENT_TYPE_LIST }) { item ->
                        when (item) {
                            is Song -> LocalSongListItem(
                                song = item,
                                isCurrent = item.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                onClick = {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        val songs = result.map.getOrDefault(LocalFilter.SONG, emptyList())
                                            .filterIsInstance<Song>()
                                            .map { it.toMediaItem() }
                                        if (songs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = queueSearchedSongs,
                                                    items = songs,
                                                    startIndex = songs.indexOfFirst { it.mediaId == item.id }.coerceAtLeast(0),
                                                )
                                            )
                                        }
                                    }
                                },
                                onMoreClick = { },
                            )

                            is Album -> SimpleLocalSearchRow(
                                title = item.album.title,
                                subtitle = stringResource(R.string.albums),
                                icon = Icons.Rounded.Album,
                                onClick = { onLocalItemClick(item) },
                            )

                            is Artist -> SimpleLocalSearchRow(
                                title = item.artist.name,
                                subtitle = stringResource(R.string.artists),
                                icon = Icons.Rounded.Person,
                                onClick = { onLocalItemClick(item) },
                            )

                            is Playlist -> SimpleLocalSearchRow(
                                title = item.playlist.name,
                                subtitle = stringResource(R.string.playlists),
                                icon = Icons.Rounded.PlaylistPlay,
                                onClick = { onLocalItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    filter: LocalFilter,
    current: LocalFilter,
    onSelect: (LocalFilter) -> Unit,
) {
    FilterChip(
        selected = current == filter,
        onClick = { onSelect(filter) },
        label = { Text(filterLabel(filter)) },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(3.dp, 16.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(text = title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptySearchState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.no_results_found),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SimpleLocalSearchRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun filterLabel(filter: LocalFilter): String = stringResource(
    when (filter) {
        LocalFilter.ALL -> R.string.filter_all
        LocalFilter.SONG -> R.string.filter_songs
        LocalFilter.ALBUM -> R.string.filter_albums
        LocalFilter.ARTIST -> R.string.filter_artists
        LocalFilter.PLAYLIST -> R.string.filter_playlists
    }
)
