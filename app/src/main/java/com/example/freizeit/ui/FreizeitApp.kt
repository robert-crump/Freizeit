package com.example.freizeit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.freizeit.R
import com.example.freizeit.ui.explore.ExploreScreen
import com.example.freizeit.ui.home.HomeScreen
import com.example.freizeit.ui.settings.SettingsScreen

enum class FreizeitDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    EXPLORE("explore", R.string.tab_explore, Icons.Filled.Place, Icons.Outlined.Place),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun FreizeitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

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
            composable(FreizeitDestination.EXPLORE.route) { ExploreScreen() }
            composable(FreizeitDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
