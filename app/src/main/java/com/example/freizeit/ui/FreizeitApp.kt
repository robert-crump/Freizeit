package com.example.freizeit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.freizeit.R
import com.example.freizeit.ui.checkin.CheckInHistoryScreen
import com.example.freizeit.ui.checkin.CheckInScreen
import com.example.freizeit.ui.map.MapScreen
import com.example.freizeit.ui.map.MapViewModel
import com.example.freizeit.ui.map.SearchOverlay
import com.example.freizeit.ui.home.HomeScreen
import com.example.freizeit.ui.settings.SettingsScreen

enum class FreizeitDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    MAP("map", R.string.tab_map, Icons.Filled.Place, Icons.Outlined.Place),
    CHECKIN("checkin", R.string.tab_checkin, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

private const val CHECKIN_ENTRY_ROUTE = "checkin/entry"
private const val CHECKIN_HISTORY_ROUTE = "checkin/history"

@Composable
fun FreizeitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Hoisted here (a sibling to the NavHost) rather than obtained inside MapScreen, so the
    // full-screen search overlay below shares the exact same instance/state (#37).
    val mapViewModel: MapViewModel = viewModel(factory = MapViewModel.Factory)
    var searchOverlayOpen by rememberSaveable { mutableStateOf(false) }
    var searchOverlayPreload by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    FreizeitDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon
                                    else destination.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = FreizeitDestination.HOME.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(FreizeitDestination.HOME.route) { HomeScreen() }
                composable(FreizeitDestination.MAP.route) {
                    MapScreen(
                        viewModel = mapViewModel,
                        onOpenSearch = { preloadQuery ->
                            searchOverlayPreload = preloadQuery
                            searchOverlayOpen = true
                        }
                    )
                }
                navigation(
                    startDestination = CHECKIN_ENTRY_ROUTE,
                    route = FreizeitDestination.CHECKIN.route
                ) {
                    composable(CHECKIN_ENTRY_ROUTE) {
                        CheckInScreen(onHistoryClick = { navController.navigate(CHECKIN_HISTORY_ROUTE) })
                    }
                    composable(CHECKIN_HISTORY_ROUTE) {
                        CheckInHistoryScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(FreizeitDestination.SETTINGS.route) { SettingsScreen() }
            }
        }

        if (searchOverlayOpen) {
            SearchOverlay(
                viewModel = mapViewModel,
                initialQuery = searchOverlayPreload,
                onDismiss = { searchOverlayOpen = false }
            )
        }
    }
}
