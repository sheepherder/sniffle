package de.schaefer.sniffle.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.schaefer.sniffle.ui.detail.DetailMapScreen
import de.schaefer.sniffle.ui.detail.DetailScreen
import de.schaefer.sniffle.ui.detail.SensorChartScreen
import de.schaefer.sniffle.ui.map.MapScreen
import de.schaefer.sniffle.ui.onboarding.OnboardingScreen
import de.schaefer.sniffle.ui.onboarding.needsOnboarding
import de.schaefer.sniffle.ui.scan.ScanScreen
import de.schaefer.sniffle.ui.scan.ScanViewModel
import de.schaefer.sniffle.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object ScanRoute
@Serializable object MapRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val mac: String)
@Serializable data class DetailMapRoute(val mac: String)
@Serializable data class DetailChartRoute(val mac: String, val key: String)

enum class Tab(val route: Any, val label: String, val icon: ImageVector) {
    Scan(ScanRoute, "Live", Icons.Default.Radar),
    Map(MapRoute, "Karte", Icons.Default.Map),
    Settings(SettingsRoute, "Einstellungen", Icons.Default.Settings),
}

@Composable
fun SniffleApp(
    deepLinkMac: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var showOnboarding by remember { mutableStateOf(needsOnboarding(context)) }

    if (showOnboarding) {
        OnboardingScreen(onComplete = { showOnboarding = false })
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(deepLinkMac) {
        if (deepLinkMac != null) {
            navController.navigate(DetailRoute(mac = deepLinkMac)) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.let { dest ->
        !dest.hasRoute<DetailRoute>() && !dest.hasRoute<DetailMapRoute>() && !dest.hasRoute<DetailChartRoute>()
    } ?: true

    val scanViewModel: ScanViewModel = viewModel()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = currentDestination?.hasRoute(tab.route::class) == true,
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScanRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<ScanRoute> {
                ScanScreen(
                    onDeviceTap = { mac -> navController.navigate(DetailRoute(mac = mac)) },
                    viewModel = scanViewModel,
                )
            }
            composable<MapRoute> {
                MapScreen(
                    onMarkerTap = { mac -> navController.navigate(DetailRoute(mac = mac)) }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onScanSettingsChanged = { scanViewModel.restartScans() })
            }
            composable<DetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<DetailRoute>()
                DetailScreen(
                    mac = route.mac,
                    onBack = { navController.popBackStack() },
                    onOpenMap = { navController.navigate(DetailMapRoute(mac = route.mac)) },
                    onOpenChart = { key -> navController.navigate(DetailChartRoute(mac = route.mac, key = key)) },
                )
            }
            composable<DetailMapRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<DetailMapRoute>()
                DetailMapScreen(
                    mac = route.mac,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<DetailChartRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<DetailChartRoute>()
                SensorChartScreen(
                    mac = route.mac,
                    key = route.key,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
