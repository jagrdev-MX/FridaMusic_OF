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
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.YTContentCard
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.viewmodels.NewReleaseViewModel
import com.music.innertube.models.AlbumItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    onAlbumClick: (AlbumItem) -> Unit,
    onPlayAlbum: (AlbumItem) -> Unit,
    onBack: () -> Unit,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val albums by viewModel.newReleaseAlbums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value
    val isPlaying = playerConnection?.isPlaying?.collectAsState()?.value == true
    var menuAlbum by remember { mutableStateOf<AlbumItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.albums_and_singles)) },
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

            albums.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.collection_empty),
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
                        items = albums.distinctBy { album -> album.browseId },
                        key = { album -> album.browseId },
                    ) { album ->
                        YTContentCard(
                            item = album,
                            currentMediaId = mediaMetadata?.id,
                            currentAlbumId = mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            onClick = { onAlbumClick(album) },
                            onPlay = album.playlistId.takeIf(String::isNotBlank)
                                ?.let { { onPlayAlbum(album) } },
                            onMore = { menuAlbum = album },
                            fillMaxWidth = true,
                        )
                    }
                }
            }
        }
    }
    UniversalYTItemActionsHost(
        item = menuAlbum,
        onDismiss = { menuAlbum = null },
        onOpen = { menuAlbum?.let(onAlbumClick) },
        onPlay = menuAlbum
            ?.playlistId
            ?.takeIf(String::isNotBlank)
            ?.let { { menuAlbum?.let(onPlayAlbum) } },
    )
}
