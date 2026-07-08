package com.jagr.fridamusic.presentation.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.jagr.fridamusic.presentation.LocalPlayerConnection
import com.jagr.fridamusic.presentation.components.MiniPlayer
import com.jagr.fridamusic.presentation.components.ModernBottomNav
import com.jagr.fridamusic.presentation.playSong
import com.jagr.fridamusic.presentation.playYTItem

@Composable
@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
fun MainScreen(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    val playerConnection = LocalPlayerConnection.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (playerConnection != null) {
                    MiniPlayer(
                        playerConnection = playerConnection,
                        onClick = { /* pantalla Now Playing: siguiente paso */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
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
                    onFabClick = { navController.navigate("settings") },
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = "home") {
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
                        onItemClick = { item -> playerConnection?.playYTItem(item) },
                    )
                }
                composable(
                    route = "search_result/{query}",
                    arguments = listOf(navArgument("query") { type = NavType.StringType }),
                ) {
                    SearchResultScreen(
                        onItemClick = { item -> playerConnection?.playYTItem(item) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("library") { PlaceholderScreen("Library") }
                composable("settings") { PlaceholderScreen("Settings") }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$name — próximamente",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}