package app.mittsu.lessons

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.database.ReviewStatus
import app.mittsu.domain.model.ConnectionState
import app.mittsu.quiz.AdaptiveDifficulty
import app.mittsu.quiz.CompletedItem
import app.mittsu.quiz.Explanation
import app.mittsu.quiz.QuestionGenerator
import app.mittsu.quiz.QuestionType
import app.mittsu.quiz.QuizPhase
import app.mittsu.quiz.QuizSession
import app.mittsu.quiz.QuizSubject
import app.mittsu.quiz.QuizUiState
import app.mittsu.quiz.SessionItem
import app.mittsu.quiz.SubmissionSummary
import app.mittsu.repository.AccountRepository
import app.mittsu.repository.SubjectRepository
import app.mittsu.settings.AppSettings
import app.mittsu.settings.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class LessonStep { LOADING, NOT_CONNECTED, OVERVIEW, TEACHING, QUIZ, DONE, ERROR }

data class LessonUiState(
    val step: LessonStep = LessonStep.LOADING,
    val available: Int = 0,
    val excluded: Int = 0,
    val batchSize: Int = DEFAULT_BATCH,
    val batch: List<LessonCard> = emptyList(),
    val cardIndex: Int = 0,
    val quiz: QuizUiState = QuizUiState(),
    /** Lessons of this batch that WaniKani has started, is waiting for, or refused. */
    val result: SubmissionSummary = SubmissionSummary(),
    val learnedThisBatch: Int = 0,
    val waitingToSend: Int = 0,
    val refused: List<String> = emptyList(),
    val vacation: Boolean = false,
    val message: String? = null,
) {
    val card: LessonCard? get() = batch.getOrNull(cardIndex)

    companion object {
        const val DEFAULT_BATCH = 5
        val BATCH_OPTIONS = listOf(3, 5, 10)
    }
}

/**
 * Lessons: teaching cards for a small batch, then a three-answer quiz over that batch. A lesson
 * is started on WaniKani (PUT /assignments/{id}/start) only once every required answer for it
 * has been chosen correctly in the quiz, exactly as WaniKani's own lesson quiz works.
 */
class LessonViewModel(
    private val lessons: LessonRepository,
    private val subjects: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val account: AccountRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    private var queue = LessonQueue.EMPTY
    private var generator: QuestionGenerator? = null
    private var pool: Map<Long, QuizSubject> = emptyMap()
    private var settings = AppSettings()
    private var adaptive = AdaptiveDifficulty()
    private var session: QuizSession? = null
    private var nextJob: Job? = null
    private var resultJob: Job? = null
    private val persistLock = Mutex()

    private val errors = CoroutineExceptionHandler { _, e ->
        Log.e("MittsuLessons", "Lesson failed", e)
        _state.update { it.copy(step = LessonStep.ERROR, message = "Something went wrong. Lessons you already finished are saved and will still be sent.") }
    }

    init {
        viewModelScope.launch {
            lessons.outstanding().collect { rows ->
                _state.update { s ->
                    s.copy(
                        waitingToSend = rows.count { it.status == ReviewStatus.PENDING },
                        refused = rows.filter { it.status == ReviewStatus.REJECTED }.map { r ->
                            "${pool[r.subjectId]?.characters ?: "Subject ${r.subjectId}"}: ${r.message ?: "Not accepted"}"
                        },
                    )
                }
            }
        }
    }

    /** Loads available lessons, after sending anything waiting and a light sync. */
    fun onOpen() {
        viewModelScope.launch(errors) {
            if (!account.isConnected) {
                _state.update { it.copy(step = LessonStep.NOT_CONNECTED) }
                return@launch
            }
            if (_state.value.step != LessonStep.LOADING) return@launch
            reload()
            lessons.sendNow()
            account.sync(force = false)
            reload()
        }
    }

    private suspend fun reload() {
        settings = settingsRepository.settings.first()
        val all = subjects.quizPool()
        pool = all.associateBy { it.id }
        val gen = QuestionGenerator(all, random, shuffleOptions = settings.randomiseAnswers)
        generator = gen
        queue = lessons.queue(gen)
        val user = (account.connection.first() as? ConnectionState.Connected)?.user
        _state.update {
            // A background refresh must never pull the user out of cards or a quiz they've started.
            val step = if (it.step == LessonStep.LOADING || it.step == LessonStep.OVERVIEW) LessonStep.OVERVIEW else it.step
            it.copy(step = step, available = queue.ready.size, excluded = queue.excluded, vacation = user?.vacationStartedAt != null)
        }
    }

    fun chooseBatchSize(size: Int) { _state.update { it.copy(batchSize = size) } }

    fun startBatch() {
        val batch = queue.ready.take(_state.value.batchSize)
        if (batch.isEmpty()) return
        _state.update { it.copy(step = LessonStep.TEACHING, batch = batch, cardIndex = 0, learnedThisBatch = 0, result = SubmissionSummary()) }
    }

    fun nextCard() {
        val s = _state.value
        if (s.cardIndex < s.batch.lastIndex) _state.update { it.copy(cardIndex = it.cardIndex + 1) } else startQuiz()
    }

    fun previousCard() { _state.update { it.copy(cardIndex = (it.cardIndex - 1).coerceAtLeast(0)) } }

    fun startQuiz() {
        val batch = _state.value.batch
        if (batch.isEmpty()) return
        val items = batch.map { SessionItem(it.subject.id, it.assignmentId, needsMeaning = true, needsReading = QuestionType.READING in it.requiredTypes) }
        adaptive = AdaptiveDifficulty()
        session = QuizSession.start(items, clock(), random)
        _state.update { it.copy(step = LessonStep.QUIZ, quiz = QuizUiState()) }
        showNext()
    }

    private fun currentDifficulty() = if (settings.adaptiveDifficulty) adaptive.current else settings.difficulty

    private fun showNext() {
        if (nextJob?.isActive == true) return
        nextJob = viewModelScope.launch(errors) { showNextNow() }
    }

    private suspend fun showNextNow() {
        val s = session ?: return
        while (true) {
            val pending = s.current ?: return finishQuiz()
            val subject = pool[pending.subjectId]
            val question = subject?.let { withContext(Dispatchers.Default) { generator!!.generate(it, pending.type, currentDifficulty()) } }
            if (question == null || question.isSelfCheck) {
                s.removeItem(pending.subjectId) // left as a lesson on WaniKani
                continue
            }
            _state.update {
                it.copy(quiz = it.quiz.copy(
                    phase = QuizPhase.Asking, question = question, selected = null, explanation = null,
                    questionNumber = s.stats.answered + 1, completedItems = s.completedItems, totalItems = s.totalItems,
                    difficulty = currentDifficulty(), adaptive = settings.adaptiveDifficulty, stats = s.stats,
                ))
            }
            return
        }
    }

    fun select(choice: Int) {
        val s = session ?: return
        val q = _state.value.quiz
        val question = q.question ?: return
        if (_state.value.step != LessonStep.QUIZ || q.answered || choice !in question.options.indices) return
        val correct = question.isCorrect(choice)
        val completed = s.answer(correct)
        if (settings.adaptiveDifficulty) adaptive = adaptive.record(correct)
        _state.update {
            it.copy(quiz = it.quiz.copy(selected = choice, explanation = Explanation.after(question, choice), stats = s.stats, completedItems = s.completedItems))
        }
        if (completed != null) viewModelScope.launch(errors) { record(completed) }
    }

    private suspend fun record(item: CompletedItem) = persistLock.withLock {
        lessons.recordFinished(checkNotNull(item.assignmentId), item.subjectId, clock())
    }

    fun next() { if (_state.value.quiz.answered) showNext() }

    /** Ends the quiz now. Lessons not yet answered correctly stay as lessons on WaniKani. */
    fun endQuiz() { viewModelScope.launch(errors) { finishQuiz() } }

    private suspend fun finishQuiz() {
        val s = session ?: return
        persistLock.withLock { } // wait for any lesson still being recorded
        val ids = _state.value.batch.map { it.assignmentId }.toSet()
        _state.update { it.copy(step = LessonStep.DONE, learnedThisBatch = s.completedItems, quiz = it.quiz.copy(phase = QuizPhase.Finished, stats = s.stats)) }
        resultJob?.cancel()
        resultJob = viewModelScope.launch {
            lessons.finishedSince(s.startedAtMillis).collect { rows ->
                val mine = rows.filter { it.assignmentId in ids }
                _state.update {
                    it.copy(result = SubmissionSummary(
                        sent = mine.count { r -> r.status == ReviewStatus.SUBMITTED },
                        waiting = mine.count { r -> r.status == ReviewStatus.PENDING },
                        refused = mine.count { r -> r.status == ReviewStatus.REJECTED },
                    ))
                }
            }
        }
    }

    /** Back to the overview with a fresh count, ready for the next batch. */
    fun backToOverview() {
        viewModelScope.launch(errors) {
            session = null
            _state.update { it.copy(step = LessonStep.LOADING, batch = emptyList()) }
            reload()
        }
    }

    fun sendNow() { lessons.sendSoon() }
    fun dismissRefused() { viewModelScope.launch { lessons.dismissRejected() } }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LessonViewModel(container.lessonRepository, container.subjectRepository, container.settingsRepository, container.accountRepository) }
        }
    }
}
