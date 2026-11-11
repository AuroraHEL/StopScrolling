package com.example.stopscrolling.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.stopscrolling.PermissionScreen
import com.example.stopscrolling.ui.dashboard.DashboardScreen

/** Names of the screens, so that we never write a route as a raw string elsewhere. */
object Routes {
    const val PERMISSION = "permission"
    const val DASHBOARD = "dashboard"
    const val NEW_GROUP = "new_group"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.PERMISSION) {

        composable(Routes.PERMISSION) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                PermissionScreen(
                    onPermissionGranted = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.PERMISSION) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNewGroupClick = { navController.navigate(Routes.NEW_GROUP) }
            )
        }

        composable(Routes.NEW_GROUP) {
            // TODO US-08: replace by the real NewGroupScreen
            NewGroupPlaceholder()
        }
    }
}

// Temporary screen, removed when the "New group" screen is added.
@Composable
private fun NewGroupPlaceholder() {
    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("New group screen (coming soon)")
        }
    }
}