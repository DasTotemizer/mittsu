package app.mittsu.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.data.TokenStore
import app.mittsu.database.MittsuDatabase
import app.mittsu.domain.model.ConnectionState
import app.mittsu.domain.model.SyncStatus
import app.mittsu.network.FakeWaniKani
import app.mittsu.network.WaniKaniError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

class MemoryTokenStore : TokenStore {
    var value: String? = null
    override fun get() = value
    override fun save(token: String) { value = token }
    override fun clear() { value = null }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AccountRepositoryTest {
    private lateinit var fake: FakeWaniKani
    private lateinit var db: MittsuDatabase
    private lateinit var tokens: MemoryTokenStore
    private lateinit var repo: WaniKaniAccountRepository

    @Before fun setUp() {
        fake = FakeWaniKani().start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MittsuDatabase::class.java)
            .allowMainThreadQueries().build()
        tokens = MemoryTokenStore()
        repo = WaniKaniAccountRepository(tokens, fake.client(tokens::get), db)
    }

    @After fun tearDown() { db.close(); fake.shutdown() }

    @Test fun connectValidatesBeforeSaving() = runBlocking {
        val result = repo.connect("  ${fake.validToken} ")
        assertTrue(result.isSuccess)
        assertEquals("testlearner", result.getOrThrow().username)
        assertTrue(result.getOrThrow().isLifetime)
        assertEquals(fake.validToken, tokens.value)
        val state = repo.connection.first { it is ConnectionState.Connected } as ConnectionState.Connected
        assertEquals(14, state.user?.level)
    }

    @Test fun rejectedTokenIsNeverSaved() = runBlocking {
        val result = repo.connect("99999999-1111-2222-3333-444444444444")
        assertTrue(result.exceptionOrNull() is WaniKaniError.Unauthorized)
        assertNull(tokens.value)
        assertEquals(ConnectionState.Disconnected, repo.connection.first())
    }

    @Test fun malformedTokenFailsWithoutNetworkCall() = runBlocking {
        val result = repo.connect("hunter2")
        assertTrue(result.isFailure)
        assertTrue(fake.requests.isEmpty())
        assertNull(tokens.value)
    }

    @Test fun firstSyncDownloadsAndValidatesEverything() = runBlocking {
        repo.connect(fake.validToken)
        assertTrue(repo.sync().isSuccess)
        // 9999 is a kanji with no readings: skipped instead of becoming a broken question.
        assertEquals(3, db.subjects().count())
        assertNull(db.subjects().get(9999))
        val kanji = db.subjects().get(440)!!
        assertEquals(listOf("いち"), kanji.readings.filter { it.accepted }.map { it.reading })
        assertEquals(3, kanji.readings.size)
        assertEquals("1", kanji.auxiliaryMeanings.single().meaning)
        val vocab = db.subjects().get(2467)!!
        assertEquals("一ど、あいましょう。", vocab.contextSentences.single().ja)
        assertEquals(3, db.assignments().count())
        assertEquals(listOf(0L, 7200L, 7200L), db.srsSystems().all().single().stageIntervalsSeconds)
        assertTrue(repo.syncStatus.value is SyncStatus.Done)
    }

    @Test fun secondSyncIsIncremental() = runBlocking {
        repo.connect(fake.validToken)
        repo.sync()
        fake.requests.clear()
        repo.sync(force = true)
        val urls = fake.requests.map { it.requestUrl!!.toString() }
        assertTrue(urls.any { it.contains("subjects?updated_after=2026-11-20T12:00:00.000000Z") })
        assertTrue(urls.any { it.contains("assignments?updated_after=2026-11-26T09:00:00.000000Z") })
        // The changed assignment was applied, the others kept.
        val radical = db.assignments().visible().single { it.subjectId == 1L }
        assertEquals(5, radical.srsStage)
        assertEquals(3, db.assignments().count())
        assertEquals(3, db.subjects().count())
    }

    @Test fun unforcedSyncSkipsWhenRecent() = runBlocking {
        repo.connect(fake.validToken)
        repo.sync()
        fake.requests.clear()
        repo.sync(force = false)
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun revokedTokenAsksToReconnectButKeepsCache() = runBlocking {
        repo.connect(fake.validToken)
        repo.sync()
        fake.revokeToken = true
        assertTrue(repo.sync().isFailure)
        val status = repo.syncStatus.value as SyncStatus.Failed
        assertTrue(status.needsReconnect)
        assertEquals(3, db.subjects().count())
    }

    @Test fun disconnectRemovesTokenAndCache() = runBlocking {
        repo.connect(fake.validToken)
        repo.sync()
        repo.disconnect()
        assertNull(tokens.value)
        assertEquals(0, db.subjects().count())
        assertNull(db.users().get())
        assertEquals(ConnectionState.Disconnected, repo.connection.first())
        assertFalse(repo.isConnected)
        assertNotNull(repo.sync().exceptionOrNull())
    }

    @Test fun tokenNeverAppearsInErrorMessages() = runBlocking {
        fake.revokeToken = true
        val error = repo.connect(fake.validToken).exceptionOrNull()!!
        assertFalse(error.message!!.contains(fake.validToken))
    }
}
