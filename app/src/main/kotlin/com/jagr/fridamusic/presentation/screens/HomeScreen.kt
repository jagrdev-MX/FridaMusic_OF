package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.presentation.theme.EchoLavender
import com.jagr.fridamusic.presentation.theme.FridaPurple
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumnHome(
            quickPicks = quickPicks,
            homePage = homePage,
            isLoading = isLoading,
            selectedChip = selectedChip,
            onChipClick = { viewModel.toggleChip(it) },
            onSongClick = onSongClick,
            onItemClick = onItemClick,
            onSettingsClick = onSettingsClick,
            onHistoryClick = onHistoryClick,
        )
    }
}

@Composable
private fun LazyColumnHome(
    quickPicks: List<Song>?,
    homePage: HomePage?,
    isLoading: Boolean,
    selectedChip: HomePage.Chip?,
    onChipClick: (HomePage.Chip?) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { HomeHeader(onHistoryClick = onHistoryClick, onSettingsClick = onSettingsClick) }
        item {
            MoodChipsRow(
                chips = homePage?.chips,
                selectedChip = selectedChip,
                onChipClick = onChipClick,
            )
        }

        if (!quickPicks.isNullOrEmpty()) {
            item {
                HomeSection(title = stringResource(R.string.quick_picks), subtitle = stringResource(R.string.quick_picks_subtitle)) {
                    items(quickPicks, key = { it.song.id }) { song ->
                        SongCard(song = song, onClick = { onSongClick(song, quickPicks) })
                    }
                }
            }
        }

        homePage?.sections?.forEach { section ->
            item(key = section.title) {
                HomeSection(title = section.title, subtitle = section.label) {
                    items(section.items, key = { it.id }) { ytItem ->
                        YTItemCard(item = ytItem, onClick = { onItemClick(ytItem) })
                    }
                }
            }
        }

        if (isLoading && quickPicks.isNullOrEmpty() && homePage == null) {
            item { HomeLoadingState() }
        }

        if (!isLoading && quickPicks.isNullOrEmpty() && homePage == null) {
            item { HomeEmptyState() }
        }
    }
}

@Composable
private fun HomeHeader(onHistoryClick: () -> Unit, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FridaMusic",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onHistoryClick) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = stringResource(R.string.history),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoodChipsRow(
    chips: List<HomePage.Chip>?,
    selectedChip: HomePage.Chip?,
    onChipClick: (HomePage.Chip?) -> Unit,
) {
    val displayChips = chips?.ifEmpty { null }
        ?: listOf(
            HomePage.Chip(stringResource(R.string.mood_para_ti), null, null),
            HomePage.Chip(stringResource(R.string.mood_enfoque), null, null),
            HomePage.Chip(stringResource(R.string.mood_fiesta), null, null),
            HomePage.Chip(stringResource(R.string.mood_relax), null, null),
            HomePage.Chip(stringResource(R.string.mood_entrenar), null, null),
        )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(displayChips) { chip ->
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
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
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
private fun HomeSection(
    title: String,
    subtitle: String?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoLavender,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private const val CARD_IMAGE_SIZE_DP = 140

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    MediaCard(
        title = song.song.title,
        subtitle = song.artists.joinToString(", ") { it.name },
        imageUrl = (song.song.thumbnailUrl ?: "https://i.ytimg.com/vi/${song.song.id}/hqdefault.jpg")
            .resize(width = CARD_IMAGE_SIZE_DP * 2),
        onClick = onClick,
    )
}

@Composable
private fun YTItemCard(item: YTItem, onClick: () -> Unit) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: stringResource(R.string.albums)
        is PlaylistItem -> item.author?.name ?: stringResource(R.string.playlists)
        is ArtistItem -> stringResource(R.string.artists)
        else -> null
    }
    val isRound = item is ArtistItem
    MediaCard(
        title = item.title,
        subtitle = subtitle,
        imageUrl = item.thumbnail?.resize(width = CARD_IMAGE_SIZE_DP * 2) ?: "",
        round = isRound,
        onClick = onClick,
    )
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String?,
    imageUrl: String,
    round: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(CARD_IMAGE_SIZE_DP.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(CARD_IMAGE_SIZE_DP.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
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

@Composable
private fun HomeLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = EchoLavender)
        Text(
            text = stringResource(R.string.preparing_music),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(FridaPurple.copy(alpha = 0.3f), EchoLavender.copy(alpha = 0.3f))))
        )
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.home_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}