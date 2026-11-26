package app.mittsu.repository

import app.mittsu.domain.model.DashboardSummary
import kotlinx.coroutines.flow.Flow

/** Source of the home-screen summary. Phase 1 uses sample data; Phase 2 swaps in WaniKani. */
interface DashboardRepository {
    fun summary(): Flow<DashboardSummary>
    suspend fun refresh()
}
