package app.mittsu.lessons

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.ReviewStatus
import app.mittsu.database.StoredSentence
import app.mittsu.network.FakeWaniKani
import app.mittsu.network.WaniKaniError
import app.mittsu.quiz.FakeSettings
import app.mittsu.quiz.QuestionType
import app.mittsu.quiz.QuizPhase
import app.mittsu.repository.MemoryTokenStore
import app.mittsu.repository.SubjectRepository
import app.mittsu.repository.WaniKaniAccountRepository
import app.mittsu.reviews.ReviewTestData
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
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class LessonTests {
    private lateinit var fake: FakeWaniKani
    private lateinit var db: MittsuDatabase
    private lateinit var account: WaniKaniAccountRepository
    private lateinit var starter: LessonStarter
    private lateinit var repo: LessonRepository
    private lateinit var scope: CoroutineScope
    private val now = Instant.parse("2026-11-26T12:00:00Z")

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeWaniKani().start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MittsuDatabase::class.java)
            .allowMainThreadQueries().build()
        val tokens = MemoryTokenStore()
        val client = fake.client(tokens::get)
        account = WaniKaniAccountRepository(tokens, client, db)
        runBlocking {
            account.connect(fake.validToken)
            account.sync() // so the screen's light sync is skipped
            // Hide the fixture's own lesson so only the seeded ones below are available.
            db.assignments().upsertAll(db.assignments().visible().map { it.copy(hidden = true) })
            // 40 learned kanji (distractor pool) and 6 new lessons with mnemonics.
            val learned = (1..40).map { ReviewTestData.kanji(5000L + it, it) }
            db.subjects().upsertAll(learned)
            db.assignments().upsertAll(learned.map { ReviewTestData.assignment(1000L + it.id, it.id, now.plusSeconds(86_400).toEpochMilli()) })
            val fresh = (41..46).map {
                ReviewTestData.kanji(5000L + it, it).copy(
                    level = if (it == 41) 9 else 3, lessonPosition = 100 - it,
                    meaningMnemonic = "The <radical>ground</radical> is <kanji>here</kanji>.",
                    contextSentences = listOf(StoredSentence("Example.", "例です。")),
                )
            }
            db.subjects().upsertAll(fresh)
            db.assignments().upsertAll(fresh.map { ReviewTestData.assignment(1000L + it.id, it.id, null, stage = 0).copy(startedAt = null, unlockedAt = 0) })
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        starter = LessonStarter(client, db, Clock.fixed(now, ZoneOffset.UTC))
        repo = LessonRepository(db, starter, { }, scope)
        fake.requests.clear()
    }

    @After fun tearDown() { scope.cancel(); Dispatchers.resetMain(); db.close(); fake.shutdown() }

    private fun vm() = LessonViewModel(repo, SubjectRepository(db), FakeSettings(), account, clock = { now.toEpochMilli() }, random = Random(3))

    private fun LessonViewModel.await(match: (LessonUiState) -> Boolean) = runBlocking {
        try {
            withTimeout(10_000) { while (!match(state.value)) delay(10) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val s = state.value
            throw AssertionError("Timed out in ${s.step}: available=${s.available} learned=${s.learnedThisBatch} result=${s.result} quiz=${s.quiz.phase} answered=${s.quiz.answered} msg=${s.message}")
        }
    }

    @Test fun starterSendsOnceWithTheFinishTime() = runBlocking {
        assertTrue(starter.record(6041, 5041, now.minusSeconds(60).toEpochMilli()))
        assertFalse(starter.record(6041, 5041, now.toEpochMilli()))
        val report = starter.submitPending()
        assertEquals(1, report.started)
        assertEquals(1, fake.lessonStarts.size)
        assertEquals(6041L, fake.lessonStarts.single().first)
        assertTrue(fake.lessonStarts.single().second, fake.lessonStarts.single().second.contains("\"started_at\":\"2026-11-26T11:59:00Z\""))
        assertEquals(1, db.assignments().get(6041)!!.srsStage)
        starter.submitPending()
        assertEquals(1, fake.lessonStarts.size)
    }

    @Test fun starterKeepsLessonsWhenOfflineAndSetsAsideRefusals() = runBlocking {
        starter.record(6041, 5041, now.toEpochMilli())
        fake.lessonResponder = { _, _ -> MockResponse().setResponseCode(503) }
        val failed = starter.submitPending()
        assertTrue(failed.error is WaniKaniError.Server)
        assertEquals(1, failed.stillPending)
        assertEquals(1, fake.lessonStarts.size) // no blind retry
        fake.lessonResponder = { _, _ -> fake.reviewRefused("Assignment has already been started") }
        val refused = starter.submitPending()
        assertEquals(1, refused.rejected)
        assertEquals(ReviewStatus.REJECTED, db.pendingLessons().get(6041)!!.status)
    }

    @Test fun queueFollowsWaniKaniOrder() = runBlocking {
        val q = repo.queue(app.mittsu.quiz.QuestionGenerator(SubjectRepository(db).quizPool()))
        // Level 3 first, by lesson position (46 has the lowest), then the level 9 subject last.
        assertEquals(listOf(5046L, 5045L, 5044L, 5043L, 5042L, 5041L), q.ready.map { it.subject.id })
        assertEquals("例です。", q.ready.first().contextSentences.first().first)
    }

    @Test fun lessonIsStartedOnlyAfterItsQuizIsAnsweredCorrectly() {
        val vm = vm()
        vm.onOpen()
        vm.await { it.step == LessonStep.OVERVIEW }
        assertEquals(6, vm.state.value.available)
        vm.chooseBatchSize(3)
        vm.startBatch()
        assertEquals(LessonStep.TEACHING, vm.state.value.step)
        assertEquals(3, vm.state.value.batch.size)
        repeat(3) { vm.nextCard() }
        vm.await { it.step == LessonStep.QUIZ && it.quiz.phase == QuizPhase.Asking }
        assertEquals(0, fake.lessonStarts.size) // nothing is started just by reading the cards

        var wrongOnce = false
        var guard = 0
        while (vm.state.value.step == LessonStep.QUIZ && guard++ < 40) {
            val q = vm.state.value.quiz.question!!
            assertEquals(3, q.options.size)
            assertFalse(q.isSelfCheck)
            val wrong = !wrongOnce && q.type == QuestionType.READING
            if (wrong) wrongOnce = true
            vm.select(if (wrong) q.options.indices.first { it != q.correctIndex } else q.correctIndex)
            vm.next()
            vm.await { it.step != LessonStep.QUIZ || !it.quiz.answered }
        }
        vm.await { it.step == LessonStep.DONE }
        assertEquals(3, vm.state.value.learnedThisBatch)
        runBlocking { withTimeout(10_000) { while (fake.lessonStarts.size < 3) delay(20) } }
        assertEquals(3, fake.lessonStarts.map { it.first }.toSet().size)
        vm.await { it.result.sent == 3 }

        vm.backToOverview()
        vm.await { it.step == LessonStep.OVERVIEW }
        assertEquals(3, vm.state.value.available)
    }

    @Test fun endingTheQuizEarlyLeavesUnfinishedLessonsAlone() {
        val vm = vm()
        vm.onOpen()
        vm.await { it.step == LessonStep.OVERVIEW }
        vm.chooseBatchSize(3)
        vm.startBatch()
        vm.startQuiz()
        vm.await { it.quiz.phase == QuizPhase.Asking }
        vm.endQuiz()
        vm.await { it.step == LessonStep.DONE }
        assertEquals(0, vm.state.value.learnedThisBatch)
        runBlocking { delay(200) }
        assertEquals(0, fake.lessonStarts.size)
    }
}
