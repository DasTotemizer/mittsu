package app.mittsu.repository

import android.util.Log
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.SyncStateEntity
import app.mittsu.network.WaniKaniClient
import app.mittsu.network.WaniKaniError
import java.time.Clock

/**
 * Pulls the account from WaniKani into the local cache. After the first full download each
 * collection is fetched incrementally with `updated_after`, as WaniKani recommends.
 */
class WaniKaniSync(
    private val client: WaniKaniClient,
    private val db: MittsuDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    data class Report(val subjects: Int, val assignments: Int, val reviewStatistics: Int, val skippedInvalid: Int)

    suspend fun run(onStep: (String) -> Unit = {}): Report {
        val api = client.service
        var skipped = 0

        onStep("Checking your account")
        val user = client.call { api.user() }
        if (user.objectType != "user") throw WaniKaniError.InvalidResponse("expected a user")
        db.users().upsert(WaniKaniMappers.user(user.data, clock.millis()))

        onStep("Loading SRS stages")
        syncCollection("spaced_repetition_systems", api::srsSystems) { items ->
            val rows = items.mapNotNull(WaniKaniMappers::srsSystem)
            skipped += items.size - rows.size
            db.srsSystems().upsertAll(rows)
        }

        onStep("Downloading subjects")
        val subjectCount = syncCollection("subjects", api::subjects, forceFull = db.subjects().count() == 0) { items ->
            val rows = items.mapNotNull(WaniKaniMappers::subject)
            skipped += items.size - rows.size
            rows.chunked(500).forEach { db.subjects().upsertAll(it) }
        }

        onStep("Downloading your progress")
        val assignmentCount = syncCollection("assignments", api::assignments, forceFull = db.assignments().count() == 0) { items ->
            val rows = items.mapNotNull(WaniKaniMappers::assignment)
            skipped += items.size - rows.size
            rows.chunked(500).forEach { db.assignments().upsertAll(it) }
        }

        onStep("Downloading review statistics")
        val statCount = syncCollection("review_statistics", api::reviewStatistics) { items ->
            val rows = items.mapNotNull(WaniKaniMappers::reviewStatistic)
            skipped += items.size - rows.size
            rows.chunked(500).forEach { db.reviewStatistics().upsertAll(it) }
        }

        if (skipped > 0) Log.w(TAG, "Skipped $skipped invalid resources during sync")
        return Report(subjectCount, assignmentCount, statCount, skipped)
    }

    private suspend fun <T> syncCollection(
        name: String,
        fetch: suspend (String) -> retrofit2.Response<app.mittsu.network.CollectionDto<T>>,
        forceFull: Boolean = false,
        store: suspend (List<app.mittsu.network.ResourceDto<T>>) -> Unit,
    ): Int {
        val previous = if (forceFull) null else db.syncState().get(name)?.updatedAfter
        val url = if (previous != null) "$name?updated_after=$previous" else name
        val result = client.fetchAll(url, fetch)
        store(result.items)
        db.syncState().upsert(SyncStateEntity(name, result.dataUpdatedAt ?: previous, clock.millis()))
        return result.items.size
    }

    private companion object { const val TAG = "MittsuSync" }
}
