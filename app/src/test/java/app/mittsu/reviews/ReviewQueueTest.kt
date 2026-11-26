package app.mittsu.reviews

import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.quiz.QuestionGenerator
import app.mittsu.quiz.toQuizSubject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReviewQueueTest {
    private val now = Instant.parse("2026-11-26T12:00:00Z")
    private val past = now.minusSeconds(3600).toEpochMilli()
    private val subjects = (1..40).map { ReviewTestData.kanji(it.toLong(), it) }
    private val index = subjects.associate { it.id to it.toQuizSubject() }
    private val generator = QuestionGenerator(index.values.filterNotNull())

    @Test fun onlyDueReviewsOldestFirst() {
        val assignments = listOf(
            ReviewTestData.assignment(1, 1, past + 50),
            ReviewTestData.assignment(2, 2, past),
            ReviewTestData.assignment(3, 3, now.plusSeconds(60).toEpochMilli()), // not yet due
            ReviewTestData.assignment(4, 4, null), // e.g. Vacation Mode
            ReviewTestData.assignment(5, 5, past, stage = 0), // a lesson, not a review
            ReviewTestData.assignment(6, 6, past, stage = 9), // burned
        )
        val q = ReviewQueue.build(assignments, index, generator, emptySet(), now)
        assertEquals(listOf(2L, 1L), q.ready.map { it.assignment.id })
        assertEquals(0, q.excluded)
    }

    @Test fun leavesOutWhatCannotBeAskedFairly() {
        val radical = ReviewTestData.imageRadical(99)
        val idx = index + (radical.id to radical.toQuizSubject()) // null: image-only
        val assignments = listOf(
            ReviewTestData.assignment(1, 1, past),
            ReviewTestData.assignment(2, 99, past, type = "radical"),
            ReviewTestData.assignment(3, 12345, past), // subject not downloaded
        )
        val q = ReviewQueue.build(assignments, idx, generator, emptySet(), now)
        assertEquals(listOf(1L), q.ready.map { it.assignment.id })
        assertEquals(1, q.cannotQuiz)
        assertEquals(1, q.notDownloaded)
        assertEquals(2, q.excluded)
    }

    @Test fun subjectWithoutTwoSafeAlternativesIsLeftOut() {
        val tiny = listOf(subjects[0], subjects[1]).associate { it.id to it.toQuizSubject() }
        val q = ReviewQueue.build(listOf(ReviewTestData.assignment(1, 1, past)), tiny, QuestionGenerator(tiny.values.filterNotNull()), emptySet(), now)
        assertEquals(0, q.ready.size)
        assertEquals(1, q.cannotQuiz)
    }

    @Test fun alreadyFinishedReviewsAreNotAskedAgain() {
        val assignments = listOf(ReviewTestData.assignment(1, 1, past), ReviewTestData.assignment(2, 2, past))
        val q = ReviewQueue.build(assignments, index, generator, setOf(1L to past), now)
        assertEquals(listOf(2L), q.ready.map { it.assignment.id })
        assertEquals(1, q.awaitingUpload)
    }

    @Test fun kanjiNeedsMeaningAndReading() {
        val q = ReviewQueue.build(listOf(ReviewTestData.assignment(1, 1, past)), index, generator, emptySet(), now)
        assertEquals(2, q.ready.single().requiredTypes.size)
    }

    @Test fun sessionSizeFollowsReviewMode() {
        assertEquals(SessionSize.ALL, SessionSize.default(ReviewManagementMode.STANDARD, 50, 0, 200))
        assertEquals(50, SessionSize.default(ReviewManagementMode.DAILY_BALANCE, 50, 0, 200))
        assertEquals(20, SessionSize.default(ReviewManagementMode.DAILY_BALANCE, 50, 20, 200)) // 30 left
        assertEquals(10, SessionSize.default(ReviewManagementMode.DAILY_BALANCE, 50, 50, 200)) // target met
        assertEquals(SessionSize.ALL, SessionSize.default(ReviewManagementMode.DAILY_BALANCE, 50, 0, 12))
        assertEquals(listOf(10, 20, SessionSize.ALL), SessionSize.options(35))
        assertEquals(listOf(SessionSize.ALL), SessionSize.options(8))
    }
}
