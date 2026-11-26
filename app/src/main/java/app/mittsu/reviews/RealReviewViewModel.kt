package app.mittsu.reviews

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.database.ReviewStatus
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
import app.mittsu.repository.DashboardCalculator
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
import java.time.Instant
import kotlin.random.Random

/**
 * A real WaniKani review session.
 *
 * Only subjects WaniKani says are due right now are asked. Each review is handed to
 * [ReviewRepository] once, at the moment every required answer (meaning and, where WaniKani
 * asks for it, reading) has been answered correctly, with the number of wrong answers of each
 * type. A subject that can't be asked with three real answers is taken out of the session and
 * left untouched on WaniKani; nothing is ever guessed or self-rated for the real account.
 */
class RealReviewViewModel(
    private val reviews: ReviewRepository,
    private val subjects: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val account: AccountRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var session: QuizSession? = null
    private var generator: QuestionGenerator? = null
    private var pool: Map<Long, QuizSubject> = emptyMap()
    private var availableAt: Map<Long, Long> = emptyMap()
    private var excluded = 0
    private var adaptive = AdaptiveDifficulty()
    private var settings = AppSettings()
    private var started = false
    private var ended = false
    private var nextJob: Job? = null
    private var summaryJob: Job? = null
    /** Saves run one at a time and in order, so a newer snapshot is never overwritten by an older one. */
    private val persistLock = Mutex()

    private val errors = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Review session failed", e)
        _state.value = QuizUiState(
            phase = QuizPhase.Unavailable("Something went wrong preparing this review. Reviews you already finished are saved and will still be sent."),
        )
    }

    /** Starts a new session of [size] reviews ([SessionSize.ALL] for every ready review). */
    fun start(size: Int) {
        if (started) return
        started = true
        viewModelScope.launch(errors) {
            if (!account.isConnected) return@launch unavailable("Connect your WaniKani account to do real reviews.")
            prepare()
            val queue = reviews.queue(subjects.quizIndex(), generator!!)
            val chosen = if (size == SessionSize.ALL) queue.ready else queue.ready.take(size)
            if (chosen.isEmpty()) return@launch unavailable("No reviews are ready right now.")
            val items = chosen.map { c ->
                SessionItem(
                    subjectId = c.subject.id,
                    assignmentId = c.assignment.id,
                    needsMeaning = true,
                    needsReading = QuestionType.READING in c.requiredTypes,
                )
            }
            availableAt = chosen.associate { it.assignment.id to it.assignment.availableAt!! }
            excluded = 0
            session = QuizSession.start(items, clock(), random)
            persist(null)
            showNext()
        }
    }

    /** Picks up the saved session, dropping anything WaniKani no longer has due. */
    fun resume() {
        if (started) return
        started = true
        viewModelScope.launch(errors) {
            if (!account.isConnected) return@launch unavailable("Connect your WaniKani account to do real reviews.")
            val saved = reviews.loadSession() ?: return@launch unavailable("There's no saved session to continue.")
            prepare()
            val restored = QuizSession.restore(saved.snapshot, random)
            val now = Instant.ofEpochMilli(clock())
            val current = reviews.assignments().associateBy { it.id }
            var dropped = 0
            for (item in saved.snapshot.items) {
                if (item.isComplete) continue
                val a = item.assignmentId?.let(current::get)
                val stillDue = a != null && DashboardCalculator.isDueNow(a, now) && a.availableAt == saved.availableAt[a.id]
                if (!stillDue || item.subjectId !in pool) {
                    restored.removeItem(item.subjectId)
                    dropped++
                }
            }
            session = restored
            availableAt = saved.availableAt
            excluded = saved.excluded
            if (restored.isFinished && dropped > 0 && restored.totalItems == 0) {
                reviews.clearSession()
                return@launch unavailable("Those reviews were already done elsewhere, so there's nothing left to continue.")
            }
            persist(null)
            showNext()
        }
    }

    private suspend fun prepare() {
        settings = settingsRepository.settings.first()
        val all = subjects.quizPool()
        pool = all.associateBy { it.id }
        generator = QuestionGenerator(all, random, shuffleOptions = settings.randomiseAnswers)
        adaptive = AdaptiveDifficulty()
    }

    private fun unavailable(message: String) {
        _state.value = QuizUiState(phase = QuizPhase.Unavailable(message))
    }

    private fun currentDifficulty() = if (settings.adaptiveDifficulty) adaptive.current else settings.difficulty

    private fun showNext() {
        if (nextJob?.isActive == true) return
        nextJob = viewModelScope.launch(errors) { showNextNow() }
    }

    private suspend fun showNextNow() {
        val s = session ?: return
        while (true) {
            val pending = s.current ?: return finishSession()
            val subject = pool[pending.subjectId]
            val question = subject?.let {
                withContext(Dispatchers.Default) { generator!!.generate(it, pending.type, currentDifficulty()) }
            }
            if (question == null || question.isSelfCheck) {
                // Can't offer three real answers: leave this review untouched on WaniKani.
                s.removeItem(pending.subjectId)
                excluded++
                persist(null)
                continue
            }
            _state.update {
                it.copy(
                    phase = QuizPhase.Asking, question = question, selected = null, explanation = null,
                    questionNumber = s.stats.answered + 1, completedItems = s.completedItems, totalItems = s.totalItems,
                    difficulty = currentDifficulty(), adaptive = settings.adaptiveDifficulty, stats = s.stats,
                    excluded = excluded,
                )
            }
            return
        }
    }

    fun select(choice: Int) {
        val s = session ?: return
        val current = _state.value
        val question = current.question ?: return
        if (current.phase != QuizPhase.Asking || current.answered || choice !in question.options.indices) return
        val correct = question.isCorrect(choice)
        val completed = s.answer(correct)
        if (settings.adaptiveDifficulty) adaptive = adaptive.record(correct)
        _state.update {
            it.copy(selected = choice, explanation = Explanation.after(question, choice), stats = s.stats, completedItems = s.completedItems)
        }
        viewModelScope.launch(errors) { persist(completed) }
    }

    /** Records a finished review (if any) and then saves the session, strictly in that order. */
    private suspend fun persist(completed: CompletedItem?) {
        val s = session ?: return
        persistLock.withLock {
            if (completed != null) {
                val assignmentId = checkNotNull(completed.assignmentId)
                reviews.recordFinished(
                    assignmentId = assignmentId,
                    subjectId = completed.subjectId,
                    availableAt = availableAt.getValue(assignmentId),
                    incorrectMeaning = completed.incorrectMeaning,
                    incorrectReading = completed.incorrectReading,
                    completedAt = clock(),
                )
            }
            if (!ended && !s.isFinished) reviews.saveSession(SavedReviewSession(s.snapshot(), availableAt, excluded))
        }
    }

    fun next() {
        if (_state.value.answered) showNext()
    }

    /**
     * Ends the session now. Finished reviews are already saved and sent. Subjects only partly
     * answered are not sent: they stay due on WaniKani exactly as before.
     */
    fun finish() {
        viewModelScope.launch(errors) { finishSession() }
    }

    private suspend fun finishSession() {
        val s = session ?: return
        persistLock.withLock {
            ended = true
            reviews.clearSession()
        }
        _state.update {
            it.copy(
                phase = QuizPhase.Finished, question = null, selected = null, explanation = null,
                stats = s.stats, completedItems = s.completedItems, durationMillis = clock() - s.startedAtMillis,
                excluded = excluded, submission = it.submission ?: SubmissionSummary(),
            )
        }
        if (summaryJob == null) {
            summaryJob = viewModelScope.launch {
                reviews.finishedSince(s.startedAtMillis).collect { rows ->
                    val ids = s.snapshot().items.mapNotNull { it.assignmentId }.toSet()
                    val mine = rows.filter { it.assignmentId in ids && it.availableAt == availableAt[it.assignmentId] }
                    _state.update {
                        it.copy(
                            submission = SubmissionSummary(
                                sent = mine.count { r -> r.status == ReviewStatus.SUBMITTED },
                                waiting = mine.count { r -> r.status == ReviewStatus.PENDING },
                                refused = mine.count { r -> r.status == ReviewStatus.REJECTED },
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Tries again to send anything still waiting (button on the summary). */
    fun retrySending() {
        reviews.sendSoon()
    }

    companion object {
        private const val TAG = "MittsuReviews"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RealReviewViewModel(container.reviewRepository, container.subjectRepository, container.settingsRepository, container.accountRepository)
            }
        }
    }
}
