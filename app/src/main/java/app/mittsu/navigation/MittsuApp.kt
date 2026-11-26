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
import androidx.compose.runtime.LaunchedEffect
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
import app.mittsu.lessons.LessonActions
import app.mittsu.lessons.LessonViewModel
import app.mittsu.lessons.LessonsScreen
import app.mittsu.network.WaniKaniConfig
import app.mittsu.quiz.PracticeQuizViewModel
import app.mittsu.quiz.PracticeActions
import app.mittsu.quiz.PracticeScreen
import app.mittsu.quiz.PracticeSetupViewModel
import app.mittsu.quiz.QuizPhase
import app.mittsu.quiz.QuizScreen
import app.mittsu.quiz.practiceMode
import app.mittsu.quiz.realMode
import app.mittsu.reviews.RealReviewViewModel
import app.mittsu.reviews.ReviewsActions
import app.mittsu.reviews.ReviewsScreen
import app.mittsu.reviews.ReviewsViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.mittsu.settings.ConnectScreen
import app.mittsu.settings.ConnectViewModel
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
                LaunchedEffect(Unit) { vm.onOpen() }
                DashboardScreen(
                    state = state,
                    actions = DashboardActions(
                        onStartReviews = { navController.navigate(Routes.REVIEWS) },
                        onStartLessons = { navController.navigate(Routes.LESSONS) },
                        onPractice = { navController.navigateTopLevel(Routes.PRACTICE) },
                        onHoliday = { navController.navigate(Routes.HOLIDAY) },
                        onConnect = { navController.navigate(Routes.CONNECT) },
                        onRefresh = vm::refresh,
                        onOpenVacationSettings = { context.openExternalUrl(WaniKaniConfig.ACCOUNT_SETTINGS_URL) },
                    ),
                )
            }
            composable(Routes.REVIEWS) {
                val vm: ReviewsViewModel = viewModel(factory = ReviewsViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.onOpen() }
                ReviewsScreen(
                    state = state,
                    actions = ReviewsActions(
                        onStart = { size -> navController.navigate(Routes.reviewQuiz(size)) },
                        onResume = { navController.navigate(Routes.reviewQuiz(Routes.REVIEW_RESUME)) },
                        onDiscardSaved = vm::discardSavedSession,
                        onChooseSize = vm::chooseSize,
                        onSendNow = vm::sendNow,
                        onDismissRefused = vm::dismissRefused,
                        onConnect = { navController.navigate(Routes.CONNECT) },
                        onOpenVacationSettings = { context.openExternalUrl(WaniKaniConfig.ACCOUNT_SETTINGS_URL) },
                        onBack = navController::popBackStack,
                    ),
                )
            }
            composable(
                Routes.REVIEW_QUIZ,
                arguments = listOf(navArgument("size") { type = NavType.IntType }),
            ) { entry ->
                val size = entry.arguments?.getInt("size") ?: 0
                val vm: RealReviewViewModel = viewModel(factory = RealReviewViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { if (size == Routes.REVIEW_RESUME) vm.resume() else vm.start(size) }
                QuizScreen(
                    state = state,
                    mode = realMode(),
                    onSelect = vm::select,
                    onContinue = vm::next,
                    onFinishEarly = vm::finish,
                    onExit = navController::popBackStack,
                    onRestart = navController::popBackStack,
                    onRetrySending = vm::retrySending,
                )
            }
            composable(Routes.LESSONS) {
                val vm: LessonViewModel = viewModel(factory = LessonViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.onOpen() }
                LessonsScreen(
                    state = state,
                    actions = LessonActions(
                        onChooseBatch = vm::chooseBatchSize,
                        onStartBatch = vm::startBatch,
                        onNextCard = vm::nextCard,
                        onPreviousCard = vm::previousCard,
                        onStartQuiz = vm::startQuiz,
                        onSelect = vm::select,
                        onContinue = vm::next,
                        onEndQuiz = vm::endQuiz,
                        onMoreLessons = vm::backToOverview,
                        onSendNow = vm::sendNow,
                        onDismissRefused = vm::dismissRefused,
                        onConnect = { navController.navigate(Routes.CONNECT) },
                        onOpenVacationSettings = { context.openExternalUrl(WaniKaniConfig.ACCOUNT_SETTINGS_URL) },
                        onBack = navController::popBackStack,
                    ),
                )
            }
            composable(Routes.PRACTICE) {
                val vm: PracticeSetupViewModel = viewModel(factory = PracticeSetupViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { vm.onOpen() }
                PracticeScreen(
                    state = state,
                    actions = PracticeActions(
                        onQuickPractice = { navController.navigate(Routes.practiceQuiz(custom = false)) },
                        onCustomPractice = { navController.navigate(Routes.practiceQuiz(custom = true)) },
                        onLevels = vm::setLevels,
                        onToggleType = vm::toggleType,
                        onToggleSrs = vm::toggleSrs,
                        onWeakOnly = vm::setWeakOnly,
                        onSize = vm::setSize,
                    ),
                )
            }
            composable(Routes.PRACTICE_QUIZ, arguments = listOf(navArgument("custom") { type = NavType.BoolType })) { entry ->
                val custom = entry.arguments?.getBoolean("custom") == true
                val vm: PracticeQuizViewModel = viewModel(factory = PracticeQuizViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    if (vm.state.value.phase == QuizPhase.Loading) {
                        if (custom) container.practiceFilters.value.let { vm.start(it.size, it) } else vm.start()
                    }
                }
                QuizScreen(
                    state = state,
                    mode = practiceMode(),
                    onSelect = vm::select,
                    onContinue = vm::next,
                    onFinishEarly = vm::finish,
                    onExit = navController::popBackStack,
                    onRestart = vm::restart,
                )
            }
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
                val connection by vm.connection.collectAsStateWithLifecycle()
                val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    onTheme = vm::setTheme,
                    onDifficulty = vm::setDifficulty,
                    onReviewMode = vm::setReviewMode,
                    onDailyTarget = vm::setDailyTarget,
                    onOpenWaniKaniAccount = { context.openExternalUrl(WaniKaniConfig.ACCOUNT_SETTINGS_URL) },
                    connection = connection,
                    syncStatus = syncStatus,
                    onConnect = { navController.navigate(Routes.CONNECT) },
                    onSyncNow = vm::syncNow,
                    onDisconnect = vm::disconnect,
                    onAdaptive = vm::setAdaptive,
                    onQuizFocus = vm::setQuizFocus,
                    onRandomise = vm::setRandomise,
                )
            }
            composable(Routes.CONNECT) {
                val vm: ConnectViewModel = viewModel(factory = ConnectViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                ConnectScreen(
                    state = state,
                    onTokenChange = vm::onTokenChange,
                    onConnect = vm::connect,
                    onOpenTokenPage = { context.openExternalUrl(WaniKaniConfig.TOKEN_SETTINGS_URL) },
                    onDone = { navController.navigateTopLevel(Routes.HOME) },
                    onBack = navController::popBackStack,
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
