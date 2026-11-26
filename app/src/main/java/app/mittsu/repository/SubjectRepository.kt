package app.mittsu.repository

import app.mittsu.database.MittsuDatabase
import app.mittsu.quiz.PracticeInfo
import app.mittsu.quiz.QuizSubject
import app.mittsu.quiz.toQuizSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only access to cached subjects for quizzes. Has no way to write to WaniKani. */
class SubjectRepository(private val db: MittsuDatabase) {

    /** Every cached subject that can be quizzed safely; used as the distractor pool. */
    suspend fun quizPool(): List<QuizSubject> = withContext(Dispatchers.IO) {
        db.subjects().byLevels(1, 60).mapNotNull { it.toQuizSubject() }
    }

    /** Every cached, visible subject by id; the value is null when it can't be quizzed safely. */
    suspend fun quizIndex(): Map<Long, QuizSubject?> = withContext(Dispatchers.IO) {
        db.subjects().byLevels(1, 60).associate { it.id to it.toQuizSubject() }
    }

    /** Subjects the user has already started on WaniKani (passed their lesson). */
    suspend fun learnedSubjectIds(): Set<Long> = withContext(Dispatchers.IO) {
        db.assignments().visible().filter { it.startedAt != null }.map { it.subjectId }.toSet()
    }

    /** SRS stage and accuracy of every learned subject, for practice filters. */
    suspend fun practiceInfo(): Map<Long, PracticeInfo> = withContext(Dispatchers.IO) {
        val stats = db.reviewStatistics().visible().associateBy { it.subjectId }
        db.assignments().visible().filter { it.startedAt != null && it.srsStage > 0 }.associate { a ->
            val st = stats[a.subjectId]
            a.subjectId to PracticeInfo(a.srsStage, st?.percentageCorrect, (st?.meaningIncorrect ?: 0) + (st?.readingIncorrect ?: 0))
        }
    }
}
