package app.mittsu.domain

import app.mittsu.domain.model.NextAction
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.nextAction
import app.mittsu.repository.SampleDashboardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextActionTest {
    private suspend fun base() = SampleDashboardRepository().summary().first()

    @Test fun sampleDataIsAlwaysFlagged() = runTest { assertTrue(base().isSampleData) }

    @Test fun reviewsComeFirstWhenDue() = runTest {
        assertEquals(NextAction.REVIEWS, base().copy(reviewsCompletedToday = 0).nextAction())
    }

    @Test fun holidayOverridesEverything() = runTest {
        assertEquals(NextAction.HOLIDAY, base().copy(holidayActive = true).nextAction())
    }

    @Test fun targetMetInDailyBalance() = runTest {
        val s = base().copy(reviewsCompletedToday = 60, dailyTarget = 50, reviewMode = ReviewManagementMode.DAILY_BALANCE)
        assertEquals(NextAction.TARGET_MET, s.nextAction())
    }

    @Test fun standardModeIgnoresTarget() = runTest {
        val s = base().copy(reviewsCompletedToday = 60, dailyTarget = 50, reviewMode = ReviewManagementMode.STANDARD)
        assertEquals(NextAction.REVIEWS, s.nextAction())
    }

    @Test fun lessonsWhenNoReviewsDue() = runTest {
        assertEquals(NextAction.LESSONS, base().copy(reviewsDueNow = 0, lessonsAvailable = 5).nextAction())
    }

    @Test fun allClearWhenNothingToDo() = runTest {
        val s = base().copy(reviewsDueNow = 0, lessonsAvailable = 0, reviewsCompletedToday = 0)
        assertEquals(NextAction.ALL_CLEAR, s.nextAction())
    }
}
