package com.jagr.fridamusic.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.jagr.fridamusic.R
import com.jagr.fridamusic.ads.InterstitialAdManager
import com.jagr.fridamusic.db.entities.Album as LocalAlbum
import com.jagr.fridamusic.db.entities.Artist as LocalArtist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist as LocalPlaylist
import com.jagr.fridamusic.db.entities.Song
import com.jagr.fridamusic.extensions.toMediaItem
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.presentation.components.CastDrawerItem
import com.jagr.fridamusic.presentation.components.HomeContentType
import com.jagr.fridamusic.presentation.components.HomeMediaCard
import com.jagr.fridamusic.presentation.components.HomeSectionHeader
import com.jagr.fridamusic.presentation.components.MediaArtworkActionButton
import com.jagr.fridamusic.presentation.components.MediaPlaybackIndicator
import com.jagr.fridamusic.presentation.components.SongOptionsButton
import com.jagr.fridamusic.presentation.components.SongActionContext
import com.jagr.fridamusic.presentation.components.SupportCenterSheet
import com.jagr.fridamusic.presentation.components.YTContentCard
import com.jagr.fridamusic.presentation.components.UniversalSongActionsHost
import com.jagr.fridamusic.presentation.components.UniversalYTItemActionsHost
import com.jagr.fridamusic.presentation.components.toSongActionContext
import com.jagr.fridamusic.presentation.components.universalMediaClickable
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.playback.queues.ListQueue
import com.jagr.fridamusic.support.SupportBillingManager
import com.jagr.fridamusic.utils.MemoryDiagnostics
import com.jagr.fridamusic.utils.rememberIsLowEndDevice
import com.jagr.fridamusic.utils.resize
import com.jagr.fridamusic.viewmodels.HomeViewModel
import com.jagr.fridamusic.viewmodels.NotificationHistoryViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as YTArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.MoodAndGenres
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onItemClick: (YTItem) -> Unit = {},
    onPlayItem: (YTItem) -> Unit = {},
    onBrowseClick: (BrowseEndpoint, String) -> Unit = { _, _ -> },
    onNewReleasesClick: () -> Unit = {},
    onMoodAndGenresClick: () -> Unit = {},
    onCollectionClick: (HomeCollectionKind, Int, String) -> Unit = { _, _, _ -> },
    onSettingsClick: () -> Unit = {},
    onRecapClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    reselectToken: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
    notificationHistoryViewModel: NotificationHistoryViewModel = hiltViewModel(),
) {
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val dailyDiscover by viewModel.dailyDiscover.collectAsStateWithLifecycle()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val communityPlaylists by viewModel.communityPlaylists.collectAsStateWithLifecycle()
    val echoBrainPlaylists by viewModel.echoBrainPlaylists.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedChip by viewModel.selectedChip.collectAsStateWithLifecycle()
    val pinnedItems by viewModel.pinnedItems.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val unreadNotificationCount by notificationHistoryViewModel.unreadCount.collectAsStateWithLifecycle()
    val unreadRecapCount by notificationHistoryViewModel.unreadRecapCount.collectAsStateWithLifecycle()
    var pinnedSelected by rememberSaveable { mutableStateOf(false) }
    val playerConnection = LocalPlayerConnection.current
    val currentMediaMetadata = playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val currentQueueTitle = playerConnection?.queueTitle?.collectAsStateWithLifecycle()?.value
    var drawerArtistChoices by remember(currentMediaMetadata?.id) {
        mutableStateOf<List<MediaMetadata.Artist>?>(null)
    }
    val currentIsPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value == true
    val isLowEnd = rememberIsLowEndDevice()
    val quickPickItems = quickPicks.orEmpty()
    val dailyDiscoverItems = dailyDiscover.orEmpty()
    val dailyDiscoverPreviewItems = remember(dailyDiscover) {
        dailyDiscover.orEmpty()
            .asSequence()
            .map { it.recommendation }
            .take(HOME_SECTION_PREVIEW_LIMIT)
            .toList()
    }
    val forgottenFavoriteItems = forgottenFavorites.orEmpty()
    val keepListeningItems = keepListening.orEmpty()
    val similarRecommendationItems = similarRecommendations.orEmpty()
    val accountPlaylistItems = accountPlaylists.orEmpty()
    val newReleaseItems = explorePage?.newReleaseAlbums.orEmpty()
    val communityPlaylistItems = communityPlaylists.orEmpty()
    val echoBrainPlaylistItems = echoBrainPlaylists.orEmpty()
    val moodAndGenreItems = explorePage?.moodAndGenres.orEmpty()
    val remoteSections = homePage?.sections.orEmpty()
    val quickPicksTitle = stringResource(R.string.quick_picks)
    val quickPicksSubtitle = stringResource(R.string.quick_picks_subtitle)
    val dailyDiscoverTitle = stringResource(R.string.your_daily_discover)
    val keepListeningTitle = stringResource(R.string.keep_listening)
    val forgottenFavoritesTitle = stringResource(R.string.forgotten_favorites)
    val accountPlaylistsTitle = stringResource(R.string.your_youtube_playlists)
    val newReleasesTitle = stringResource(R.string.new_release_albums)
    val similarToTitle = stringResource(R.string.similar_to)
    val communityTitle = stringResource(R.string.from_the_community)
    val pinnedTitle = stringResource(R.string.pinned_items)
    val moodAndGenresTitle = stringResource(R.string.mood_and_genres)
    val hasGeneralContent = quickPickItems.isNotEmpty() ||
            dailyDiscoverItems.isNotEmpty() ||
            forgottenFavoriteItems.isNotEmpty() ||
            keepListeningItems.isNotEmpty() ||
            similarRecommendationItems.isNotEmpty() ||
            accountPlaylistItems.isNotEmpty() ||
            !homePage?.sections.isNullOrEmpty() ||
            newReleaseItems.isNotEmpty() ||
            moodAndGenreItems.isNotEmpty() ||
            communityPlaylistItems.isNotEmpty() ||
            echoBrainPlaylistItems.isNotEmpty()
    val hasVisibleContent = when {
        pinnedSelected -> pinnedItems.isNotEmpty()
        selectedChip == null -> hasGeneralContent
        else -> !homePage?.sections.isNullOrEmpty()
    }

    // Estado para controlar la visibilidad del popup de apoyo
    var showSupportDialog by remember { mutableStateOf(false) }
    var showDrawerOverlay by remember { mutableStateOf(false) }
    var pendingDrawerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var menuContext by remember { mutableStateOf<SongActionContext?>(null) }
    var remoteMenuItem by remember { mutableStateOf<YTItem?>(null) }
    val context = LocalContext.current
    val activity = context.findActivity()
    val interstitialAdManager = remember(activity) { InterstitialAdManager(activity) }
    val supportBillingManager = remember(context.applicationContext) {
        SupportBillingManager(context.applicationContext)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val pullRefreshHaptic = LocalHapticFeedback.current
    var handledReselectToken by remember { mutableIntStateOf(reselectToken) }
    val quickReturnState = rememberTopAppBarState()
    var quickReturnHeaderHeightPx by remember { mutableIntStateOf(0) }
    val quickReturnHeaderHeight = with(LocalDensity.current) { quickReturnHeaderHeightPx.toDp() }
    val quickReturnConnection = rememberQuickReturnConnection(quickReturnState)
    val quickReturnTouchBlocker = remember { MutableInteractionSource() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    DisposableEffect(interstitialAdManager, supportBillingManager) {
        MemoryDiagnostics.log("Home entered")
        onDispose {
            interstitialAdManager.release()
            supportBillingManager.close()
        }
    }

    LaunchedEffect(showSupportDialog, interstitialAdManager, supportBillingManager) {
        if (showSupportDialog) {
            supportBillingManager.start()
            interstitialAdManager.load()
        }
    }

    LaunchedEffect(isLoading, hasGeneralContent) {
        if (!isLoading && hasGeneralContent) MemoryDiagnostics.log("Home loaded")
    }

    DisposableEffect(lifecycleOwner, supportBillingManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                supportBillingManager.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(reselectToken) {
        val shouldScroll = reselectToken != handledReselectToken
        handledReselectToken = reselectToken
        if (shouldScroll) {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateReselectScrollToTop(directAnimationItemLimit = 8)
            }
            if (quickReturnState.heightOffset != 0f) {
                animate(
                    initialValue = quickReturnState.heightOffset,
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { value, _ ->
                    quickReturnState.heightOffset = value.coerceIn(
                        quickReturnState.heightOffsetLimit,
                        0f,
                    )
                }
                quickReturnState.contentOffset = 0f
            }
        }
    }

    LaunchedEffect(pullToRefreshState) {
        snapshotFlow { pullToRefreshState.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { activated ->
                if (activated) {
                    pullRefreshHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
    }

    PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(quickReturnConnection),
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = {
                pullRefreshHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.refresh()
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = quickReturnHeaderHeight,
                        bottom = 140.dp,
                    ),
                ) {

        if (pinnedSelected) {
            ytSection(
                key = "pinned_items",
                title = pinnedTitle,
                items = pinnedItems,
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                currentQueueTitle = currentQueueTitle,
                isPlaying = currentIsPlaying,
                onItemMore = { remoteMenuItem = it },
            )
        }

        if (!pinnedSelected && selectedChip == null) {
            localSongSection(
                key = "quick_picks",
                title = quickPicksTitle,
                subtitle = quickPicksSubtitle,
                songs = quickPickItems.take(HOME_SECTION_PREVIEW_LIMIT),
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
                currentMediaId = currentMediaMetadata?.id,
                isPlaying = currentIsPlaying,
                onSongMore = { menuContext = it.toSongActionContext() },
                onSeeAll = {
                    onCollectionClick(HomeCollectionKind.QUICK_PICKS, 0, quickPicksTitle)
                },
            )

            ytSection(
                key = "daily_discover",
                title = dailyDiscoverTitle,
                items = dailyDiscoverPreviewItems,
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                currentQueueTitle = currentQueueTitle,
                isPlaying = currentIsPlaying,
                onSeeAll = {
                    onCollectionClick(HomeCollectionKind.DAILY_DISCOVER, 0, dailyDiscoverTitle)
                },
                onItemMore = { remoteMenuItem = it },
            )

            keepListeningSection(
                key = "keep_listening",
                title = keepListeningTitle,
                items = keepListeningItems.take(HOME_SECTION_PREVIEW_LIMIT),
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                isPlaying = currentIsPlaying,
                onSongMore = { menuContext = it.toSongActionContext() },
                onItemMore = { remoteMenuItem = it },
                onSeeAll = {
                    onCollectionClick(HomeCollectionKind.KEEP_LISTENING, 0, keepListeningTitle)
                },
            )

            localSongSection(
                key = "forgotten_favorites",
                title = forgottenFavoritesTitle,
                songs = forgottenFavoriteItems.take(HOME_SECTION_PREVIEW_LIMIT),
                isLowEnd = isLowEnd,
                onSongClick = onSongClick,
                currentMediaId = currentMediaMetadata?.id,
                isPlaying = currentIsPlaying,
                onSongMore = { menuContext = it.toSongActionContext() },
                onSeeAll = {
                    onCollectionClick(HomeCollectionKind.FORGOTTEN_FAVORITES, 0, forgottenFavoritesTitle)
                },
            )

            echoBrainPlaylistItems.forEachIndexed { index, playlist ->
                if (playlist.playlist.id == ECHO_BRAIN_MADE_FOR_YOU_ID) {
                    madeForYouHomeSection(
                        key = "echo_brain_${index}_${playlist.playlist.id}",
                        title = playlist.playlist.title,
                        subtitle = null,
                        thumbnail = null,
                        circularThumbnail = false,
                        items = playlist.songs.take(HOME_SECTION_PREVIEW_LIMIT),
                        isLowEnd = isLowEnd,
                        onItemClick = onItemClick,
                        onPlayItem = onPlayItem,
                        currentMediaId = currentMediaMetadata?.id,
                        currentAlbumId = currentMediaMetadata?.album?.id,
                        currentQueueTitle = currentQueueTitle,
                        isPlaying = currentIsPlaying,
                        onSeeAll = {
                            onCollectionClick(
                                HomeCollectionKind.ECHO_BRAIN,
                                index,
                                playlist.playlist.title,
                            )
                        },
                        onItemMore = { remoteMenuItem = it },
                    )
                } else {
                    ytSection(
                        key = "echo_brain_${index}_${playlist.playlist.id}",
                        title = playlist.playlist.title,
                        items = playlist.songs.take(HOME_SECTION_PREVIEW_LIMIT),
                        isLowEnd = isLowEnd,
                        onItemClick = onItemClick,
                        onPlayItem = onPlayItem,
                        currentMediaId = currentMediaMetadata?.id,
                        currentAlbumId = currentMediaMetadata?.album?.id,
                        currentQueueTitle = currentQueueTitle,
                        isPlaying = currentIsPlaying,
                        onSeeAll = {
                            onCollectionClick(
                                HomeCollectionKind.ECHO_BRAIN,
                                index,
                                playlist.playlist.title,
                            )
                        },
                        onItemMore = { remoteMenuItem = it },
                    )
                }
            }
        }

        if (!pinnedSelected) {
            remoteSections.forEachIndexed { index, section ->
                val endpoint = section.endpoint
                    ?.takeIf { candidate -> candidate.browseId.isNotBlank() }
                val opensWrongMoodCollection = endpoint?.browseId == MOOD_AND_GENRES_BROWSE_ID &&
                    section.items.any { it is PlaylistItem }
                if (isDailyDiscoverHomeSection(section, dailyDiscoverTitle)) {
                    dailyDiscoverHomeSection(
                        key = "youtube_${index}_${section.title}",
                        title = section.title,
                        label = section.label,
                        items = section.items,
                        isLowEnd = isLowEnd,
                        currentMediaId = currentMediaMetadata?.id,
                        isPlaying = currentIsPlaying,
                        onPlayItem = { item -> onPlayItem(item) },
                        onTogglePlayPause = { playerConnection?.togglePlayPause() },
                        onPlayAll = { songs ->
                            if (songs.isNotEmpty()) {
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = section.title,
                                        items = songs.map { it.toMediaItem() },
                                    ),
                                )
                            }
                        },
                        onItemMore = { item -> remoteMenuItem = item },
                    )
                } else {
                    ytSection(
                        key = "youtube_${index}_${section.title}",
                        title = section.title,
                        subtitle = section.label,
                        thumbnail = section.thumbnail,
                        circularThumbnail = section.endpoint?.isArtistEndpoint == true,
                        items = section.items.take(HOME_SECTION_PREVIEW_LIMIT),
                        isLowEnd = isLowEnd,
                        onItemClick = onItemClick,
                        onPlayItem = onPlayItem,
                        currentMediaId = currentMediaMetadata?.id,
                        currentAlbumId = currentMediaMetadata?.album?.id,
                        currentQueueTitle = currentQueueTitle,
                        isPlaying = currentIsPlaying,
                        onSeeAll = when {
                            opensWrongMoodCollection -> ({
                                onCollectionClick(
                                    HomeCollectionKind.REMOTE_SECTION,
                                    index,
                                    section.title,
                                )
                            })
                            endpoint != null -> ({ onBrowseClick(endpoint, section.title) })
                            else -> null
                        },
                        onItemMore = { remoteMenuItem = it },
                    )
                }
            }
        }

        homePage?.continuation?.takeUnless { pinnedSelected }?.let { continuation ->
            item(key = "continuation_${selectedChip?.title.orEmpty()}_$continuation") {
                LaunchedEffect(continuation, selectedChip) {
                    viewModel.loadMoreYouTubeItems(continuation)
                }
                Spacer(modifier = Modifier.height(1.dp))
            }
        }

        if (!pinnedSelected && selectedChip == null) {
            ytSection(
                key = "account_playlists",
                title = accountPlaylistsTitle,
                items = accountPlaylistItems.take(HOME_SECTION_PREVIEW_LIMIT),
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                currentQueueTitle = currentQueueTitle,
                isPlaying = currentIsPlaying,
                onSeeAll = {
                    onCollectionClick(HomeCollectionKind.ACCOUNT_PLAYLISTS, 0, accountPlaylistsTitle)
                },
                onItemMore = { remoteMenuItem = it },
            )

            ytSection(
                key = "new_releases",
                title = newReleasesTitle,
                items = newReleaseItems.take(HOME_SECTION_PREVIEW_LIMIT),
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                currentQueueTitle = currentQueueTitle,
                isPlaying = currentIsPlaying,
                onSeeAll = onNewReleasesClick,
                onItemMore = { remoteMenuItem = it },
            )

            moodAndGenresSection(
                title = moodAndGenresTitle,
                items = moodAndGenreItems.take(MOOD_AND_GENRES_PREVIEW_LIMIT),
                onItemClick = { item -> onBrowseClick(item.endpoint, item.title) },
                onSeeAll = onMoodAndGenresClick,
            )

            similarRecommendationItems.forEachIndexed { index, recommendation ->
                ytSection(
                    key = "similar_${index}_${recommendation.title.id}",
                    title = "$similarToTitle ${recommendation.title.title}",
                    items = recommendation.items.take(HOME_SECTION_PREVIEW_LIMIT),
                    isLowEnd = isLowEnd,
                    onItemClick = onItemClick,
                    onPlayItem = onPlayItem,
                    currentMediaId = currentMediaMetadata?.id,
                    currentAlbumId = currentMediaMetadata?.album?.id,
                    currentQueueTitle = currentQueueTitle,
                    isPlaying = currentIsPlaying,
                    onSeeAll = {
                        onCollectionClick(
                            HomeCollectionKind.SIMILAR,
                            index,
                            "$similarToTitle ${recommendation.title.title}",
                        )
                    },
                    onItemMore = { remoteMenuItem = it },
                )
            }

            ytSection(
                key = "community",
                title = communityTitle,
                items = communityPlaylistItems
                    .map { it.playlist }
                    .take(HOME_SECTION_PREVIEW_LIMIT),
                isLowEnd = isLowEnd,
                onItemClick = onItemClick,
                onPlayItem = onPlayItem,
                currentMediaId = currentMediaMetadata?.id,
                currentAlbumId = currentMediaMetadata?.album?.id,
                currentQueueTitle = currentQueueTitle,
                isPlaying = currentIsPlaying,
                onSeeAll = {
                    onCollectionClick(HomeCollectionKind.COMMUNITY, 0, communityTitle)
                },
                onItemMore = { remoteMenuItem = it },
            )
        }

        if (isLoading && !hasVisibleContent) {
            item(key = "loading") {
                FridaLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
        }

        if (!isLoading && !hasVisibleContent) {
            item(key = "empty") { HomeEmptyState() }
        }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .graphicsLayer { translationY = quickReturnState.heightOffset }
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = quickReturnTouchBlocker,
                        indication = null,
                        onClick = {},
                    )
                    .onSizeChanged { size ->
                        if (size.height > 0 && quickReturnHeaderHeightPx != size.height) {
                            quickReturnHeaderHeightPx = size.height
                            val limit = -size.height.toFloat()
                            quickReturnState.heightOffsetLimit = limit
                            quickReturnState.heightOffset =
                                quickReturnState.heightOffset.coerceIn(limit, 0f)
                        }
                    },
            ) {
                HomeHeader(
                    onMenuClick = { showDrawerOverlay = true },
                    onSettingsClick = onSettingsClick,
                    onSupportClick = { showSupportDialog = true },
                    accountImageUrl = accountImageUrl,
                    showActivityIndicator = unreadNotificationCount > 0,
                )
                MoodChipsRow(
                    chips = homePage?.chips,
                    selectedChip = selectedChip,
                    pinnedTitle = pinnedTitle,
                    pinnedSelected = pinnedSelected,
                    onPinnedClick = {
                        val nextSelected = !pinnedSelected
                        pinnedSelected = nextSelected
                        if (nextSelected && selectedChip != null) {
                            viewModel.toggleChip(null)
                        }
                    },
                    onChipClick = {
                        pinnedSelected = false
                        viewModel.toggleChip(it)
                    },
                )
            }
        }
    }

    val closeDrawer = { onClosed: () -> Unit ->
        if (pendingDrawerAction == null) {
            pendingDrawerAction = onClosed
            drawerScope.launch { drawerState.close() }
        }
    }

    if (showDrawerOverlay) {
        val currentArtistId = currentMediaMetadata?.artists?.firstOrNull()?.id
        val currentArtistImageUrl by remember(currentArtistId, viewModel.database) {
            currentArtistId?.let { artistId ->
                viewModel.database.artist(artistId)
                    .map { artist -> artist?.thumbnailUrl }
                    .distinctUntilChanged()
            } ?: flowOf(null)
        }.collectAsStateWithLifecycle(initialValue = null)

        Dialog(
            onDismissRequest = {
                closeDrawer {}
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = true,
            ),
        ) {
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                dialogWindow?.apply {
                    setWindowAnimations(0)
                    setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setDimAmount(0f)
                }
            }
            LaunchedEffect(drawerState) {
                var drawerBecameActive = false
                snapshotFlow {
                    Triple(
                        drawerState.currentValue,
                        drawerState.targetValue,
                        pendingDrawerAction,
                    )
                }.collect { (currentValue, targetValue, pendingAction) ->
                    val isFullyClosed = currentValue == DrawerValue.Closed &&
                        targetValue == DrawerValue.Closed
                    if (!isFullyClosed) {
                        drawerBecameActive = true
                    } else if (drawerBecameActive || pendingAction != null) {
                        pendingDrawerAction = null
                        showDrawerOverlay = false
                        pendingAction?.invoke()
                    }
                }
            }
            LaunchedEffect(drawerState) {
                drawerState.open()
            }
            ModalNavigationDrawer(
                drawerState = drawerState,
                scrimColor = DrawerDefaults.scrimColor,
                drawerContent = {
                    BoxWithConstraints {
                        val drawerWidth = (maxWidth * 0.8f).coerceAtMost(420.dp)
                        HomeNavigationDrawer(
                            modifier = Modifier.width(drawerWidth),
                            currentMediaMetadata = currentMediaMetadata,
                            currentArtistImageUrl = currentArtistImageUrl,
                            accountName = accountName,
                            accountImageUrl = accountImageUrl,
                            unreadNotificationCount = unreadNotificationCount,
                            unreadRecapCount = unreadRecapCount,
                            playerConnection = playerConnection,
                            onArtworkClick = {
                                closeDrawer {
                                    onPlayerClick()
                                }
                            },
                            onTitleClick = {
                                val albumId = currentMediaMetadata?.album?.id
                                    ?.trim()
                                    ?.takeIf {
                                        it.startsWith("MPREb_") || it.startsWith("LOCAL_ALBUM_")
                                    }
                                val searchQuery = currentMediaMetadata?.let { metadata ->
                                    listOfNotNull(
                                        metadata.title.trim().takeIf(String::isNotEmpty),
                                        metadata.artists.firstOrNull()?.name
                                            ?.trim()
                                            ?.takeIf(String::isNotEmpty),
                                    ).joinToString(" ")
                                }.orEmpty()
                                closeDrawer {
                                    if (albumId != null) {
                                        onAlbumClick(albumId)
                                    } else if (searchQuery.isNotEmpty()) {
                                        onSearchClick(searchQuery)
                                    }
                                }
                            },
                            onArtistClick = {
                                val artists = currentMediaMetadata?.artists.orEmpty()
                                val singleArtistId = artists.singleOrNull()
                                    ?.id
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                closeDrawer {
                                    if (artists.size > 1) {
                                        drawerArtistChoices = artists
                                    } else if (singleArtistId != null) {
                                        onArtistClick(singleArtistId)
                                    }
                                }
                            },
                            onRecapClick = {
                                closeDrawer {
                                    onRecapClick()
                                }
                            },
                            onNotificationsClick = {
                                closeDrawer {
                                    onNotificationsClick()
                                }
                            },
                            onStatsClick = {
                                closeDrawer {
                                    onStatsClick()
                                }
                            },
                            onEqualizerClick = {
                                closeDrawer {
                                    onEqualizerClick()
                                }
                            },
                            onAboutClick = {
                                closeDrawer {
                                    onAboutClick()
                                }
                            },
                            onDonateClick = {
                                closeDrawer {
                                    showSupportDialog = true
                                }
                            },
                        )
                    }
                },
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }

    drawerArtistChoices?.let { artists ->
        DrawerArtistPickerSheet(
            artists = artists,
            onDismiss = { drawerArtistChoices = null },
            onSelect = { artistId ->
                drawerArtistChoices = null
                onArtistClick(artistId)
            },
        )
    }

    if (showSupportDialog) {
        SupportCenterSheet(
            billing = supportBillingManager,
            onDismiss = { showSupportDialog = false },
            onWatchAdClick = {
                showSupportDialog = false
                interstitialAdManager.show()
            },
        )
    }
    UniversalSongActionsHost(
        context = menuContext,
        onDismiss = { menuContext = null },
    )
    UniversalYTItemActionsHost(
        item = remoteMenuItem,
        onDismiss = { remoteMenuItem = null },
        onOpen = { remoteMenuItem?.let(onItemClick) },
        onPlay = remoteMenuItem?.homePlayAction(onPlayItem),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun HomeNavigationDrawer(
    currentMediaMetadata: MediaMetadata?,
    currentArtistImageUrl: String?,
    accountName: String,
    accountImageUrl: String?,
    unreadNotificationCount: Int,
    unreadRecapCount: Int,
    playerConnection: com.jagr.fridamusic.playback.PlayerConnection?,
    onArtworkClick: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit,
    onRecapClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDonateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val easterEggScope = rememberCoroutineScope()
    var logoTapCount by remember { mutableIntStateOf(0) }
    val easterEggMessages = listOf(
        stringResource(R.string.frida_easter_egg_queue),
        stringResource(R.string.frida_easter_egg_sniff),
        stringResource(R.string.frida_easter_egg_dj),
        stringResource(R.string.frida_easter_egg_shuffle),
    )
    ModalDrawerSheet(
        modifier = modifier.fillMaxHeight(),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    Column {
                    DrawerContextHeader(
                        currentMediaMetadata = currentMediaMetadata,
                        currentArtistImageUrl = currentArtistImageUrl,
                        accountName = accountName,
                        accountImageUrl = accountImageUrl,
                        onArtworkClick = onArtworkClick,
                        onTitleClick = onTitleClick,
                        onArtistClick = onArtistClick,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationDrawerItem(
                        label = { DrawerItemLabel(stringResource(R.string.support_project)) },
                        selected = false,
                        onClick = onDonateClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.VolunteerActivism,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    NavigationDrawerItem(
                        label = { DrawerItemLabel(stringResource(R.string.notifications)) },
                        selected = false,
                        onClick = onNotificationsClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        badge = { DrawerCountBadge(unreadNotificationCount) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    CastDrawerItem(
                        playerConnection = playerConnection,
                    ) { onCastClick ->
                        NavigationDrawerItem(
                            label = { DrawerItemLabel(stringResource(R.string.chromecast)) },
                            selected = false,
                            onClick = onCastClick,
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Cast,
                                    contentDescription = stringResource(R.string.cast_select_device),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    NavigationDrawerItem(
                        label = { DrawerItemLabel(stringResource(R.string.fridamusic_recap)) },
                        selected = false,
                        onClick = onRecapClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        badge = { DrawerCountBadge(unreadRecapCount) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    NavigationDrawerItem(
                        label = { DrawerItemLabel(stringResource(R.string.stats)) },
                        selected = false,
                        onClick = onStatsClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    NavigationDrawerItem(
                        label = { DrawerItemLabel(stringResource(R.string.equalizer)) },
                        selected = false,
                        onClick = onEqualizerClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    NavigationDrawerItem(
                        label = { DrawerItemLabel(stringResource(R.string.about)) },
                        selected = false,
                        onClick = onAboutClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                }
                item {
                    DrawerBrand(
                        onLogoClick = {
                            logoTapCount += 1
                            if (logoTapCount >= 5) {
                                val message = easterEggMessages[
                                    (logoTapCount - 5) % easterEggMessages.size
                                ]
                                easterEggScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = message,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(
                            start = 24.dp,
                            top = 60.dp,
                            end = 24.dp,
                            bottom = 32.dp,
                        ),
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun DrawerCountBadge(count: Int) {
    if (count > 0) {
        Badge {
            Text(if (count > 99) "99+" else count.toString())
        }
    }
}

@Composable
private fun DrawerBrand(
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brandColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.frida_music_logo_monochrome),
            contentDescription = stringResource(R.string.frida_logo_easter_egg),
            tint = brandColor,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .clickable(onClick = onLogoClick),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "FridaMusic",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = brandColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerArtistPickerSheet(
    artists: List<MediaMetadata.Artist>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.artists),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            artists.forEach { artist ->
                val artistId = artist.id?.trim()?.takeIf(String::isNotEmpty)
                ListItem(
                    headlineContent = {
                        Text(
                            text = artist.name,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = artistId != null) {
                            artistId?.let(onSelect)
                        }
                        .graphicsLayer { alpha = if (artistId != null) 1f else 0.45f },
                )
            }
        }
    }
}

@Composable
private fun DrawerItemLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 2,
    )
}

@Composable
private fun DrawerContextHeader(
    currentMediaMetadata: MediaMetadata?,
    currentArtistImageUrl: String?,
    accountName: String,
    accountImageUrl: String?,
    onArtworkClick: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentMediaMetadata != null) {
        val artworkUrl = currentMediaMetadata.thumbnailUrl?.takeIf(String::isNotBlank)
        val backdropUrl = currentArtistImageUrl?.takeIf(String::isNotBlank) ?: artworkUrl
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(152.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            backdropUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl.resize(720, 720),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(12.dp),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.42f),
                            0.55f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.58f),
                            1f to MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onArtworkClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    artworkUrl?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl.resize(240, 240),
                            contentDescription = currentMediaMetadata.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentMediaMetadata.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onTitleClick)
                            .padding(vertical = 2.dp),
                    )
                    Text(
                        text = currentMediaMetadata.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                enabled = currentMediaMetadata.artists.isNotEmpty(),
                                onClick = onArtistClick,
                            )
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                accountImageUrl = accountImageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FridaMusic",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!accountName.equals("Guest", ignoreCase = true) && accountName.isNotBlank()) {
                    Text(
                        text = accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSupportClick: () -> Unit,
    accountImageUrl: String?,
    showActivityIndicator: Boolean,
) {
    val brandColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(36.dp),
    ) {
        HeaderIconButton(
            icon = Icons.Rounded.Menu,
            description = "Navigation menu",
            onClick = onMenuClick,
            tint = brandColor,
            showActivityIndicator = showActivityIndicator,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = "FridaMusic",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = brandColor,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(
                icon = Icons.Rounded.VolunteerActivism,
                description = stringResource(R.string.support_project),
                onClick = onSupportClick,
                tint = MaterialTheme.colorScheme.primary,
            )
            ProfileButton(
                accountImageUrl = accountImageUrl,
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showActivityIndicator: Boolean = false,
) {
    Box(
        modifier = modifier.size(36.dp),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showActivityIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(11.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun ProfileButton(
    accountImageUrl: String?,
    onClick: () -> Unit,
) {
    ProfileAvatar(
        accountImageUrl = accountImageUrl,
        contentDescription = stringResource(R.string.settings),
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ProfileAvatar(
    accountImageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(0.5f),
        )
        accountImageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun MoodChipsRow(
    chips: List<HomePage.Chip>?,
    selectedChip: HomePage.Chip?,
    pinnedTitle: String,
    pinnedSelected: Boolean,
    onPinnedClick: () -> Unit,
    onChipClick: (HomePage.Chip?) -> Unit,
) {
    val displayChips = chips?.ifEmpty { null } ?: listOf(
        HomePage.Chip(stringResource(R.string.podcasts), null, null),
        HomePage.Chip(stringResource(R.string.energy), null, null),
        HomePage.Chip(stringResource(R.string.mood_entrenar), null, null),
        HomePage.Chip(stringResource(R.string.mood_relax), null, null),
        HomePage.Chip(stringResource(R.string.mood_fiesta), null, null),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item(key = "pinned_items_chip") {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (pinnedSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onPinnedClick),
            ) {
                Text(
                    text = pinnedTitle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (pinnedSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(displayChips, key = { it.title }) { chip ->
            val isSelected = chip == selectedChip
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(enabled = chip.endpoint != null) {
                    onChipClick(if (isSelected) null else chip)
                },
            ) {
                Text(
                    text = chip.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val HOME_SECTION_PREVIEW_LIMIT = 10
private const val MOOD_AND_GENRES_PREVIEW_LIMIT = 3
private const val MOOD_AND_GENRES_BROWSE_ID = "FEmusic_moods_and_genres"
private const val ECHO_BRAIN_MADE_FOR_YOU_ID = "echo_brain_mix_local"
private val DAILY_DISCOVERY_TERM = Regex(
    """\b(?:discover|discovery|discoveries|descubrimiento|descubrimientos)\b""",
)
private val DAILY_CADENCE_TERM = Regex(
    """\b(?:daily|diario|diaria|diarios|diarias|hoy)\b|\b(?:cada|del)\s+dia\b""",
)

private fun isDailyDiscoverHomeSection(
    section: HomePage.Section,
    localizedDailyDiscoverTitle: String,
): Boolean {
    fun String.normalizedForDailyDiscoverMatch(): String = Normalizer
        .normalize(this, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .trim()

    val normalizedTitle = section.title.normalizedForDailyDiscoverMatch()
    val normalizedLocalizedTitle = localizedDailyDiscoverTitle.normalizedForDailyDiscoverMatch()
    if (normalizedTitle == normalizedLocalizedTitle) return true

    val mentionsDiscovery = DAILY_DISCOVERY_TERM.containsMatchIn(normalizedTitle)
    val mentionsDailyCadence = DAILY_CADENCE_TERM.containsMatchIn(normalizedTitle)
    return mentionsDiscovery && mentionsDailyCadence
}

private fun LazyListScope.moodAndGenresSection(
    title: String,
    items: List<MoodAndGenres.Item>,
    onItemClick: (MoodAndGenres.Item) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "mood_and_genres_header") {
        HomeSectionHeader(title = title, onSeeAll = onSeeAll)
    }
    item(key = "mood_and_genres_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = items,
                key = { item -> "mood_${item.endpoint.browseId}_${item.title}" },
            ) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .width(168.dp)
                        .height(72.dp)
                        .clickable { onItemClick(item) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(Color(item.stripeColor)),
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.localSongSection(
    key: String,
    title: String,
    subtitle: String? = null,
    songs: List<Song>,
    isLowEnd: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
    onSongMore: (Song) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    if (songs.isEmpty()) return

    item(key = "${key}_header") {
        HomeSectionHeader(title = title, subtitle = subtitle, onSeeAll = onSeeAll)
    }
    item(key = "${key}_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = songs,
                key = { index, song -> "${key}_${song.id}_$index" },
            ) { _, song ->
                HomeMediaCard(
                    type = HomeContentType.SONG,
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name },
                    imageUrl = (song.song.thumbnailUrl
                        ?: "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg")
                        .resize(width = if (isLowEnd) 320 else 480),
                    isActive = song.id == currentMediaId,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song, songs) },
                    onPlay = { onSongClick(song, songs) },
                    onMoreClick = { onSongMore(song) },
                    isLocal = song.song.isLocal,
                )
            }
        }
    }
}

private val KEEP_LISTENING_CARD_MIN_WIDTH = 160.dp
private val KEEP_LISTENING_CARD_MAX_WIDTH = 176.dp
private val KEEP_LISTENING_CARD_MIN_HEIGHT = 225.dp
private val KEEP_LISTENING_CARD_MAX_HEIGHT = 245.dp

private fun LazyListScope.keepListeningSection(
    key: String,
    title: String,
    items: List<LocalItem>,
    isLowEnd: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onPlayItem: (YTItem) -> Unit,
    currentMediaId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onSongMore: (Song) -> Unit,
    onItemMore: (YTItem) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val songQueue = items.filterIsInstance<Song>()

    item(key = "${key}_header") {
        HomeSectionHeader(title = title, onSeeAll = onSeeAll)
    }
    item(key = "${key}_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { index, localItem -> "${key}_${localItem.id}_$index" },
            ) { _, localItem ->
                val subtitle = when (localItem) {
                    is Song -> localItem.artists.joinToString(", ") { it.name }
                    is LocalAlbum -> localItem.artists.joinToString(", ") { it.name }
                    is LocalArtist -> stringResource(R.string.artists)
                    is LocalPlaylist -> stringResource(R.string.playlists)
                }
                val type = when (localItem) {
                    is Song -> HomeContentType.SONG
                    is LocalAlbum -> HomeContentType.ALBUM
                    is LocalArtist -> HomeContentType.ARTIST
                    is LocalPlaylist -> HomeContentType.PLAYLIST
                }
                val remoteItem = localItem.toYTItemOrNull()
                KeepListeningCard(
                    type = type,
                    title = localItem.title,
                    subtitle = subtitle,
                    imageUrl = localItem.thumbnailUrl.orEmpty()
                        .resize(width = if (isLowEnd) 320 else 480),
                    isActive = when (localItem) {
                        is Song -> localItem.id == currentMediaId
                        is LocalAlbum -> localItem.id == currentAlbumId
                        else -> false
                    },
                    isPlaying = isPlaying,
                    onClick = {
                        when (localItem) {
                            is Song -> onSongClick(localItem, songQueue)
                            else -> remoteItem?.let(onItemClick)
                        }
                    },
                    onPlay = when {
                        localItem is Song -> ({ onSongClick(localItem, songQueue) })
                        remoteItem is AlbumItem && remoteItem.playlistId.isNotBlank() ->
                            ({ onPlayItem(remoteItem) })
                        else -> null
                    },
                    onMoreClick = when {
                        localItem is Song -> ({ onSongMore(localItem) })
                        remoteItem != null -> ({ onItemMore(remoteItem) })
                        else -> null
                    },
                    isLocal = when (localItem) {
                        is Song -> localItem.song.isLocal
                        is LocalAlbum -> localItem.album.isLocal
                        is LocalArtist -> localItem.artist.isLocal
                        is LocalPlaylist -> localItem.playlist.isLocal
                    },
                )
            }
        }
    }
}

@Composable
private fun KeepListeningCard(
    type: HomeContentType,
    title: String,
    subtitle: String?,
    imageUrl: String,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    onMoreClick: (() -> Unit)?,
    isLocal: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = ((screenWidth - 50.dp) / 2f).coerceIn(
        KEEP_LISTENING_CARD_MIN_WIDTH,
        KEEP_LISTENING_CARD_MAX_WIDTH,
    )
    val cardHeight = (cardWidth * 1.4f).coerceIn(
        KEEP_LISTENING_CARD_MIN_HEIGHT,
        KEEP_LISTENING_CARD_MAX_HEIGHT,
    )
    val dedicatedAction = if (isActive && playerConnection != null) {
        playerConnection::togglePlayPause
    } else {
        onPlay
    }
    val isArtist = type == HomeContentType.ARTIST

    Surface(
        modifier = Modifier
            .size(
                width = cardWidth,
                height = cardHeight,
            )
            .universalMediaClickable(
                onClick = onClick,
                onLongClick = onMoreClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            )

            if (onMoreClick != null && !isArtist) {
                SongOptionsButton(
                    onClick = onMoreClick,
                    modifier = Modifier.align(Alignment.TopEnd),
                    iconColor = Color.White,
                    containerColor = Color.Black.copy(alpha = 0.38f),
                )
            }

            if (isLocal) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.filter_local),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            when (type) {
                HomeContentType.SONG -> {
                    if (isActive) {
                        MediaPlaybackIndicator(
                            isPlaying = isPlaying,
                            onClick = dedicatedAction,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else if (dedicatedAction != null) {
                        MediaArtworkActionButton(
                            icon = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            onClick = dedicatedAction,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                HomeContentType.ALBUM -> {
                    if (isActive) {
                        MediaPlaybackIndicator(
                            isPlaying = isPlaying,
                            onClick = dedicatedAction,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(7.dp),
                        )
                    } else if (dedicatedAction != null) {
                        MediaArtworkActionButton(
                            icon = Icons.Rounded.Album,
                            contentDescription = stringResource(R.string.play),
                            onClick = dedicatedAction,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(7.dp),
                        )
                    }
                }

                HomeContentType.PLAYLIST -> {
                    MediaArtworkActionButton(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.playlists),
                        onClick = dedicatedAction,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(8.dp),
                    )
                }

                HomeContentType.ARTIST -> Unit
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.localItemSection(
    key: String,
    title: String,
    items: List<LocalItem>,
    isLowEnd: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onPlayItem: (YTItem) -> Unit,
    currentMediaId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
    onSongMore: (Song) -> Unit,
    onItemMore: (YTItem) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val songQueue = items.filterIsInstance<Song>()

    item(key = "${key}_header") {
        HomeSectionHeader(title = title, onSeeAll = onSeeAll)
    }
    item(key = "${key}_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { index, localItem -> "${key}_${localItem.id}_$index" },
            ) { _, localItem ->
                val subtitle = when (localItem) {
                    is Song -> localItem.artists.joinToString(", ") { it.name }
                    is LocalAlbum -> localItem.artists.joinToString(", ") { it.name }
                    is LocalArtist -> stringResource(R.string.artists)
                    is LocalPlaylist -> stringResource(R.string.playlists)
                }
                val type = when (localItem) {
                    is Song -> HomeContentType.SONG
                    is LocalAlbum -> HomeContentType.ALBUM
                    is LocalArtist -> HomeContentType.ARTIST
                    is LocalPlaylist -> HomeContentType.PLAYLIST
                }
                val remoteItem = localItem.toYTItemOrNull()
                HomeMediaCard(
                    type = type,
                    title = localItem.title,
                    subtitle = subtitle,
                    imageUrl = localItem.thumbnailUrl.orEmpty()
                        .resize(width = if (isLowEnd) 320 else 480),
                    isActive = when (localItem) {
                        is Song -> localItem.id == currentMediaId
                        is LocalAlbum -> localItem.id == currentAlbumId
                        else -> false
                    },
                    isPlaying = isPlaying,
                    onClick = {
                        when (localItem) {
                            is Song -> onSongClick(localItem, songQueue)
                            else -> remoteItem?.let(onItemClick)
                        }
                    },
                    onPlay = when {
                        localItem is Song -> ({ onSongClick(localItem, songQueue) })
                        remoteItem is AlbumItem && remoteItem.playlistId.isNotBlank() ->
                            ({ onPlayItem(remoteItem) })
                        else -> null
                    },
                    onMoreClick = when {
                        localItem is Song -> ({ onSongMore(localItem) })
                        remoteItem != null -> ({ onItemMore(remoteItem) })
                        else -> null
                    },
                    isLocal = when (localItem) {
                        is Song -> localItem.song.isLocal
                        is LocalAlbum -> localItem.album.isLocal
                        is LocalArtist -> localItem.artist.isLocal
                        is LocalPlaylist -> localItem.playlist.isLocal
                    },
                )
            }
        }
    }
}

private fun LazyListScope.dailyDiscoverHomeSection(
    key: String,
    title: String,
    label: String?,
    items: List<YTItem>,
    isLowEnd: Boolean,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlayItem: (SongItem) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayAll: (List<SongItem>) -> Unit,
    onItemMore: (SongItem) -> Unit,
) {
    val songs = items
        .filterIsInstance<SongItem>()
        .distinctBy { item -> item.id }
    if (songs.isEmpty()) return

    item(key = "${key}_daily_discover_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 15.sp,
                    maxFontSize = 22.sp,
                    stepSize = 0.5.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
            OutlinedButton(
                onClick = { onPlayAll(songs) },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.height(36.dp),
                shape = CircleShape,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.play_all),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
    item(key = "${key}_daily_discover_row") {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth - 48.dp)
                .coerceAtMost(344.dp)
                .coerceAtLeast(280.dp)
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(20.dp)
            val rowState = rememberLazyListState()
            val focusedIndex by remember(rowState) {
                derivedStateOf {
                    val layoutInfo = rowState.layoutInfo
                    val viewportCenter =
                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    layoutInfo.visibleItemsInfo.minByOrNull { visibleItem ->
                        abs((visibleItem.offset + visibleItem.size / 2) - viewportCenter)
                    }?.index ?: 0
                }
            }
            val rowIsSettled by remember(rowState) {
                derivedStateOf { !rowState.isScrollInProgress }
            }
            val snapFlingBehavior = rememberSnapFlingBehavior(
                lazyListState = rowState,
                snapPosition = SnapPosition.Center,
            )

            LazyRow(
                state = rowState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier.padding(bottom = 22.dp),
                contentPadding = PaddingValues(horizontal = sidePadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(
                    items = songs.take(HOME_SECTION_PREVIEW_LIMIT),
                    key = { _, item -> "${item::class.qualifiedName}:${item.id}" },
                ) { index, item ->
                    val isCurrent = item.id == currentMediaId
                    val primaryAction = if (isCurrent) {
                        onTogglePlayPause
                    } else {
                        { onPlayItem(item) }
                    }
                    HomeDailyDiscoverCard(
                        item = item,
                        reason = label,
                        cardWidth = cardWidth,
                        isLowEnd = isLowEnd,
                        isFocused = rowIsSettled && index == focusedIndex,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        primaryAction = primaryAction,
                        onLongClick = { onItemMore(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDailyDiscoverCard(
    item: SongItem,
    reason: String?,
    cardWidth: Dp,
    isLowEnd: Boolean,
    isFocused: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    primaryAction: () -> Unit,
    onLongClick: () -> Unit,
) {
    val density = LocalDensity.current
    val thumbnailWidthPx = remember(cardWidth, density.density, isLowEnd) {
        with(density) { cardWidth.roundToPx() }
            .coerceAtMost(if (isLowEnd) 720 else 1024)
            .coerceAtLeast(1)
    }
    val imageUrl = item.thumbnail.resize(width = thumbnailWidthPx)
    val artworkPainter = rememberAsyncImagePainter(model = imageUrl)
    val artists = item.artists.joinToString(", ") { artist -> artist.name }
    val bottomCaption = artists.takeIf(String::isNotBlank)
        ?: reason?.trim()?.takeIf(String::isNotEmpty)
    val zoomProgress = remember(item.id) { Animatable(0f) }
    val artworkScale = 1f + (0.16f * zoomProgress.value)
    val artworkTranslationY = with(LocalDensity.current) {
        (-16).dp.toPx()
    } * zoomProgress.value

    LaunchedEffect(item.id, isFocused) {
        zoomProgress.snapTo(0f)
        if (isFocused) {
            delay(700)
            zoomProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 15_000,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(0.94f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .universalMediaClickable(
                onClick = primaryAction,
                onLongClick = onLongClick,
            ),
    ) {
        Image(
            painter = artworkPainter,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                    translationY = artworkTranslationY
                },
        )

        if (!isLowEnd) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(DAILY_DISCOVER_TOP_BLUR_FRACTION)
                    .clipToBounds(),
            ) {
                Image(
                    painter = artworkPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = artworkScale
                            scaleY = artworkScale
                            translationY = artworkTranslationY
                            transformOrigin = TransformOrigin(
                                pivotFractionX = 0.5f,
                                pivotFractionY = 0.5f / DAILY_DISCOVER_TOP_BLUR_FRACTION,
                            )
                        }
                        .dailyDiscoverEdgeFade(isTop = true)
                        .blur(12.dp),
                )
            }
        }

        Text(
            text = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = 18.dp, vertical = 17.dp),
            style = MaterialTheme.typography.titleLarge,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = 22.sp,
                stepSize = 0.5.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )

        if (!isLowEnd) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(DAILY_DISCOVER_BOTTOM_BLUR_FRACTION)
                    .clipToBounds(),
            ) {
                Image(
                    painter = artworkPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = artworkScale
                            scaleY = artworkScale
                            translationY = artworkTranslationY
                            transformOrigin = TransformOrigin(
                                pivotFractionX = 0.5f,
                                pivotFractionY = 1f -
                                    (0.5f / DAILY_DISCOVER_BOTTOM_BLUR_FRACTION),
                            )
                        }
                        .dailyDiscoverEdgeFade(isTop = false)
                        .blur(12.dp),
                )
            }
        }

        if (bottomCaption != null) {
            Text(
                text = bottomCaption,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
                    maxFontSize = 16.sp,
                    stepSize = 0.5.sp,
                ),
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 3,
                overflow = TextOverflow.Clip,
            )
        }

        if (isCurrent) {
            MediaPlaybackIndicator(
                isPlaying = isPlaying,
                onClick = primaryAction,
                modifier = Modifier.align(Alignment.Center),
                indicatorColor = Color.White,
                pausedIcon = Icons.Rounded.PlayArrow,
            )
        }
    }
}

private const val DAILY_DISCOVER_TOP_BLUR_FRACTION = 0.30f
private const val DAILY_DISCOVER_BOTTOM_BLUR_FRACTION = 0.34f

private fun Modifier.dailyDiscoverEdgeFade(isTop: Boolean): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = if (isTop) {
                    Brush.verticalGradient(
                        0f to Color.White,
                        0.5f to Color.White.copy(alpha = 0.90f),
                        1f to Color.Transparent,
                    )
                } else {
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.12f to Color.Transparent,
                        0.56f to Color.White.copy(alpha = 0.90f),
                        1f to Color.White,
                    )
                },
                blendMode = BlendMode.DstIn,
            )
        }

private val MADE_FOR_YOU_CARD_MIN_WIDTH = 216.dp
private val MADE_FOR_YOU_CARD_MAX_WIDTH = 318.dp
private val MADE_FOR_YOU_CARD_HEIGHT = 72.dp
private val MADE_FOR_YOU_ARTWORK_SIZE = 60.dp
private val MADE_FOR_YOU_ROW_SPACING = 10.dp

private fun LazyListScope.madeForYouHomeSection(
    key: String,
    title: String,
    subtitle: String?,
    thumbnail: String?,
    circularThumbnail: Boolean,
    items: List<YTItem>,
    isLowEnd: Boolean,
    onItemClick: (YTItem) -> Unit,
    onPlayItem: (YTItem) -> Unit,
    currentMediaId: String?,
    currentAlbumId: String?,
    currentQueueTitle: String?,
    isPlaying: Boolean,
    onSeeAll: (() -> Unit)?,
    onItemMore: (YTItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "${key}_made_for_you_header") {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle,
            thumbnail = thumbnail,
            circularThumbnail = circularThumbnail,
            onSeeAll = onSeeAll,
        )
    }
    item(key = "${key}_made_for_you_grid") {
        LazyHorizontalStaggeredGrid(
            rows = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .height(
                    (MADE_FOR_YOU_CARD_HEIGHT * 2) +
                        MADE_FOR_YOU_ROW_SPACING +
                        18.dp,
                )
                .padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalItemSpacing = 10.dp,
            verticalArrangement = Arrangement.spacedBy(MADE_FOR_YOU_ROW_SPACING),
        ) {
            items(
                count = items.size,
                key = { index -> "${key}_${items[index].madeForYouStableKey()}" },
            ) { index ->
                val item = items[index]
                MadeForYouCompactCard(
                    item = item,
                    isLowEnd = isLowEnd,
                    currentMediaId = currentMediaId,
                    currentAlbumId = currentAlbumId,
                    currentQueueTitle = currentQueueTitle,
                    isPlaying = isPlaying,
                    onClick = { onItemClick(item) },
                    onPlay = item.homePlayAction(onPlayItem),
                    onMore = { onItemMore(item) },
                )
            }
        }
    }
}

private fun YTItem.madeForYouStableKey(): String = when (this) {
    is SongItem -> "song:$id"
    is AlbumItem -> "album:$id"
    is PlaylistItem -> "playlist:$id"
    is ArtistItem -> "artist:$id"
}

@Composable
private fun MadeForYouCompactCard(
    item: YTItem,
    isLowEnd: Boolean,
    currentMediaId: String?,
    currentAlbumId: String?,
    currentQueueTitle: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    onMore: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current
    val metadata = when (item) {
        is SongItem -> item.artists.joinToString(", ") { artist -> artist.name }
        is AlbumItem -> buildList {
            item.artists
                ?.joinToString(", ") { artist -> artist.name }
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
            item.year?.toString()?.let(::add)
        }.joinToString(" • ").ifBlank { stringResource(R.string.album_text) }
        is PlaylistItem -> item.author?.name ?: stringResource(R.string.playlists)
        is ArtistItem -> stringResource(R.string.artists)
    }
    val isActive = when (item) {
        is SongItem -> item.id == currentMediaId
        is AlbumItem -> item.browseId == currentAlbumId
        is PlaylistItem -> currentMediaId != null && item.title == currentQueueTitle
        is ArtistItem -> false
    }
    val primaryAction = if (isActive && playerConnection != null) {
        playerConnection::togglePlayPause
    } else {
        onPlay
    }
    val cardClickAction = if (item is SongItem && isActive) {
        primaryAction ?: onClick
    } else {
        onClick
    }
    val titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val metadataStyle = MaterialTheme.typography.bodySmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val measuredTextWidth = with(density) {
        maxOf(
            textMeasurer.measure(
                text = AnnotatedString(item.title),
                style = titleStyle,
                softWrap = false,
                maxLines = 1,
            ).size.width,
            textMeasurer.measure(
                text = AnnotatedString(metadata),
                style = metadataStyle,
                softWrap = false,
                maxLines = 1,
            ).size.width,
        ).toDp()
    }
    val fixedContentWidth = 80.dp + if (primaryAction != null) 40.dp else 0.dp
    val cardWidth = (fixedContentWidth + measuredTextWidth)
        .coerceIn(MADE_FOR_YOU_CARD_MIN_WIDTH, MADE_FOR_YOU_CARD_MAX_WIDTH)
    val artworkShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(13.dp)

    Surface(
        modifier = Modifier
            .width(cardWidth)
            .height(MADE_FOR_YOU_CARD_HEIGHT)
            .universalMediaClickable(
                onClick = cardClickAction,
                onLongClick = onMore,
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = item.thumbnail?.resize(width = if (isLowEnd) 160 else 240).orEmpty(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(MADE_FOR_YOU_ARTWORK_SIZE)
                    .clip(artworkShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    style = titleStyle,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata,
                    style = metadataStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (primaryAction != null) {
                if (isActive) {
                    MediaPlaybackIndicator(
                        isPlaying = isPlaying,
                        onClick = primaryAction,
                        buttonSize = 32.dp,
                        indicatorSize = 18.dp,
                        indicatorColor = Color.White,
                        pausedIcon = Icons.Rounded.PlayArrow,
                    )
                } else {
                    MediaArtworkActionButton(
                        icon = when (item) {
                            is AlbumItem -> Icons.Rounded.Album
                            is PlaylistItem -> Icons.AutoMirrored.Rounded.QueueMusic
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = stringResource(R.string.play),
                        onClick = primaryAction,
                        buttonSize = 32.dp,
                        iconSize = 18.dp,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.ytSection(
    key: String,
    title: String,
    subtitle: String? = null,
    thumbnail: String? = null,
    circularThumbnail: Boolean = false,
    items: List<YTItem>,
    isLowEnd: Boolean,
    onItemClick: (YTItem) -> Unit,
    onPlayItem: (YTItem) -> Unit,
    currentMediaId: String?,
    currentAlbumId: String?,
    currentQueueTitle: String?,
    isPlaying: Boolean,
    onSeeAll: (() -> Unit)? = null,
    onItemMore: (YTItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "${key}_header") {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle,
            thumbnail = thumbnail,
            circularThumbnail = circularThumbnail,
            onSeeAll = onSeeAll,
        )
    }
    item(key = "${key}_row") {
        LazyRow(
            modifier = Modifier.padding(bottom = 18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${key}_${item.id}_$index" },
            ) { _, item ->
                YTContentCard(
                    item = item,
                    currentMediaId = currentMediaId,
                    currentAlbumId = currentAlbumId,
                    currentQueueTitle = currentQueueTitle,
                    isPlaying = isPlaying,
                    onClick = { onItemClick(item) },
                    onPlay = when (item) {
                        is SongItem -> ({ onPlayItem(item) })
                        is AlbumItem -> item.playlistId
                            .takeIf(String::isNotBlank)
                            ?.let { { onPlayItem(item) } }
                        is PlaylistItem -> (
                            item.playEndpoint ?: item.radioEndpoint ?: item.shuffleEndpoint
                            )?.let { { onPlayItem(item) } }
                        is ArtistItem -> null
                    },
                    onMore = onItemMore,
                    thumbnailWidth = if (isLowEnd) 320 else 480,
                )
            }
        }
    }
}

private fun YTItem.homePlayAction(onPlayItem: (YTItem) -> Unit): (() -> Unit)? = when (this) {
    is SongItem -> ({ onPlayItem(this) })
    is AlbumItem -> playlistId.takeIf(String::isNotBlank)?.let { { onPlayItem(this) } }
    is PlaylistItem -> (playEndpoint ?: radioEndpoint ?: shuffleEndpoint)
        ?.let { { onPlayItem(this) } }
    is ArtistItem -> null
}

private fun LocalItem.toYTItemOrNull(): YTItem? = when (this) {
    is LocalAlbum -> AlbumItem(
        browseId = id,
        playlistId = album.playlistId.orEmpty(),
        title = title,
        artists = artists.map { artist -> YTArtist(name = artist.name, id = artist.id) },
        year = album.year,
        thumbnail = thumbnailUrl.orEmpty(),
    )
    is LocalArtist -> ArtistItem(
        id = id,
        title = title,
        thumbnail = thumbnailUrl,
        channelId = artist.channelId,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
    is LocalPlaylist -> null
    is Song -> null
}

@Composable
private fun HomeEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
