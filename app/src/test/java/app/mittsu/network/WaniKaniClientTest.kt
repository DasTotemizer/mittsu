package app.mittsu.network

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WaniKaniClientTest {
    private lateinit var fake: FakeWaniKani

    @Before fun setUp() { fake = FakeWaniKani().start() }
    @After fun tearDown() { fake.shutdown() }

    @Test fun sendsBearerTokenAndRevisionHeader() = runTest {
        val client = fake.client { fake.validToken }
        val user = client.call { client.service.user() }
        assertEquals("testlearner", user.data.username)
        assertEquals(14, user.data.level)
        assertEquals("lifetime", user.data.subscription.type)
        val req = fake.requests.single()
        assertEquals("Bearer ${fake.validToken}", req.getHeader("Authorization"))
        assertEquals("20170710", req.getHeader("Wanikani-Revision"))
    }

    @Test fun noTokenMeansNoAuthorizationHeader() = runTest {
        val client = fake.client { null }
        try { client.call { client.service.user() }; fail() } catch (_: WaniKaniError.Unauthorized) { }
        assertNull(fake.requests.single().getHeader("Authorization"))
    }

    @Test fun explicitTokenOverridesStoredToken() = runTest {
        val client = fake.client { "stored-but-wrong" }
        val user = client.call { client.service.userWithToken("Bearer ${fake.validToken}") }
        assertEquals("testlearner", user.data.username)
        assertEquals(1, fake.requests.single().headers.values("Authorization").size)
    }

    @Test fun badTokenIsUnauthorized() = runTest {
        val client = fake.client { "11111111-1111-1111-1111-111111111111" }
        try { client.call { client.service.user() }; fail("expected Unauthorized") } catch (e: WaniKaniError.Unauthorized) {
            assertTrue(e.message!!.contains("did not accept"))
        }
    }

    @Test fun followsPaginationAcrossPages() = runTest {
        val client = fake.client { fake.validToken }
        val result = client.fetchAll("subjects", client.service::subjects)
        assertEquals(listOf(1L, 440L, 2467L, 9999L), result.items.map { it.id })
        assertEquals("2026-11-20T12:00:00.000000Z", result.dataUpdatedAt)
        assertEquals(2, fake.requests.size)
        assertTrue(fake.requests[1].requestUrl!!.query!!.contains("page_after_id=440"))
    }

    @Test fun waitsAndRetriesWhenRateLimited() = runTest {
        fake.rateLimitNext = 2
        val client = fake.client { fake.validToken }
        assertEquals("testlearner", client.call { client.service.user() }.data.username)
        assertEquals(3, fake.requests.size)
    }

    @Test fun givesUpAfterRepeatedRateLimits() = runTest {
        fake.rateLimitNext = 10
        val client = fake.client { fake.validToken }
        try { client.call { client.service.user() }; fail() } catch (_: WaniKaniError.RateLimited) { }
        assertEquals(4, fake.requests.size) // first try + 3 waits
    }

    @Test fun retriesServerErrors() = runTest {
        fake.serverErrorNext = 1
        val client = fake.client { fake.validToken }
        assertEquals(14, client.call { client.service.user() }.data.level)
    }

    @Test fun networkFailureIsReportedAsNetworkError() = runTest {
        val client = fake.client { fake.validToken }
        fake.shutdown()
        try { client.call { client.service.user() }; fail() } catch (_: WaniKaniError.Network) { }
    }

    @Test fun rateLimitWaitUsesResetHeader() {
        val now = Instant.parse("2026-11-26T12:00:00Z")
        val client = WaniKaniClient(fake.client { null }.service, Clock.fixed(now, ZoneOffset.UTC))
        assertEquals(20_500L, client.rateLimitWaitMillis((now.epochSecond + 20).toString()))
        assertEquals(1_000L, client.rateLimitWaitMillis((now.epochSecond - 50).toString()))
        assertEquals(65_000L, client.rateLimitWaitMillis((now.epochSecond + 600).toString()))
        assertEquals(10_000L, client.rateLimitWaitMillis(null))
    }
}
