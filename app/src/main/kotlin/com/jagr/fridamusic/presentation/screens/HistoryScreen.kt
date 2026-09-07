package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.constants.HistorySource
import com.jagr.fridamusic.db.entities.EventWithSong
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.MarqueeText
import com.jagr.fridamusic.presentation.components.MediaArtworkActionButton
import com.jagr.fridamusic.presentation.components.MediaPlaybackIndicator
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.presentation.components.universalMediaClickable
import com.jagr.fridamusic.viewmodels.DateAgo
import com.jagr.fridamusic.viewmodels.HistoryViewModel
import com.music.innertube.models.SongItem
import com.music.innertube.pages.HistoryPage
import androidx.compose.material.icons.rounded.PlayArrow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onLocalSongClick: (Song, List<Song>) -> Unit,
    onRemoteSongClick: (SongItem) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val source by viewModel.historySource.collectAsState()
    val localEvents by viewModel.events.collectAsState()
    val remotePage by viewModel.historyPage.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val currentMediaId = playerConnection?.mediaMetadata?.collectAsState()?.value?.id
    val isPlaying = playerConnection?.isEffectivelyPlaying?.collectAsState()?.value == true
    val displayedLocalEvents = remember(localEvents, currentMediaId) {
        localEvents.withCurrentSongFirst(currentMediaId)
    }
    val displayedRemoteSections = remember(remotePage?.sections, currentMediaId) {
        remotePage?.sections.orEmpty().withCurrentSongFirst(currentMediaId)
    }
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }
    val pagerState = rememberPagerState(
        initialPage = if (source == HistorySource.LOCAL) 0 else 1,
        pageCount = { 2 },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.historySource.value = if (page == 0) HistorySource.LOCAL else HistorySource.REMOTE
                if (page == 1) viewModel.fetchRemoteHistory()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.local_history)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.youtube_music_history)) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> {
                    val queue = displayedLocalEvents.values.flatten().map(EventWithSong::song)
                    if (queue.isEmpty()) {
                        HistoryEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            displayedLocalEvents.forEach { (dateAgo, events) ->
                                item(key = "local_header_${dateAgo.key()}") {
                                    HistorySectionTitle(dateAgo.label())
                                }
                                items(
                                    items = events,
                                    key = { event -> "${dateAgo.key()}_${event.song.id}" },
                                ) { event ->
                                    val isCurrent = currentMediaId == event.song.id
                                    HistorySongRow(
                                        title = event.song.title,
                                        subtitle = event.song.artists.joinToString(", ") { it.name },
                                        artworkUrl = event.song.thumbnailUrl,
                                        isCurrent = isCurrent,
                                        isPlaying = isPlaying,
                                        onClick = {
                                            if (isCurrent) playerConnection?.togglePlayPause()
                                            else onLocalSongClick(event.song, queue)
                                        },
                                        onMoreClick = { menuContext = event.song.toSongActionContext() },
                                    )
                                }
                            }
                        }
                    }
                }
                    1 -> {
                    val sections = displayedRemoteSections
                    when {
                        remotePage == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        sections.isEmpty() -> HistoryEmptyState()
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            sections.forEachIndexed { sectionIndex, section ->
                                item(key = "remote_header_${sectionIndex}_${section.title}") {
                                    HistorySectionTitle(section.title)
                                }
                                items(
                                    items = section.songs,
                                    key = { song -> "remote_${sectionIndex}_${song.id}" },
                                ) { song ->
                                    val isCurrent = currentMediaId == song.id
                                    HistorySongRow(
                                        title = song.title,
                                        subtitle = song.artists.joinToString(", ") { it.name },
                                        artworkUrl = song.thumbnail,
                                        isCurrent = isCurrent,
                                        isPlaying = isPlaying,
                                        onClick = {
                                            if (isCurrent) playerConnection?.togglePlayPause()
                                            else onRemoteSongClick(song)
                                        },
                                        onMoreClick = { menuContext = song.toSongActionContext() },
                                    )
                                }
                            }
                        }
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
private fun HistorySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun HistorySongRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .universalMediaClickable(onClick = onClick, onLongClick = onMoreClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isCurrent) {
                    MediaPlaybackIndicator(
                        isPlaying = isPlaying,
                        onClick = onClick,
                        modifier = Modifier.align(Alignment.Center),
                        buttonSize = 40.dp,
                        indicatorSize = 23.dp,
                    )
                } else {
                    MediaArtworkActionButton(
                        icon = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        onClick = onClick,
                        modifier = Modifier.align(Alignment.Center),
                        buttonSize = 40.dp,
                        iconSize = 23.dp,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SongOptionsButton(onClick = onMoreClick)
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun HistoryEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.history_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DateAgo.label(): String = when (this) {
    DateAgo.Today -> stringResource(R.string.today)
    DateAgo.Yesterday -> stringResource(R.string.yesterday)
    DateAgo.ThisWeek -> stringResource(R.string.this_week)
    DateAgo.LastWeek -> stringResource(R.string.last_week)
    is DateAgo.Other -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
}

private fun DateAgo.key(): String = when (this) {
    DateAgo.Today -> "today"
    DateAgo.Yesterday -> "yesterday"
    DateAgo.ThisWeek -> "this_week"
    DateAgo.LastWeek -> "last_week"
    is DateAgo.Other -> date.toString()
}

private fun Map<DateAgo, List<EventWithSong>>.withCurrentSongFirst(
    mediaId: String?,
): Map<DateAgo, List<EventWithSong>> {
    val currentEvent = mediaId?.let { id ->
        values.asSequence().flatten().firstOrNull { it.song.id == id }
    } ?: return this

    return buildMap {
        put(
            DateAgo.Today,
            listOf(currentEvent) +
                this@withCurrentSongFirst[DateAgo.Today].orEmpty().filterNot { it.song.id == mediaId },
        )
        this@withCurrentSongFirst.forEach { (period, events) ->
            if (period != DateAgo.Today) {
                events.filterNot { it.song.id == mediaId }
                    .takeIf { it.isNotEmpty() }
                    ?.let { put(period, it) }
            }
        }
    }
}

private fun List<HistoryPage.HistorySection>.withCurrentSongFirst(
    mediaId: String?,
): List<HistoryPage.HistorySection> {
    if (isEmpty()) return this
    val currentSong = mediaId?.let { id ->
        asSequence().flatMap { it.songs.asSequence() }.firstOrNull { it.id == id }
    } ?: return this

    val first = first().copy(
        songs = listOf(currentSong) + first().songs.filterNot { it.id == mediaId },
    )
    return listOf(first) + drop(1).mapNotNull { section ->
        section.copy(songs = section.songs.filterNot { it.id == mediaId })
            .takeIf { it.songs.isNotEmpty() }
    }
}
