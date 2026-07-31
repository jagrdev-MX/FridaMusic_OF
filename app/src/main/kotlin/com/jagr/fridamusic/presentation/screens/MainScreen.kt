package com.jagr.fridamusic.presentation.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jagr.fridamusic.db.entities.Album
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.LocalItem
import com.jagr.fridamusic.db.entities.Playlist
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.MiniPlayer
import com.jagr.fridamusic.presentation.components.ModernBottomNav
import com.jagr.fridamusic.presentation.playSong
import com.jagr.fridamusic.presentation.playYTItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.YTItem

@Composable
fun MainScreen(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    val playerConnection = LocalPlayerConnection.current

    val showOverlay = currentRoute != "now_playing" && currentRoute != "settings" && currentRoute != "login" && currentRoute != "spotify_import" && currentRoute != "stats" && currentRoute != "about"

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Contenido principal (ocupa toda la pantalla) ──────────────────
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home") {
                HomeScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onItemClick = { item -> playerConnection?.playYTItem(item) },
                    onSettingsClick = { navController.navigate("settings") },
                )
            }
            composable("search") {
                SearchScreen(
                    onSearchSubmit = { query ->
                        navController.navigate("search_result/${Uri.encode(query)}")
                    },
                    onItemClick = { item ->
                        navController.handleYTItemClick(item, playerConnection?.let { pc -> { pc.playYTItem(item) } })
                    },
                )
            }
            composable(
                route = "search_result/{query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType }),
            ) {
                SearchResultScreen(
                    onItemClick = { item ->
                        navController.handleYTItemClick(item, playerConnection?.let { pc -> { pc.playYTItem(item) } })
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("library") {
                LibraryScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onLocalItemClick = { item -> navController.navigateToDetail(item) },
                )
            }
            composable("settings") {
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
            composable("stats") {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToArtist = { id -> navController.navigate("artist/${java.net.URLEncoder.encode(id, "UTF-8")}") },
                    onNavigateToAlbum = { id -> navController.navigate("album/${java.net.URLEncoder.encode(id, "UTF-8")}") },
                    onSongClick = { song ->
                        playerConnection?.playQueue(
                            com.jagr.fridamusic.playback.queues.YouTubeQueue(
                                endpoint = com.music.innertube.models.WatchEndpoint(videoId = song.id),
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
            ) {
                AlbumScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "artist/{artistId}",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                ArtistScreen(
                    onSongClick = { song, queue -> playerConnection?.playSong(song, queue) },
                    onAlbumClick = { album -> navController.navigateToDetail(album) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) {
                OnlinePlaylistScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "local_playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
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
                    .navigationBarsPadding(),
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
                )
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
        else -> { /* type not supported yet */ }
    }
}

private fun NavHostController.handleYTItemClick(item: YTItem, playSong: (() -> Unit)?) {
    val encodedId = Uri.encode(item.id)
    when (item) {
        is ArtistItem -> navigate("artist/$encodedId")
        is AlbumItem -> navigate("album/$encodedId")
        is PlaylistItem -> navigate("playlist/$encodedId")
        else -> playSong?.invoke()
    }
}
