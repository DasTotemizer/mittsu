package app.mittsu.quiz

import app.mittsu.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveDifficultyTest {
    private fun AdaptiveDifficulty.recordAll(vararg results: Boolean) = results.fold(this) { a, r -> a.record(r) }

    @Test fun startsAtNormal() = assertEquals(Difficulty.NORMAL, AdaptiveDifficulty().current)

    @Test fun highAccuracyRaisesDifficulty() {
        val a = AdaptiveDifficulty().recordAll(*BooleanArray(10) { true })
        assertEquals(Difficulty.HARD, a.current)
    }

    @Test fun lowAccuracyLowersDifficulty() {
        val a = AdaptiveDifficulty().recordAll(*BooleanArray(10) { it < 5 })
        assertEquals(Difficulty.EASY, a.current)
    }

    @Test fun steadyAccuracyKeepsLevel() {
        val a = AdaptiveDifficulty().recordAll(*BooleanArray(20) { it % 5 != 0 }) // 80%
        assertEquals(Difficulty.NORMAL, a.current)
    }

    @Test fun neverChangesBeforeAFullWindow() {
        assertEquals(Difficulty.NORMAL, AdaptiveDifficulty().recordAll(*BooleanArray(9) { true }).current)
    }

    @Test fun staysWithinBounds() {
        val up = AdaptiveDifficulty().recordAll(*BooleanArray(40) { true })
        assertEquals(Difficulty.HARD, up.current)
        val down = AdaptiveDifficulty().recordAll(*BooleanArray(40) { false })
        assertEquals(Difficulty.EASY, down.current)
    }

    @Test fun recoversAfterAToughStretch() {
        val a = AdaptiveDifficulty().recordAll(*BooleanArray(10) { false }).recordAll(*BooleanArray(10) { true })
        assertEquals(Difficulty.NORMAL, a.current)
    }
}
