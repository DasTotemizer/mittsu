package app.mittsu.lessons

import android.util.Log
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.PendingLessonEntity
import app.mittsu.database.ReviewStatus
import app.mittsu.network.StartAssignmentBody
import app.mittsu.network.WaniKaniClient
import app.mittsu.network.WaniKaniError
import app.mittsu.repository.WaniKaniMappers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant

/**
 * The only code in the app that finishes lessons on WaniKani (PUT /assignments/{id}/start).
 * Works like [app.mittsu.reviews.ReviewSubmitter]: store first, send second, never twice.
 */
class LessonStarter(
    private val client: WaniKaniClient,
    private val db: MittsuDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    data class Report(val started: Int, val rejected: Int, val stillPending: Int, val error: WaniKaniError? = null) {
        val needsReconnect: Boolean get() = error is WaniKaniError.Unauthorized || error is WaniKaniError.Forbidden
    }

    private val mutex = Mutex()

    /** Saves a finished lesson. Returns false if it was already recorded. */
    suspend fun record(assignmentId: Long, subjectId: Long, completedAt: Long): Boolean = withContext(Dispatchers.IO) {
        db.pendingLessons().insert(PendingLessonEntity(assignmentId, subjectId, completedAt)) != -1L
    }

    suspend fun submitPending(): Report = withContext(Dispatchers.IO) {
        mutex.withLock {
            var started = 0
            var rejected = 0
            var error: WaniKaniError? = null
            for (row in db.pendingLessons().pending()) {
                val current = db.pendingLessons().get(row.assignmentId)
                if (current?.status != ReviewStatus.PENDING) continue
                try {
                    val assignment = try {
                        start(row, includeTime = true)
                    } catch (e: WaniKaniError.Unprocessable) {
                        // started_at must not be in the future or before unlocked_at: let WaniKani pick the time.
                        if (e.detail.contains("started_at", ignoreCase = true)) start(row, includeTime = false) else throw e
                    }
                    db.pendingLessons().markSubmitted(row.assignmentId, clock.millis())
                    try {
                        WaniKaniMappers.assignment(assignment)?.let { db.assignments().upsertAll(listOf(it)) }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "Could not apply the started assignment; the next sync will")
                    }
                    started++
                } catch (e: WaniKaniError.Unprocessable) {
                    db.pendingLessons().markRejected(row.assignmentId, e.detail)
                    rejected++
                } catch (e: WaniKaniError) {
                    db.pendingLessons().markAttemptFailed(row.assignmentId, e.message ?: "Not sent yet")
                    error = e
                    break
                }
            }
            Report(started, rejected, db.pendingLessons().pending().size, error)
        }
    }

    private suspend fun start(row: PendingLessonEntity, includeTime: Boolean): app.mittsu.network.ResourceDto<app.mittsu.network.AssignmentDto> {
        val body = StartAssignmentBody(if (includeTime) Instant.ofEpochMilli(row.completedAt).toString() else null)
        val response = client.call(retryServerErrors = false) { client.service.startAssignment(row.assignmentId, body) }
        if (response.objectType != "assignment" || response.id != row.assignmentId) throw WaniKaniError.InvalidResponse("unexpected assignment response")
        return response
    }

    suspend fun dismissRejected() = withContext(Dispatchers.IO) { db.pendingLessons().dismissRejected() }

    private companion object { const val TAG = "MittsuLessons" }
}
