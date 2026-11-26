package app.mittsu.repository

import app.mittsu.domain.model.DashboardSummary
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.SrsDistribution
import app.mittsu.domain.model.SrsGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Duration

/**
 * Placeholder numbers so the dashboard can be designed before an account is connected.
 * Always flagged with [DashboardSummary.isSampleData] so the UI labels it clearly.
 * This is NOT WaniKani data and is never used once a token is connected.
 */
class SampleDashboardRepository(private val clock: Clock = Clock.systemDefaultZone()) : DashboardRepository {

    private val state = MutableStateFlow(sample())

    override fun summary(): Flow<DashboardSummary> = state.asStateFlow()

    override suspend fun refresh() {
        state.value = sample()
    }

    private fun sample() = DashboardSummary(
        username = "Sample learner",
        level = 12,
        maxLevel = 60,
        reviewsDueNow = 84,
        totalOutstandingReviews = 132,
        lessonsAvailable = 15,
        reviewsCompletedToday = 32,
        dailyTarget = 50,
        nextReviewAt = clock.instant().plus(Duration.ofMinutes(42)),
        reviewsInNextDay = 61,
        srsDistribution = SrsDistribution(
            mapOf(
                SrsGroup.APPRENTICE to 96,
                SrsGroup.GURU to 214,
                SrsGroup.MASTER to 187,
                SrsGroup.ENLIGHTENED to 241,
                SrsGroup.BURNED to 318,
            ),
        ),
        recentAccuracy = 0.87f,
        reviewMode = ReviewManagementMode.DAILY_BALANCE,
        holidayActive = false,
        isSampleData = true,
    )
}
