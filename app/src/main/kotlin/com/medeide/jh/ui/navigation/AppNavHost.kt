package com.medeide.jh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.medeide.jh.screens.home.HomeScreen
import com.medeide.jh.screens.permission.PermissionGuideScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = PermissionGuide,
        modifier = modifier,
    ) {
        composable<PermissionGuide> {
            PermissionGuideScreen(
                onComplete = {
                    navController.navigate(Home) {
                        popUpTo(PermissionGuide) { inclusive = true }
                    }
                }
            )
        }
        composable<Home> {
            HomeScreen()
        }
    }
}
