package com.jagr.fridamusic.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.components.FridaLoadingDefaults
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.SearchInput
import com.jagr.fridamusic.viewmodels.OnlineSearchSuggestionViewModel
import com.jagr.fridamusic.viewmodels.OnlineSearchViewModel
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class SearchTab(
    val label: String,
    val icon: ImageVector,
    val filter: YouTube.SearchFilter?,
)

private val SEARCH_TABS = @Composable {
    listOf(
        SearchTab(stringResource(R.string.filter_all), Icons.Rounded.Explore, null),
        SearchTab(stringResource(R.string.filter_songs), Icons.Rounded.MusicNote, YouTube.SearchFilter.FILTER_SONG),
        SearchTab(stringResource(R.string.filter_videos), Icons.Rounded.VideoLibrary, YouTube.SearchFilter.FILTER_VIDEO),
        SearchTab(stringResource(R.string.filter_albums), Icons.Rounded.Album, YouTube.SearchFilter.FILTER_ALBUM),
        SearchTab(stringResource(R.string.filter_artists), Icons.Rounded.Person, YouTube.SearchFilter.FILTER_ARTIST),
        SearchTab(
            stringResource(R.string.filter_featured_playlists),
            Icons.AutoMirrored.Rounded.PlaylistPlay,
            YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST,
        ),
        SearchTab(
            stringResource(R.string.filter_community_playlists),
            Icons.AutoMirrored.Rounded.QueueMusic,
            YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultScreen(
    onItemClick: (YTItem) -> Unit,
    onBack: () -> Unit,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    var searchText by remember(viewModel) { mutableStateOf(viewModel.query) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val tabs = SEARCH_TABS()
    val suggestionState by suggestionViewModel.viewState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeSearchEditor() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun submitQuery(candidate: String) {
        val newQuery = candidate.trim()
        if (newQuery.isEmpty()) return

        searchText = newQuery
        suggestionViewModel.saveSearch(newQuery)
        viewModel.submitQuery(newQuery)
        closeSearchEditor()
    }

    LaunchedEffect(isSearchFocused) {
        if (isSearchFocused) {
            suggestionViewModel.query.value = searchText
        }
    }

    BackHandler(enabled = isSearchFocused) {
        closeSearchEditor()
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (isSearchFocused) closeSearchEditor() else onBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            SearchInput(
                value = searchText,
                onValueChange = { value ->
                    searchText = value
                    suggestionViewModel.query.value = value
                },
                onSubmit = ::submitQuery,
                onClear = {
                    searchText = ""
                    suggestionViewModel.query.value = ""
                    focusRequester.requestFocus()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isSearchFocused = it.isFocused },
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            key(viewModel.query) {
                SearchCategoryPager(
                    tabs = tabs,
                    viewModel = viewModel,
                    onItemClick = onItemClick,
                )
            }

            if (isSearchFocused) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SearchSuggestionContent(
                        text = searchText,
                        viewState = suggestionState,
                        onSubmit = ::submitQuery,
                        onFill = { value ->
                            searchText = value
                            suggestionViewModel.query.value = value
                            focusRequester.requestFocus()
                        },
                        onItemClick = { item ->
                            closeSearchEditor()
                            onItemClick(item)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchCategoryPager(
    tabs: List<SearchTab>,
    viewModel: OnlineSearchViewModel,
    onItemClick: (YTItem) -> Unit,
) {
    val initialPage = tabs.indexOfFirst { it.filter == viewModel.filter.value }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    val tabListState = rememberLazyListState()
    val resultListStates = tabs.map { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val selectedTabIndex = pagerState.targetPage

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.targetPage }.collect { page ->
            viewModel.selectFilter(tabs[page].filter)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val targetIndex = pagerState.settledPage
        val layoutInfo = snapshotFlow { tabListState.layoutInfo }
            .first { it.viewportEndOffset > it.viewportStartOffset }
        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val viewportCenter = (viewportStart + viewportEnd) / 2
        val targetCenter = targetItem?.let { it.offset + it.size / 2 }
        val isFullyVisible = targetItem != null &&
            targetItem.offset >= viewportStart &&
            targetItem.offset + targetItem.size <= viewportEnd
        val centerTolerance = with(density) { 24.dp.roundToPx() }
        val isNearCenter = targetCenter != null && abs(targetCenter - viewportCenter) <= centerTolerance

        if (!isFullyVisible || !isNearCenter) {
            val estimatedItemWidth = targetItem?.size ?: with(density) { 132.dp.roundToPx() }
            val centeredOffset = ((viewportEnd - viewportStart - estimatedItemWidth) / 2).coerceAtLeast(0)
            tabListState.animateScrollToItem(targetIndex, scrollOffset = -centeredOffset)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            state = tabListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.filter?.value ?: "summary" }) { index, tab ->
                SearchCategoryChip(
                    label = tab.label,
                    icon = tab.icon,
                    selected = selectedTabIndex == index,
                    onClick = {
                        if (index != pagerState.currentPage) {
                            viewModel.selectFilter(tab.filter)
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key = { page -> tabs[page].filter?.value ?: "summary" },
        ) { page ->
            val tab = tabs[page]
            val listState = resultListStates[page]
            if (tab.filter == null) {
                SearchSummaryContent(
                    viewModel = viewModel,
                    listState = listState,
                    onItemClick = onItemClick,
                )
            } else {
                SearchFilteredContent(
                    viewModel = viewModel,
                    filterValue = tab.filter.value,
                    listState = listState,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}

@Composable
private fun SearchCategoryChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (selected) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "search_chip_scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "search_chip_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "search_chip_content",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(containerColor)
                .padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = label,
                color = contentColor,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

@Composable
private fun SearchSummaryContent(
    viewModel: OnlineSearchViewModel,
    listState: LazyListState,
    onItemClick: (YTItem) -> Unit,
) {
    val summaryPage = viewModel.summaryPage

    if (summaryPage == null) {
        if (viewModel.isLoading) {
            FridaLoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                contentAlignment = Alignment.TopCenter,
            )
        } else {
            EmptyResults()
        }
        return
    }
    if (summaryPage.summaries.isEmpty()) {
        EmptyResults()
        return
    }

    LazyColumn(
        state = listState,
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
    listState: LazyListState,
    onItemClick: (YTItem) -> Unit,
) {
    val viewState = viewModel.viewStateMap[filterValue]

    if (viewState == null) {
        if (viewModel.isLoading) {
            FridaLoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                contentAlignment = Alignment.TopCenter,
            )
        } else {
            EmptyResults()
        }
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
                    viewModel.loadMore(filterValue)
                }
                FridaLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    indicatorSize = FridaLoadingDefaults.MediumIndicatorSize,
                )
            }
        }
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
