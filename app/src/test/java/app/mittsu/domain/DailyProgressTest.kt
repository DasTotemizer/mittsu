package app.mittsu.domain

import app.mittsu.domain.model.DailyProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyProgressTest {
    @Test fun remainingCountsDownToZero() {
        assertEquals(18, DailyProgress(completed = 32, target = 50).remaining)
        assertEquals(0, DailyProgress(completed = 50, target = 50).remaining)
        assertEquals(0, DailyProgress(completed = 70, target = 50).remaining)
    }

    @Test fun fractionIsClampedBetweenZeroAndOne() {
        assertEquals(0.64f, DailyProgress(32, 50).fraction, 0.0001f)
        assertEquals(1f, DailyProgress(80, 50).fraction, 0f)
        assertEquals(0f, DailyProgress(0, 50).fraction, 0f)
    }

    @Test fun zeroTargetNeverDividesByZeroOrCountsAsReached() {
        val p = DailyProgress(10, 0)
        assertEquals(0f, p.fraction, 0f)
        assertFalse(p.targetReached)
    }

    @Test fun beyondTargetTracksExtraReviews() {
        val p = DailyProgress(65, 50)
        assertTrue(p.targetReached)
        assertEquals(15, p.beyondTarget)
    }
}
