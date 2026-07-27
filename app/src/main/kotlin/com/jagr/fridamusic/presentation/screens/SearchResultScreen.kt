package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.viewmodels.OnlineSearchViewModel
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem

private data class SearchTab(val label: String, val filter: YouTube.SearchFilter?)

private val SEARCH_TABS = @Composable {
    listOf(
        SearchTab(stringResource(R.string.filter_all), null),
        SearchTab(stringResource(R.string.filter_songs), YouTube.SearchFilter.FILTER_SONG),
        SearchTab(stringResource(R.string.filter_videos), YouTube.SearchFilter.FILTER_VIDEO),
        SearchTab(stringResource(R.string.filter_albums), YouTube.SearchFilter.FILTER_ALBUM),
        SearchTab(stringResource(R.string.filter_artists), YouTube.SearchFilter.FILTER_ARTIST),
        SearchTab(stringResource(R.string.filter_featured_playlists), YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST),
        SearchTab(stringResource(R.string.filter_community_playlists), YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
    )
}

@Composable
fun SearchResultScreen(
    onItemClick: (YTItem) -> Unit,
    onBack: () -> Unit,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = SEARCH_TABS()
    val selectedFilter = tabs[selectedTabIndex].filter

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
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
            Text(
                text = viewModel.query,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }


        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 20.dp,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTabIndex == index
                Tab(
                    selected = isSelected,
                    onClick = {
                        selectedTabIndex = index
                        viewModel.filter.value = tab.filter
                    },
                    text = {
                        Text(
                            text = tab.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                )
            }
        }

        if (selectedFilter == null) {
            SearchSummaryContent(viewModel = viewModel, onItemClick = onItemClick)
        } else {
            SearchFilteredContent(
                viewModel = viewModel,
                filterValue = selectedFilter.value,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun SearchSummaryContent(viewModel: OnlineSearchViewModel, onItemClick: (YTItem) -> Unit) {
    val summaryPage = viewModel.summaryPage

    if (summaryPage == null) {
        LoadingIndicator()
        return
    }
    if (summaryPage.summaries.isEmpty()) {
        EmptyResults()
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        summaryPage.summaries.forEach { summary ->
            item(key = summary.title) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                )
            }
            items(summary.items, key = { "${summary.title}-${it.id}" }) { item ->
                YTItemRow(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun SearchFilteredContent(
    viewModel: OnlineSearchViewModel,
    filterValue: String,
    onItemClick: (YTItem) -> Unit,
) {
    val viewState = viewModel.viewStateMap[filterValue]
    val listState = rememberLazyListState()

    if (viewState == null) {
        LoadingIndicator()
        return
    }
    if (viewState.items.isEmpty()) {
        EmptyResults()
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(viewState.items, key = { it.id }) { item ->
            YTItemRow(item = item, onClick = { onItemClick(item) })
        }


        if (viewState.continuation != null) {
            item {

                LaunchedEffect(Unit) {
                    viewModel.loadMore()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary, // o EchoLavender
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyResults() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 100.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.no_results_found),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.no_results_found_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}