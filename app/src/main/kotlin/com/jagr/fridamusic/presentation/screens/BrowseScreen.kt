package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.YTContentCard
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.viewmodels.YouTubeBrowseViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    requestedTitle: String?,
    onItemClick: (YTItem) -> Unit,
    onPlayItem: (YTItem) -> Unit,
    onBack: () -> Unit,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value
    val isPlaying = playerConnection?.isPlaying?.collectAsState()?.value == true
    var menuItem by remember { mutableStateOf<YTItem?>(null) }
    val items = result?.items.orEmpty()
        .flatMap { section -> section.items }
        .distinctBy { item -> "${item.javaClass.name}:${item.id}" }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = result?.title?.takeIf(String::isNotBlank)
                        ?: requestedTitle.orEmpty(),
                    maxLines = 1,
                )
            },
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
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    FridaLoadingIndicator()
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (loadFailed) R.string.collection_load_error
                            else R.string.collection_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 136.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 10.dp,
                        end = 16.dp,
                        bottom = 156.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(
                        items = items,
                        key = { item -> "${item.javaClass.name}:${item.id}" },
                    ) { item ->
                        YTContentCard(
                            item = item,
                            currentMediaId = mediaMetadata?.id,
                            currentAlbumId = mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            onClick = { onItemClick(item) },
                            onPlay = item.playAction(onPlayItem),
                            onMore = { menuItem = it },
                            fillMaxWidth = true,
                        )
                    }

                    result?.continuation?.let { continuation ->
                        item(
                            key = "browse_continuation_$continuation",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            LaunchedEffect(continuation) {
                                viewModel.loadMore()
                            }
                            if (isLoadingMore) {
                                FridaLoadingIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    UniversalYTItemActionsHost(
        item = menuItem,
        onDismiss = { menuItem = null },
        onOpen = { menuItem?.let(onItemClick) },
        onPlay = menuItem?.playAction(onPlayItem),
    )
}

internal fun YTItem.playAction(onPlayItem: (YTItem) -> Unit): (() -> Unit)? = when (this) {
    is SongItem -> ({ onPlayItem(this) })
    is AlbumItem -> playlistId.takeIf(String::isNotBlank)?.let { { onPlayItem(this) } }
    is PlaylistItem -> (playEndpoint ?: radioEndpoint ?: shuffleEndpoint)
        ?.let { { onPlayItem(this) } }
    is ArtistItem -> null
}
