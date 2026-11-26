package app.mittsu.reviews

import app.mittsu.database.ActiveSessionEntity
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.PendingReviewEntity
import app.mittsu.quiz.QuestionGenerator
import app.mittsu.quiz.QuizSubject
import app.mittsu.quiz.SessionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock

/** A saved real session: the quiz state plus each assignment's available_at when it was asked. */
@Serializable
data class SavedReviewSession(
    val snapshot: SessionSnapshot,
    /** assignment id to available_at (epoch ms), the identity of each review. */
    val availableAt: Map<Long, Long>,
    val excluded: Int = 0,
)

/**
 * Everything real reviews need besides the quiz engine: which reviews are due, the saved
 * session, and handing finished reviews to [ReviewSubmitter].
 */
class ReviewRepository(
    private val db: MittsuDatabase,
    private val submitter: ReviewSubmitter,
    private val scheduler: UploadScheduler,
    private val clock: Clock = Clock.systemUTC(),
    /** Outlives screens, so a send that has started is never cut off by leaving the screen. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _lastReport = MutableStateFlow<ReviewSubmitter.Report?>(null)
    val lastReport: StateFlow<ReviewSubmitter.Report?> = _lastReport.asStateFlow()

    /** Due reviews, given the downloaded subjects. [index] and [generator] come from the same pool. */
    suspend fun queue(index: Map<Long, QuizSubject?>, generator: QuestionGenerator): ReviewQueue = withContext(Dispatchers.IO) {
        val assignments = db.assignments().visible()
        val recorded = db.pendingReviews().all().map { it.assignmentId to it.availableAt }.toSet()
        withContext(Dispatchers.Default) { ReviewQueue.build(assignments, index, generator, recorded, clock.instant()) }
    }

    suspend fun assignments() = withContext(Dispatchers.IO) { db.assignments().visible() }

    /** Stores a finished review, then sends it straight away (and again later if that fails). */
    suspend fun recordFinished(
        assignmentId: Long, subjectId: Long, availableAt: Long,
        incorrectMeaning: Int, incorrectReading: Int, completedAt: Long,
    ): Boolean {
        val stored = submitter.record(assignmentId, subjectId, availableAt, incorrectMeaning, incorrectReading, completedAt)
        if (stored) sendSoon()
        return stored
    }

    /** Sends waiting reviews in the background. A worker with a network constraint is the backup. */
    fun sendSoon() {
        scheduler.schedule()
        scope.launch { _lastReport.value = submitter.submitPending() }
    }

    suspend fun sendNow(): ReviewSubmitter.Report = submitter.submitPending().also { _lastReport.value = it }

    suspend fun dismissRejected() = submitter.dismissRejected()

    fun outstanding(): Flow<List<PendingReviewEntity>> = db.pendingReviews().observeOutstanding()

    fun finishedSince(since: Long): Flow<List<PendingReviewEntity>> = db.pendingReviews().observeSince(since)

    // ---- saved session ----

    suspend fun saveSession(session: SavedReviewSession) = withContext(Dispatchers.IO) {
        db.activeSession().save(ActiveSessionEntity(json = json.encodeToString(SavedReviewSession.serializer(), session), savedAt = clock.millis()))
    }

    suspend fun loadSession(): SavedReviewSession? = withContext(Dispatchers.IO) {
        val row = db.activeSession().get() ?: return@withContext null
        try {
            json.decodeFromString(SavedReviewSession.serializer(), row.json)
        } catch (_: Exception) {
            db.activeSession().clear() // unreadable (e.g. from an older build): drop it
            null
        }
    }

    fun hasSavedSession(): Flow<ActiveSessionEntity?> = db.activeSession().observe()

    suspend fun clearSession() = withContext(Dispatchers.IO) { db.activeSession().clear() }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
