package app.mittsu.reviews

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.ReviewStatus
import app.mittsu.network.FakeWaniKani
import app.mittsu.quiz.FakeSettings
import app.mittsu.quiz.QuestionType
import app.mittsu.quiz.QuizPhase
import app.mittsu.repository.MemoryTokenStore
import app.mittsu.repository.SubjectRepository
import app.mittsu.repository.WaniKaniAccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RealReviewViewModelTest {
    private lateinit var fake: FakeWaniKani
    private lateinit var db: MittsuDatabase
    private lateinit var account: WaniKaniAccountRepository
    private lateinit var repo: ReviewRepository
    private lateinit var scope: CoroutineScope
    private var scheduled = 0
    private val now = Instant.parse("2026-11-26T12:00:00Z")
    private val dueAt = now.minusSeconds(7200).toEpochMilli()

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeWaniKani().start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MittsuDatabase::class.java)
            .allowMainThreadQueries().build()
        val tokens = MemoryTokenStore()
        val client = fake.client(tokens::get)
        account = WaniKaniAccountRepository(tokens, client, db)
        runBlocking { account.connect(fake.validToken) }
        runBlocking { ReviewTestData.seed(db, due = 3, dueAt = dueAt) }
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = ReviewRepository(db, ReviewSubmitter(client, db, clock), { scheduled++ }, clock, scope)
        fake.requests.clear()
    }

    @After fun tearDown() { scope.cancel(); Dispatchers.resetMain(); db.close(); fake.shutdown() }

    private fun vm() = RealReviewViewModel(repo, SubjectRepository(db), FakeSettings(), account, clock = { now.toEpochMilli() }, random = Random(7))

    private fun RealReviewViewModel.awaitAsking() = runBlocking {
        withTimeout(10_000) {
            while (state.value.phase == QuizPhase.Loading || (state.value.phase == QuizPhase.Asking && state.value.answered)) delay(10)
        }
    }

    private fun RealReviewViewModel.answer(correct: Boolean) {
        val q = state.value.question!!
        assertEquals(3, q.options.size)
        assertTrue("real reviews never use the self-check", !q.isSelfCheck)
        select(if (correct) q.correctIndex else q.options.indices.first { it != q.correctIndex })
        next()
        awaitAsking()
    }

    private fun awaitRows(n: Int, status: ReviewStatus) = runBlocking {
        withTimeout(10_000) { while (db.pendingReviews().all().count { it.status == status } < n) delay(20) }
    }

    @Test fun completedReviewsAreSentOnceWithTheirMistakes() {
        val vm = vm()
        vm.start(SessionSize.ALL)
        vm.awaitAsking()
        assertEquals(3, vm.state.value.totalItems)
        var wrongMeaning = false
        var guard = 0
        while (vm.state.value.phase == QuizPhase.Asking && guard++ < 50) {
            // Get the first meaning question of the session wrong once.
            val makeMistake = !wrongMeaning && vm.state.value.question!!.type == QuestionType.MEANING
            if (makeMistake) wrongMeaning = true
            vm.answer(correct = !makeMistake)
        }
        assertEquals(QuizPhase.Finished, vm.state.value.phase)
        awaitRows(3, ReviewStatus.SUBMITTED)

        assertEquals(3, fake.postedReviews.size)
        assertEquals(3, fake.postedReviews.map { Regex("\"assignment_id\":(\\d+)").find(it)!!.groupValues[1] }.toSet().size)
        assertEquals(1, fake.postedReviews.count { it.contains("\"incorrect_meaning_answers\":1") })
        assertTrue(fake.postedReviews.all { it.contains("\"incorrect_reading_answers\":0") })
        runBlocking { withTimeout(5_000) { while (vm.state.value.submission?.sent != 3) delay(20) } }
        assertNull(runBlocking { repo.loadSession() })
        assertTrue(scheduled >= 3)
    }

    @Test fun onlyFullyAnsweredSubjectsAreSentWhenEndingEarly() {
        val vm = vm()
        vm.start(SessionSize.ALL)
        vm.awaitAsking()
        // Answer until one subject is complete, then stop.
        var guard = 0
        while (vm.state.value.completedItems == 0 && guard++ < 10) vm.answer(correct = true)
        vm.finish()
        runBlocking { withTimeout(5_000) { while (vm.state.value.phase != QuizPhase.Finished) delay(10) } }
        awaitRows(1, ReviewStatus.SUBMITTED)
        runBlocking { delay(300) }
        assertEquals(1, fake.postedReviews.size)
        assertEquals(1, runBlocking { db.pendingReviews().all() }.size)
    }

    @Test fun anInterruptedSessionResumesWithoutRepeatingFinishedReviews() {
        val first = vm()
        first.start(SessionSize.ALL)
        first.awaitAsking()
        var guard = 0
        while (first.state.value.completedItems == 0 && guard++ < 10) first.answer(correct = true)
        val answered = first.state.value.stats.answered
        awaitRows(1, ReviewStatus.SUBMITTED)
        // The app is closed here: the ViewModel is gone without finishing.
        assertNotNull(runBlocking { repo.loadSession() })

        val second = vm()
        second.resume()
        second.awaitAsking()
        assertEquals(answered + 1, second.state.value.questionNumber)
        assertEquals(1, second.state.value.completedItems)
        guard = 0
        while (second.state.value.phase == QuizPhase.Asking && guard++ < 50) second.answer(correct = true)
        awaitRows(3, ReviewStatus.SUBMITTED)
        runBlocking { delay(300) }
        assertEquals(3, fake.postedReviews.size)
    }

    @Test fun resumeSkipsReviewsDoneElsewhereMeanwhile() {
        val first = vm()
        first.start(SessionSize.ALL)
        first.awaitAsking()
        // Meanwhile the user reviews everything on the website and Mittsu syncs.
        runBlocking { db.assignments().upsertAll((1..3).map { ReviewTestData.assignment(1000L + it, it.toLong(), now.plusSeconds(86_400).toEpochMilli()) }) }
        val second = vm()
        second.resume()
        runBlocking { withTimeout(5_000) { while (second.state.value.phase == QuizPhase.Loading) delay(10) } }
        assertTrue(second.state.value.phase is QuizPhase.Unavailable || second.state.value.phase == QuizPhase.Finished)
        assertEquals(0, fake.postedReviews.size)
    }

    @Test fun nothingDueMeansNothingToStart() {
        runBlocking { db.assignments().upsertAll((1..3).map { ReviewTestData.assignment(1000L + it, it.toLong(), null) }) }
        val vm = vm()
        vm.start(SessionSize.ALL)
        runBlocking { withTimeout(5_000) { while (vm.state.value.phase == QuizPhase.Loading) delay(10) } }
        assertTrue(vm.state.value.phase is QuizPhase.Unavailable)
        assertEquals(0, fake.requests.size)
    }

    @Test fun sessionSizeLimitsTheSession() {
        runBlocking { ReviewTestData.seed(db, due = 25, dueAt = dueAt) }
        val vm = vm()
        vm.start(10)
        vm.awaitAsking()
        assertEquals(10, vm.state.value.totalItems)
    }
}
