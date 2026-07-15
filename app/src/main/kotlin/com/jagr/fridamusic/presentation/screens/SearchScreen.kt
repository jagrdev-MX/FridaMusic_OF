package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.OnlineSearchSuggestionViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem


@Composable
fun SearchScreen(
    onSearchSubmit: (String) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    var text by remember { mutableStateOf("") }
    val viewState by viewModel.viewState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submit(query: String) {
        if (query.isBlank()) return
        viewModel.saveSearch(query)
        onSearchSubmit(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.query.value = it
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Buscar canciones, artistas, álbumes…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            if (text.isBlank()) {
                items(viewState.history, key = { it.id }) { history ->
                    SuggestionRow(
                        icon = Icons.Rounded.History,
                        text = history.query,
                        onClick = {
                            text = history.query
                            submit(history.query)
                        },
                        onFillClick = { text = history.query; viewModel.query.value = history.query },
                    )
                }
            } else {
                items(viewState.suggestions) { suggestion ->
                    SuggestionRow(
                        icon = Icons.Rounded.Search,
                        text = suggestion,
                        onClick = { submit(suggestion) },
                        onFillClick = { text = suggestion; viewModel.query.value = suggestion },
                    )
                }

                if (viewState.items.isNotEmpty()) {
                    item {
                        Text(
                            text = "Resultados directos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(viewState.items, key = { it.id }) { item ->
                        YTItemRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    onFillClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onFillClick) {
            Icon(
                imageVector = Icons.Rounded.NorthWest,
                contentDescription = "Completar búsqueda",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun YTItemRow(item: YTItem, onClick: () -> Unit) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: "Álbum"
        is PlaylistItem -> item.author?.name ?: "Playlist"
        is ArtistItem -> "Artista"
        else -> null
    }
    val isRound = item is ArtistItem

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = item.thumbnail?.resize(width = 96),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(if (isRound) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
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
}