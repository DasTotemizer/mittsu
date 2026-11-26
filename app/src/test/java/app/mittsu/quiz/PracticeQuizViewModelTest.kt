package app.mittsu.quiz

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.database.MittsuDatabase
import app.mittsu.network.FakeWaniKani
import app.mittsu.repository.MemoryTokenStore
import app.mittsu.repository.SubjectRepository
import app.mittsu.repository.WaniKaniAccountRepository
import app.mittsu.settings.AppSettings
import app.mittsu.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun setTheme(theme: app.mittsu.domain.model.ThemePreference) { settings.value = settings.value.copy(theme = theme) }
    override suspend fun setDifficulty(difficulty: app.mittsu.domain.model.Difficulty) { settings.value = settings.value.copy(difficulty = difficulty) }
    override suspend fun setReviewMode(mode: app.mittsu.domain.model.ReviewManagementMode) { settings.value = settings.value.copy(reviewMode = mode) }
    override suspend fun setDailyTarget(target: Int) { settings.value = settings.value.copy(dailyTarget = target) }
    override suspend fun setAdaptiveDifficulty(enabled: Boolean) { settings.value = settings.value.copy(adaptiveDifficulty = enabled) }
    override suspend fun setQuizFocus(focus: QuizFocus) { settings.value = settings.value.copy(quizFocus = focus) }
    override suspend fun setRandomiseAnswers(enabled: Boolean) { settings.value = settings.value.copy(randomiseAnswers = enabled) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PracticeQuizViewModelTest {
    private lateinit var fake: FakeWaniKani
    private lateinit var db: MittsuDatabase

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeWaniKani().start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MittsuDatabase::class.java)
            .allowMainThreadQueries().build()
        val tokens = MemoryTokenStore()
        val account = WaniKaniAccountRepository(tokens, fake.client(tokens::get), db)
        runBlocking { account.connect(fake.validToken); account.sync() }
        fake.requests.clear()
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close(); fake.shutdown() }

    private fun PracticeQuizViewModel.awaitPhase(match: (QuizPhase) -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!match(state.value.phase)) kotlinx.coroutines.delay(20) }
    }

    @Test fun practiceRunsToCompletionWithoutContactingWaniKani() {
        val vm = PracticeQuizViewModel(SubjectRepository(db), FakeSettings(), random = Random(1))
        vm.start()
        vm.awaitPhase { it != QuizPhase.Loading }
        // Learned subjects in the fixture: the radical 一 (meaning) and the kanji 一 (meaning + reading).
        assertEquals(2, vm.state.value.totalItems)
        var guard = 0
        while (vm.state.value.phase == QuizPhase.Asking && guard++ < 50) {
            val q = vm.state.value.question!!
            assertEquals(3, q.options.size)
            vm.select(q.correctIndex)
            vm.next()
        }
        assertEquals(QuizPhase.Finished, vm.state.value.phase)
        assertEquals(3, vm.state.value.stats.answered)
        assertEquals(2, vm.state.value.completedItems)
        assertTrue("practice must never call WaniKani", fake.requests.isEmpty())
    }

    @Test fun meaningOnlyFocusAsksNoReadings() {
        val vm = PracticeQuizViewModel(SubjectRepository(db), FakeSettings(AppSettings(quizFocus = QuizFocus.MEANING_ONLY)), random = Random(2))
        vm.start()
        vm.awaitPhase { it != QuizPhase.Loading }
        while (vm.state.value.phase == QuizPhase.Asking) {
            assertEquals(QuestionType.MEANING, vm.state.value.question!!.type)
            vm.select(0); vm.next()
        }
    }

    @Test fun emptyCacheExplainsWhatToDo() {
        db.clearAllTables()
        val vm = PracticeQuizViewModel(SubjectRepository(db), FakeSettings())
        vm.start()
        vm.awaitPhase { it != QuizPhase.Loading }
        assertTrue(vm.state.value.phase is QuizPhase.Unavailable)
    }
}
