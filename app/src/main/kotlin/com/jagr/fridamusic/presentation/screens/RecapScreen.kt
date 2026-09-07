package com.jagr.fridamusic.presentation.screens

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.jagr.fridamusic.presentation.components.MarqueeText
import com.jagr.fridamusic.db.entities.Album as LocalAlbum
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.SongWithStats
import com.jagr.fridamusic.recap.RecapPeriodType
import com.jagr.fridamusic.utils.ComposeToImage
import com.jagr.fridamusic.utils.MemoryDiagnostics
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val navigationWidth = if (maxWidth < 400.dp) 40.dp else 64.dp
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
                        .width(navigationWidth)
                        .fillMaxHeight()
                        .clickable(enabled = pagerState.currentPage > 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(navigationWidth)
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors))
    ) {
        val compact = maxHeight < 560.dp || maxWidth < 360.dp
        val horizontalPadding = when {
            maxWidth < 360.dp -> 20.dp
            maxWidth < 480.dp -> 24.dp
            else -> 34.dp
        }
        val artworkSize = minOf(
            if (compact) 168.dp else 260.dp,
            maxWidth - horizontalPadding * 2,
        )
        // Keeps the pager indicator in its own top band rather than overlaying story content.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = horizontalPadding, end = horizontalPadding, top = 32.dp, bottom = if (compact) 16.dp else 28.dp),
        ) {
            when (page) {
                RecapPage.INTRO -> RecapHeadline(
                    eyebrow = stringResource(R.string.fridamusic_recap).uppercase(),
                    headline = state.range.introText(),
                    supporting = state.range.label,
                    icon = Icons.Rounded.AutoAwesome,
                    compact = compact,
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
                        compact = compact,
                    )
                }
                RecapPage.ACTIVITY -> RecapActivity(state, compact)
                RecapPage.TOP_SONGS -> RecapRanking(
                    title = stringResource(R.string.recap_top_songs),
                    rows = state.topSongs.map { Triple(it.title, it.artistName.orEmpty(), it.thumbnailUrl) },
                    compact = compact,
                )
                RecapPage.SONG_SPOTLIGHT -> state.topSongs.firstOrNull()?.let { song ->
                    RecapSpotlight(
                        label = stringResource(R.string.recap_top_song),
                        title = song.title,
                        subtitle = stringResource(R.string.stat_plays, song.songCountListened),
                        artworkUrl = song.thumbnailUrl,
                        artworkSize = artworkSize,
                        compact = compact,
                        onClick = { onSongClick(song) },
                    )
                }
                RecapPage.ARTIST_SPOTLIGHT -> state.topArtists.firstOrNull()?.let { artist ->
                    RecapSpotlight(
                        label = stringResource(R.string.recap_top_artist),
                        title = artist.title,
                        subtitle = stringResource(R.string.recap_artist_spotlight_subtitle),
                        artworkUrl = artist.thumbnailUrl,
                        artworkSize = artworkSize,
                        compact = compact,
                    )
                }
                RecapPage.TOP_ARTISTS -> RecapRanking(
                    title = stringResource(R.string.recap_top_artists),
                    rows = state.topArtists.map { Triple(it.title, "", it.thumbnailUrl) },
                    compact = compact,
                )
                RecapPage.TOP_ALBUMS -> RecapRanking(
                    title = stringResource(R.string.recap_top_albums),
                    rows = state.topAlbums.map { Triple(it.title, it.artists.joinToString(", ") { artist -> artist.name }, it.thumbnailUrl) },
                    compact = compact,
                )
                RecapPage.PERSONALITY -> state.personality?.let { personality ->
                    RecapHeadline(
                        eyebrow = stringResource(R.string.recap_personality),
                        headline = personality.name,
                        supporting = personality.description,
                        icon = Icons.Rounded.AutoAwesome,
                        compact = compact,
                    )
                }
                RecapPage.SUMMARY -> RecapSummary(state, onShare, compact)
            }
        }
    }
}

@Composable
private fun RecapHeadline(
    eyebrow: String,
    headline: String,
    supporting: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    compact: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (compact) 48.dp else 58.dp))
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        Text(eyebrow, color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Text(
            headline,
            color = Color.White,
            fontSize = if (compact) 40.sp else 52.sp,
            lineHeight = if (compact) 44.sp else 56.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Text(
            supporting,
            color = Color.White.copy(alpha = 0.84f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecapActivity(state: RecapUiState, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.recap_activity),
            color = Color.White,
            fontSize = if (compact) 34.sp else 40.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        RecapMetric(stringResource(R.string.recap_total_plays), state.totalPlays.toString(), Icons.Rounded.MusicNote, compact)
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        RecapMetric(stringResource(R.string.unique_songs), state.uniqueSongs.toString(), Icons.Rounded.Headphones, compact)
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        RecapMetric(stringResource(R.string.unique_artists), state.uniqueArtists.toString(), Icons.Rounded.Person, compact)
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        RecapMetric(stringResource(R.string.unique_albums), state.uniqueAlbums.toString(), Icons.Rounded.Album, compact)
    }
}

@Composable
private fun RecapMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 18.dp else 22.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(if (compact) 12.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MarqueeText(
            text = value,
            modifier = Modifier.weight(1f, fill = false),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = if (compact) 20.sp else 24.sp,
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RecapRanking(title: String, rows: List<Triple<String, String, String?>>, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = if (compact) 32.sp else 38.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        rows.take(5).forEachIndexed { index, (name, subtitle, artwork) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(if (compact) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (compact) 20.sp else 22.sp)
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(if (compact) 40.dp else 48.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (index < minOf(rows.size, 5) - 1) Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        }
    }
}

@Composable
private fun RecapSpotlight(
    label: String,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    artworkSize: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(artworkSize)
                .clip(RoundedCornerShape(if (compact) 24.dp else 34.dp))
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        )
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        MarqueeText(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (compact) 30.sp else 34.sp,
            ),
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecapSummary(state: RecapUiState, onShare: () -> Unit, compact: Boolean) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.recap_summary),
                color = Color.White,
                fontSize = if (compact) 36.sp else 42.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Text(
                state.range.label,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
            RecapMetric(stringResource(R.string.recap_time_title), state.listeningTime(), Icons.Rounded.Headphones, compact)
            state.topSongs.firstOrNull()?.let {
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                RecapMetric(stringResource(R.string.recap_top_song), it.title, Icons.Rounded.MusicNote, compact)
            }
            state.topArtists.firstOrNull()?.let {
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                RecapMetric(stringResource(R.string.recap_top_artist), it.title, Icons.Rounded.Person, compact)
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
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
    MemoryDiagnostics.log("After recap share saved")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_recap)))
}
