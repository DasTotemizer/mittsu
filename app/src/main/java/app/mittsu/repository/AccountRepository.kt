package app.mittsu.repository

import app.mittsu.data.TokenFormat
import app.mittsu.data.TokenStore
import app.mittsu.database.MittsuDatabase
import app.mittsu.domain.model.ConnectionState
import app.mittsu.domain.model.SyncStatus
import app.mittsu.domain.model.WaniKaniUser
import app.mittsu.network.WaniKaniClient
import app.mittsu.network.WaniKaniError
import app.mittsu.network.WaniKaniHeadersInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant

interface AccountRepository {
    val connection: Flow<ConnectionState>
    val syncStatus: StateFlow<SyncStatus>
    val isConnected: Boolean
    suspend fun connect(rawToken: String): Result<WaniKaniUser>
    suspend fun sync(force: Boolean = true): Result<Unit>
    suspend fun disconnect()
}

class WaniKaniAccountRepository(
    private val tokenStore: TokenStore,
    private val client: WaniKaniClient,
    private val db: MittsuDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val minSyncInterval: Duration = Duration.ofMinutes(5),
) : AccountRepository {

    private val hasToken = MutableStateFlow(tokenStore.get() != null)
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    private val syncMutex = Mutex()
    private var lastSuccess: Instant? = null

    override val isConnected: Boolean get() = hasToken.value

    @OptIn(ExperimentalCoroutinesApi::class)
    override val connection: Flow<ConnectionState> = hasToken.flatMapLatest { connected ->
        if (!connected) flowOf(ConnectionState.Disconnected)
        else db.users().observe().map { ConnectionState.Connected(it?.let(WaniKaniMappers::user)) }
    }

    override suspend fun connect(rawToken: String): Result<WaniKaniUser> = withContext(Dispatchers.IO) {
        val token = TokenFormat.normalise(rawToken)
        if (!TokenFormat.looksValid(token)) {
            return@withContext Result.failure(IllegalArgumentException("That doesn't look like a WaniKani API token. It should look like 8-4-4-4-12 letters and numbers."))
        }
        try {
            val res = client.call { client.service.userWithToken(WaniKaniHeadersInterceptor.bearer(token)) }
            if (res.objectType != "user") throw WaniKaniError.InvalidResponse("expected a user")
            // Switching accounts must never mix cached data.
            val previous = db.users().get()
            if (previous != null && previous.username != res.data.username) db.clearAllTables()
            tokenStore.save(token)
            db.users().upsert(WaniKaniMappers.user(res.data, clock.millis()))
            hasToken.value = true
            Result.success(WaniKaniMappers.user(db.users().get()!!))
        } catch (e: WaniKaniError) {
            Result.failure(e)
        }
    }

    override suspend fun sync(force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasToken.value) return@withContext Result.failure(IllegalStateException("Not connected"))
        syncMutex.withLock {
            val last = lastSuccess
            if (!force && last != null && Duration.between(last, clock.instant()) < minSyncInterval) {
                return@withLock Result.success(Unit)
            }
            try {
                WaniKaniSync(client, db, clock).run { step -> _syncStatus.value = SyncStatus.Running(step) }
                val now = clock.instant()
                lastSuccess = now
                _syncStatus.value = SyncStatus.Done(now)
                Result.success(Unit)
            } catch (e: WaniKaniError) {
                _syncStatus.value = SyncStatus.Failed(e.message ?: "Sync failed", needsReconnect = e is WaniKaniError.Unauthorized)
                Result.failure(e)
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        tokenStore.clear()
        db.clearAllTables()
        lastSuccess = null
        _syncStatus.value = SyncStatus.Idle
        hasToken.value = false
    }
}
