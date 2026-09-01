package com.jagr.fridamusic.presentation.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.AnimatedLibraryHeartButton
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.HomeSectionHeader
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.presentation.components.universalMediaClickable
import com.jagr.fridamusic.presentation.components.resolveRemoteArtistPage
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.jagr.fridamusic.presentation.playYTItem
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.ArtistViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.launch

@Composable
fun ArtistScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onRemoteItemClick: (YTItem) -> Unit,
    onBrowseClick: (BrowseEndpoint, String, Int?, Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val isBookmarkUpdating by viewModel.isBookmarkUpdating.collectAsState()
    val artistPage = viewModel.artistPage


    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name ?: ""
    val artistThumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
    val subscriberCount = artistPage?.subscriberCountText
    val description = artistPage?.description
    val artistLink = resolveArtistShareLink(
        remoteArtist = artistPage?.artist,
        fallbackChannelId = libraryArtist?.artist?.channelId,
    )


    val remoteSections = artistPage?.sections ?: emptyList()
    val remoteSongs = remoteSections
        .flatMap { it.items }
        .filterIsInstance<SongItem>()
    val remoteAlbums = remoteSections
        .flatMap { it.items }
        .filterIsInstance<AlbumItem>()
    val effectiveSongCount = remember(librarySongs, remoteSongs) {
        (librarySongs.map { it.song.id } + remoteSongs.map { it.id })
            .filter(String::isNotBlank)
            .toSet()
            .size
    }
    val effectiveAlbumCount = remember(libraryAlbums, remoteAlbums) {
        (libraryAlbums.map { it.album.id } + remoteAlbums.map { it.browseId })
            .filter(String::isNotBlank)
            .toSet()
            .size
    }
    val countSummary = listOfNotNull(
        effectiveSongCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.n_song, it, it)
        },
        effectiveAlbumCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.n_album, it, it)
        },
    ).joinToString(" • ")

    val isLoadingRemote = viewModel.isLoadingRemote
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }
    var remoteMenuItem by remember { mutableStateOf<YTItem?>(null) }
    var isResolvingRadio by remember { mutableStateOf(false) }

    fun showLinkUnavailable() {
        Toast.makeText(context, R.string.artist_link_unavailable, Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (artistThumbnail != null) {
            AsyncImage(
                model = artistThumbnail.resize(width = 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(60.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp),
        ) {

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val link = artistLink
                            if (link == null) {
                                showLinkUnavailable()
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(artistName, link)
                                )
                                Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = stringResource(R.string.copy_link),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = {
                            val link = artistLink
                            if (link == null) {
                                showLinkUnavailable()
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, link)
                                }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(intent, context.getString(R.string.share))
                                    )
                                }.onFailure { showLinkUnavailable() }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.share),
                            tint = Color.White,
                        )
                    }
                    AnimatedLibraryHeartButton(
                        isSaved = libraryArtist?.artist?.bookmarkedAt != null,
                        enabled = !isBookmarkUpdating && (libraryArtist != null || artistPage != null),
                        contentDescription = stringResource(
                            if (libraryArtist?.artist?.bookmarkedAt != null) {
                                R.string.unfollow_artist
                            } else {
                                R.string.follow_artist
                            }
                        ),
                        onClick = viewModel::toggleFollow,
                    )
                }
            }


            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = artistThumbnail?.resize(width = 400),
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (subscriberCount != null) {
                        Text(
                            text = subscriberCount,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (countSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = countSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }


                    if (remoteSongs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Button(
                                onClick = { playerConnection?.playYTItem(remoteSongs.first()) },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.play),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val connection = playerConnection
                                    if (connection == null || isResolvingRadio) return@OutlinedButton

                                    val immediateEndpoint = artistPage?.artist?.radioEndpoint
                                    if (immediateEndpoint != null) {
                                        connection.playQueue(YouTubeQueue(immediateEndpoint))
                                    } else {
                                        coroutineScope.launch {
                                            isResolvingRadio = true
                                            try {
                                                val resolvedPage = resolveRemoteArtistPage(
                                                    listOf(
                                                        artistPage?.artist?.id,
                                                        artistPage?.artist?.channelId,
                                                        viewModel.artistId,
                                                        libraryArtist?.artist?.channelId,
                                                    )
                                                )
                                                val radioEndpoint = resolvedPage?.artist?.radioEndpoint
                                                if (radioEndpoint == null) {
                                                    Toast.makeText(
                                                        context,
                                                        R.string.recommendation_unavailable,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    connection.playQueue(YouTubeQueue(radioEndpoint))
                                                }
                                            } finally {
                                                isResolvingRadio = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isResolvingRadio,
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.radio),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    remoteSongs.shuffled().firstOrNull()
                                        ?.let { playerConnection?.playYTItem(it) }
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.shuffle),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }


            item {
                AnimatedVisibility(
                    visible = isLoadingRemote,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    FridaLoadingIndicator(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }


            remoteSections.forEachIndexed { sectionIndex, section ->
                val songs = section.items.filterIsInstance<SongItem>()
                val albums = section.items.filterIsInstance<AlbumItem>()
                val others = section.items.filter { it !is SongItem && it !is AlbumItem }

                if (section.items.isNotEmpty()) {
                    item(key = "section_title_${sectionIndex}_${section.title}") {
                        HomeSectionHeader(
                            title = section.title,
                            onSeeAll = section.moreEndpoint?.let { endpoint ->
                                {
                                    onBrowseClick(
                                        endpoint,
                                        section.title,
                                        if (section.items.all { it is SongItem }) 50 else null,
                                        true,
                                    )
                                }
                            },
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                        )
                    }
                }


                if (songs.isNotEmpty()) {
                    itemsIndexed(
                        items = songs,
                        key = { songIndex, song ->
                            "remote_song_${sectionIndex}_${section.title}_${song.id}_$songIndex"
                        },
                    ) { _, song ->
                        RemoteSongRow(
                            song = song,
                            onClick = { playerConnection?.playYTItem(song) },
                            onMoreClick = { menuContext = song.toSongActionContext() },
                        )
                    }
                }

                // Albums / playlists row
                if (albums.isNotEmpty() || others.isNotEmpty()) {
                    item(key = "section_row_${sectionIndex}_${section.title}") {
                        val rowItems: List<YTItem> = albums + others
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(
                                items = rowItems,
                                key = { itemIndex, item ->
                                    "remote_item_${sectionIndex}_${item.id}_$itemIndex"
                                },
                            ) { _, item ->
                                RemoteItemCard(
                                    item = item,
                                    onClick = { onRemoteItemClick(item) },
                                    onMoreClick = { remoteMenuItem = item },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (description != null) {
                item {
                    Text(
                        text = stringResource(R.string.about_artist),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (remoteSections.isEmpty() && libraryAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.albums),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(libraryAlbums, key = { it.id }) { album ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { onAlbumClick(album) },
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AsyncImage(
                                    model = album.album.thumbnailUrl?.resize(width = 300),
                                    contentDescription = album.album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Text(
                                    text = album.album.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (remoteSections.isEmpty() && librarySongs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.library_songs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                items(librarySongs, key = { "local_${it.song.id}" }) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .universalMediaClickable(
                                onClick = { onSongClick(song, librarySongs) },
                                onLongClick = { menuContext = song.toSongActionContext() },
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
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
                        Text(
                            text = song.song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        SongOptionsButton(
                            onClick = { menuContext = song.toSongActionContext() },
                        )
                    }
                }
            }
        }
        UniversalSongActionsHost(
            context = menuContext,
            onDismiss = { menuContext = null },
        )
        UniversalYTItemActionsHost(
            item = remoteMenuItem,
            onDismiss = { remoteMenuItem = null },
            onOpen = { remoteMenuItem?.let(onRemoteItemClick) },
        )
    }
}

private fun resolveArtistShareLink(
    remoteArtist: ArtistItem?,
    fallbackChannelId: String?,
): String? {
    remoteArtist?.shareLink
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { return it }

    val channelId = listOf(remoteArtist?.channelId, fallbackChannelId)
        .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
        ?: return null
    return "https://music.youtube.com/channel/$channelId"
}

@Composable
private fun RemoteSongRow(
    song: SongItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .universalMediaClickable(onClick = onClick, onLongClick = onMoreClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = song.thumbnail.resize(width = 96),
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
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
        SongOptionsButton(onClick = onMoreClick)
    }
}

@Composable
private fun RemoteItemCard(
    item: YTItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val subtitle = when (item) {
        is AlbumItem -> item.year?.toString() ?: ""
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is PlaylistItem -> item.author?.name ?: item.songCountText.orEmpty()
        is ArtistItem -> ""
    }

    Column(
        modifier = Modifier
            .width(150.dp)
            .universalMediaClickable(onClick = onClick, onLongClick = onMoreClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(150.dp)) {
            AsyncImage(
                model = item.thumbnail?.resize(width = 300),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (item !is ArtistItem) {
                SongOptionsButton(
                    onClick = onMoreClick,
                    modifier = Modifier.align(Alignment.TopEnd),
                    iconColor = Color.White,
                    containerColor = Color.Black.copy(alpha = 0.38f),
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
