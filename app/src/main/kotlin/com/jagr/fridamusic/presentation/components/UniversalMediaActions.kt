package com.jagr.fridamusic.presentation.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.SpeedDialItem
import com.jagr.fridamusic.db.entities.ArtistEntity
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.playSong
import com.jagr.fridamusic.presentation.playYTItem
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LocalCollectionActionContext(
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val circularThumbnail: Boolean,
    val onOpen: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.universalMediaClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        onClick = onClick,
        onLongClick = onLongClick?.let { longClick -> {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            longClick()
        } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalYTItemActionsHost(
    item: YTItem?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
) {
    val selectedItem = item ?: return
    if (selectedItem is SongItem) {
        UniversalSongActionsHost(
            context = selectedItem.toSongActionContext(),
            onDismiss = onDismiss,
        )
        return
    }
    if (selectedItem is ArtistItem) {
        UniversalArtistActionsSheet(
            artist = selectedItem,
            onDismiss = onDismiss,
        )
        return
    }

    val androidContext = LocalContext.current
    val database = LocalPlayerConnection.current?.database
    val scope = rememberCoroutineScope()
    val pinnedFlow = remember(database, selectedItem.id) {
        database?.speedDialDao?.isPinned(selectedItem.id) ?: flowOf(false)
    }
    val isPinned by pinnedFlow.collectAsState(initial = false)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val subtitle = when (selectedItem) {
        is AlbumItem -> selectedItem.artists?.joinToString(", ") { it.name }
            ?: stringResource(R.string.album_text)
        is ArtistItem -> stringResource(R.string.artists)
        is PlaylistItem -> selectedItem.author?.name ?: stringResource(R.string.playlists)
        is SongItem -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(
                    model = selectedItem.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(if (selectedItem is ArtistItem) CircleShape else RoundedCornerShape(14.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedItem.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            UniversalMediaActionRow(
                icon = Icons.Rounded.OpenInNew,
                label = stringResource(R.string.open),
                onClick = {
                    onDismiss()
                    onOpen()
                },
            )
            onPlay?.let { play ->
                UniversalMediaActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.play),
                    onClick = {
                        onDismiss()
                        play()
                    },
                )
            }
            if (database != null) {
                UniversalMediaActionRow(
                    icon = Icons.Rounded.PushPin,
                    label = stringResource(
                        if (isPinned) R.string.unpin_from_speed_dial else R.string.pin_to_speed_dial,
                    ),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            if (isPinned) {
                                database.speedDialDao.delete(selectedItem.id)
                            } else {
                                database.speedDialDao.insert(SpeedDialItem.fromYTItem(selectedItem))
                            }
                        }
                    },
                )
            }
            UniversalMediaActionRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.share),
                onClick = {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, selectedItem.shareLink)
                    }
                    androidContext.startActivity(Intent.createChooser(intent, null))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversalArtistActionsSheet(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    val androidContext = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val database = playerConnection?.database
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val storedArtistFlow = remember(database, artist.id) {
        database?.artist(artist.id) ?: flowOf(null)
    }
    val storedArtist by storedArtistFlow.collectAsState(initial = null)
    val isFollowing = storedArtist?.artist?.bookmarkedAt != null

    fun unavailable() {
        Toast.makeText(
            androidContext,
            R.string.recommendation_unavailable,
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun playArtist(shuffle: Boolean) {
        val connection = playerConnection
        if (connection == null) {
            unavailable()
            return
        }
        scope.launch {
            val initialEndpoint = if (shuffle) {
                artist.shuffleEndpoint ?: artist.radioEndpoint ?: artist.playEndpoint
            } else {
                artist.playEndpoint ?: artist.radioEndpoint ?: artist.shuffleEndpoint
            }

            // Artists reconstructed from Room (for example, Keep Listening) do not
            // carry remote play endpoints. Their listening history is the most reliable
            // source and avoids treating a valid local artist as an unavailable remote
            // recommendation.
            if (initialEndpoint == null && database != null) {
                val historySongs = database.artistSongsForPlayback(artist.id).first()
                val queue = if (shuffle) historySongs.shuffled() else historySongs
                queue.firstOrNull()?.let { firstSong ->
                    connection.playSong(firstSong, queue)
                    onDismiss()
                    return@launch
                }
            }

            val resolvedPage = if (initialEndpoint == null) {
                listOfNotNull(artist.id, artist.channelId)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .firstNotNullOfOrNull { artistId ->
                        YouTube.artist(artistId).getOrNull()
                    }
            } else null
            val endpoint = initialEndpoint ?: resolvedPage?.artist?.let {
                if (shuffle) {
                    it.shuffleEndpoint ?: it.radioEndpoint ?: it.playEndpoint
                } else {
                    it.playEndpoint ?: it.radioEndpoint ?: it.shuffleEndpoint
                }
            }
            if (endpoint != null) {
                connection.playQueue(YouTubeQueue(endpoint))
                onDismiss()
                return@launch
            }

            val remoteSongs = resolvedPage?.sections
                .orEmpty()
                .flatMap { it.items }
                .filterIsInstance<SongItem>()
            val fallbackSong = if (shuffle) remoteSongs.randomOrNull() else remoteSongs.firstOrNull()
            if (fallbackSong == null) {
                unavailable()
            } else {
                connection.playYTItem(fallbackSong)
            }
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(
                    model = artist.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape),
                )
                Text(
                    text = artist.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            UniversalMediaActionRow(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(R.string.play),
                onClick = { playArtist(shuffle = false) },
            )
            UniversalMediaActionRow(
                icon = Icons.Rounded.Shuffle,
                label = stringResource(R.string.shuffle),
                onClick = { playArtist(shuffle = true) },
            )
            UniversalMediaActionRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.share),
                onClick = {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            artist.shareLink,
                        )
                    }
                    androidContext.startActivity(Intent.createChooser(intent, null))
                },
            )
            if (database != null) {
                UniversalMediaActionRow(
                    icon = if (isFollowing) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                    label = stringResource(
                        if (isFollowing) R.string.unfollow_artist else R.string.follow_artist,
                    ),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val current = database.getArtistById(artist.id)
                            val shouldFollow = current?.bookmarkedAt == null
                            val channelId = artist.channelId
                                ?: current?.channelId
                                ?: YouTube.getChannelId(artist.id)
                            database.withTransaction {
                                val latest = getArtistById(artist.id)
                                if (latest == null) {
                                    insert(
                                        ArtistEntity(
                                            id = artist.id,
                                            name = artist.title,
                                            thumbnailUrl = artist.thumbnail,
                                            channelId = channelId,
                                        ).localToggleLike(),
                                    )
                                } else {
                                    update(latest.localToggleLike())
                                }
                            }
                            if (channelId.isNotBlank()) {
                                YouTube.subscribeChannel(channelId, shouldFollow)
                            }
                            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                                onDismiss()
                            }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalLocalCollectionActionsHost(
    context: LocalCollectionActionContext?,
    onDismiss: () -> Unit,
) {
    val selectedContext = context ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(
                    model = selectedContext.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(
                            if (selectedContext.circularThumbnail) CircleShape
                            else RoundedCornerShape(14.dp),
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedContext.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    selectedContext.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            UniversalMediaActionRow(
                icon = Icons.Rounded.OpenInNew,
                label = stringResource(R.string.open),
                onClick = {
                    onDismiss()
                    selectedContext.onOpen()
                },
            )
        }
    }
}

@Composable
private fun UniversalMediaActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
