package de.schaefer.sniffle.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.schaefer.sniffle.ui.detail.DetailScreen
import de.schaefer.sniffle.ui.history.HistoryScreen
import de.schaefer.sniffle.ui.scan.LiveScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Live("live", "Live", Icons.Default.Radar),
    History("history", "Funde", Icons.Default.History),
    Map("map", "Karte", Icons.Default.Map),
    Settings("settings", "Einstellungen", Icons.Default.Settings),
}

@Composable
fun SniffleApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Live.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Tab.Live.route) {
                LiveScreen(
                    onDeviceTap = { mac ->
                        navController.navigate("detail/$mac")
                    }
                )
            }
            composable(Tab.History.route) {
                HistoryScreen(
                    onDeviceTap = { mac ->
                        navController.navigate("detail/$mac")
                    }
                )
            }
            composable(Tab.Map.route) {
                PlaceholderScreen("Karte")
            }
            composable(Tab.Settings.route) {
                PlaceholderScreen("Einstellungen")
            }
            composable("detail/{mac}") { backStackEntry ->
                val mac = backStackEntry.arguments?.getString("mac") ?: return@composable
                DetailScreen(
                    mac = mac,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
