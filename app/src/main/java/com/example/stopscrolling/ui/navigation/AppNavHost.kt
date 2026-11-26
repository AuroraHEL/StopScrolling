package com.example.stopscrolling.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.stopscrolling.PermissionScreen
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.data.InstalledApp
import com.example.stopscrolling.ui.dashboard.DailyUsageViewModel
import com.example.stopscrolling.ui.dashboard.DashboardScreen
import com.example.stopscrolling.ui.dashboard.DashboardViewModel
import com.example.stopscrolling.ui.editgroup.EditGroupScreen
import com.example.stopscrolling.ui.newgroup.NewGroupScreen
import com.example.stopscrolling.ui.newgroup.NewGroupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object Routes {
    const val PERMISSION = "permission"
    const val DASHBOARD = "dashboard"
    const val NEW_GROUP = "new_group"
    const val EDIT_GROUP = "edit_group/{groupId}"

    fun editGroup(groupId: Int): String {
        return "edit_group/$groupId"
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.PERMISSION
    ) {

        // ---------- Permission Screen ----------

        composable(Routes.PERMISSION) {
            Box(
                modifier = Modifier
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

        // ---------- Dashboard Screen ----------

        composable(Routes.DASHBOARD) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            val viewModel: DashboardViewModel =
                viewModel(factory = DashboardViewModel.Factory)

            val dailyUsageViewModel: DailyUsageViewModel =
                viewModel(factory = DailyUsageViewModel.Factory)

            val groups by
            viewModel.groups.collectAsStateWithLifecycle()

            val pendingChanges by
            viewModel.pendingChanges.collectAsStateWithLifecycle()

            val isUpdatingLimit by
            viewModel.isUpdatingLimit.collectAsStateWithLifecycle()

            val limitUpdateError by
            viewModel.limitUpdateError.collectAsStateWithLifecycle()

            val dailyUsageState by
            dailyUsageViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        dailyUsageViewModel.refreshUsage()
                        delay(DailyUsageViewModel.REFRESH_INTERVAL_MILLIS)
                    }
                }
            }

            DashboardScreen(
                groups = groups,
                pendingChanges = pendingChanges,
                dailyUsageState = dailyUsageState,

                onOpenUsageSettings = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },

                onNewGroupClick = {
                    navController.navigate(Routes.NEW_GROUP)
                },

                onEditGroupClick = { groupId ->
                    navController.navigate(
                        Routes.editGroup(groupId)
                    )
                },

                onUpdateLimit = viewModel::updateLimit,

                isUpdatingLimit = isUpdatingLimit,

                limitUpdateError = limitUpdateError,

                onClearLimitUpdateError =
                    viewModel::clearLimitUpdateError
            )
        }

        // ---------- New Group Screen ----------

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

        // ---------- Edit Group Screen ----------

        composable(
            route = Routes.EDIT_GROUP,
            arguments = listOf(
                navArgument("groupId") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->

            val groupId = backStackEntry.arguments
                ?.getInt("groupId") ?: return@composable

            val dashboardViewModel: DashboardViewModel =
                viewModel(factory = DashboardViewModel.Factory)

            val groups by dashboardViewModel.groups
                .collectAsStateWithLifecycle()

            // US-09: Observe Save status.
            val isUpdatingGroup by dashboardViewModel
                .isUpdatingGroup.collectAsStateWithLifecycle()

            val updateGroupError by dashboardViewModel
                .updateGroupError.collectAsStateWithLifecycle()

            val group = groups.find {
                it.group.id == groupId
            }

            val context = LocalContext.current

            val app =
                context.applicationContext as StopScrollingApp

            var availableApps by remember {
                mutableStateOf<List<InstalledApp>>(emptyList())
            }

            LaunchedEffect(Unit) {
                availableApps = withContext(Dispatchers.IO) {
                    app.installedAppsRepository
                        .getInstalledApps()
                        .sortedWith(
                            compareBy(
                                String.CASE_INSENSITIVE_ORDER
                            ) {
                                it.name
                            }
                        )
                }
            }

            if (group != null) {

                EditGroupScreen(
                    groupId = groupId,

                    initialName = group.group.name,

                    initialPackages = group.apps.map {
                        it.packageName
                    },

                    initialTimeLimit = group.group.timeLimit,

                    initialLimitType = group.group.limitType,

                    availableApps = availableApps,

                    onBack = {
                        navController.popBackStack()
                    },

                    // US-09: Save edited group.
                    onSave = {
                            selectedGroupId,
                            name,
                            packageNames,
                            timeLimit,
                            limitType ->

                        dashboardViewModel.updateGroup(
                            groupId = selectedGroupId,
                            name = name,
                            packageNames = packageNames,
                            timeLimit = timeLimit,
                            limitType = limitType,
                            onSuccess = {
                                navController.popBackStack()
                            }
                        )
                    },

                    // US-09: Delete group.
                    onDelete = { selectedGroupId ->
                        dashboardViewModel.deleteGroup(
                            groupId = selectedGroupId,
                            onSuccess = {
                                navController.popBackStack()
                            }
                        )
                    },

                    // US-09 Step 9.3:
                    // Saving and error states.
                    isSaving = isUpdatingGroup,
                    hasSaveError = updateGroupError
                )
            }
        }
    }
}
