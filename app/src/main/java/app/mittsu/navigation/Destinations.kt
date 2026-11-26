package app.mittsu.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val REVIEWS = "reviews"
    const val LESSONS = "lessons"
    const val PRACTICE = "practice"
    const val HOLIDAY = "holiday"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CONNECT = "connect"
    const val PRACTICE_QUIZ = "practice_quiz"
}

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Today", Icons.Rounded.Home),
    PRACTICE(Routes.PRACTICE, "Practice", Icons.Rounded.SelfImprovement),
    STATS(Routes.STATS, "Progress", Icons.Rounded.Insights),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Tune),
}
