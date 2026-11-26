package app.mittsu.repository

import app.mittsu.database.AssignmentEntity
import app.mittsu.database.ReviewStatisticEntity
import app.mittsu.database.UserEntity
import app.mittsu.domain.model.DashboardSummary
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.SrsDistribution
import java.time.Duration
import java.time.Instant

/** Pure rules that turn cached WaniKani data into dashboard numbers. */
object DashboardCalculator {

    /** In the SRS and not burned (stages 1–8). Stage 0 is an unstarted lesson, 9 is burned. */
    fun isInReview(a: AssignmentEntity) = !a.hidden && a.startedAt != null && a.srsStage in 1..8

    fun isDueNow(a: AssignmentEntity, now: Instant) =
        isInReview(a) && a.availableAt != null && a.availableAt <= now.toEpochMilli()

    fun isAvailableLesson(a: AssignmentEntity) =
        !a.hidden && a.unlockedAt != null && a.startedAt == null && a.srsStage == 0

    fun summarise(
        user: UserEntity,
        assignments: List<AssignmentEntity>,
        statistics: List<ReviewStatisticEntity>,
        now: Instant,
        dailyTarget: Int,
        reviewMode: ReviewManagementMode,
        reviewsCompletedToday: Int = 0,
    ): DashboardSummary {
        val nowMs = now.toEpochMilli()
        val dayAhead = now.plus(Duration.ofHours(24)).toEpochMilli()
        val inReview = assignments.filter(::isInReview)
        val due = inReview.count { it.availableAt != null && it.availableAt <= nowMs }
        val upcoming = inReview.mapNotNull { it.availableAt }.filter { it > nowMs }

        return DashboardSummary(
            username = user.username,
            level = user.level,
            maxLevel = 60,
            reviewsDueNow = due,
            // Every available review is outstanding; WaniKani has no separate backlog.
            totalOutstandingReviews = due,
            lessonsAvailable = assignments.count(::isAvailableLesson),
            reviewsCompletedToday = reviewsCompletedToday,
            dailyTarget = dailyTarget,
            nextReviewAt = upcoming.minOrNull()?.let(Instant::ofEpochMilli),
            reviewsInNextDay = upcoming.count { it <= dayAhead },
            srsDistribution = SrsDistribution.fromStages(
                assignments.filter { !it.hidden && it.startedAt != null }.map { it.srsStage },
            ),
            recentAccuracy = accuracy(statistics),
            accuracyCaption = "All-time accuracy from WaniKani's review statistics",
            reviewMode = reviewMode,
            holidayActive = false,
            isSampleData = false,
            vacationActive = user.vacationStartedAt != null,
        )
    }

    /** Correct answers over all answers, meaning and reading combined. Null when there are none. */
    fun accuracy(statistics: List<ReviewStatisticEntity>): Float? {
        val visible = statistics.filter { !it.hidden }
        val correct = visible.sumOf { it.meaningCorrect + it.readingCorrect }
        val total = correct + visible.sumOf { it.meaningIncorrect + it.readingIncorrect }
        return if (total == 0) null else correct.toFloat() / total
    }
}
