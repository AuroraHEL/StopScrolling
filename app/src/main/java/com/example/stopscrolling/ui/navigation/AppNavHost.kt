package com.example.stopscrolling.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.stopscrolling.PermissionScreen
import com.example.stopscrolling.ui.dashboard.DashboardScreen
import com.example.stopscrolling.ui.dashboard.DashboardViewModel
import com.example.stopscrolling.ui.newgroup.NewGroupScreen
import com.example.stopscrolling.ui.newgroup.NewGroupViewModel

object Routes {
    const val PERMISSION = "permission"
    const val DASHBOARD = "dashboard"
    const val NEW_GROUP = "new_group"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.PERMISSION
    ) {
        composable(Routes.PERMISSION) {
            Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                PermissionScreen(
                    onPermissionGranted = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.PERMISSION) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(Routes.DASHBOARD) {
            val viewModel: DashboardViewModel =
                viewModel(factory = DashboardViewModel.Factory)

            val groups by viewModel.groups.collectAsStateWithLifecycle()
            val isUpdatingLimit by
            viewModel.isUpdatingLimit.collectAsStateWithLifecycle()
            val limitUpdateError by
            viewModel.limitUpdateError.collectAsStateWithLifecycle()

            DashboardScreen(
                groups = groups,
                onNewGroupClick = {
                    navController.navigate(Routes.NEW_GROUP)
                },
                onUpdateLimit = viewModel::updateLimit,
                isUpdatingLimit = isUpdatingLimit,
                limitUpdateError = limitUpdateError,
                onClearLimitUpdateError = viewModel::clearLimitUpdateError
            )
        }

        composable(Routes.NEW_GROUP) {
            val viewModel: NewGroupViewModel =
                viewModel(factory = NewGroupViewModel.Factory)

            NewGroupScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
