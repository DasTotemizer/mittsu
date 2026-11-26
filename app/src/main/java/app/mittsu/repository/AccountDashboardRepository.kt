package app.mittsu.repository

import app.mittsu.database.MittsuDatabase
import app.mittsu.database.ReviewStatus
import app.mittsu.domain.model.ConnectionState
import app.mittsu.domain.model.DashboardSummary
import app.mittsu.domain.model.ReviewManagementMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.ZoneId

/**
 * Shows the real account once connected, and clearly labelled sample data before that.
 * Numbers are recomputed every minute so "due now" stays accurate as reviews become available.
 */
class AccountDashboardRepository(
    private val account: AccountRepository,
    private val db: MittsuDatabase,
    private val sample: DashboardRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : DashboardRepository {

    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun summary(): Flow<DashboardSummary> = account.connection.flatMapLatest { state ->
        when (state) {
            ConnectionState.Disconnected -> sample.summary()
            is ConnectionState.Connected -> combine(
                db.users().observe().filterNotNull(),
                db.assignments().observeVisible(),
                db.reviewStatistics().observeVisible(),
                db.pendingReviews().observeSince(clock.millis() - Duration.ofDays(2).toMillis()),
                ticker,
            ) { user, assignments, stats, finished, now ->
                val midnight = now.atZone(zone()).toLocalDate().atStartOfDay(zone()).toInstant().toEpochMilli()
                // WaniKani's API no longer lists past reviews, so "today" counts reviews done in Mittsu.
                val today = finished.count { it.status != ReviewStatus.REJECTED && it.completedAt >= midnight }
                // Reviews finished here but not yet sent are no longer due from the user's point of view.
                val waiting = finished.filter { it.status == ReviewStatus.PENDING }.map { it.assignmentId }.toSet()
                // Target and mode are overlaid from settings by the ViewModel.
                DashboardCalculator.summarise(
                    user, assignments.filterNot { it.id in waiting }, stats, now, 50, ReviewManagementMode.DAILY_BALANCE,
                    reviewsCompletedToday = today,
                )
            }
        }
    }

    override suspend fun refresh() {
        if (account.isConnected) account.sync(force = true) else sample.refresh()
    }
}
