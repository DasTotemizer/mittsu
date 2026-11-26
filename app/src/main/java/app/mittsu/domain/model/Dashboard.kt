package app.mittsu.domain.model

import java.time.Instant

/** Everything the home screen needs, independent of where it came from. */
data class DashboardSummary(
    val username: String,
    val level: Int,
    val maxLevel: Int,
    val reviewsDueNow: Int,
    val totalOutstandingReviews: Int,
    val lessonsAvailable: Int,
    val reviewsCompletedToday: Int,
    val dailyTarget: Int,
    val nextReviewAt: Instant?,
    val reviewsInNextDay: Int,
    val srsDistribution: SrsDistribution,
    val recentAccuracy: Float?,
    val accuracyCaption: String = "Sample figure",
    val reviewMode: ReviewManagementMode,
    val holidayActive: Boolean,
    val isSampleData: Boolean,
    /** WaniKani reports Vacation Mode as on (read-only: it can only be changed on WaniKani). */
    val vacationActive: Boolean = false,
)

/** Progress towards today's target. Pure so it can be unit tested. */
data class DailyProgress(val completed: Int, val target: Int) {
    val remaining: Int get() = (target - completed).coerceAtLeast(0)
    val fraction: Float get() = if (target <= 0) 0f else (completed.toFloat() / target).coerceIn(0f, 1f)
    val targetReached: Boolean get() = target > 0 && completed >= target
    val beyondTarget: Int get() = (completed - target).coerceAtLeast(0)
}

/** What the dashboard suggests doing next, in priority order. */
enum class NextAction { HOLIDAY, REVIEWS, LESSONS, TARGET_MET, ALL_CLEAR }

fun DashboardSummary.dailyProgress(): DailyProgress = DailyProgress(reviewsCompletedToday, dailyTarget)

fun DashboardSummary.nextAction(): NextAction {
    val progress = dailyProgress()
    val usesTarget = reviewMode != ReviewManagementMode.STANDARD
    return when {
        holidayActive -> NextAction.HOLIDAY
        reviewsDueNow > 0 && !(usesTarget && progress.targetReached) -> NextAction.REVIEWS
        lessonsAvailable > 0 && reviewsDueNow == 0 -> NextAction.LESSONS
        usesTarget && progress.targetReached -> NextAction.TARGET_MET
        else -> NextAction.ALL_CLEAR
    }
}
