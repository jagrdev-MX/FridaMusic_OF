

package com.jagr.fridamusic.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.pages.BrowseResult
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.constants.HideYoutubeShortsKey
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.get
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")

    val result = MutableStateFlow<BrowseResult?>(null)
    val isLoading = MutableStateFlow(true)
    val isLoadingMore = MutableStateFlow(false)
    val loadFailed = MutableStateFlow(false)
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                result.value = YouTube.browse(browseId, params)
                    .getOrThrow()
                    .applyContentFilters()
            } catch (error: Throwable) {
                loadFailed.value = true
                reportException(error)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadMore() {
        val continuation = result.value?.continuation ?: return
        if (isLoadingMore.value) return

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            isLoadingMore.value = true
            try {
                val next = YouTube.browseContinuation(continuation)
                    .getOrThrow()
                    .applyContentFilters()
                val current = result.value ?: return@launch
                result.value = current.copy(
                    items = current.items + next.items,
                    continuation = next.continuation.takeUnless { it == continuation },
                )
            } catch (error: Throwable) {
                reportException(error)
                result.value = result.value?.copy(continuation = null)
            } finally {
                isLoadingMore.value = false
            }
        }
    }

    private suspend fun BrowseResult.applyContentFilters(): BrowseResult {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        return filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .filterYoutubeShorts(hideYoutubeShorts)
    }
}
