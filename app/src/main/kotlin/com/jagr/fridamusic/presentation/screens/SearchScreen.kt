package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.SearchHistory
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.SearchInput
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.presentation.components.universalMediaClickable
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.OnlineSearchSuggestionViewModel
import com.jagr.fridamusic.viewmodels.SearchSuggestionViewState
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
    reselectToken: Int = 0,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    var text by remember { mutableStateOf("") }
    val viewState by viewModel.viewState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var handledReselectToken by remember { mutableIntStateOf(reselectToken) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(reselectToken) {
        val shouldScroll = reselectToken != handledReselectToken
        handledReselectToken = reselectToken
        if (
            shouldScroll &&
            (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)
        ) {
            listState.animateReselectScrollToTop(directAnimationItemLimit = 24)
        }
    }

    fun submit(query: String) {
        if (query.isBlank()) return
        keyboardController?.hide()
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            SearchInput(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.query.value = it
                },
                onSubmit = ::submit,
                onClear = {
                    text = ""
                    viewModel.query.value = ""
                    focusRequester.requestFocus()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }

        SearchSuggestionContent(
            text = text,
            viewState = viewState,
            onSubmit = { query ->
                text = query
                submit(query)
            },
            onFill = { query ->
                text = query
                viewModel.query.value = query
            },
            onItemClick = { item ->
                keyboardController?.hide()
                onItemClick(item)
            },
            onDeleteHistory = viewModel::deleteSearch,
            onClearHistory = viewModel::clearSearchHistory,
            listState = listState,
        )
    }
}

@Composable
internal fun SearchSuggestionContent(
    text: String,
    viewState: SearchSuggestionViewState,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = topPadding, bottom = 140.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (text.isBlank()) {
            if (viewState.history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recent_searches),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = onClearHistory,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                items(viewState.history, key = { it.id }) { history ->
                    SuggestionRow(
                        icon = Icons.Rounded.History,
                        text = history.query,
                        onClick = { onSubmit(history.query) },
                        onFillClick = { onFill(history.query) },
                        onDeleteClick = { onDeleteHistory(history) },
                    )
                }
            }
        } else {
            items(viewState.suggestions) { suggestion ->
                SuggestionRow(
                    icon = Icons.Rounded.Search,
                    text = suggestion,
                    onClick = { onSubmit(suggestion) },
                    onFillClick = { onFill(suggestion) },
                )
            }

            if (viewState.items.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.direct_results),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                items(viewState.items, key = { it.id }) { item ->
                    YTItemRow(item = item, onClick = { onItemClick(item) })
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
    onDeleteClick: (() -> Unit)? = null,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(
                onClick = onFillClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.NorthWest,
                    contentDescription = stringResource(R.string.complete_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun YTItemRow(
    item: YTItem,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: stringResource(R.string.albums)
        is PlaylistItem -> item.author?.name ?: stringResource(R.string.playlists)
        is ArtistItem -> stringResource(R.string.artists)
        else -> null
    }
    val isRound = item is ArtistItem
    var menuItem by remember(item.id) { mutableStateOf<YTItem?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .universalMediaClickable(
                onClick = onClick,
                onLongClick = { menuItem = item },
            )
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
                fontWeight = FontWeight.Bold,
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
        if (item !is ArtistItem) {
            SongOptionsButton(onClick = { menuItem = item })
        }
    }

    UniversalYTItemActionsHost(
        item = menuItem,
        onDismiss = { menuItem = null },
        onOpen = onClick,
    )
}
