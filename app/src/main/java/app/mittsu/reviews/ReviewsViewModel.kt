package app.mittsu.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.database.ReviewStatus
import app.mittsu.domain.model.ConnectionState
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.quiz.QuestionGenerator
import app.mittsu.repository.AccountRepository
import app.mittsu.repository.DashboardCalculator
import app.mittsu.repository.SubjectRepository
import app.mittsu.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class RefusedReview(val characters: String, val message: String)

data class ReviewsUiState(
    val loading: Boolean = true,
    val connected: Boolean = true,
    val checking: Boolean = false,
    val ready: Int = 0,
    val excluded: Int = 0,
    val notDownloaded: Int = 0,
    val sizeOptions: List<Int> = emptyList(),
    val selectedSize: Int = SessionSize.ALL,
    val hasSavedSession: Boolean = false,
    val waitingToSend: Int = 0,
    val refused: List<RefusedReview> = emptyList(),
    val sending: Boolean = false,
    val sendProblem: String? = null,
    val vacation: Boolean = false,
    val nextReviewAt: Instant? = null,
    val reviewMode: ReviewManagementMode = ReviewManagementMode.DAILY_BALANCE,
    val dailyTarget: Int = 50,
    val completedToday: Int = 0,
)

/** The Reviews start screen: what's due, what's waiting to be sent, and a saved session. */
class ReviewsViewModel(
    private val reviews: ReviewRepository,
    private val subjects: SubjectRepository,
    settingsRepository: SettingsRepository,
    private val account: AccountRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private data class QueueInfo(val queue: ReviewQueue, val nextReviewAt: Instant?, val loading: Boolean, val checking: Boolean)

    private val queueInfo = MutableStateFlow(QueueInfo(ReviewQueue.EMPTY, null, loading = true, checking = false))
    private val chosenSize = MutableStateFlow<Int?>(null)
    private val sending = MutableStateFlow(false)
    private var subjectChars: Map<Long, String> = emptyMap()

    private val local = combine(reviews.outstanding(), reviews.finishedSince(startOfToday()), reviews.hasSavedSession()) { outstanding, today, saved ->
        Triple(outstanding, today.count { it.status != ReviewStatus.REJECTED && it.completedAt >= startOfToday() }, saved != null)
    }

    val state: StateFlow<ReviewsUiState> = combine(
        combine(queueInfo, chosenSize, sending, ::Triple),
        local,
        settingsRepository.settings,
        account.connection,
        reviews.lastReport,
    ) { (info, chosen, isSending), (outstanding, completedToday, saved), settings, connection, report ->
        val user = (connection as? ConnectionState.Connected)?.user
        val ready = info.queue.ready.size
        val default = SessionSize.default(settings.reviewMode, settings.dailyTarget, completedToday, ready)
        val options = SessionSize.options(ready)
        ReviewsUiState(
            loading = info.loading,
            connected = connection is ConnectionState.Connected,
            checking = info.checking,
            ready = ready,
            excluded = info.queue.excluded,
            notDownloaded = info.queue.notDownloaded,
            sizeOptions = options,
            selectedSize = chosen?.takeIf { it in options } ?: default,
            hasSavedSession = saved,
            waitingToSend = outstanding.count { it.status == ReviewStatus.PENDING },
            refused = outstanding.filter { it.status == ReviewStatus.REJECTED }.map {
                RefusedReview(subjectChars[it.subjectId] ?: "Subject ${it.subjectId}", it.message ?: "Not accepted")
            },
            sending = isSending,
            sendProblem = report?.takeIf { it.stillPending > 0 }?.error?.message,
            vacation = user?.vacationStartedAt != null,
            nextReviewAt = info.nextReviewAt,
            reviewMode = settings.reviewMode,
            dailyTarget = settings.dailyTarget,
            completedToday = completedToday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewsUiState())

    /** Sends anything waiting, refreshes from WaniKani if it's been a while, then counts what's due. */
    fun onOpen() {
        viewModelScope.launch {
            if (!account.isConnected) {
                queueInfo.value = QueueInfo(ReviewQueue.EMPTY, null, loading = false, checking = false)
                return@launch
            }
            recount(checking = true)
            reviews.sendNow()
            account.sync(force = false)
            recount(checking = false)
        }
    }

    private suspend fun recount(checking: Boolean) {
        val pool = subjects.quizPool()
        subjectChars = pool.associate { it.id to (it.characters ?: "?") }
        val queue = reviews.queue(subjects.quizIndex(), QuestionGenerator(pool))
        val now = Instant.ofEpochMilli(clock())
        val next = reviews.assignments()
            .filter { DashboardCalculator.isInReview(it) && it.availableAt != null && it.availableAt > now.toEpochMilli() }
            .minOfOrNull { it.availableAt!! }?.let(Instant::ofEpochMilli)
        queueInfo.value = QueueInfo(queue, next, loading = false, checking = checking)
    }

    fun chooseSize(size: Int) { chosenSize.value = size }

    fun sendNow() {
        if (sending.value) return
        viewModelScope.launch {
            sending.value = true
            try { reviews.sendNow() } finally { sending.value = false }
        }
    }

    fun dismissRefused() { viewModelScope.launch { reviews.dismissRejected() } }

    fun discardSavedSession() { viewModelScope.launch { reviews.clearSession(); recount(checking = false) } }

    private fun startOfToday(): Long = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReviewsViewModel(container.reviewRepository, container.subjectRepository, container.settingsRepository, container.accountRepository)
            }
        }
    }
}
