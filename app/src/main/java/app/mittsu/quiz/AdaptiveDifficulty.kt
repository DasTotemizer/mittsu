package app.mittsu.quiz

import app.mittsu.domain.model.Difficulty

/**
 * Optional adaptive difficulty. Starts at Normal; after each window of answers it steps up when
 * accuracy stays high and down when it falls. Only changes which distractors are shown.
 */
data class AdaptiveDifficulty(
    val current: Difficulty = Difficulty.NORMAL,
    val window: List<Boolean> = emptyList(),
    val windowSize: Int = 10,
    val raiseAt: Double = 0.9,
    val lowerBelow: Double = 0.7,
) {
    fun record(correct: Boolean): AdaptiveDifficulty {
        val next = window + correct
        if (next.size < windowSize) return copy(window = next)
        val accuracy = next.count { it }.toDouble() / next.size
        val level = when {
            accuracy >= raiseAt -> step(+1)
            accuracy < lowerBelow -> step(-1)
            else -> current
        }
        return copy(current = level, window = if (level != current) emptyList() else next.drop(1))
    }

    private fun step(by: Int): Difficulty =
        Difficulty.entries[(current.ordinal + by).coerceIn(0, Difficulty.entries.lastIndex)]
}
