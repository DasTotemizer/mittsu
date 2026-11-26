package app.mittsu.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.domain.model.Difficulty
import app.mittsu.repository.SubjectRepository
import app.mittsu.settings.AppSettings
import app.mittsu.settings.SettingsRepository
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed interface QuizPhase {
    data object Loading : QuizPhase
    data class Unavailable(val message: String) : QuizPhase
    data object Asking : QuizPhase
    data object Finished : QuizPhase
}

data class QuizUiState(
    val phase: QuizPhase = QuizPhase.Loading,
    val question: Question? = null,
    val selected: Int? = null,
    val explanation: String? = null,
    val questionNumber: Int = 0,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val adaptive: Boolean = false,
    val stats: SessionStats = SessionStats(),
    val durationMillis: Long = 0,
    /** Real reviews only: what happened to the finished reviews of this session. */
    val submission: SubmissionSummary? = null,
    /** Real reviews only: due items left on WaniKani because they can't be asked fairly here. */
    val excluded: Int = 0,
) {
    val answered: Boolean get() = selected != null
    val progress: Float get() = if (totalItems == 0) 0f else completedItems.toFloat() / totalItems
}

/** How many of a real session's finished reviews WaniKani has, is waiting for, or refused. */
data class SubmissionSummary(val sent: Int = 0, val waiting: Int = 0, val refused: Int = 0) {
    val total: Int get() = sent + waiting + refused
}

/**
 * Practice quiz over cached subjects. This ViewModel has no access to any WaniKani write
 * endpoint: practice answers cannot reach the account.
 */
class PracticeQuizViewModel(
    private val subjects: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var session: QuizSession? = null
    private var generator: QuestionGenerator? = null
    private var pool: Map<Long, QuizSubject> = emptyMap()
    private var adaptive = AdaptiveDifficulty()
    private var settings = AppSettings()

    /** Any unexpected failure ends the session with a message instead of crashing the app. */
    private val errors = CoroutineExceptionHandler { _, e ->
        Log.e("MittsuQuiz", "Practice session failed", e)
        _state.value = QuizUiState(phase = QuizPhase.Unavailable("Something went wrong preparing this question. Please try again."))
    }

    /** Quick practice of [size] random learned subjects, or a custom session when [filters] is given. */
    fun start(size: Int = 10, filters: PracticeFilters? = null) {
        lastFilters = filters
        viewModelScope.launch(errors) {
            _state.value = QuizUiState()
            settings = settingsRepository.settings.first()
            val all = subjects.quizPool()
            val learned = subjects.learnedSubjectIds()
            pool = all.associateBy { it.id }
            val candidates = all.filter { it.id in learned && it.questionTypes(settings.quizFocus).isNotEmpty() }
            if (all.isEmpty() || candidates.isEmpty()) {
                _state.value = QuizUiState(
                    phase = QuizPhase.Unavailable(
                        if (all.isEmpty()) "Connect your WaniKani account first. Practice uses your downloaded subjects."
                        else "Nothing to practise yet with these settings. Finish a few lessons on WaniKani first.",
                    ),
                )
                return@launch
            }
            val chosen = if (filters == null) candidates.shuffled(random).take(size)
                else PracticeFilters.select(candidates, subjects.practiceInfo(), filters, random)
            if (chosen.isEmpty()) {
                _state.value = QuizUiState(phase = QuizPhase.Unavailable("No learned subjects match these filters. Try widening them."))
                return@launch
            }
            val items = chosen.map { s ->
                val types = s.questionTypes(settings.quizFocus)
                SessionItem(s.id, null, QuestionType.MEANING in types, QuestionType.READING in types)
            }
            generator = QuestionGenerator(all, random, shuffleOptions = settings.randomiseAnswers)
            adaptive = AdaptiveDifficulty()
            session = QuizSession.start(items, clock(), random)
            showNext()
        }
    }

    private var nextJob: kotlinx.coroutines.Job? = null
    private var lastFilters: PracticeFilters? = null

    /** Another session with the same choices as the last one. */
    fun restart() = start(lastFilters?.size ?: 10, lastFilters)

    private fun showNext() {
        if (nextJob?.isActive == true) return // ignore double taps on Continue
        nextJob = viewModelScope.launch(errors) { showNextNow() }
    }

    private fun currentDifficulty() = if (settings.adaptiveDifficulty) adaptive.current else settings.difficulty

    private suspend fun showNextNow() {
        val s = session ?: return
        val pending = s.current
        if (pending == null) {
            _state.update {
                it.copy(phase = QuizPhase.Finished, question = null, selected = null, explanation = null,
                    stats = s.stats, completedItems = s.completedItems, durationMillis = clock() - s.startedAtMillis)
            }
            return
        }
        val subject = pool.getValue(pending.subjectId)
        val difficulty = currentDifficulty()
        // Scoring thousands of candidates is CPU work: keep it off the main thread.
        val question = withContext(Dispatchers.Default) { generator!!.generate(subject, pending.type, difficulty) }
        _state.update {
            it.copy(
                phase = QuizPhase.Asking, question = question, selected = null, explanation = null,
                questionNumber = s.stats.answered + 1, completedItems = s.completedItems, totalItems = s.totalItems,
                difficulty = currentDifficulty(), adaptive = settings.adaptiveDifficulty, stats = s.stats,
            )
        }
    }

    fun select(choice: Int) {
        val s = session ?: return
        val current = _state.value
        val question = current.question ?: return
        if (current.answered || choice !in question.options.indices) return
        val correct = question.isCorrect(choice)
        s.answer(correct)
        if (settings.adaptiveDifficulty) adaptive = adaptive.record(correct)
        _state.update {
            it.copy(selected = choice, explanation = Explanation.after(question, choice), stats = s.stats, completedItems = s.completedItems)
        }
    }

    fun next() {
        if (_state.value.answered) showNext()
    }

    /** Ends early, keeping what has been answered. */
    fun finish() {
        val s = session ?: return
        _state.update {
            it.copy(phase = QuizPhase.Finished, question = null, selected = null, explanation = null,
                stats = s.stats, completedItems = s.completedItems, durationMillis = clock() - s.startedAtMillis)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PracticeQuizViewModel(container.subjectRepository, container.settingsRepository) }
        }
    }
}
