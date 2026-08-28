

package com.jagr.fridamusic.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.constants.HideYoutubeShortsKey
import com.jagr.fridamusic.models.ItemsPage
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.get
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private companion object {
        const val SELECTED_SEARCH_FILTER_KEY = "selectedSearchFilter"
    }

    var query by mutableStateOf(try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    })
        private set

    private val _filter = MutableStateFlow(
        savedStateHandle.get<String>(SELECTED_SEARCH_FILTER_KEY)?.let { YouTube.SearchFilter(it) },
    )
    val filter = _filter.asStateFlow()
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        private set
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()
    var isLoading by mutableStateOf(false)
        private set

    private var requestGeneration = 0L
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        startSearch(filter = _filter.value)
    }

    fun submitQuery(newQuery: String) {
        val normalizedQuery = newQuery.trim()
        if (normalizedQuery.isEmpty() || normalizedQuery == query) return

        requestGeneration++
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _filter.value = null
        savedStateHandle.remove<String>(SELECTED_SEARCH_FILTER_KEY)
        query = normalizedQuery
        savedStateHandle["query"] = normalizedQuery
        summaryPage = null
        viewStateMap.clear()
        startSearch(filter = null)
    }

    fun selectFilter(newFilter: YouTube.SearchFilter?) {
        if (_filter.value == newFilter) return

        searchJob?.cancel()
        loadMoreJob?.cancel()
        _filter.value = newFilter
        if (newFilter == null) {
            savedStateHandle.remove<String>(SELECTED_SEARCH_FILTER_KEY)
        } else {
            savedStateHandle[SELECTED_SEARCH_FILTER_KEY] = newFilter.value
        }
        startSearch(filter = newFilter)
    }

    private fun startSearch(filter: YouTube.SearchFilter?) {
        if (filter == null && summaryPage != null) {
            isLoading = false
            return
        }
        if (filter != null && viewStateMap[filter.value] != null) {
            isLoading = false
            return
        }

        val expectedGeneration = requestGeneration
        val expectedQuery = query
        isLoading = true
        searchJob = viewModelScope.launch {
            try {
                if (filter == null) {
                    YouTube.searchSummary(expectedQuery)
                        .onSuccess { result ->
                            if (!isCurrentRequest(expectedGeneration, expectedQuery, filter)) return@onSuccess

                            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                            if (!isCurrentRequest(expectedGeneration, expectedQuery, filter)) return@onSuccess
                            summaryPage = result
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts)
                        }.onFailure {
                            if (isCurrentRequest(expectedGeneration, expectedQuery, filter)) {
                                reportException(it)
                            }
                        }
                } else {
                    YouTube.search(expectedQuery, filter)
                        .onSuccess { result ->
                            if (!isCurrentRequest(expectedGeneration, expectedQuery, filter)) return@onSuccess

                            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                            if (!isCurrentRequest(expectedGeneration, expectedQuery, filter)) return@onSuccess
                            viewStateMap[filter.value] = ItemsPage(
                                result.items
                                    .distinctBy { it.id }
                                    .filterExplicit(hideExplicit)
                                    .let { items ->
                                        if (filter.value == YouTube.SearchFilter.FILTER_VIDEO.value) items
                                        else items.filterVideoSongs(hideVideoSongs)
                                    }
                                    .filterYoutubeShorts(hideYoutubeShorts),
                                result.continuation,
                            )
                        }.onFailure {
                            if (isCurrentRequest(expectedGeneration, expectedQuery, filter)) {
                                reportException(it)
                            }
                        }
                }
            } finally {
                if (isCurrentRequest(expectedGeneration, expectedQuery, filter)) {
                    isLoading = false
                }
            }
        }
    }

    private fun isCurrentRequest(
        expectedGeneration: Long,
        expectedQuery: String,
        expectedFilter: YouTube.SearchFilter?,
    ): Boolean {
        return requestGeneration == expectedGeneration &&
            query == expectedQuery &&
            _filter.value == expectedFilter
    }

    fun loadMore(filterValue: String) {
        val selectedFilter = _filter.value?.takeIf { it.value == filterValue } ?: return
        if (loadMoreJob?.isActive == true) return

        val expectedGeneration = requestGeneration
        val expectedQuery = query
        val viewState = viewStateMap[filterValue] ?: return
        val continuation = viewState.continuation ?: return
        loadMoreJob = viewModelScope.launch {
            val searchResult = YouTube.searchContinuation(continuation).getOrElse {
                if (isCurrentRequest(expectedGeneration, expectedQuery, selectedFilter)) {
                    reportException(it)
                    viewStateMap[filterValue] = viewState.copy(continuation = null)
                }
                return@launch
            }

            if (!isCurrentRequest(expectedGeneration, expectedQuery, selectedFilter) ||
                viewStateMap[filterValue]?.continuation != continuation
            ) {
                return@launch
            }

            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            if (!isCurrentRequest(expectedGeneration, expectedQuery, selectedFilter) ||
                viewStateMap[filterValue]?.continuation != continuation
            ) {
                return@launch
            }
            val newItems = searchResult.items
                .filterExplicit(hideExplicit)
                .let { items ->
                    if (filterValue == YouTube.SearchFilter.FILTER_VIDEO.value) items
                    else items.filterVideoSongs(hideVideoSongs)
                }
                .filterYoutubeShorts(hideYoutubeShorts)
            viewStateMap[filterValue] = ItemsPage(
                (viewState.items + newItems).distinctBy { it.id },
                searchResult.continuation,
            )
        }
    }
}
