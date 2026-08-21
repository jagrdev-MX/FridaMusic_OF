package com.jagr.fridamusic.presentation.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.FabMenuAction
import com.jagr.fridamusic.presentation.components.FabOverflowMenu
import com.jagr.fridamusic.presentation.components.MiniPlayer
import com.jagr.fridamusic.presentation.components.ModernBottomNav
import com.jagr.fridamusic.presentation.playCachedSong
import com.jagr.fridamusic.presentation.playSong
import com.jagr.fridamusic.presentation.playYTItem
import com.jagr.fridamusic.notifications.RecommendationNotificationManager
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import timber.log.Timber

@Composable
fun MainScreen(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val showOverlay = currentRoute != "now_playing" &&
            currentRoute != "settings" &&
            currentRoute != "login" &&
            currentRoute != "spotify_import" &&
            currentRoute != "stats" &&
            currentRoute != "about"

    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.fillMaxSize()) {

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
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
                    onSettingsClick = { navController.navigate("settings") },
                )
            }
            composable("search") {
                SearchScreen(
                    onSearchSubmit = { query ->
                        navController.navigate("search_result/${Uri.encode(query)}")
                    },
                    onItemClick = { item ->
                        navController.handleYTItemClick(
                            item,
                            playerConnection?.let { pc -> { pc.playYTItem(item) } },
                        )
                    },
                )
            }
            composable(
                route = "search_result/{query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType }),
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
                    onExternalPlaylistClick = { navController.navigate("spotify_import") },
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
                    onNavigateToSpotifyImport = { navController.navigate("spotify_import") },
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToAbout = { navController.navigate("about") },
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
            composable("now_playing") {
                if (playerConnection != null) {
                    NowPlayingScreen(
                        playerConnection = playerConnection,
                        onBack = { navController.popBackStack() },
                    )
                }
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
            FabOverflowMenu(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                menuIcon = Icons.Rounded.MoreHoriz,
                menuContentDescription = "Actions",
                actions = listOf(
                    FabMenuAction(
                        label = "Aleatorio",
                        icon = Icons.Rounded.Shuffle,
                        onClick = { /* TODO */ },
                    ),
                    FabMenuAction(
                        label = "Reconocer música",
                        icon = Icons.Rounded.Mic,
                        onClick = { /* TODO */ },
                    ),
                ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                if (playerConnection != null) {
                    MiniPlayer(
                        playerConnection = playerConnection,
                        onClick = { navController.navigate("now_playing") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 4.dp),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ModernBottomNav(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 96.dp),
                    )
                }
            }
        }
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
