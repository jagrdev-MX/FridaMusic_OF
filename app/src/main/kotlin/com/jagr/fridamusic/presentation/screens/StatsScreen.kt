package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.OptionStats
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.SongWithStats
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.viewmodels.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onSongClick: (SongWithStats) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val selectedOption by viewModel.selectedOption.collectAsState()
    val indexChips by viewModel.indexChips.collectAsState()

    val totalPlayTime by viewModel.totalPlayTime.collectAsState()
    val allTimePlayTime by viewModel.allTimePlayTime.collectAsState()
    val uniqueSongs by viewModel.uniqueSongsCount.collectAsState()
    val uniqueArtists by viewModel.uniqueArtistsCount.collectAsState()
    val uniqueAlbums by viewModel.uniqueAlbumsCount.collectAsState()
    val allTimeSongs by viewModel.allTimeSongsCount.collectAsState()
    val allTimeArtists by viewModel.allTimeArtistsCount.collectAsState()
    val allTimeAlbums by viewModel.allTimeAlbumsCount.collectAsState()
    val topSongsStats by viewModel.mostPlayedSongsStats.collectAsState()
    val topArtists by viewModel.mostPlayedArtists.collectAsState()
    val topAlbums by viewModel.mostPlayedAlbums.collectAsState()
    val firstEvent by viewModel.firstEvent.collectAsState()

    var contentTab by remember { mutableIntStateOf(0) }
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                PeriodSelector(
                    selectedOption = selectedOption,
                    indexChips = indexChips,
                    onOptionSelected = { viewModel.selectedOption.value = it },
                    onIndexSelected = { viewModel.indexChips.value = it },
                )
            }

            item {
                SummarySection(
                    playTimeMs = totalPlayTime,
                    songCount = uniqueSongs,
                    artistCount = uniqueArtists,
                    albumCount = uniqueAlbums,
                    allTimePlayTimeMs = allTimePlayTime,
                    allTimeSongs = allTimeSongs,
                    allTimeArtists = allTimeArtists,
                    allTimeAlbums = allTimeAlbums,
                    firstEventMs = firstEvent?.event?.timestamp?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }

            item {
                SecondaryScrollableTabRow(
                    selectedTabIndex = contentTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 16.dp,
                ) {
                    Tab(selected = contentTab == 0, onClick = { contentTab = 0 }, text = { Text(stringResource(R.string.most_played_songs)) })
                    Tab(selected = contentTab == 1, onClick = { contentTab = 1 }, text = { Text(stringResource(R.string.most_played_artists)) })
                    Tab(selected = contentTab == 2, onClick = { contentTab = 2 }, text = { Text(stringResource(R.string.most_played_albums)) })
                }
            }

            if (contentTab == 0) {
                topSongItems(
                    songs = topSongsStats,
                    onSongClick = onSongClick,
                    onMoreClick = { menuContext = it.toSongActionContext() },
                )
            } else {
                item(key = "stats_content_$contentTab") {
                    AnimatedContent(
                        targetState = contentTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "content_tab",
                    ) { tab ->
                        when (tab) {
                            1 -> TopArtistsSection(
                                artists = topArtists,
                                onArtistClick = { onNavigateToArtist(it.artist.id) },
                            )
                            2 -> TopAlbumsSection(
                                albums = topAlbums,
                                onAlbumClick = { onNavigateToAlbum(it.album.id) },
                            )
                            else -> {}
                        }
                    }
                }
            }
        }
    }
    UniversalSongActionsHost(
        context = menuContext,
        onDismiss = { menuContext = null },
    )
}

@Composable
private fun PeriodSelector(
    selectedOption: OptionStats,
    indexChips: Int,
    onOptionSelected: (OptionStats) -> Unit,
    onIndexSelected: (Int) -> Unit,
) {
    val options = listOf(
        OptionStats.CONTINUOUS to stringResource(R.string.stat_continuous),
        OptionStats.WEEKS      to stringResource(R.string.stat_weeks),
        OptionStats.MONTHS     to stringResource(R.string.stat_months),
        OptionStats.YEARS      to stringResource(R.string.stat_years),
    )

    val continuousLabels = listOf(
        stringResource(R.string.stat_period_1w),
        stringResource(R.string.stat_period_1m),
        stringResource(R.string.stat_period_3m),
        stringResource(R.string.stat_period_6m),
        stringResource(R.string.stat_period_1y),
        stringResource(R.string.stat_period_all),
    )
    val weekLabels   = (0..11).map { if (it == 0) stringResource(R.string.stat_this_week) else "-${it}w" }
    val monthLabels  = (0..11).map { if (it == 0) stringResource(R.string.stat_this_month) else "-${it}m" }
    val yearLabels   = (0..4).map { if (it == 0) stringResource(R.string.stat_this_year) else "-${it}y" }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(options) { _, (opt, label) ->
                FilterChip(
                    selected = selectedOption == opt,
                    onClick = { onOptionSelected(opt); onIndexSelected(0) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        val subLabels = when (selectedOption) {
            OptionStats.CONTINUOUS -> continuousLabels
            OptionStats.WEEKS      -> weekLabels
            OptionStats.MONTHS     -> monthLabels
            OptionStats.YEARS      -> yearLabels
        }

        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(subLabels) { index, label ->
                FilterChip(
                    selected = indexChips == index,
                    onClick = { onIndexSelected(index) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SummarySection(
    playTimeMs: Long,
    songCount: Int,
    artistCount: Int,
    albumCount: Int,
    allTimePlayTimeMs: Long,
    allTimeSongs: Int,
    allTimeArtists: Int,
    allTimeAlbums: Int,
    firstEventMs: Long?,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = playTimeMs.formatPlayTime(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.stat_total_play_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                if (allTimePlayTimeMs > 0 && playTimeMs != allTimePlayTimeMs) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.stat_all_time, allTimePlayTimeMs.formatPlayTime()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.MusicNote,
                value = songCount.toString(),
                allTime = allTimeSongs.toString(),
                label = stringResource(R.string.songs),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Person,
                value = artistCount.toString(),
                allTime = allTimeArtists.toString(),
                label = stringResource(R.string.artists),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Album,
                value = albumCount.toString(),
                allTime = allTimeAlbums.toString(),
                label = stringResource(R.string.albums),
            )
        }

        firstEventMs?.let { ts ->
            Text(
                text = stringResource(R.string.stat_tracking_since, formatTimestamp(ts)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    allTime: String,
    label: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (value != allTime) {
                Text(
                    text = stringResource(R.string.stat_all_time, allTime),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun LazyListScope.topSongItems(
    songs: List<SongWithStats>,
    onSongClick: (SongWithStats) -> Unit,
    onMoreClick: (SongWithStats) -> Unit,
) {
    val visibleSongs = songs.take(50)
    if (visibleSongs.isEmpty()) {
        item(key = "top_songs_empty") {
            EmptyState(stringResource(R.string.stat_no_data))
        }
        return
    }

    itemsIndexed(
        items = visibleSongs,
        key = { _, song -> song.id },
        contentType = { _, _ -> "top_song" },
    ) { index, song ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(
                    top = if (index == 0) 8.dp else 0.dp,
                    bottom = if (index == visibleSongs.lastIndex) 8.dp else 0.dp,
                ),
        ) {
            RankedSongRow(
                rank = index + 1,
                song = song,
                onClick = { onSongClick(song) },
                onMoreClick = { onMoreClick(song) },
            )
            if (index < visibleSongs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun RankedSongRow(
    rank: Int,
    song: SongWithStats,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (rank <= 3) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            song.artistName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            song.timeListened?.let { ms ->
                Text(
                    text = ms.formatPlayTime(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.stat_plays, song.songCountListened),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SongOptionsButton(onClick = onMoreClick)
    }
}

@Composable
private fun TopArtistsSection(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
) {
    if (artists.isEmpty()) {
        EmptyState(stringResource(R.string.stat_no_data))
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        artists.take(50).forEachIndexed { index, artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artist.artist.thumbnailUrl != null) {
                        AsyncImage(
                            model = artist.artist.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    text = artist.artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(horizontalAlignment = Alignment.End) {
                    artist.timeListened?.let { ms ->
                        if (ms > 0) {
                            Text(
                                text = ms.toLong().formatPlayTime(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.stat_plays, artist.songCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index < artists.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 80.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun TopAlbumsSection(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyState(stringResource(R.string.stat_no_data))
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        albums.take(50).forEachIndexed { index, album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlbumClick(album) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    AsyncImage(
                        model = album.album.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.album.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val artistNames = album.artists.joinToString(", ") { it.name }
                    if (artistNames.isNotBlank()) {
                        Text(
                            text = artistNames,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    album.timeListened?.let { ms ->
                        if (ms > 0) {
                            Text(
                                text = ms.formatPlayTime(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    album.songCountListened?.let { count ->
                        Text(
                            text = stringResource(R.string.stat_plays, count),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (index < albums.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 80.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
    }
}

private fun Long.formatPlayTime(): String {
    val totalMinutes = this / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours >= 24 -> "${hours / 24}d ${hours % 24}h"
        hours > 0   -> "${hours}h ${minutes}m"
        else        -> "${minutes}m"
    }
}


private fun formatTimestamp(ms: Long): String {
    val date = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    return "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
}
