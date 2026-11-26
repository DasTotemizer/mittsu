package app.mittsu.quiz

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizSessionTest {
    private val radical = SessionItem(1, assignmentId = 11, needsMeaning = true, needsReading = false)
    private val kanji = SessionItem(440, assignmentId = 12, needsMeaning = true, needsReading = true)
    private val vocab = SessionItem(2467, assignmentId = 13, needsMeaning = true, needsReading = true)

    private fun session(vararg items: SessionItem) = QuizSession.start(items.toList(), 0, Random(0), shuffle = false)


    @Test fun radicalCompletesAfterOneCorrectMeaning() {
        val s = session(radical)
        assertEquals(1, s.remainingQuestions)
        val done = s.answer(true)
        assertEquals(CompletedItem(1, 11, 0, 0), done)
        assertTrue(s.isFinished)
    }

    @Test fun kanjiNeedsBothMeaningAndReading() {
        val s = session(kanji)
        assertNull(s.answer(true)) // meaning
        assertFalse(s.isFinished)
        assertEquals(CompletedItem(440, 12, 0, 0), s.answer(true)) // reading
    }

    @Test fun incorrectAnswersAreCountedPerTypeAndRepeated() {
        val s = session(kanji, vocab, radical)
        // Get the kanji meaning wrong twice and its reading wrong once; everything else right.
        var wrongMeaningLeft = 2
        var wrongReadingLeft = 1
        var completed: CompletedItem? = null
        var guard = 0
        while (!s.isFinished && guard++ < 100) {
            val q = s.current!!
            val correct = when (q) {
                PendingQuestion(440, QuestionType.MEANING) -> wrongMeaningLeft-- <= 0
                PendingQuestion(440, QuestionType.READING) -> wrongReadingLeft-- <= 0
                else -> true
            }
            s.answer(correct)?.let { if (it.subjectId == 440L) completed = it }
        }
        assertTrue(s.isFinished)
        assertEquals(CompletedItem(440, 12, incorrectMeaning = 2, incorrectReading = 1), completed)
        assertEquals(3, s.stats.incorrect)
        assertEquals(2, s.stats.meaningIncorrect)
        assertEquals(1, s.stats.readingIncorrect)
    }

    @Test fun eachItemIsReportedCompleteExactlyOnce() {
        val s = session(kanji, vocab, radical)
        val done = mutableListOf<CompletedItem>()
        var i = 0
        while (!s.isFinished) s.answer(i++ % 3 != 0)?.let(done::add)
        assertEquals(listOf(1L, 440L, 2467L), done.map { it.subjectId }.sorted())
        assertEquals(3, s.completedItems)
        assertNull(s.answer(true))
    }

    @Test fun wrongAnswerIsNotAskedAgainImmediately() {
        val items = (1L..6L).map { SessionItem(it, null, needsMeaning = true, needsReading = false) }
        val s = QuizSession.start(items, 0, Random(1), shuffle = false)
        val first = s.current
        s.answer(false)
        assertNotEquals(first, s.current)
    }

    @Test fun lastRemainingWrongQuestionComesStraightBack() {
        val s = session(radical)
        s.answer(false)
        assertEquals(PendingQuestion(1, QuestionType.MEANING), s.current)
        assertEquals(CompletedItem(1, 11, 1, 0), s.answer(true))
    }

    @Test fun snapshotSurvivesSerialisationAndResumes() {
        val s = session(kanji, vocab)
        s.answer(true)
        s.answer(false)
        val json = Json.encodeToString(SessionSnapshot.serializer(), s.snapshot())
        val restored = QuizSession.restore(Json.decodeFromString(SessionSnapshot.serializer(), json), Random(0))
        assertEquals(s.current, restored.current)
        assertEquals(s.stats, restored.stats)
        assertEquals(s.remainingQuestions, restored.remainingQuestions)
        val done = mutableListOf<CompletedItem>()
        while (!restored.isFinished) restored.answer(true)?.let(done::add)
        assertEquals(2, done.size)
        assertEquals(1, done.sumOf { it.incorrectMeaning + it.incorrectReading })
    }

    @Test fun wrapUpKeepsOnlyStartedItems() {
        val s = session(kanji, vocab, radical)
        s.answer(true) // kanji meaning
        s.wrapUp()
        assertEquals(1, s.totalItems)
        assertNotNull(s.item(440))
        assertEquals(CompletedItem(440, 12, 0, 0), s.answer(true))
        assertTrue(s.isFinished)
    }

    @Test fun statsSplitMeaningAndReadingAccuracy() {
        val s = session(kanji)
        s.answer(false); s.answer(true); s.answer(true)
        assertEquals(3, s.stats.answered)
        assertEquals(2f / 3, s.stats.accuracy!!, 0.001f)
        assertEquals(0.5f, s.stats.meaningAccuracy!!, 0.001f)
        assertEquals(1f, s.stats.readingAccuracy!!, 0.001f)
    }
}
