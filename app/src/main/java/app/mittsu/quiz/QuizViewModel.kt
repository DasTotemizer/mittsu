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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) {
    val answered: Boolean get() = selected != null
    val progress: Float get() = if (totalItems == 0) 0f else completedItems.toFloat() / totalItems
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

    fun start(size: Int = 10) {
        viewModelScope.launch {
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
            val chosen = candidates.shuffled(random).take(size)
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

    private fun currentDifficulty() = if (settings.adaptiveDifficulty) adaptive.current else settings.difficulty

    private fun showNext() {
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
        val question = generator!!.generate(subject, pending.type, currentDifficulty())
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
