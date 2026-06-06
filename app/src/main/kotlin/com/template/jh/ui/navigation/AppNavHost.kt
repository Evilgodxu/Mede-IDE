package com.template.jh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.template.jh.screens.home.HomeScreen
import com.template.jh.screens.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            HomeScreen(
                onNavigateToSettings = {
                    navController.navigate(Settings) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<Settings> {
            SettingsScreen(
                onNavigateBack = {
                    if (navController.currentBackStackEntry?.destination?.route == Settings.serializer().descriptor.serialName) {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
