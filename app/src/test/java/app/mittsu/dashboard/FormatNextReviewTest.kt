package app.mittsu.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FormatNextReviewTest {
    private val now = Instant.parse("2026-11-26T12:00:00Z")

    @Test fun formatsRelativeTimes() {
        assertEquals("–", formatNextReview(null, now))
        assertEquals("Now", formatNextReview(now.minusSeconds(5), now))
        assertEquals("1 min", formatNextReview(now.plusSeconds(20), now))
        assertEquals("42 min", formatNextReview(now.plus(Duration.ofMinutes(42)), now))
        assertEquals("3 h", formatNextReview(now.plus(Duration.ofMinutes(200)), now))
        assertEquals("2 d", formatNextReview(now.plus(Duration.ofHours(50)), now))
    }
}
