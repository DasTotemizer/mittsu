package app.mittsu.reviews

import android.util.Log
import androidx.room.withTransaction
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.PendingReviewEntity
import app.mittsu.network.CreateReviewBody
import app.mittsu.network.CreateReviewRequest
import app.mittsu.network.CreateReviewResponse
import app.mittsu.network.WaniKaniClient
import app.mittsu.network.WaniKaniError
import app.mittsu.repository.WaniKaniMappers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The only code in the app that writes reviews to WaniKani (POST /reviews).
 *
 * Every finished review goes through [record] first, which stores it locally, and only then
 * through [submitPending]. That order means nothing is lost if the app closes or the network
 * drops, and the unique key on the outbox means nothing is ever stored or sent twice. A mutex
 * keeps two senders (the screen and the background worker) from sending the same row at once.
 */
class ReviewSubmitter(
    private val client: WaniKaniClient,
    private val db: MittsuDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    data class Report(val submitted: Int, val rejected: Int, val stillPending: Int, val error: WaniKaniError? = null) {
        val needsReconnect: Boolean get() = error is WaniKaniError.Unauthorized || error is WaniKaniError.Forbidden
    }

    private val mutex = Mutex()

    /**
     * Saves a finished review. Returns false if this exact review (same assignment and same
     * available_at) was already recorded, in which case nothing changes.
     */
    suspend fun record(
        assignmentId: Long,
        subjectId: Long,
        availableAt: Long,
        incorrectMeaning: Int,
        incorrectReading: Int,
        completedAt: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        require(incorrectMeaning >= 0 && incorrectReading >= 0)
        db.pendingReviews().insert(
            PendingReviewEntity(
                assignmentId = assignmentId,
                subjectId = subjectId,
                availableAt = availableAt,
                incorrectMeaning = incorrectMeaning,
                incorrectReading = incorrectReading,
                completedAt = completedAt,
            ),
        ) != -1L
    }

    /** Sends every waiting review, oldest first. Stops at the first connection or account problem. */
    suspend fun submitPending(): Report = withContext(Dispatchers.IO) {
        mutex.withLock {
            var submitted = 0
            var rejected = 0
            var error: WaniKaniError? = null
            for (row in db.pendingReviews().pending()) {
                when (val outcome = send(row)) {
                    Outcome.Submitted -> submitted++
                    Outcome.Rejected -> rejected++
                    Outcome.Skipped -> Unit
                    is Outcome.Retry -> {
                        error = outcome.error
                        break
                    }
                }
            }
            db.pendingReviews().pruneBefore(clock.millis() - KEEP_FINISHED_FOR.toMillis())
            Report(submitted, rejected, db.pendingReviews().pending().size, error)
        }
    }

    private sealed interface Outcome {
        data object Submitted : Outcome
        data object Rejected : Outcome
        data object Skipped : Outcome
        data class Retry(val error: WaniKaniError) : Outcome
    }

    private suspend fun send(row: PendingReviewEntity): Outcome {
        // Re-read inside the lock: another sender may have finished this row a moment ago.
        val current = db.pendingReviews().get(row.id)
        if (current == null || current.status != app.mittsu.database.ReviewStatus.PENDING) return Outcome.Skipped
        return try {
            val response = try {
                post(row, includeCreatedAt = true)
            } catch (e: WaniKaniError.Unprocessable) {
                // created_at must be in the past and after available_at. If the phone clock is off,
                // let WaniKani use the time it receives the review instead.
                if (e.detail.contains("created_at", ignoreCase = true)) post(row, includeCreatedAt = false) else throw e
            }
            // Recorded first and on its own: whatever happens next, this review is never sent again.
            db.pendingReviews().markSubmitted(row.id, clock.millis())
            applyUpdates(response)
            Outcome.Submitted
        } catch (e: WaniKaniError.Unprocessable) {
            // WaniKani refused this review for good (for example the assignment was already
            // reviewed on the website). Retrying would never succeed, so it is set aside and shown.
            Log.w(TAG, "WaniKani refused a review")
            db.pendingReviews().markRejected(row.id, e.detail)
            Outcome.Rejected
        } catch (e: WaniKaniError) {
            // Offline, server trouble, rate limit or token problem: keep it and try again later.
            db.pendingReviews().markAttemptFailed(row.id, e.message ?: "Not sent yet")
            Outcome.Retry(e)
        }
    }

    /** Refreshes the cached assignment and statistics from the response. The next sync would too. */
    private suspend fun applyUpdates(response: CreateReviewResponse) {
        try {
            db.withTransaction {
                response.resourcesUpdated?.assignment?.let(WaniKaniMappers::assignment)?.let { db.assignments().upsertAll(listOf(it)) }
                response.resourcesUpdated?.reviewStatistic?.let(WaniKaniMappers::reviewStatistic)?.let { db.reviewStatistics().upsertAll(listOf(it)) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Could not apply the updated assignment; the next sync will")
        }
    }

    private suspend fun post(row: PendingReviewEntity, includeCreatedAt: Boolean): CreateReviewResponse {
        val body = CreateReviewRequest(
            CreateReviewBody(
                assignmentId = row.assignmentId,
                incorrectMeaningAnswers = row.incorrectMeaning,
                incorrectReadingAnswers = row.incorrectReading,
                createdAt = if (includeCreatedAt) Instant.ofEpochMilli(row.completedAt).toString() else null,
            ),
        )
        val response = client.call(retryServerErrors = false) { client.service.createReview(body) }
        if (response.objectType != "review" || response.data.assignmentId != row.assignmentId) {
            throw WaniKaniError.InvalidResponse("unexpected review response")
        }
        return response
    }

    suspend fun dismissRejected() = withContext(Dispatchers.IO) { db.pendingReviews().dismissRejected() }

    private companion object {
        const val TAG = "MittsuReviews"
        val KEEP_FINISHED_FOR: Duration = Duration.ofDays(3)
    }
}
