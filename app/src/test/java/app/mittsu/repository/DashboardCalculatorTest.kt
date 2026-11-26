package app.mittsu.repository

import app.mittsu.database.AssignmentEntity
import app.mittsu.database.ReviewStatisticEntity
import app.mittsu.database.UserEntity
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.SrsGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DashboardCalculatorTest {
    private val now = Instant.parse("2026-11-26T12:00:00Z")
    private val user = UserEntity(username = "u", level = 9, maxLevelGranted = 60, subscriptionType = "lifetime",
        subscriptionActive = true, vacationStartedAt = null, profileUrl = null, startedAt = null, fetchedAt = 0)

    private var nextId = 1L
    private fun a(stage: Int, availableIn: Duration?, started: Boolean = true, hidden: Boolean = false, unlocked: Boolean = true) =
        AssignmentEntity(
            id = nextId++, subjectId = nextId, subjectType = "kanji", srsStage = stage,
            unlockedAt = if (unlocked) 0 else null, startedAt = if (started) 0 else null, passedAt = null,
            burnedAt = if (stage == 9) 0 else null, availableAt = availableIn?.let { now.plus(it).toEpochMilli() },
            resurrectedAt = null, hidden = hidden,
        )

    private val assignments = listOf(
        a(1, Duration.ofHours(-30)),          // overdue
        a(4, Duration.ZERO),                  // due exactly now
        a(8, Duration.ofMinutes(-1)),         // due
        a(5, Duration.ofMinutes(45)),         // next review
        a(6, Duration.ofHours(23)),           // within 24 h
        a(7, Duration.ofHours(30)),           // beyond 24 h
        a(9, null),                           // burned: never due
        a(2, Duration.ofHours(-5), hidden = true), // hidden subject: ignored
        a(0, null, started = false),          // lesson available
        a(0, null, started = false),          // lesson available
        a(0, null, started = false, unlocked = false), // locked: not a lesson
    )

    @Test fun countsDueReviewsLessonsAndForecast() {
        val s = DashboardCalculator.summarise(user, assignments, emptyList(), now, 50, ReviewManagementMode.DAILY_BALANCE)
        assertEquals(9, s.level)
        assertEquals(3, s.reviewsDueNow)
        assertEquals(3, s.totalOutstandingReviews)
        assertEquals(2, s.lessonsAvailable)
        assertEquals(now.plus(Duration.ofMinutes(45)), s.nextReviewAt)
        assertEquals(2, s.reviewsInNextDay)
        assertFalse(s.isSampleData)
    }

    @Test fun srsDistributionUsesStartedVisibleItems() {
        val s = DashboardCalculator.summarise(user, assignments, emptyList(), now, 50, ReviewManagementMode.STANDARD)
        assertEquals(2, s.srsDistribution.count(SrsGroup.APPRENTICE))
        assertEquals(2, s.srsDistribution.count(SrsGroup.GURU))
        assertEquals(1, s.srsDistribution.count(SrsGroup.MASTER))
        assertEquals(1, s.srsDistribution.count(SrsGroup.ENLIGHTENED))
        assertEquals(1, s.srsDistribution.count(SrsGroup.BURNED))
        assertEquals(7, s.srsDistribution.total)
    }

    @Test fun accuracyCombinesMeaningAndReading() {
        fun st(mc: Int, mi: Int, rc: Int, ri: Int, hidden: Boolean = false) =
            ReviewStatisticEntity(nextId++, nextId, "kanji", mc, mi, rc, ri, 0, hidden)
        assertEquals(0.8f, DashboardCalculator.accuracy(listOf(st(8, 2, 0, 0), st(4, 1, 4, 1)))!!, 0.0001f)
        assertEquals(1f, DashboardCalculator.accuracy(listOf(st(1, 0, 0, 0), st(0, 50, 0, 0, hidden = true)))!!, 0f)
        assertNull(DashboardCalculator.accuracy(emptyList()))
    }

    @Test fun nothingScheduledMeansNoNextReview() {
        val s = DashboardCalculator.summarise(user, listOf(a(9, null)), emptyList(), now, 50, ReviewManagementMode.STANDARD)
        assertEquals(0, s.reviewsDueNow)
        assertNull(s.nextReviewAt)
    }
}
