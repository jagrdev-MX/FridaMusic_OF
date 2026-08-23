package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.models.toMediaMetadata
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.SearchBar
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.jagr.fridamusic.viewmodels.OnlineSearchSuggestionViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    navController: NavController,
    onSearchSubmit: (String) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onBack: (() -> Unit)? = null,
    onToggleToLocalSearch: (() -> Unit)? = null,
    pureBlack: Boolean = false,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    var text by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val viewState by viewModel.viewState.collectAsState()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(text) {
        viewModel.query.value = text
    }

    fun submit(query: String) {
        if (query.isBlank()) return
        keyboardController?.hide()
        viewModel.saveSearch(query)
        onSearchSubmit(query)
        active = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        SearchBar(
            query = text,
            onQueryChange = {
                text = it
                active = true
            },
            onSearch = { submit(it) },
            active = active,
            onActiveChange = { active = it },
            onToggleMode = onToggleToLocalSearch,
            toggleContentDescription = stringResource(R.string.search_library),
            toggleIcon = Icons.Rounded.Language,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding() + 140.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewState.history, key = { "history_${it.query}" }) { history ->
                    SuggestionItem(
                        query = history.query,
                        online = false,
                        onClick = {
                            text = history.query
                            submit(history.query)
                        },
                        onDelete = {
                        },
                        onFillTextField = {
                            text = history.query
                            active = true
                        },
                        modifier = Modifier.animateItem(),
                        pureBlack = pureBlack
                    )
                }

                items(viewState.suggestions, key = { "suggestion_$it" }) { query ->
                    SuggestionItem(
                        query = query,
                        online = true,
                        onClick = {
                            text = query
                            submit(query)
                        },
                        onFillTextField = {
                            text = query
                            active = true
                        },
                        modifier = Modifier.animateItem(),
                        pureBlack = pureBlack
                    )
                }

                if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Top Results",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pureBlack) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(viewState.items.distinctBy { it.id }, key = { "item_${it.id}" }) { item ->
                    YTItemRow(
                        item = item,
                        onClick = {
                            keyboardController?.hide()
                            when (item) {
                                is SongItem -> {
                                    playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                                    active = false
                                }
                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                    active = false
                                }
                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                    active = false
                                }
                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                    active = false
                                }
                                else -> onItemClick(item)
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean
) {
    val iconContainerColor = if (pureBlack) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }

    val iconTint = if (pureBlack) {
        Color.White.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = iconContainerColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = if (online) Icons.Rounded.Search else Icons.Rounded.History,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = if (pureBlack) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (!online) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (pureBlack) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(onClick = onFillTextField) {
            Icon(
                imageVector = Icons.Rounded.NorthWest,
                contentDescription = null,
                tint = if (pureBlack) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YTItemRow(
    item: YTItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: stringResource(R.string.albums)
        is PlaylistItem -> item.author?.name ?: stringResource(R.string.playlists)
        is ArtistItem -> stringResource(R.string.artists)
        else -> null
    }
    val isRound = item is ArtistItem

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
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