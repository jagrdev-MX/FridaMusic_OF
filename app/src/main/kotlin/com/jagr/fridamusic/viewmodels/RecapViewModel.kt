package com.jagr.fridamusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.SongWithStats
import com.jagr.fridamusic.recap.RecapPeriodResolver
import com.jagr.fridamusic.recap.RecapPeriodType
import com.jagr.fridamusic.recap.RecapPersonality
import com.jagr.fridamusic.recap.RecapPersonalityEngine
import com.jagr.fridamusic.recap.RecapRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

data class RecapUiState(
    val range: RecapRange,
    val totalPlayTimeMs: Long = 0,
    val totalPlays: Int = 0,
    val uniqueSongs: Int = 0,
    val uniqueArtists: Int = 0,
    val uniqueAlbums: Int = 0,
    val topSongs: List<SongWithStats> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val topAlbums: List<Album> = emptyList(),
    val personality: RecapPersonality? = null,
    val canGoToPreviousPeriod: Boolean = false,
) {
    val hasData: Boolean
        get() = totalPlays > 0
}

private data class RecapCoreStats(
    val playTimeMs: Long,
    val plays: Int,
    val songs: Int,
    val artists: Int,
    val albums: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    val selectedType = MutableStateFlow(RecapPeriodType.WEEK)
    val periodOffset = MutableStateFlow(0)

    val range = combine(selectedType, periodOffset) { type, offset ->
        RecapPeriodResolver.resolve(type, offset)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        RecapPeriodResolver.resolve(RecapPeriodType.WEEK, 0),
    )

    private val coreStats = range.flatMapLatest { selectedRange ->
        combine(
            database.getTotalPlayTimeInRange(selectedRange.startMillis, selectedRange.endMillis)
                .map { it ?: 0L },
            database.getPlayCountInRange(selectedRange.startMillis, selectedRange.endMillis),
            database.getUniqueSongCountInRange(selectedRange.startMillis, selectedRange.endMillis),
            database.getUniqueArtistCountInRange(selectedRange.startMillis, selectedRange.endMillis),
            database.getUniqueAlbumCountInRange(selectedRange.startMillis, selectedRange.endMillis),
        ) { playTime, plays, songs, artists, albums ->
            RecapCoreStats(playTime, plays, songs, artists, albums)
        }
    }

    private val topSongs = range.flatMapLatest { selectedRange ->
        database.mostPlayedSongsStats(
            fromTimeStamp = selectedRange.startMillis,
            toTimeStamp = selectedRange.endMillis,
            limit = 5,
        )
    }

    private val topArtists = range.flatMapLatest { selectedRange ->
        database.mostPlayedArtists(
            fromTimeStamp = selectedRange.startMillis,
            toTimeStamp = selectedRange.endMillis,
            limit = 5,
        )
    }

    private val topAlbums = range.flatMapLatest { selectedRange ->
        database.mostPlayedAlbums(
            fromTimeStamp = selectedRange.startMillis,
            toTimeStamp = selectedRange.endMillis,
            limit = 5,
        )
    }

    private val firstEventMillis = database.firstEvent().map { event ->
        event?.event?.timestamp
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
    }

    val uiState = combine(
        range,
        coreStats,
        topSongs,
        topArtists,
        topAlbums,
    ) { selectedRange, core, songs, artists, albums ->
        RecapUiState(
            range = selectedRange,
            totalPlayTimeMs = core.playTimeMs,
            totalPlays = core.plays,
            uniqueSongs = core.songs,
            uniqueArtists = core.artists,
            uniqueAlbums = core.albums,
            topSongs = songs,
            topArtists = artists,
            topAlbums = albums,
            personality = RecapPersonalityEngine.evaluate(
                totalPlayTimeMs = core.playTimeMs,
                totalPlays = core.plays,
                uniqueSongs = core.songs,
                uniqueArtists = core.artists,
                uniqueAlbums = core.albums,
                topSongPlays = songs.firstOrNull()?.songCountListened ?: 0,
            ),
        )
    }.combine(firstEventMillis) { state, firstEvent ->
        state.copy(
            canGoToPreviousPeriod = firstEvent != null && firstEvent < state.range.startMillis,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RecapUiState(range = RecapPeriodResolver.resolve(RecapPeriodType.WEEK, 0)),
    )

    fun selectType(type: RecapPeriodType) {
        selectedType.value = type
        periodOffset.value = 0
    }

    fun previousPeriod() {
        if (uiState.value.canGoToPreviousPeriod) periodOffset.value += 1
    }

    fun nextPeriod() {
        if (periodOffset.value > 0) periodOffset.value -= 1
    }

    fun applyDeepLink(type: String?, offset: Int?) {
        val parsedType = type?.uppercase()?.let { value ->
            runCatching { RecapPeriodType.valueOf(value) }.getOrNull()
        } ?: return
        selectedType.value = parsedType
        periodOffset.value = offset?.coerceAtLeast(0) ?: 0
    }
}
