package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.viewmodels.OnlineSearchViewModel
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import com.music.innertube.pages.SearchSummary
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.launch

@Composable
fun SearchResultScreen(
    onItemClick: (YTItem) -> Unit,
    onBack: () -> Unit,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val searchFilter by viewModel.filter.collectAsState()
    val searchSummary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }

    val allModeSections = buildList<SearchSummary> {
        searchSummary?.summaries?.firstOrNull()?.takeIf { it.items.isNotEmpty() }?.let(::add)

        listOf(
            YouTube.SearchFilter.FILTER_SONG to stringResource(R.string.filter_songs),
            YouTube.SearchFilter.FILTER_VIDEO to stringResource(R.string.filter_videos),
            YouTube.SearchFilter.FILTER_ALBUM to stringResource(R.string.filter_albums),
            YouTube.SearchFilter.FILTER_ARTIST to stringResource(R.string.filter_artists),
            YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
            YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
        ).forEach { (sectionFilter, sectionTitle) ->
            viewModel.viewStateMap[sectionFilter.value]
                ?.items
                ?.takeIf { it.isNotEmpty() }
                ?.let { items ->
                    add(SearchSummary(title = sectionTitle, items = items))
                }
        }
    }

    val isAllModeLoaded = searchSummary != null || listOf(
        YouTube.SearchFilter.FILTER_SONG, YouTube.SearchFilter.FILTER_VIDEO,
        YouTube.SearchFilter.FILTER_ALBUM, YouTube.SearchFilter.FILTER_ARTIST,
        YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
    ).all { viewModel.viewStateMap.containsKey(it.value) }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item ->
        YTItemRow(item = item, onClick = {
            keyboardController?.hide()
            onItemClick(item)
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = viewModel.query,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.complete_search),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            ChipsRow(
                chips = listOf(
                    null to stringResource(R.string.filter_all),
                    YouTube.SearchFilter.FILTER_SONG to stringResource(R.string.filter_songs),
                    YouTube.SearchFilter.FILTER_VIDEO to stringResource(R.string.filter_videos),
                    YouTube.SearchFilter.FILTER_ALBUM to stringResource(R.string.filter_albums),
                    YouTube.SearchFilter.FILTER_ARTIST to stringResource(R.string.filter_artists),
                    YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                    YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
                ),
                currentValue = searchFilter,
                onValueUpdate = {
                    if (viewModel.filter.value != it) {
                        viewModel.filter.value = it
                    }
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                icons = mapOf(
                    null to Icons.Rounded.Search,
                    YouTube.SearchFilter.FILTER_SONG to Icons.Rounded.MusicNote,
                    YouTube.SearchFilter.FILTER_VIDEO to Icons.Rounded.OndemandVideo,
                    YouTube.SearchFilter.FILTER_ALBUM to Icons.Rounded.Album,
                    YouTube.SearchFilter.FILTER_ARTIST to Icons.Rounded.Person,
                    YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST to Icons.Rounded.QueueMusic,
                    YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST to Icons.Rounded.PlaylistPlay,
                ),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues().let {
                    PaddingValues(
                        top = 8.dp,
                        bottom = it.calculateBottomPadding() + 140.dp
                    )
                },
            modifier = Modifier.fillMaxSize()
        ) {
            if (searchFilter == null) {
                allModeSections.forEachIndexed { index, summary ->
                    if (index > 0) {
                        item(key = "divider_$index") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }

                    item(key = "section_header_${summary.title}_$index") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = summary.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    itemsIndexed(
                        items = summary.items,
                        key = { itemIndex, item -> "${summary.title}/${item.id}/$itemIndex" },
                    ) { _, item ->
                        ytItemContent(item)
                    }

                    item(key = "section_spacer_${summary.title}_$index") {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (allModeSections.isEmpty() && isAllModeLoaded) {
                    item {
                        EmptyPlaceholder(text = stringResource(R.string.no_results_found))
                    }
                }
            } else {
                items(
                    items = itemsPage?.items.orEmpty().distinctBy { it.id },
                    key = { "filtered_${it.id}" },
                    itemContent = ytItemContent,
                )

                if (itemsPage?.continuation != null) {
                    item(key = "loading") {
                        ShimmerHost {
                            repeat(3) { ListItemPlaceHolder() }
                        }
                    }
                }

                if (itemsPage?.items?.isEmpty() == true) {
                    item {
                        EmptyPlaceholder(text = stringResource(R.string.no_results_found))
                    }
                }
            }

            if ((searchFilter == null && allModeSections.isEmpty() && !isAllModeLoaded) || (searchFilter != null && itemsPage == null)) {
                item {
                    ShimmerHost {
                        repeat(8) { ListItemPlaceHolder() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    icons: Map<E, ImageVector> = emptyMap(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.width(12.dp))
        chips.forEach { (value, label) ->
            val isSelected = currentValue == value
            val iconVec = icons[value]

            FilterChip(
                selected = isSelected,
                onClick = { onValueUpdate(value) },
                label = { Text(label) },
                leadingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    } else if (iconVec != null) {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors = FilterChipDefaults.filterChipColors(containerColor = containerColor),
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        modifier = modifier
            .shimmer()
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                    blendMode = BlendMode.DstIn,
                )
            },
        content = content,
    )
}

@Composable
private fun ListItemPlaceHolder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun EmptyPlaceholder(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 80.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.no_results_found_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}