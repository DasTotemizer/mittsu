package app.mittsu.quiz

import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.SubjectType
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A real account has thousands of subjects. Small fixtures hid a crash where sorting with a
 * random tie-breaker broke the sort contract on large lists; this test uses a realistic size.
 */
class LargePoolTest {
    private val kana = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわん"

    private fun pool(size: Int, seed: Int): List<QuizSubject> {
        val r = Random(seed)
        fun reading() = (1..r.nextInt(2, 5)).map { kana[r.nextInt(kana.length)] }.joinToString("")
        return (1..size).map { i ->
            val type = when (i % 10) { 0 -> SubjectType.RADICAL; in 1..3 -> SubjectType.KANJI; 9 -> SubjectType.KANA_VOCABULARY; else -> SubjectType.VOCABULARY }
            val readings = if (type.hasReading) listOf(reading()) else emptyList()
            QuizSubject(
                id = i.toLong(), type = type, level = 1 + i % 60, characters = "字$i",
                meanings = listOf("Meaning ${i % 1500}", "Alt ${i}"),
                whitelistMeanings = if (i % 7 == 0) listOf("Meaning ${(i + 1) % 1500}") else emptyList(),
                acceptedReadings = readings, otherReadings = if (i % 3 == 0) listOf(reading()) else emptyList(),
                primaryReadingType = if (type == SubjectType.KANJI) listOf("onyomi", "kunyomi")[i % 2] else null,
                partsOfSpeech = if (type == SubjectType.VOCABULARY) listOf(listOf("noun", "verb", "adjective")[i % 3]) else emptyList(),
                visuallySimilarIds = if (type == SubjectType.KANJI) listOf((i + 4).toLong(), (i + 8).toLong()) else emptyList(),
            )
        }
    }

    @Test fun generatesSafeQuestionsFromAFullSizedAccount() {
        val subjects = pool(9000, 1)
        for (d in Difficulty.entries) {
            val gen = QuestionGenerator(subjects, Random(d.ordinal))
            subjects.shuffled(Random(5)).take(60).forEach { s ->
                for (t in s.questionTypes(QuizFocus.MIXED)) {
                    val q = gen.generate(s, t, d)
                    assertTrue(QuestionSafety.isSafe(q))
                    assertTrue(!q.isSelfCheck)
                }
            }
        }
    }
}
