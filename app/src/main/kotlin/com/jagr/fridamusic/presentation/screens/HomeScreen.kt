package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.utils.rememberIsLowEndDevice
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.HomeViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage

@Composable
fun HomeScreen(
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onItemClick: (YTItem) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val quickPicks by viewModel.quickPicks.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()
    val isLowEnd = rememberIsLowEndDevice()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item(key = "header") {
            HomeHeader(
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
            )
        }

        item(key = "chips") {
            MoodChipsRow(
                chips = homePage?.chips,
                selectedChip = selectedChip,
                onChipClick = { viewModel.toggleChip(it) },
            )
        }

        if (!quickPicks.isNullOrEmpty()) {
            item(key = "quickpicks_header") {
                SectionTitle(title = stringResource(R.string.quick_picks), subtitle = stringResource(R.string.quick_picks_subtitle))
            }
            item(key = "quickpicks_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(quickPicks!!, key = { it.song.id }) { song ->
                        WideCard(
                            title = song.song.title,
                            subtitle = song.artists.joinToString(", ") { it.name },
                            imageUrl = (song.song.thumbnailUrl
                                ?: "https://i.ytimg.com/vi/${song.song.id}/hqdefault.jpg")
                                .resize(width = if (isLowEnd) 320 else 480),
                            onClick = { onSongClick(song, quickPicks!!) },
                        )
                    }
                }
            }
        }

        homePage?.sections?.forEach { section ->
            item(key = "section_header_${section.title}") {
                SectionTitle(title = section.title, subtitle = section.label)
            }
            item(key = "section_row_${section.title}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.items, key = { it.id }) { ytItem ->
                        val subtitle = when (ytItem) {
                            is SongItem -> ytItem.artists.joinToString(", ") { it.name }
                            is AlbumItem -> ytItem.artists?.joinToString(", ") { it.name } ?: stringResource(R.string.album_text)
                            is PlaylistItem -> ytItem.author?.name ?: stringResource(R.string.playlists)
                            is ArtistItem -> stringResource(R.string.artist)
                            else -> null
                        }
                        if (ytItem is ArtistItem) {
                            ArtistCard(
                                name = ytItem.title,
                                imageUrl = ytItem.thumbnail?.resize(width = if (isLowEnd) 200 else 300) ?: "",
                                onClick = { onItemClick(ytItem) },
                            )
                        } else {
                            WideCard(
                                title = ytItem.title,
                                subtitle = subtitle,
                                imageUrl = ytItem.thumbnail?.resize(width = if (isLowEnd) 320 else 480) ?: "",
                                onClick = { onItemClick(ytItem) },
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && quickPicks.isNullOrEmpty() && homePage == null) {
            item(key = "loading") { HomeLoadingState() }
        }

        if (!isLoading && quickPicks.isNullOrEmpty() && homePage == null) {
            item(key = "empty") { HomeEmptyState() }
        }
    }
}

@Composable
private fun HomeHeader(
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FridaMusic",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HeaderIconButton(Icons.Rounded.History, stringResource(R.string.history), onHistoryClick)
            HeaderIconButton(Icons.Rounded.CalendarMonth, stringResource(R.string.calendar), {})
            HeaderIconButton(Icons.Rounded.NotificationsNone, stringResource(R.string.notifications), {})
            HeaderIconButton(Icons.Rounded.Settings, stringResource(R.string.settings), onSettingsClick)
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MoodChipsRow(
    chips: List<HomePage.Chip>?,
    selectedChip: HomePage.Chip?,
    onChipClick: (HomePage.Chip?) -> Unit,
) {
    val displayChips = chips?.ifEmpty { null } ?: listOf(
        HomePage.Chip(stringResource(R.string.podcasts), null, null),
        HomePage.Chip(stringResource(R.string.energy), null, null),
        HomePage.Chip(stringResource(R.string.mood_entrenar), null, null),
        HomePage.Chip(stringResource(R.string.mood_relax), null, null),
        HomePage.Chip(stringResource(R.string.mood_fiesta), null, null),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(displayChips, key = { it.title }) { chip ->
            val isSelected = chip == selectedChip
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(enabled = chip.endpoint != null) {
                    onChipClick(if (isSelected) null else chip)
                },
            ) {
                Text(
                    text = chip.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String?) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun WideCard(
    title: String,
    subtitle: String?,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f),
                        ),
                        startY = 60f,
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    name: String,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
        )
    }
}

@Composable
private fun HomeEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}