package app.mittsu.quiz

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One subject in a session and the question types it still needs. */
@Serializable
data class SessionItem(
    val subjectId: Long,
    /** WaniKani assignment id for real reviews; null in practice. */
    val assignmentId: Long? = null,
    val needsMeaning: Boolean,
    val needsReading: Boolean,
    val meaningDone: Boolean = false,
    val readingDone: Boolean = false,
    val incorrectMeaning: Int = 0,
    val incorrectReading: Int = 0,
) {
    val isComplete: Boolean get() = (!needsMeaning || meaningDone) && (!needsReading || readingDone)
}

@Serializable
data class PendingQuestion(val subjectId: Long, val type: QuestionType)

/** A subject whose every required question has now been answered correctly. */
data class CompletedItem(
    val subjectId: Long,
    val assignmentId: Long?,
    val incorrectMeaning: Int,
    val incorrectReading: Int,
)

@Serializable
data class SessionStats(
    val answered: Int = 0,
    val correct: Int = 0,
    val meaningCorrect: Int = 0,
    val meaningIncorrect: Int = 0,
    val readingCorrect: Int = 0,
    val readingIncorrect: Int = 0,
) {
    val incorrect: Int get() = answered - correct
    val accuracy: Float? get() = if (answered == 0) null else correct.toFloat() / answered
    val meaningAccuracy: Float? get() = (meaningCorrect + meaningIncorrect).let { if (it == 0) null else meaningCorrect.toFloat() / it }
    val readingAccuracy: Float? get() = (readingCorrect + readingIncorrect).let { if (it == 0) null else readingCorrect.toFloat() / it }
}

/** Serializable state so an interrupted session can be resumed exactly where it stopped. */
@Serializable
data class SessionSnapshot(
    val items: List<SessionItem>,
    val queue: List<PendingQuestion>,
    val stats: SessionStats,
    val startedAtMillis: Long,
)

/**
 * Pure session logic shared by real reviews and practice.
 *
 * Meaning and reading are tracked separately. A wrong answer adds one to that type's incorrect
 * count and puts the question back a few places later. An item completes only when every
 * required type has been answered correctly; it is reported exactly once through the return
 * value of [answer].
 */
class QuizSession private constructor(
    private val items: MutableMap<Long, SessionItem>,
    private val queue: ArrayDeque<PendingQuestion>,
    stats: SessionStats,
    val startedAtMillis: Long,
    private val random: Random,
) {
    var stats: SessionStats = stats
        private set

    val current: PendingQuestion? get() = queue.firstOrNull()
    val isFinished: Boolean get() = queue.isEmpty()
    val totalItems: Int get() = items.size
    val completedItems: Int get() = items.values.count { it.isComplete }
    val remainingQuestions: Int get() = queue.size
    fun item(subjectId: Long): SessionItem? = items[subjectId]

    /**
     * Records the answer to [current] and moves on.
     * @return the item if this answer completed it, otherwise null.
     */
    fun answer(correct: Boolean): CompletedItem? {
        val q = queue.removeFirstOrNull() ?: return null
        val before = items.getValue(q.subjectId)
        val after = when (q.type) {
            QuestionType.MEANING -> if (correct) before.copy(meaningDone = true) else before.copy(incorrectMeaning = before.incorrectMeaning + 1)
            QuestionType.READING -> if (correct) before.copy(readingDone = true) else before.copy(incorrectReading = before.incorrectReading + 1)
        }
        items[q.subjectId] = after
        stats = stats.copy(
            answered = stats.answered + 1,
            correct = stats.correct + if (correct) 1 else 0,
            meaningCorrect = stats.meaningCorrect + if (correct && q.type == QuestionType.MEANING) 1 else 0,
            meaningIncorrect = stats.meaningIncorrect + if (!correct && q.type == QuestionType.MEANING) 1 else 0,
            readingCorrect = stats.readingCorrect + if (correct && q.type == QuestionType.READING) 1 else 0,
            readingIncorrect = stats.readingIncorrect + if (!correct && q.type == QuestionType.READING) 1 else 0,
        )
        if (!correct) {
            // Ask again soon, but not immediately, so the answer isn't just remembered from a second ago.
            val position = if (queue.isEmpty()) 0 else minOf(queue.size, REQUEUE_MIN + random.nextInt(REQUEUE_SPREAD))
            queue.add(position, q)
        }
        return if (!before.isComplete && after.isComplete) {
            CompletedItem(after.subjectId, after.assignmentId, after.incorrectMeaning, after.incorrectReading)
        } else null
    }

    /**
     * Takes an item out of the session entirely (its questions too). Used when a real review
     * can't be asked fairly, so it is left untouched on WaniKani rather than guessed at.
     */
    fun removeItem(subjectId: Long) {
        items.remove(subjectId)
        queue.removeAll { it.subjectId == subjectId }
    }

    /** Stops early: drops questions for items not yet started so a session can end cleanly. */
    fun wrapUp() {
        val started = items.values.filter { it.meaningDone || it.readingDone || it.incorrectMeaning + it.incorrectReading > 0 }.map { it.subjectId }.toSet()
        queue.removeAll { it.subjectId !in started }
        items.keys.retainAll(started)
    }

    fun snapshot() = SessionSnapshot(items.values.toList(), queue.toList(), stats, startedAtMillis)

    companion object {
        private const val REQUEUE_MIN = 3
        private const val REQUEUE_SPREAD = 4

        fun start(items: List<SessionItem>, startedAtMillis: Long, random: Random = Random.Default, shuffle: Boolean = true): QuizSession {
            val questions = items.flatMap { item ->
                buildList {
                    if (item.needsMeaning) add(PendingQuestion(item.subjectId, QuestionType.MEANING))
                    if (item.needsReading) add(PendingQuestion(item.subjectId, QuestionType.READING))
                }
            }.let { if (shuffle) it.shuffled(random) else it }
            return QuizSession(items.associateBy { it.subjectId }.toMutableMap(), ArrayDeque(questions), SessionStats(), startedAtMillis, random)
        }

        fun restore(snapshot: SessionSnapshot, random: Random = Random.Default) = QuizSession(
            snapshot.items.associateBy { it.subjectId }.toMutableMap(),
            ArrayDeque(snapshot.queue),
            snapshot.stats,
            snapshot.startedAtMillis,
            random,
        )
    }
}
