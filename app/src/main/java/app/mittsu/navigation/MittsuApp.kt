package app.mittsu.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.mittsu.AppContainer
import app.mittsu.dashboard.DashboardActions
import app.mittsu.dashboard.DashboardScreen
import app.mittsu.dashboard.DashboardViewModel
import app.mittsu.holiday.HolidayScreen
import app.mittsu.lessons.LessonsScreen
import app.mittsu.network.WaniKaniConfig
import app.mittsu.quiz.PracticeScreen
import app.mittsu.reviews.ReviewsScreen
import app.mittsu.settings.SettingsScreen
import app.mittsu.settings.SettingsViewModel
import app.mittsu.stats.StatsScreen

@Composable
fun MittsuApp(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navController.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                            modifier = Modifier.testTag("nav-${dest.route}"),
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable(Routes.HOME) {
                val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                DashboardScreen(
                    state = state,
                    actions = DashboardActions(
                        onStartReviews = { navController.navigate(Routes.REVIEWS) },
                        onStartLessons = { navController.navigate(Routes.LESSONS) },
                        onPractice = { navController.navigateTopLevel(Routes.PRACTICE) },
                        onHoliday = { navController.navigate(Routes.HOLIDAY) },
                    ),
                )
            }
            composable(Routes.REVIEWS) { ReviewsScreen(onBack = navController::popBackStack) }
            composable(Routes.LESSONS) { LessonsScreen(onBack = navController::popBackStack) }
            composable(Routes.PRACTICE) { PracticeScreen() }
            composable(Routes.HOLIDAY) {
                HolidayScreen(
                    onOpenVacationSettings = { context.openExternalUrl(WaniKaniConfig.ACCOUNT_SETTINGS_URL) },
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                val settings by vm.settings.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    onTheme = vm::setTheme,
                    onDifficulty = vm::setDifficulty,
                    onReviewMode = vm::setReviewMode,
                    onDailyTarget = vm::setDailyTarget,
                    onOpenWaniKaniAccount = { context.openExternalUrl(WaniKaniConfig.ACCOUNT_SETTINGS_URL) },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
