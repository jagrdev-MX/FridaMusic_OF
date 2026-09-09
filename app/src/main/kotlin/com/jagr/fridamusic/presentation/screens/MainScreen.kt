package com.jagr.fridamusic.presentation.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.LocalSongActionsNavigation
import com.jagr.fridamusic.presentation.SongActionsNavigation
import com.jagr.fridamusic.presentation.components.FabMenuAction
import com.jagr.fridamusic.presentation.components.FabOverflowMenu
import com.jagr.fridamusic.presentation.components.InteractivePlayer
import com.jagr.fridamusic.presentation.components.ModernBottomNav
import com.jagr.fridamusic.presentation.components.RootDestination
import com.jagr.fridamusic.presentation.playCachedSong
import com.jagr.fridamusic.presentation.playSong
import com.jagr.fridamusic.presentation.playYTItem
import com.jagr.fridamusic.notifications.RecommendationNotificationManager
import com.jagr.fridamusic.models.MediaMetadata
import com.jagr.fridamusic.playback.PlayerConnection
import com.jagr.fridamusic.playback.queues.YouTubeAlbumRadio
import com.jagr.fridamusic.playback.queues.YouTubeQueue
import com.jagr.fridamusic.viewmodels.GlobalShuffleSelection
import com.jagr.fridamusic.viewmodels.HomeViewModel
import com.jagr.fridamusic.viewmodels.PlaylistsViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.shazamkit.models.RecognitionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun MainScreen(
    navController: NavHostController = rememberNavController(),
    pendingDeepLink: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    var retainedRoot by rememberSaveable { mutableStateOf(RootDestination.HOME) }
    val routeRoot = RootDestination.fromRoute(currentRoute)
    val currentRoot = routeRoot
        ?: retainedRoot.takeIf { root -> navController.hasRootOnBackStack(root) }
        ?: RootDestination.HOME
    var homeReselectToken by remember { mutableIntStateOf(0) }
    var searchReselectToken by remember { mutableIntStateOf(0) }
    var libraryReselectToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentRoot) {
        retainedRoot = currentRoot
    }
    LaunchedEffect(pendingDeepLink) {
        val deepLink = pendingDeepLink ?: return@LaunchedEffect
        navController.handleDeepLink(Intent(Intent.ACTION_VIEW, deepLink))
        onDeepLinkConsumed()
    }
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = hiltViewModel()
    val fabScope = rememberCoroutineScope()
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var shuffleJob by remember { mutableStateOf<Job?>(null) }
    val playRemoteItem: (YTItem) -> Unit = { item ->
        if (playerConnection?.playHomeItem(item) != true) {
            Toast.makeText(
                context,
                R.string.recommendation_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val playerPlaylistsViewModel = if (currentRoute == "now_playing" && backStackEntry != null) {
        hiltViewModel<PlaylistsViewModel>(backStackEntry!!)
    } else {
        null
    }

    val showOverlay = currentRoute != "now_playing" &&
            currentRoute != "settings" &&
            currentRoute != "login" &&
            currentRoute != "spotify_import" &&
            currentRoute != "stats" &&
            currentRoute != "history" &&
            currentRoute != "notifications" &&
            currentRoute != RECAP_ROUTE &&
            currentRoute != "about" &&
            currentRoute != "music_recognition"

    CompositionLocalProvider(
        LocalSongActionsNavigation provides SongActionsNavigation(
            openAlbum = { id -> navController.navigate("album/${Uri.encode(id)}") },
            openArtist = { id -> navController.navigate("artist/${Uri.encode(id)}") },
            openSearchResult = navController::navigateToSearchResult,
        ),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            composable(
                route = "home",
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = RecommendationNotificationManager.HOME_DEEP_LINK_PATTERN
                    },
                ),
            ) {
                HomeScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onItemClick = { item ->
                        navController.handleYTItemClick(
                            item = item,
                            playSong = playerConnection?.let { connection ->
                                { connection.playYTItem(item) }
                            },
                            onUnavailable = {
                                Toast.makeText(
                                    context,
                                    R.string.recommendation_unavailable,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    },
                    onPlayItem = playRemoteItem,
                    onBrowseClick = { endpoint, title ->
                        navController.navigateToBrowse(endpoint, title)
                    },
                    onNewReleasesClick = {
                        navController.navigate("new_releases")
                    },
                    onMoodAndGenresClick = {
                        navController.navigate("mood_and_genres")
                    },
                    onCollectionClick = { kind, index, title ->
                        navController.navigate(
                            "home_collection/${kind.name}/$index?title=${Uri.encode(title)}",
                        )
                    },
                    onSettingsClick = { navController.navigate("settings") },
                    onHistoryClick = { navController.navigate("history") },
                    onRecapClick = { navController.navigate("recap") },
                    onNotificationsClick = { navController.navigate("notifications") },
                    reselectToken = homeReselectToken,
                    viewModel = homeViewModel,
                )
            }
            composable(
                route = "home_collection/{kind}/{index}?title={title}",
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("index") { type = NavType.IntType },
                    navArgument("title") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val kind = entry.arguments?.getString("kind")
                    ?.let { runCatching { HomeCollectionKind.valueOf(it) }.getOrNull() }
                    ?: HomeCollectionKind.QUICK_PICKS
                HomeCollectionScreen(
                    kind = kind,
                    index = entry.arguments?.getInt("index") ?: 0,
                    title = entry.arguments?.getString("title").orEmpty(),
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onLocalItemClick = { item -> navController.navigateToDetail(item) },
                    onItemClick = { item ->
                        navController.handleYTItemClick(
                            item = item,
                            playSong = playerConnection?.let { connection ->
                                { connection.playYTItem(item) }
                            },
                        )
                    },
                    onPlayItem = playRemoteItem,
                    onBack = { navController.popBackStack() },
                    viewModel = homeViewModel,
                )
            }
            composable("search") {
                SearchScreen(
                    onSearchSubmit = navController::navigateToSearchResult,
                    onItemClick = { item ->
                        navController.handleYTItemClick(
                            item,
                            playerConnection?.let { pc -> { pc.playYTItem(item) } },
                        )
                    },
                    reselectToken = searchReselectToken,
                )
            }
            composable(
                route = "search_result/{query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "fridamusic://search/{query}" }),
            ) {
                SearchResultScreen(
                    onItemClick = { item ->
                        navController.handleYTItemClick(
                            item,
                            playerConnection?.let { pc -> { pc.playYTItem(item) } },
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "browse/{browseId}?params={params}&title={title}&maxItems={maxItems}&artistItems={artistItems}",
                arguments = listOf(
                    navArgument("browseId") { type = NavType.StringType },
                    navArgument("params") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("title") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("maxItems") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("artistItems") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                BrowseScreen(
                    requestedTitle = entry.arguments?.getString("title"),
                    maxItems = entry.arguments?.getInt("maxItems")?.takeIf { it > 0 },
                    onItemClick = { item ->
                        navController.handleYTItemClick(
                            item = item,
                            playSong = playerConnection?.let { connection ->
                                { connection.playYTItem(item) }
                            },
                        )
                    },
                    onPlayItem = playRemoteItem,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("new_releases") {
                NewReleaseScreen(
                    onAlbumClick = { album ->
                        navController.handleYTItemClick(
                            item = album,
                            playSong = null,
                        )
                    },
                    onPlayAlbum = playRemoteItem,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("mood_and_genres") {
                MoodAndGenresScreen(
                    onItemClick = { endpoint, title ->
                        navController.navigateToBrowse(endpoint, title)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "library",
                exitTransition = {
                    if (targetState.destination.route == "stats") ExitTransition.None else null
                },
                popEnterTransition = {
                    if (initialState.destination.route == "stats") EnterTransition.None else null
                },
            ) {
                LibraryScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onCachedSongClick = { song, queue -> playerConnection?.playCachedSong(song, queue) },
                    onLocalItemClick = { item -> navController.navigateToDetail(item) },
                    onStatsClick = { navController.navigate("stats") },
                    reselectToken = libraryReselectToken,
                )
            }
            composable(
                route = "settings",
                exitTransition = {
                    if (targetState.destination.route == "stats") ExitTransition.None else null
                },
                popEnterTransition = {
                    if (initialState.destination.route == "stats") EnterTransition.None else null
                },
            ) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToLogin = { navController.navigate("login") },
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToAbout = { navController.navigate("about") },
                )
            }
            composable(
                route = "history",
                deepLinks = listOf(
                    navDeepLink { uriPattern = RecommendationNotificationManager.HISTORY_DEEP_LINK_PATTERN },
                ),
            ) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onLocalSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onRemoteSongClick = { song -> playerConnection?.playYTItem(song) },
                )
            }
            composable(
                route = RECAP_ROUTE,
                arguments = listOf(
                    navArgument("period") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("offset") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = RecommendationNotificationManager.RECAP_DEEP_LINK_PATTERN },
                ),
            ) { entry ->
                RecapScreen(
                    onBack = { navController.popBackStack() },
                    initialPeriod = entry.arguments?.getString("period"),
                    initialOffset = entry.arguments?.getInt("offset"),
                    onSongClick = { song ->
                        playerConnection?.playQueue(
                            YouTubeQueue(
                                endpoint = WatchEndpoint(videoId = song.id),
                                preloadItem = MediaMetadata(
                                    id = song.id,
                                    title = song.title,
                                    artists = listOfNotNull(
                                        song.artistName?.let { name ->
                                            MediaMetadata.Artist(id = null, name = name)
                                        },
                                    ),
                                    duration = -1,
                                    thumbnailUrl = song.thumbnailUrl,
                                    musicVideoType = if (song.isVideo) "MUSIC_VIDEO_TYPE_OMV" else null,
                                ),
                            ),
                        )
                    },
                )
            }
            composable(
                route = "notifications",
                deepLinks = listOf(
                    navDeepLink { uriPattern = RecommendationNotificationManager.NOTIFICATIONS_DEEP_LINK_PATTERN },
                ),
            ) {
                NotificationHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDeepLink = { uri ->
                        navController.navigateFromNotificationCenter(uri)
                    },
                )
            }
            composable("spotify_import") {
                SpotifyImportScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "stats",
                enterTransition = { EnterTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = {
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = 160,
                            easing = FastOutLinearInEasing,
                        ),
                    ) + scaleOut(
                        targetScale = 0.99f,
                        animationSpec = tween(
                            durationMillis = 160,
                            easing = FastOutLinearInEasing,
                        ),
                    )
                },
            ) {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToArtist = { id ->
                        navController.navigate("artist/${java.net.URLEncoder.encode(id, "UTF-8")}")
                    },
                    onNavigateToAlbum = { id ->
                        navController.navigate("album/${java.net.URLEncoder.encode(id, "UTF-8")}")
                    },
                    onSongClick = { song ->
                        playerConnection?.playQueue(
                            com.jagr.fridamusic.playback.queues.YouTubeQueue(
                                endpoint = com.music.innertube.models.WatchEndpoint(videoId = song.id),
                                preloadItem = com.jagr.fridamusic.models.MediaMetadata(
                                    id = song.id,
                                    title = song.title,
                                    artists = listOfNotNull(
                                        song.artistName?.let {
                                            com.jagr.fridamusic.models.MediaMetadata.Artist(
                                                id = null,
                                                name = it,
                                            )
                                        },
                                    ),
                                    duration = -1,
                                    thumbnailUrl = song.thumbnailUrl,
                                    musicVideoType = if (song.isVideo) "MUSIC_VIDEO_TYPE_OMV" else null,
                                ),
                            )
                        )
                    },
                )
            }
            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.popBackStack("settings", inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("music_recognition") {
                MusicRecognitionScreen(
                    onBack = { navController.popBackStack() },
                    onSearchResult = { query ->
                        navController.navigate("search_result/${Uri.encode(query)}") {
                            popUpTo("music_recognition") { inclusive = true }
                        }
                    },
                    onPlayResult = { result ->
                        if (playerConnection?.playRecognitionResult(result) == true) {
                            navController.navigate("now_playing") {
                                popUpTo("music_recognition") { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(
                                "search_result/${Uri.encode("${result.title} ${result.artist}".trim())}",
                            ) {
                                popUpTo("music_recognition") { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable("now_playing") {
                Box(Modifier.fillMaxSize())
            }
            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = RecommendationNotificationManager.ALBUM_DEEP_LINK_PATTERN
                    },
                ),
            ) {
                AlbumScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onAlbumClick = { album -> navController.navigate("album/${album.browseId}") },
                    onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "artist/{artistId}",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = RecommendationNotificationManager.ARTIST_DEEP_LINK_PATTERN
                    },
                ),
            ) {
                ArtistScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onAlbumClick = { album -> navController.navigateToDetail(album) },
                    onRemoteItemClick = { item ->
                        navController.handleYTItemClick(
                            item = item,
                            playSong = playerConnection?.let { connection ->
                                { connection.playYTItem(item) }
                            },
                            onUnavailable = {
                                Toast.makeText(
                                    context,
                                    R.string.recommendation_unavailable,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    },
                    onBrowseClick = { endpoint, title, maxItems, artistItems ->
                        navController.navigateToBrowse(endpoint, title, maxItems, artistItems)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = RecommendationNotificationManager.PLAYLIST_DEEP_LINK_PATTERN
                    },
                ),
            ) {
                OnlinePlaylistScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "local_playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = RecommendationNotificationManager.LOCAL_PLAYLIST_DEEP_LINK_PATTERN
                    },
                ),
            ) {
                LocalPlaylistScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height }
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = (-15).dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ModernBottomNav(
                        currentRoot = currentRoot,
                        onNavigate = { destination ->
                            if (destination == currentRoot && currentRoute == destination.route) {
                                when (destination) {
                                    RootDestination.HOME -> homeReselectToken++
                                    RootDestination.SEARCH -> searchReselectToken++
                                    RootDestination.LIBRARY -> libraryReselectToken++
                                }
                            } else {
                                retainedRoot = destination
                                navController.navigateToRoot(
                                    destination = destination,
                                    currentRoot = currentRoot,
                                    currentRoute = currentRoute,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FabOverflowMenu(
                        modifier = Modifier.padding(top = 4.dp, end = 16.dp),
                        menuIcon = Icons.Rounded.MoreHoriz,
                        menuContentDescription = "Actions",
                        actions = listOf(
                            FabMenuAction(
                                label = "Aleatorio",
                                icon = Icons.Rounded.Shuffle,
                                onClick = {
                                    if (shuffleJob?.isActive != true) {
                                        shuffleJob = fabScope.launch {
                                            val selection = runCatching {
                                                homeViewModel.getGlobalShuffleSelection()
                                            }.onFailure { error ->
                                                Timber.tag("GlobalShuffle").e(
                                                    error,
                                                    "Unable to select global shuffle content",
                                                )
                                            }.getOrNull()

                                            when {
                                                selection == null -> Toast.makeText(
                                                    context,
                                                    R.string.global_shuffle_empty,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                playerConnection == null ||
                                                    !playerConnection.playGlobalShuffle(selection) ->
                                                    Toast.makeText(
                                                        context,
                                                        R.string.global_shuffle_error,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                        }
                                    }
                                },
                            ),
                            FabMenuAction(
                                label = "Reconocer música",
                                icon = Icons.Rounded.Mic,
                                onClick = {
                                    navController.navigate("music_recognition") {
                                        launchSingleTop = true
                                    }
                                },
                            ),
                        ),
                    )
                }
            }
        }

        val showInteractivePlayer = showOverlay || currentRoute == "now_playing"
        if (showInteractivePlayer && playerConnection != null) {
            InteractivePlayer(
                playerConnection = playerConnection,
                bottomBarHeightPx = bottomBarHeightPx,
                routeExpanded = currentRoute == "now_playing",
                playlistsViewModel = playerPlaylistsViewModel,
                onExpanded = {
                    if (navController.currentDestination?.route != "now_playing") {
                        navController.navigate("now_playing") { launchSingleTop = true }
                    }
                },
                onCollapseStarted = {
                    if (navController.currentDestination?.route == "now_playing") {
                        navController.popBackStack()
                    }
                },
                onCollapsed = {
                    if (navController.currentDestination?.route == "now_playing") {
                        navController.popBackStack()
                    }
                },
                onDismissed = {
                    playerConnection.service.stopAndClearPlayback()
                },
            )
        }
    }
    }
}

private fun NavHostController.hasRootOnBackStack(root: RootDestination): Boolean =
    runCatching { getBackStackEntry(root.route) }.isSuccess

private const val SEARCH_RESULT_ROUTE = "search_result/{query}"
private const val RECAP_ROUTE = "recap?period={period}&offset={offset}"

private fun NavHostController.navigateFromNotificationCenter(uri: Uri) {
    val contentId = uri.pathSegments.firstOrNull()?.takeIf(String::isNotBlank)
    val route = when (uri.host) {
        "home" -> "home"
        "history" -> "history"
        "search" -> contentId?.let { "search_result/${Uri.encode(it)}" }
        "notifications" -> return
        "album" -> contentId?.let { "album/${Uri.encode(it)}" }
        "artist" -> contentId?.let { "artist/${Uri.encode(it)}" }
        "playlist" -> contentId?.let { "playlist/${Uri.encode(it)}" }
        "local-playlist" -> contentId?.let { "local_playlist/${Uri.encode(it)}" }
        "recap" -> {
            val period = uri.getQueryParameter("period")?.takeIf(String::isNotBlank)
            val offset = uri.getQueryParameter("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (period == null) "recap" else "recap?period=${Uri.encode(period)}&offset=$offset"
        }
        else -> null
    } ?: return

    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.navigateToSearchResult(query: String) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return

    navigate("search_result/${Uri.encode(normalizedQuery)}") {
        if (currentDestination?.route == SEARCH_RESULT_ROUTE) {
            popUpTo(SEARCH_RESULT_ROUTE) { inclusive = true }
        }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToBrowse(
    endpoint: BrowseEndpoint,
    title: String,
    maxItems: Int? = null,
    artistItems: Boolean = false,
) {
    if (endpoint.browseId.isBlank()) return

    val queryArguments = buildList {
        endpoint.params?.takeIf(String::isNotBlank)?.let { params ->
            add("params=${Uri.encode(params)}")
        }
        title.takeIf(String::isNotBlank)?.let { value ->
            add("title=${Uri.encode(value)}")
        }
        maxItems?.takeIf { it > 0 }?.let { value ->
            add("maxItems=$value")
        }
        if (artistItems) add("artistItems=true")
    }
    val query = queryArguments.takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = "?", separator = "&")
        .orEmpty()
    navigate("browse/${Uri.encode(endpoint.browseId)}$query")
}

private fun NavHostController.navigateToRoot(
    destination: RootDestination,
    currentRoot: RootDestination,
    currentRoute: String,
) {
    if (currentRoute == destination.route) return

    if (!hasRootOnBackStack(currentRoot)) {
        navigate(destination.route) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
        return
    }

    if (currentRoute != currentRoot.route) {
        popBackStack(currentRoot.route, inclusive = false)
    }

    if (destination == currentRoot) return

    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    if (currentDestination?.route != destination.route) {
        popBackStack(destination.route, inclusive = false)
    }
}

private fun NavHostController.navigateToDetail(item: LocalItem) {
    when (item) {
        is Album -> navigate("album/${Uri.encode(item.id)}")
        is Artist -> navigate("artist/${Uri.encode(item.id)}")
        is Playlist -> {
            val browseId = item.playlist.browseId
            if (browseId != null && !item.playlist.isLocal) {
                navigate("playlist/${Uri.encode(browseId)}")
            } else {
                navigate("local_playlist/${Uri.encode(item.id)}")
            }
        }
        else -> {}
    }
}

private fun PlayerConnection.playGlobalShuffle(selection: GlobalShuffleSelection): Boolean =
    when (selection) {
        is GlobalShuffleSelection.SongQueue -> {
            val first = selection.songs.firstOrNull()
            if (first == null) {
                false
            } else {
                playSong(first, selection.songs)
                true
            }
        }

        is GlobalShuffleSelection.RemoteItem -> {
            when (val item = selection.item) {
                is SongItem -> {
                    playYTItem(item)
                    true
                }

                is AlbumItem -> {
                    item.playlistId.takeIf(String::isNotBlank)?.let {
                        playQueue(YouTubeAlbumRadio(it))
                    } != null
                }

                is ArtistItem -> {
                    val endpoint = item.shuffleEndpoint ?: item.radioEndpoint ?: item.playEndpoint
                    endpoint?.let { playQueue(YouTubeQueue(it)) } != null
                }

                is PlaylistItem -> {
                    val endpoint = item.shuffleEndpoint ?: item.radioEndpoint ?: item.playEndpoint
                    endpoint?.let { playQueue(YouTubeQueue(it)) } != null
                }
            }
        }
    }

private fun PlayerConnection.playHomeItem(item: YTItem): Boolean = when (item) {
    is SongItem -> {
        playYTItem(item)
        true
    }

    is AlbumItem -> item.playlistId.takeIf(String::isNotBlank)?.let { playlistId ->
        playQueue(YouTubeAlbumRadio(playlistId))
    } != null

    is ArtistItem -> {
        val endpoint = item.playEndpoint ?: item.radioEndpoint ?: item.shuffleEndpoint
        endpoint?.let { playQueue(YouTubeQueue(it)) } != null
    }

    is PlaylistItem -> {
        val endpoint = item.playEndpoint ?: item.radioEndpoint ?: item.shuffleEndpoint
        endpoint?.let { playQueue(YouTubeQueue(it)) } != null
    }
}

private fun PlayerConnection.playRecognitionResult(result: RecognitionResult): Boolean {
    val videoId = result.youtubeVideoId?.trim()?.takeIf(String::isNotBlank) ?: return false
    playQueue(
        YouTubeQueue(
            endpoint = WatchEndpoint(videoId = videoId),
            preloadItem = MediaMetadata(
                id = videoId,
                title = result.title,
                artists = listOf(MediaMetadata.Artist(id = null, name = result.artist)),
                duration = -1,
                thumbnailUrl = result.coverArtHqUrl ?: result.coverArtUrl,
                album = result.album?.takeIf(String::isNotBlank)?.let {
                    MediaMetadata.Album(id = "recognition:${result.trackId}", title = it)
                },
            ),
        ),
    )
    return true
}

private fun NavHostController.handleYTItemClick(
    item: YTItem,
    playSong: (() -> Unit)?,
    onUnavailable: () -> Unit = {},
) {
    val contentId = when (item) {
        is AlbumItem -> item.browseId
        else -> item.id
    }.trim()

    if (contentId.isEmpty()) {
        Timber.tag("RecommendationClick").w(
            "Ignoring recommendation with an empty id (type=%s)",
            item.javaClass.simpleName,
        )
        onUnavailable()
        return
    }

    try {
        val encodedId = Uri.encode(contentId)
        when (item) {
            is ArtistItem -> navigate("artist/$encodedId")
            is AlbumItem -> navigate("album/$encodedId")
            is PlaylistItem -> navigate("playlist/$encodedId")
            is SongItem -> {
                if (playSong == null) {
                    Timber.tag("RecommendationClick").w("Player unavailable for song recommendation")
                    onUnavailable()
                } else {
                    playSong()
                }
            }
        }
    } catch (error: Exception) {
        Timber.tag("RecommendationClick").e(
            error,
            "Failed to handle recommendation (type=%s)",
            item.javaClass.simpleName,
        )
        onUnavailable()
    }
}
