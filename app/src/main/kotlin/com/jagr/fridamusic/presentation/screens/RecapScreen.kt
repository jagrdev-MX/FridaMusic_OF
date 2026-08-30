package com.jagr.fridamusic.presentation.screens

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Album as LocalAlbum
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.SongWithStats
import com.jagr.fridamusic.recap.RecapPeriodType
import com.jagr.fridamusic.utils.ComposeToImage
import com.jagr.fridamusic.viewmodels.RecapUiState
import com.jagr.fridamusic.viewmodels.RecapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RecapPage {
    INTRO,
    TIME,
    ACTIVITY,
    TOP_SONGS,
    SONG_SPOTLIGHT,
    ARTIST_SPOTLIGHT,
    TOP_ARTISTS,
    TOP_ALBUMS,
    PERSONALITY,
    SUMMARY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    onBack: () -> Unit,
    onSongClick: (SongWithStats) -> Unit,
    initialPeriod: String? = null,
    initialOffset: Int? = null,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val offset by viewModel.periodOffset.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialPeriod, initialOffset) {
        viewModel.applyDeepLink(initialPeriod, initialOffset)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.fridamusic_recap), fontWeight = FontWeight.Bold) },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecapPeriodType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { viewModel.selectType(type) },
                            label = { Text(type.label()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = viewModel::previousPeriod,
                        enabled = state.canGoToPreviousPeriod,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.previous_period),
                        )
                    }
                    Text(state.range.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = viewModel::nextPeriod, enabled = offset > 0) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.next_period),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!state.hasData) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.recap_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val pages = remember(state) { state.pages() }
            val pagerState = rememberPagerState(pageCount = { pages.size })
            val haptics = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                    RecapStoryPage(
                        page = pages[pageIndex],
                        state = state,
                        onSongClick = onSongClick,
                        onShare = {
                            scope.launch {
                                runCatching { shareRecap(context, state) }
                                    .onFailure {
                                        Toast.makeText(context, R.string.recap_share_failed, Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index <= pagerState.currentPage) Color.White
                                    else Color.White.copy(alpha = 0.28f),
                                ),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(64.dp)
                        .fillMaxHeight()
                        .clickable(enabled = pagerState.currentPage > 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(64.dp)
                        .fillMaxHeight()
                        .clickable(enabled = pagerState.currentPage < pages.lastIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                )
            }
        }
    }
}

@Composable
private fun RecapStoryPage(
    page: RecapPage,
    state: RecapUiState,
    onSongClick: (SongWithStats) -> Unit,
    onShare: () -> Unit,
) {
    val colors = when (page.ordinal % 4) {
        0 -> listOf(Color(0xFF312E81), Color(0xFFBE185D))
        1 -> listOf(Color(0xFF0F766E), Color(0xFF1E3A8A))
        2 -> listOf(Color(0xFF7C2D12), Color(0xFF581C87))
        else -> listOf(Color(0xFF111827), Color(0xFF4C1D95))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors))
            .padding(horizontal = 34.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (page) {
            RecapPage.INTRO -> RecapHeadline(
                eyebrow = stringResource(R.string.fridamusic_recap).uppercase(),
                headline = state.range.introText(),
                supporting = state.range.label,
                icon = Icons.Rounded.AutoAwesome,
            )
            RecapPage.TIME -> {
                val minutes by animateIntAsState(
                    targetValue = (state.totalPlayTimeMs / 60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    label = "recap_minutes",
                )
                RecapHeadline(
                    eyebrow = stringResource(R.string.recap_time_title),
                    headline = minutes.toString(),
                    supporting = stringResource(R.string.recap_minutes_listened),
                    icon = Icons.Rounded.Headphones,
                )
            }
            RecapPage.ACTIVITY -> RecapActivity(state)
            RecapPage.TOP_SONGS -> RecapRanking(
                title = stringResource(R.string.recap_top_songs),
                rows = state.topSongs.map { Triple(it.title, it.artistName.orEmpty(), it.thumbnailUrl) },
            )
            RecapPage.SONG_SPOTLIGHT -> state.topSongs.firstOrNull()?.let { song ->
                RecapSpotlight(
                    label = stringResource(R.string.recap_top_song),
                    title = song.title,
                    subtitle = stringResource(R.string.stat_plays, song.songCountListened),
                    artworkUrl = song.thumbnailUrl,
                    onClick = { onSongClick(song) },
                )
            }
            RecapPage.ARTIST_SPOTLIGHT -> state.topArtists.firstOrNull()?.let { artist ->
                RecapSpotlight(
                    label = stringResource(R.string.recap_top_artist),
                    title = artist.title,
                    subtitle = stringResource(R.string.recap_artist_spotlight_subtitle),
                    artworkUrl = artist.thumbnailUrl,
                )
            }
            RecapPage.TOP_ARTISTS -> RecapRanking(
                title = stringResource(R.string.recap_top_artists),
                rows = state.topArtists.map { Triple(it.title, "", it.thumbnailUrl) },
            )
            RecapPage.TOP_ALBUMS -> RecapRanking(
                title = stringResource(R.string.recap_top_albums),
                rows = state.topAlbums.map { Triple(it.title, it.artists.joinToString(", ") { artist -> artist.name }, it.thumbnailUrl) },
            )
            RecapPage.PERSONALITY -> state.personality?.let { personality ->
                RecapHeadline(
                    eyebrow = stringResource(R.string.recap_personality),
                    headline = personality.name,
                    supporting = personality.description,
                    icon = Icons.Rounded.AutoAwesome,
                )
            }
            RecapPage.SUMMARY -> RecapSummary(state, onShare)
        }
    }
}

@Composable
private fun RecapHeadline(
    eyebrow: String,
    headline: String,
    supporting: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(58.dp))
        Text(eyebrow, color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(
            headline,
            color = Color.White,
            fontSize = 52.sp,
            lineHeight = 56.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            supporting,
            color = Color.White.copy(alpha = 0.84f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecapActivity(state: RecapUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.recap_activity), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
        RecapMetric(stringResource(R.string.recap_total_plays), state.totalPlays.toString(), Icons.Rounded.MusicNote)
        RecapMetric(stringResource(R.string.unique_songs), state.uniqueSongs.toString(), Icons.Rounded.Headphones)
        RecapMetric(stringResource(R.string.unique_artists), state.uniqueArtists.toString(), Icons.Rounded.Person)
        RecapMetric(stringResource(R.string.unique_albums), state.uniqueAlbums.toString(), Icons.Rounded.Album)
    }
}

@Composable
private fun RecapMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text(label, modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.82f))
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecapRanking(title: String, rows: List<Triple<String, String, String?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
        rows.take(5).forEachIndexed { index, (name, subtitle, artwork) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapSpotlight(
    label: String,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold)
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(34.dp))
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        )
        Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(subtitle, color = Color.White.copy(alpha = 0.78f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecapSummary(state: RecapUiState, onShare: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.recap_summary), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
        Text(state.range.label, color = Color.White.copy(alpha = 0.8f))
        RecapMetric(stringResource(R.string.recap_time_title), state.listeningTime(), Icons.Rounded.Headphones)
        state.topSongs.firstOrNull()?.let { RecapMetric(stringResource(R.string.recap_top_song), it.title, Icons.Rounded.MusicNote) }
        state.topArtists.firstOrNull()?.let { RecapMetric(stringResource(R.string.recap_top_artist), it.title, Icons.Rounded.Person) }
        Button(onClick = onShare) {
            Icon(Icons.Rounded.IosShare, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.share_recap))
        }
    }
}

private fun RecapUiState.pages(): List<RecapPage> = buildList {
    add(RecapPage.INTRO)
    if (totalPlayTimeMs > 0) add(RecapPage.TIME)
    if (totalPlays > 0) add(RecapPage.ACTIVITY)
    if (topSongs.isNotEmpty()) {
        add(RecapPage.TOP_SONGS)
        add(RecapPage.SONG_SPOTLIGHT)
    }
    if (topArtists.isNotEmpty()) {
        add(RecapPage.ARTIST_SPOTLIGHT)
        add(RecapPage.TOP_ARTISTS)
    }
    if (topAlbums.isNotEmpty()) add(RecapPage.TOP_ALBUMS)
    if (personality != null) add(RecapPage.PERSONALITY)
    add(RecapPage.SUMMARY)
}

@Composable
private fun RecapPeriodType.label(): String = when (this) {
    RecapPeriodType.WEEK -> stringResource(R.string.recap_week)
    RecapPeriodType.MONTH -> stringResource(R.string.recap_month)
    RecapPeriodType.YEAR -> stringResource(R.string.recap_year)
}

@Composable
private fun com.jagr.fridamusic.recap.RecapRange.introText(): String = when (type) {
    RecapPeriodType.WEEK -> stringResource(R.string.recap_intro_week)
    RecapPeriodType.MONTH -> stringResource(R.string.recap_intro_month)
    RecapPeriodType.YEAR -> stringResource(R.string.recap_intro_year)
}

private fun RecapUiState.listeningTime(): String {
    val minutes = totalPlayTimeMs / 60_000L
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

private suspend fun shareRecap(context: android.content.Context, state: RecapUiState) {
    val bitmap = ComposeToImage.createRecapImage(
        context = context,
        data = ComposeToImage.RecapImageData(
            periodLabel = state.range.label,
            listeningTime = state.listeningTime(),
            totalPlays = context.getString(R.string.recap_total_plays_count, state.totalPlays),
            topSong = state.topSongs.firstOrNull()?.title,
            topArtist = state.topArtists.firstOrNull()?.title,
            topAlbum = state.topAlbums.firstOrNull()?.title,
            personality = state.personality?.name,
            artworkUrl = state.topSongs.firstOrNull()?.thumbnailUrl,
        ),
    )
    val uri = withContext(Dispatchers.IO) {
        ComposeToImage.saveBitmapToCache(context, bitmap, "FridaMusic_Recap_${state.range.label}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_recap)))
}
