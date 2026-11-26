package app.mittsu.reviews

import app.mittsu.database.AssignmentEntity
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.quiz.QuestionGenerator
import app.mittsu.quiz.QuestionType
import app.mittsu.quiz.QuizSubject
import app.mittsu.repository.DashboardCalculator
import java.time.Instant

/** A due review that Mittsu can ask with three real answers. */
data class ReviewCandidate(val assignment: AssignmentEntity, val subject: QuizSubject) {
    /** What WaniKani requires: meaning always, reading for kanji and vocabulary. */
    val requiredTypes: List<QuestionType>
        get() = if (subject.type.hasReading) listOf(QuestionType.MEANING, QuestionType.READING) else listOf(QuestionType.MEANING)
}

/**
 * The reviews due right now, split into what can be asked and what is left on WaniKani.
 * Nothing that is left out is changed on the account: it simply stays due there.
 */
data class ReviewQueue(
    val ready: List<ReviewCandidate>,
    /** Due on WaniKani but the subject isn't in the downloaded data yet. */
    val notDownloaded: Int,
    /** Image-only radicals, or subjects without two safe wrong answers in the downloaded data. */
    val cannotQuiz: Int,
    /** Already finished here (waiting to be sent, or sent and not yet synced), so not asked again. */
    val awaitingUpload: Int,
) {
    val excluded: Int get() = notDownloaded + cannotQuiz

    companion object {
        val EMPTY = ReviewQueue(emptyList(), 0, 0, 0)

        /**
         * @param subjects quiz view of cached subjects by id (null values mean "cached but can't be quizzed").
         * @param recorded (assignment id, available_at) of every review already finished in Mittsu.
         *   The same pair can only be reviewed once; WaniKani moves available_at after a review.
         */
        fun build(
            assignments: List<AssignmentEntity>,
            subjects: Map<Long, QuizSubject?>,
            generator: QuestionGenerator,
            recorded: Set<Pair<Long, Long>>,
            now: Instant,
        ): ReviewQueue {
            val due = assignments.filter { DashboardCalculator.isDueNow(it, now) }
            var notDownloaded = 0
            var cannotQuiz = 0
            var waiting = 0
            val ready = mutableListOf<ReviewCandidate>()
            for (a in due) {
                if (a.availableAt != null && (a.id to a.availableAt) in recorded) { waiting++; continue }
                if (!subjects.containsKey(a.subjectId)) { notDownloaded++; continue }
                val subject = subjects[a.subjectId]
                if (subject == null) { cannotQuiz++; continue }
                val candidate = ReviewCandidate(a, subject)
                if (candidate.requiredTypes.all { generator.canAsk(subject, it) }) ready += candidate else cannotQuiz++
            }
            // Oldest first, so nothing waits indefinitely; then lower SRS stages, which need it most.
            ready.sortWith(compareBy<ReviewCandidate>({ it.assignment.availableAt }, { it.assignment.srsStage }, { it.assignment.id }))
            return ReviewQueue(ready, notDownloaded, cannotQuiz, waiting)
        }
    }
}

/** Session sizes offered on the start screen, and which is chosen by default. */
object SessionSize {
    const val ALL = 0
    private val PRESETS = listOf(10, 20, 50)

    fun options(ready: Int): List<Int> = PRESETS.filter { it < ready } + ALL

    /**
     * Standard mode offers everything. Daily Balance and Gentle Recovery suggest what's left of
     * today's target (at least 10), so a backlog is worked through a steady amount at a time.
     */
    fun default(mode: ReviewManagementMode, dailyTarget: Int, completedToday: Int, ready: Int): Int {
        if (mode == ReviewManagementMode.STANDARD) return ALL
        val remaining = (dailyTarget - completedToday).coerceAtLeast(10)
        if (remaining >= ready) return ALL
        // Pick the largest preset that fits within what's left, so the target isn't overshot.
        return options(ready).filter { it != ALL && it <= remaining }.maxOrNull() ?: options(ready).first()
    }

    fun label(size: Int, ready: Int) = if (size == ALL) "All $ready" else "$size"
}
