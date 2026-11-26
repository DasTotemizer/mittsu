package app.mittsu.reviews

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.ReviewStatus
import app.mittsu.network.FakeWaniKani
import app.mittsu.network.WaniKaniError
import app.mittsu.repository.MemoryTokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewSubmitterTest {
    private lateinit var fake: FakeWaniKani
    private lateinit var db: MittsuDatabase
    private lateinit var submitter: ReviewSubmitter
    private val now = Instant.parse("2026-11-26T12:00:00Z")
    private val dueAt = Instant.parse("2026-11-26T08:00:00Z").toEpochMilli()
    private val completedAt = Instant.parse("2026-11-26T11:59:00Z").toEpochMilli()

    @Before fun setUp() {
        fake = FakeWaniKani().start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MittsuDatabase::class.java)
            .allowMainThreadQueries().build()
        val tokens = MemoryTokenStore().apply { value = fake.validToken }
        submitter = ReviewSubmitter(fake.client(tokens::get), db, Clock.fixed(now, ZoneOffset.UTC))
        runBlocking { ReviewTestData.seed(db, due = 3, dueAt = dueAt) }
    }

    @After fun tearDown() { db.close(); fake.shutdown() }

    private fun record(assignmentId: Long = 1001, meaning: Int = 0, reading: Int = 0) = runBlocking {
        submitter.record(assignmentId, assignmentId - 1000, dueAt, meaning, reading, completedAt)
    }

    private fun rows() = runBlocking { db.pendingReviews().all() }

    @Test fun sendsOneReviewWithTheRightCountsAndCompletionTime() = runBlocking {
        assertTrue(record(meaning = 2, reading = 1))
        val report = submitter.submitPending()
        assertEquals(1, report.submitted)
        assertEquals(0, report.stillPending)
        assertEquals(1, fake.postedReviews.size)
        val body = fake.postedReviews.single()
        assertTrue(body, body.contains("\"assignment_id\":1001"))
        assertTrue(body, body.contains("\"incorrect_meaning_answers\":2"))
        assertTrue(body, body.contains("\"incorrect_reading_answers\":1"))
        assertTrue(body, body.contains("\"created_at\":\"2026-11-26T11:59:00Z\""))
        assertEquals(ReviewStatus.SUBMITTED, rows().single().status)
        // The assignment WaniKani returned replaces the cached one, so it is no longer due.
        assertEquals(Instant.parse("2026-12-24T00:00:00Z").toEpochMilli(), db.assignments().get(1001)!!.availableAt)

        // Nothing is sent again.
        submitter.submitPending()
        assertEquals(1, fake.postedReviews.size)
    }

    @Test fun theSameReviewIsNeverRecordedOrSentTwice() = runBlocking {
        assertTrue(record())
        assertFalse(record())
        assertFalse(record(meaning = 3))
        submitter.submitPending()
        assertEquals(1, rows().size)
        assertEquals(1, fake.postedReviews.size)
    }

    @Test fun concurrentSendersDoNotDoubleSubmit() = runBlocking {
        record(1001); record(1002); record(1003)
        listOf(async { submitter.submitPending() }, async { submitter.submitPending() }, async { submitter.submitPending() }).awaitAll()
        assertEquals(3, fake.postedReviews.size)
        assertEquals(listOf(1001L, 1002L, 1003L), fake.postedReviews.map { Regex("\"assignment_id\":(\\d+)").find(it)!!.groupValues[1].toLong() })
        assertTrue(rows().all { it.status == ReviewStatus.SUBMITTED })
    }

    @Test fun serverErrorKeepsTheReviewWithoutRetryingBlindly() = runBlocking {
        record()
        fake.reviewResponder = { _, _ -> MockResponse().setResponseCode(503) }
        val failed = submitter.submitPending()
        assertEquals(1, failed.stillPending)
        assertTrue(failed.error is WaniKaniError.Server)
        // A write is not retried automatically after a 5xx: WaniKani may already have it.
        assertEquals(1, fake.postedReviews.size)
        assertEquals(ReviewStatus.PENDING, rows().single().status)
        assertEquals(1, rows().single().attempts)

        fake.reviewResponder = { id, _ -> fake.reviewCreated(id) }
        val ok = submitter.submitPending()
        assertEquals(1, ok.submitted)
        assertEquals(2, fake.postedReviews.size)
        assertEquals(ReviewStatus.SUBMITTED, rows().single().status)
    }

    @Test fun offlineKeepsReviewsPendingInOrder() = runBlocking {
        record(1001); record(1002)
        fake.server.shutdown()
        val report = submitter.submitPending()
        assertTrue(report.error is WaniKaniError.Network)
        assertEquals(2, report.stillPending)
        assertTrue(rows().all { it.status == ReviewStatus.PENDING })
    }

    @Test fun refusedReviewIsSetAsideAndNotResent() = runBlocking {
        record()
        fake.reviewResponder = { _, _ -> fake.reviewRefused("Assignment not available for review") }
        val report = submitter.submitPending()
        assertEquals(1, report.rejected)
        val row = rows().single()
        assertEquals(ReviewStatus.REJECTED, row.status)
        assertEquals("Assignment not available for review", row.message)
        submitter.submitPending()
        assertEquals(1, fake.postedReviews.size)
    }

    @Test fun clockProblemRetriesOnceWithoutCreatedAt() = runBlocking {
        record()
        var calls = 0
        fake.reviewResponder = { id, _ -> if (calls++ == 0) fake.reviewRefused("created_at must be in the past") else fake.reviewCreated(id) }
        val report = submitter.submitPending()
        assertEquals(1, report.submitted)
        assertEquals(2, fake.postedReviews.size)
        assertTrue(fake.postedReviews[0].contains("created_at"))
        assertFalse(fake.postedReviews[1].contains("created_at"))
    }

    @Test fun revokedTokenStopsAndKeepsEverything() = runBlocking {
        record(1001); record(1002)
        fake.revokeToken = true
        val report = submitter.submitPending()
        assertTrue(report.needsReconnect)
        assertEquals(2, report.stillPending)
        assertEquals(0, fake.postedReviews.size) // the fake refuses before reading the body
        assertNull(rows().firstOrNull { it.status != ReviewStatus.PENDING })
    }
}
