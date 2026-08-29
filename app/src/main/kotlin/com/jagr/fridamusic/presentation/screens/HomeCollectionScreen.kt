package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.HomeContentType
import com.jagr.fridamusic.presentation.components.HomeMediaCard
import com.jagr.fridamusic.presentation.components.LocalCollectionActionContext
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.UniversalLocalCollectionActionsHost
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.presentation.components.YTContentCard
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.viewmodels.HomeViewModel
import com.music.innertube.models.YTItem

enum class HomeCollectionKind {
    QUICK_PICKS,
    DAILY_DISCOVER,
    KEEP_LISTENING,
    FORGOTTEN_FAVORITES,
    ECHO_BRAIN,
    ACCOUNT_PLAYLISTS,
    SIMILAR,
    COMMUNITY,
    REMOTE_SECTION,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCollectionScreen(
    kind: HomeCollectionKind,
    index: Int,
    title: String,
    onSongClick: (Song, List<Song>) -> Unit,
    onLocalItemClick: (LocalItem) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onPlayItem: (YTItem) -> Unit,
    onBack: () -> Unit,
    viewModel: HomeViewModel,
) {
    val quickPicks by viewModel.quickPicks.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val echoBrainPlaylists by viewModel.echoBrainPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value
    val isPlaying = playerConnection?.isPlaying?.collectAsState()?.value == true
    var songMenu by remember { mutableStateOf<SongActionContext?>(null) }
    var ytMenu by remember { mutableStateOf<YTItem?>(null) }
    var localMenu by remember { mutableStateOf<LocalCollectionActionContext?>(null) }

    val localItems: List<LocalItem> = when (kind) {
        HomeCollectionKind.QUICK_PICKS -> quickPicks.orEmpty()
        HomeCollectionKind.KEEP_LISTENING -> keepListening.orEmpty()
        HomeCollectionKind.FORGOTTEN_FAVORITES -> forgottenFavorites.orEmpty()
        else -> emptyList()
    }
    val ytItems: List<YTItem> = when (kind) {
        HomeCollectionKind.DAILY_DISCOVER -> dailyDiscover.orEmpty().map { it.recommendation }
        HomeCollectionKind.ECHO_BRAIN -> echoBrainPlaylists.orEmpty().getOrNull(index)?.songs.orEmpty()
        HomeCollectionKind.ACCOUNT_PLAYLISTS -> accountPlaylists.orEmpty()
        HomeCollectionKind.SIMILAR -> similarRecommendations.orEmpty().getOrNull(index)?.items.orEmpty()
        HomeCollectionKind.COMMUNITY -> communityPlaylists.orEmpty().map { it.playlist }
        HomeCollectionKind.REMOTE_SECTION -> homePage?.sections.orEmpty()
            .getOrNull(index)
            ?.items
            .orEmpty()
        else -> emptyList()
    }
    val songQueue = localItems.filterIsInstance<Song>()
    val empty = localItems.isEmpty() && ytItems.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )

        when {
            isLoading && empty -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { FridaLoadingIndicator() }

            empty -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.collection_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 136.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 156.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(localItems, key = { "local_${it.javaClass.name}_${it.id}" }) { item ->
                    val type = when (item) {
                        is Song -> HomeContentType.SONG
                        is Album -> HomeContentType.ALBUM
                        is Artist -> HomeContentType.ARTIST
                        is Playlist -> HomeContentType.PLAYLIST
                    }
                    val subtitle = when (item) {
                        is Song -> item.artists.joinToString(", ") { it.name }
                        is Album -> item.artists.joinToString(", ") { it.name }
                        is Artist -> stringResource(R.string.artists)
                        is Playlist -> stringResource(R.string.playlist_song_count, item.songCount)
                    }
                    val active = when (item) {
                        is Song -> item.id == mediaMetadata?.id
                        is Album -> item.id == mediaMetadata?.album?.id
                        else -> false
                    }
                    HomeMediaCard(
                        type = type,
                        title = item.title,
                        subtitle = subtitle,
                        imageUrl = item.thumbnailUrl.orEmpty(),
                        isActive = active,
                        isPlaying = isPlaying,
                        onClick = {
                            if (item is Song) onSongClick(item, songQueue) else onLocalItemClick(item)
                        },
                        onPlay = if (item is Song) ({ onSongClick(item, songQueue) }) else null,
                        onMoreClick = {
                            if (item is Song) {
                                songMenu = item.toSongActionContext()
                            } else {
                                localMenu = LocalCollectionActionContext(
                                    id = item.id,
                                    title = item.title,
                                    subtitle = subtitle,
                                    thumbnail = item.thumbnailUrl,
                                    circularThumbnail = item is Artist,
                                    onOpen = { onLocalItemClick(item) },
                                )
                            }
                        },
                        isLocal = when (item) {
                            is Song -> item.song.isLocal
                            is Album -> item.album.isLocal
                            is Artist -> item.artist.isLocal
                            is Playlist -> item.playlist.isLocal
                        },
                        fillMaxWidth = true,
                    )
                }

                items(ytItems, key = { "yt_${it.javaClass.name}_${it.id}" }) { item ->
                    YTContentCard(
                        item = item,
                        currentMediaId = mediaMetadata?.id,
                        currentAlbumId = mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        onClick = { onItemClick(item) },
                        onPlay = item.playAction(onPlayItem),
                        onMore = { ytMenu = it },
                        fillMaxWidth = true,
                    )
                }
            }
        }
    }

    UniversalSongActionsHost(context = songMenu, onDismiss = { songMenu = null })
    UniversalYTItemActionsHost(
        item = ytMenu,
        onDismiss = { ytMenu = null },
        onOpen = { ytMenu?.let(onItemClick) },
        onPlay = ytMenu?.playAction(onPlayItem),
    )
    UniversalLocalCollectionActionsHost(context = localMenu, onDismiss = { localMenu = null })
}
