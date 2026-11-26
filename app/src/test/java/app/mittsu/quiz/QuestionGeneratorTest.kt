package app.mittsu.quiz

import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.SubjectType
import app.mittsu.quiz.TestSubjects.all
import app.mittsu.quiz.TestSubjects.dai
import app.mittsu.quiz.TestSubjects.ichi
import app.mittsu.quiz.TestSubjects.san
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionGeneratorTest {

    private fun questions(subject: QuizSubject, type: QuestionType, difficulty: Difficulty, runs: Int = 60) =
        (0 until runs).map { QuestionGenerator(all, Random(it)).generate(subject, type, difficulty) }

    @Test fun everyQuestionHasExactlyThreeUniqueOptionsAndOneCorrect() {
        for (subject in all) for (type in subject.questionTypes(QuizFocus.MIXED)) for (d in Difficulty.entries) {
            for (q in questions(subject, type, d, runs = 15)) {
                assertEquals(3, q.options.size)
                assertEquals(1, q.options.count { it.isCorrect })
                assertTrue("unsafe: ${subject.characters} $type $d ${q.options}", QuestionSafety.isSafe(q))
                assertFalse("${subject.characters} $type fell back to self-check", q.isSelfCheck)
            }
        }
    }

    @Test fun correctAnswerIsThePrimaryMeaningOrReading() {
        val m = QuestionGenerator(all, Random(1)).generate(dai, QuestionType.MEANING, Difficulty.NORMAL)
        assertEquals("Big", m.correctAnswer)
        val r = QuestionGenerator(all, Random(1)).generate(dai, QuestionType.READING, Difficulty.NORMAL)
        assertEquals("たい", r.correctAnswer)
        assertTrue(r.isCorrect(r.correctIndex))
        assertFalse(r.isCorrect((r.correctIndex + 1) % 3))
    }

    @Test fun radicalsOnlyGetMeaningQuestions() {
        val ground = TestSubjects.radicals.first()
        assertEquals(listOf(QuestionType.MEANING), ground.questionTypes(QuizFocus.MIXED))
        assertTrue(ground.questionTypes(QuizFocus.READING_ONLY).isEmpty())
        assertTrue(runCatching { QuestionGenerator(all).generate(ground, QuestionType.READING, Difficulty.NORMAL) }.isFailure)
        questions(ground, QuestionType.MEANING, Difficulty.NORMAL).forEach { q ->
            assertTrue(q.options.filterNot { it.isCorrect }.all { it.fromSubject!!.type == SubjectType.RADICAL })
        }
    }

    @Test fun kanaVocabularyOnlyGetsMeaningQuestions() {
        assertEquals(listOf(QuestionType.MEANING), TestSubjects.arigatou.questionTypes(QuizFocus.MIXED))
    }

    @Test fun kanjiAndVocabularyGetBothTypes() {
        assertEquals(listOf(QuestionType.MEANING, QuestionType.READING), ichi.questionTypes(QuizFocus.MIXED))
        assertEquals(listOf(QuestionType.MEANING, QuestionType.READING), TestSubjects.hitotsu.questionTypes(QuizFocus.MIXED))
        assertEquals(listOf(QuestionType.READING), ichi.questionTypes(QuizFocus.READING_ONLY))
    }

    @Test fun nonAcceptedReadingsOfTheTargetAreNeverDistractors() {
        // ひと is a real reading of 一 (kun'yomi) even though WaniKani wants いち: showing it as "wrong" would mislead.
        for (d in Difficulty.entries) questions(ichi, QuestionType.READING, d).forEach { q ->
            assertTrue(q.options.none { it.text == "ひと" || it.text == "かず" })
        }
    }

    @Test fun anotherSubjectWithTheSameReadingIsNeverADistractor() {
        // 山 is also read さん, so it can't be a wrong answer for 三.
        for (d in Difficulty.entries) questions(san, QuestionType.READING, d).forEach { q ->
            assertEquals(1, q.options.count { it.text == "さん" })
            assertTrue(q.options.none { it.fromSubject?.id == TestSubjects.yama.id })
        }
    }

    @Test fun subjectsSharingAnyAcceptedMeaningAreRejected() {
        // 大きい means Big and Large, exactly like 大: never a distractor for it.
        val gen = QuestionGenerator(all)
        assertFalse(gen.isSafeCandidate(dai, TestSubjects.ookii, QuestionType.MEANING))
        for (d in Difficulty.entries) questions(dai, QuestionType.MEANING, d).forEach { q ->
            assertTrue(q.options.none { it.text == "Large" })
            assertTrue(q.options.none { it.fromSubject?.id == TestSubjects.ookii.id })
        }
    }

    @Test fun whitelistedSynonymsCountAsCorrectForms() {
        val numberOne = QuizSubject(5000, SubjectType.KANJI, 1, "壱", listOf("1"), acceptedReadings = listOf("いち"))
        assertFalse(QuestionGenerator(all).isSafeCandidate(ichi, numberOne, QuestionType.MEANING))
    }

    @Test fun normalisationTreatsToAndCaseAsSame() {
        assertEquals(AnswerText.normaliseMeaning("To Eat"), AnswerText.normaliseMeaning("eat"))
        assertEquals(AnswerText.normaliseMeaning("The Sun!"), AnswerText.normaliseMeaning("sun"))
        assertEquals("かこう", AnswerText.normaliseReading("カコウ"))
    }

    @Test fun katakanaReadingsMatchingTheTargetAreRejected() {
        val gen = QuestionGenerator(all)
        assertFalse(gen.isSafeCandidate(TestSubjects.kakou, TestSubjects.kakouKatakana, QuestionType.READING))
    }

    @Test fun subjectsWithIdenticalCharactersAreNotUsed() {
        // The kanji 山 and the vocabulary 山 share characters; mixing them would be confusing.
        assertFalse(QuestionGenerator(all).isSafeCandidate(TestSubjects.yamaVocab, TestSubjects.yama, QuestionType.MEANING))
    }

    @Test fun distractorsAreNeverSynonymsOfEachOther() {
        for (s in all) for (t in s.questionTypes(QuizFocus.MIXED)) questions(s, t, Difficulty.HARD, 10).forEach { q ->
            val wrong = q.options.filterNot { it.isCorrect }.map { it.fromSubject!! }
            if (t == QuestionType.MEANING) assertTrue(wrong[0].allMeaningForms.none { it in wrong[1].allMeaningForms })
        }
    }

    @Test fun hardPrefersVisuallySimilarKanji() {
        val similar = setOf(TestSubjects.inu.id, TestSubjects.futoi.id)
        val hard = questions(dai, QuestionType.MEANING, Difficulty.HARD, 100).count { q -> q.options.any { it.fromSubject?.id in similar } }
        val easy = questions(dai, QuestionType.MEANING, Difficulty.EASY, 100).count { q -> q.options.any { it.fromSubject?.id in similar } }
        assertTrue("hard=$hard easy=$easy", hard >= 90)
        assertTrue("hard=$hard easy=$easy", easy < hard)
    }

    @Test fun easyReadingsAreLessAlikeThanHardReadings() {
        fun avgDistance(d: Difficulty) = questions(TestSubjects.hitotsu, QuestionType.READING, d, 100).flatMap { q ->
            q.options.filterNot { it.isCorrect }.map { AnswerText.levenshtein("ひとつ", AnswerText.normaliseReading(it.text)) }
        }.average()
        assertTrue(avgDistance(Difficulty.EASY) > avgDistance(Difficulty.HARD))
    }

    @Test fun answerPositionsAreRandomised() {
        val positions = questions(ichi, QuestionType.MEANING, Difficulty.NORMAL, 60).map { it.correctIndex }.toSet()
        assertEquals(setOf(0, 1, 2), positions)
    }

    @Test fun unshuffledOptionsAreSortedAlphabetically() {
        val q = QuestionGenerator(all, Random(3), shuffleOptions = false).generate(ichi, QuestionType.MEANING, Difficulty.NORMAL)
        assertEquals(q.options.map { it.text }.sorted(), q.options.map { it.text })
    }

    @Test fun widensToOtherTypesWhenSameTypeRunsOut() {
        val pool = listOf(ichi, san) + TestSubjects.vocabulary
        val q = QuestionGenerator(pool, Random(0)).generate(ichi, QuestionType.MEANING, Difficulty.NORMAL)
        assertFalse(q.isSelfCheck)
        assertTrue(QuestionSafety.isSafe(q))
    }

    @Test fun fallsBackToSelfCheckRatherThanMisleading() {
        val q = QuestionGenerator(listOf(ichi, TestSubjects.hitotsu), Random(0)).generate(ichi, QuestionType.READING, Difficulty.NORMAL)
        assertTrue(q.isSelfCheck)
        assertEquals(3, q.options.size)
        assertEquals(1, q.options.count { it.isCorrect })
        assertTrue(q.options.none { it.text.contains("いち") }) // answer not revealed before rating
    }

    @Test fun safetyCheckCatchesBadQuestions() {
        val dup = Question(ichi, QuestionType.MEANING, listOf(AnswerOption("One", true), AnswerOption("one", false), AnswerOption("Two", false)), Difficulty.NORMAL)
        assertFalse(QuestionSafety.isSafe(dup))
        val synonym = Question(ichi, QuestionType.MEANING, listOf(AnswerOption("One", true), AnswerOption("1", false), AnswerOption("Two", false)), Difficulty.NORMAL)
        assertFalse(QuestionSafety.isSafe(synonym))
        val twoCorrect = Question(ichi, QuestionType.MEANING, listOf(AnswerOption("One", true), AnswerOption("Ten", true), AnswerOption("Two", false)), Difficulty.NORMAL)
        assertFalse(QuestionSafety.isSafe(twoCorrect))
        val hiddenReading = Question(ichi, QuestionType.READING, listOf(AnswerOption("いち", true), AnswerOption("ひと", false), AnswerOption("に", false)), Difficulty.NORMAL)
        assertFalse(QuestionSafety.isSafe(hiddenReading))
        assertNotEquals(0, all.size)
    }
}
