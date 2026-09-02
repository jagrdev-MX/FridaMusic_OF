

package com.jagr.fridamusic.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import com.jagr.fridamusic.constants.HideExplicitKey
import com.jagr.fridamusic.constants.EchoBrainEnabledKey
import com.jagr.fridamusic.constants.HideVideoSongsKey
import com.jagr.fridamusic.constants.HideYoutubeShortsKey
import com.jagr.fridamusic.constants.InnerTubeCookieKey
import com.jagr.fridamusic.constants.QuickPicks
import com.jagr.fridamusic.constants.QuickPicksKey
import com.jagr.fridamusic.db.MusicDatabase
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.db.entities.SpeedDialItem
import com.jagr.fridamusic.extensions.filterVideoSongs
import com.jagr.fridamusic.extensions.toEnum
import com.jagr.fridamusic.models.SimilarRecommendation
import com.jagr.fridamusic.localmedia.LocalAudioPreferencesRepository
import com.jagr.fridamusic.utils.dataStore
import com.jagr.fridamusic.utils.get
import com.jagr.fridamusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

sealed interface GlobalShuffleSelection {
    data class SongQueue(val songs: List<Song>) : GlobalShuffleSelection
    data class RemoteItem(val item: YTItem) : GlobalShuffleSelection
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val echoBrainEngine: com.jagr.fridamusic.engine.EchoBrainEngine,
    localAudioPreferencesRepository: LocalAudioPreferencesRepository,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val echoBrainPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    private val pinnedLocalSongs = localAudioPreferencesRepository.pinnedSongIds
        .distinctUntilChanged()
        .flatMapLatest { pinnedIds ->
            if (pinnedIds.isEmpty()) {
                flowOf(emptyList())
            } else if (pinnedIds.size <= MAX_PINNED_IDS_PER_QUERY) {
                database.localSongPreviewsByIds(pinnedIds)
            } else {
                database.localSongPreviews().map { songs ->
                    songs.filter { song -> song.id in pinnedIds }
                }
            }
        }

    val pinnedItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            pinnedLocalSongs,
        ) { speedDial, localSongs ->
            buildList {
                addAll(speedDial.map { it.toYTItem() })
                addAll(
                    localSongs
                        .map { song ->
                            SongItem(
                                id = song.id,
                                title = song.title,
                                artists = song.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = song.resolvedThumbnailUrl.orEmpty(),
                                explicit = song.explicit,
                            )
                        },
                )
            }.distinctBy { item -> "${item::class.qualifiedName}:${item.id}" }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks
        ) { pinned, keepListening, quick ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 27

            if (filled.size < targetSize) {
                
                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            if (filled.size < targetSize) {
                
                quick?.let { q ->
                    val needed = targetSize - filled.size
                    val available = q.filter { song ->
                        filled.none { p -> p.id == song.id }
                    }.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                            explicit = false
                        )
                    }
                    filled.addAll(available.take(needed))
                }
            }

            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getGlobalShuffleSelection(): GlobalShuffleSelection? {
        try {
            isRandomizing.value = true

            val songQueue = database.allSongs().first()
                .asSequence()
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                .distinctBy { it.id }
                .shuffled()
                .take(GLOBAL_SHUFFLE_QUEUE_LIMIT)
                .toList()

            val remoteItems = buildList {
                addAll(allYtItems.value)
                addAll(accountPlaylists.value.orEmpty())
                addAll(explorePage.value?.newReleaseAlbums.orEmpty())
                addAll(communityPlaylists.value.orEmpty().map { it.playlist })
            }.asSequence()
                .filter(::isPlayableGlobalShuffleItem)
                .distinctBy { "${it.javaClass.name}:${it.id}" }
                .toList()

            val availableSources = buildList<GlobalShuffleSelection> {
                if (songQueue.isNotEmpty()) add(GlobalShuffleSelection.SongQueue(songQueue))
                remoteItems.randomOrNull()?.let { add(GlobalShuffleSelection.RemoteItem(it)) }
            }

            return availableSources.randomOrNull()
        } finally {
            isRandomizing.value = false
        }
    }

    private fun isPlayableGlobalShuffleItem(item: YTItem): Boolean = when (item) {
        is SongItem -> item.id.isNotBlank()
        is AlbumItem -> item.playlistId.isNotBlank()
        is com.music.innertube.models.ArtistItem ->
            item.shuffleEndpoint != null || item.radioEndpoint != null || item.playEndpoint != null
        is PlaylistItem ->
            item.shuffleEndpoint != null || item.radioEndpoint != null || item.playEndpoint != null
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }
    
    private var lastProcessedCookie: String? = null
    
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(5)

        
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            val freshCandidates = recommendations
                                .filter { rec -> rec.id != seed.id }
                                .take(NOTIFICATION_RECOMMENDATIONS_PER_SEED)
                            database.cacheNotificationCandidates(seed.id, freshCandidates)

                            freshCandidates.take(4).forEach { recommendation ->
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }

        
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                
                val recentSong = database.events().first().firstOrNull()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val freshCandidates = page.songs
                                .filter { ytSong -> !hideVideoSongs || !ytSong.isVideoSong }
                                .take(NOTIFICATION_RECOMMENDATIONS_PER_SEED)
                            database.cacheNotificationCandidates(recentSong.id, freshCandidates)
                            freshCandidates.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                
                val combined = (relatedSongs + forgotten + ytSimilarSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(HOME_COLLECTION_TARGET_SIZE)

                quickPicks.value = combined.ifEmpty {
                    relatedSongs.shuffled().take(HOME_COLLECTION_TARGET_SIZE)
                }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.events().first().firstOrNull()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first()
                        .filterVideoSongs(hideVideoSongs)
                        .shuffled()
                        .take(HOME_COLLECTION_TARGET_SIZE)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }

            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }
            .shuffled()
            .take(HOME_COMMUNITY_PLAYLIST_LIMIT)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.mapIndexed { index, playlist ->
                launch(Dispatchers.IO) {
                    if (index >= HOME_COMMUNITY_VALIDATION_LIMIT) {
                        playlists.add(CommunityPlaylistItem(playlist, emptyList()))
                        return@launch
                    }
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    private suspend fun getEchoBrainPlaylists() {
        if (!context.dataStore.get(EchoBrainEnabledKey, false)) {
            echoBrainPlaylists.value = emptyList()
            return
        }
        val brainMix = echoBrainEngine.generateBrainMix()
        
        if (brainMix.isNotEmpty()) {
            val songs = brainMix.map { meta ->
                SongItem(
                    id = meta.id,
                    title = meta.title,
                    artists = meta.artists.map { Artist(name = it.name, id = it.id) },
                    thumbnail = meta.thumbnailUrl ?: "",
                    explicit = false
                )
            }
            
            val playlistItem = PlaylistItem(
                id = "echo_brain_mix_local",
                title = "Made for You",
                author = Artist(name = "Echo Brain", id = null),
                songCountText = "${songs.size} songs",
                thumbnail = songs.firstOrNull()?.thumbnail ?: "",
                playEndpoint = WatchEndpoint(videoId = songs.firstOrNull()?.id ?: "", playlistId = "echo_brain_mix_local"),
                shuffleEndpoint = WatchEndpoint(videoId = songs.firstOrNull()?.id ?: "", playlistId = "echo_brain_mix_local"),
                radioEndpoint = WatchEndpoint(videoId = songs.firstOrNull()?.id ?: "", playlistId = "echo_brain_mix_local")
            )
            
            echoBrainPlaylists.value = listOf(CommunityPlaylistItem(playlistItem, songs))
        } else {
            echoBrainPlaylists.value = emptyList()
        }
    }

    
    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        getQuickPicks()

        forgottenFavorites.value = database.forgottenFavorites().first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(HOME_COLLECTION_TARGET_SIZE)

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 20, offset = 5).first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(15)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 12, offset = 2).first()
            .filter { it.album.thumbnailUrl != null }.shuffled().take(8)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(7)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
    }

    
    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2

        coroutineScope {
            val artistDeferreds = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .map { artist ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(artist.id).onSuccess { page ->
                            page.sections.takeLast(3).forEach { section -> items += section.items }
                        }
                        database.cacheNotificationCandidates(
                            seedSongId = null,
                            items = items.take(NOTIFICATION_ITEMS_PER_REMOTE_SOURCE),
                        )
                        SimilarRecommendation(
                            title = artist,
                            items = items
                                .distinctBy { item -> item.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(HOME_SIMILAR_ITEMS_LIMIT)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val songDeferreds = database.mostPlayedSongs(fromTimeStamp, limit = 15).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .map { song ->
                    async(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                            ?: return@async null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                        val relatedItems = (
                            page.songs.shuffled().take(16) +
                                page.albums.shuffled().take(6) +
                                page.artists.shuffled().take(6) +
                                page.playlists.shuffled().take(6)
                            ).distinctBy { item -> item.id }
                        database.cacheNotificationCandidates(
                            seedSongId = song.id,
                            items = relatedItems.take(NOTIFICATION_ITEMS_PER_REMOTE_SOURCE),
                        )
                        SimilarRecommendation(
                            title = song,
                            items = relatedItems
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val albumDeferreds = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .map { album ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.album(album.id).onSuccess { page ->
                            page.otherVersions.let { items += it }
                        }
                        album.artists.firstOrNull()?.id?.let { artistId ->
                            YouTube.artist(artistId).onSuccess { page ->
                                page.sections.lastOrNull()?.items?.let { items += it }
                            }
                        }
                        database.cacheNotificationCandidates(
                            seedSongId = null,
                            items = items.take(NOTIFICATION_ITEMS_PER_REMOTE_SOURCE),
                        )
                        SimilarRecommendation(
                            title = album,
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(HOME_SIMILAR_ITEMS_LIMIT)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val results = (artistDeferreds + songDeferreds + albumDeferreds).awaitAll()
            similarRecommendations.value = results.filterNotNull().shuffled()
        }
    }

    
    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        coroutineScope {
            launch(Dispatchers.IO) { getDailyDiscover() }
            launch(Dispatchers.IO) { getCommunityPlaylists() }
            launch(Dispatchers.IO) { getEchoBrainPlaylists() }
            launch(Dispatchers.IO) { loadSimilarRecommendations() }
            launch(Dispatchers.IO) {
                YouTube.home().onSuccess { page ->
                    val filteredPage = page.copy(
                        sections = page.sections.mapNotNull { section ->
                            val filteredItems = section.items
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts)
                            if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                        }
                    )
                    homePage.value = filteredPage
                    database.cacheNotificationCandidates(
                        seedSongId = null,
                        items = filteredPage.sections
                            .flatMap { section -> section.items }
                            .distinctBy { item -> item.id }
                            .take(NOTIFICATION_HOME_CACHE_LIMIT),
                    )
                }.onFailure { reportException(it) }
            }
            launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    val releases = page.newReleaseAlbums.filterExplicit(hideExplicit)
                    explorePage.value = page.copy(newReleaseAlbums = releases)
                    database.cacheNotificationCandidates(
                        seedSongId = null,
                        items = releases.take(NOTIFICATION_ITEMS_PER_REMOTE_SOURCE),
                    )
                }.onFailure { reportException(it) }
            }
            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }
        }

        
        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private suspend fun load() {
        isLoading.value = true
        try {
            loadLocalDataPhase()
            loadNetworkDataPhase()
        } finally {
            isLoading.value = false
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    private var loadMoreJob: Job? = null
    private var chipLoadJob: Job? = null

    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null ||
            _isLoadingMore.value ||
            homePage.value?.continuation != continuation
        ) return

        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        val expectedChip = selectedChip.value

        loadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            try {
                var currentContinuation = continuation
                var hasNewItems = false

                while (currentContinuation != null && !hasNewItems) {
                    val nextSections = YouTube.home(currentContinuation).getOrNull() ?: break
                    currentContinuation = nextSections.continuation

                    val newSections = nextSections.sections.mapNotNull { section ->
                        val filteredItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .filterYoutubeShorts(hideYoutubeShorts)
                        if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                    }

                    if (selectedChip.value != expectedChip) return@launch

                    if (newSections.isNotEmpty()) {
                        hasNewItems = true
                        database.cacheNotificationCandidates(
                            seedSongId = null,
                            items = newSections
                                .flatMap { section -> section.items }
                                .distinctBy { item -> item.id }
                                .take(NOTIFICATION_HOME_CACHE_LIMIT),
                        )
                    }

                    homePage.value = nextSections.copy(
                        chips = homePage.value?.chips,
                        continuation = currentContinuation,
                        sections = homePage.value?.sections.orEmpty() + newSections
                    )
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private companion object {
        const val GLOBAL_SHUFFLE_QUEUE_LIMIT = 200
        const val HOME_COLLECTION_TARGET_SIZE = 30
        const val HOME_COMMUNITY_PLAYLIST_LIMIT = 15
        const val HOME_COMMUNITY_VALIDATION_LIMIT = 5
        const val HOME_SIMILAR_ITEMS_LIMIT = 20
        const val NOTIFICATION_RECOMMENDATIONS_PER_SEED = 20
        const val NOTIFICATION_ITEMS_PER_REMOTE_SOURCE = 30
        const val NOTIFICATION_HOME_CACHE_LIMIT = 80
        const val MAX_PINNED_IDS_PER_QUERY = 900
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            chipLoadJob?.cancel()
            loadMoreJob?.cancel()
            _isLoadingMore.value = false
            previousHomePage.value?.let { homePage.value = it }
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        chipLoadJob?.cancel()
        loadMoreJob?.cancel()
        _isLoadingMore.value = false
        selectedChip.value = chip

        chipLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull()
            if (nextSections == null) {
                if (selectedChip.value == chip) {
                    previousHomePage.value?.let { homePage.value = it }
                    previousHomePage.value = null
                    selectedChip.value = null
                }
                return@launch
            }

            if (selectedChip.value != chip) return@launch

            val filteredSections = nextSections.sections.mapNotNull { section ->
                val filteredItems = section.items
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
                if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
            }
            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = filteredSections,
            )
            database.cacheNotificationCandidates(
                seedSongId = null,
                items = filteredSections
                    .flatMap { section -> section.items }
                    .distinctBy { item -> item.id }
                    .take(NOTIFICATION_HOME_CACHE_LIMIT),
            )
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                chipLoadJob?.cancel()
                loadMoreJob?.cancel()
                _isLoadingMore.value = false
                previousHomePage.value?.let { homePage.value = it }
                previousHomePage.value = null
                selectedChip.value = null
                load()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    init {

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .collect { cookie ->
                    
                    if (isProcessingAccountData) return@collect

                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {

                            
                            YouTube.cookie = cookie

                            
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }
}
