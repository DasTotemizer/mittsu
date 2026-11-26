package app.mittsu.repository

import app.mittsu.database.MittsuDatabase
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

/**
 * Shows the real account once connected, and clearly labelled sample data before that.
 * Numbers are recomputed every minute so "due now" stays accurate as reviews become available.
 */
class AccountDashboardRepository(
    private val account: AccountRepository,
    private val db: MittsuDatabase,
    private val sample: DashboardRepository,
    private val clock: Clock = Clock.systemUTC(),
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
                ticker,
            ) { user, assignments, stats, now ->
                // Target and mode are overlaid from settings by the ViewModel.
                DashboardCalculator.summarise(user, assignments, stats, now, 50, ReviewManagementMode.DAILY_BALANCE)
            }
        }
    }

    override suspend fun refresh() {
        if (account.isConnected) account.sync(force = true) else sample.refresh()
    }
}
