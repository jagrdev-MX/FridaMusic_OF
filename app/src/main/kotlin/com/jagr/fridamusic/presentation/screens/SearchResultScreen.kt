package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.viewmodels.OnlineSearchViewModel
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem

private data class SearchTab(val label: String, val filter: YouTube.SearchFilter?)

private val SEARCH_TABS = listOf(
    SearchTab("Todo", null),
    SearchTab("Canciones", YouTube.SearchFilter.FILTER_SONG),
    SearchTab("Videos", YouTube.SearchFilter.FILTER_VIDEO),
    SearchTab("Álbumes", YouTube.SearchFilter.FILTER_ALBUM),
    SearchTab("Artistas", YouTube.SearchFilter.FILTER_ARTIST),
    SearchTab("Playlists destacadas", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST),
    SearchTab("Playlists de la comunidad", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
)

@Composable
fun SearchResultScreen(
    onItemClick: (YTItem) -> Unit,
    onBack: () -> Unit,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val selectedFilter = SEARCH_TABS[selectedTabIndex].filter

    LaunchedEffect(selectedFilter) {
        viewModel.filter.value = selectedFilter
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
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = viewModel.query,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            edgePadding = 20.dp,
        ) {
            SEARCH_TABS.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab.label) },
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

    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
        summaryPage.summaries.forEach { summary ->
            item(key = summary.title) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 140.dp)) {
        items(viewState.items, key = { it.id }) { item ->
            YTItemRow(item = item, onClick = { onItemClick(item) })
        }
        if (viewState.continuation != null) {
            item {
                LaunchedEffect(viewState.items.size) {
                    viewModel.loadMore()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
        CircularProgressIndicator(color = com.jagr.fridamusic.presentation.theme.EchoLavender)
    }
}

@Composable
private fun EmptyResults() {
    Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = "Sin resultados",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}