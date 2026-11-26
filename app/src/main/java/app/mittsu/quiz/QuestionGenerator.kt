package app.mittsu.quiz

import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.SubjectType
import kotlin.math.abs
import kotlin.random.Random

/**
 * Builds three-answer questions: the correct answer plus two distractors taken from real
 * WaniKani subjects. Difficulty only changes which distractors are chosen.
 *
 * Safety rules, applied before any difficulty preference:
 *  - a distractor may never equal (after normalisation) any accepted meaning, whitelisted synonym
 *    or reading of the subject being asked, accepted or not;
 *  - a distractor subject is rejected outright if any of its own accepted meanings or readings
 *    matches the target's, so synonyms such as "Big"/"Large" never appear as wrong answers;
 *  - the two distractors may not be synonyms of each other;
 *  - readings must be kana.
 * If fewer than two safe distractors exist the pool is widened step by step; if that still fails,
 * a self-check question is returned instead of a misleading one.
 */
class QuestionGenerator(
    pool: Collection<QuizSubject>,
    private val random: Random = Random.Default,
    private val shuffleOptions: Boolean = true,
) {
    private val pool: List<QuizSubject> = pool.distinctBy { it.id }

    fun generate(subject: QuizSubject, type: QuestionType, difficulty: Difficulty): Question {
        require(type == QuestionType.MEANING || subject.hasReadingQuestion) { "Subject ${subject.id} has no reading question" }
        val correct = AnswerOption(if (type == QuestionType.MEANING) subject.primaryMeaning else subject.primaryReading!!, true)
        val distractors = pickDistractors(subject, type, difficulty)
            ?: return selfCheck(subject, type, difficulty)
        val options = (listOf(correct) + distractors).let { if (shuffleOptions) it.shuffled(random) else it.sortedBy { o -> o.text } }
        return Question(subject, type, options, difficulty).also { check(QuestionSafety.isSafe(it)) { "unsafe question" } }
    }

    /** Candidate pools from narrowest to widest. Wider pools are used only if narrower ones run dry. */
    private fun tiers(subject: QuizSubject, type: QuestionType): List<List<QuizSubject>> {
        val others = pool.filter { it.id != subject.id }
        val sameType = others.filter { it.type.family == subject.type.family }
        val sameFamilyNearLevel = sameType.filter { abs(it.level - subject.level) <= 10 }
        val anyType = if (type == QuestionType.READING) others.filter { it.hasReadingQuestion } else others
        return listOf(sameFamilyNearLevel, sameType, anyType)
    }

    private fun pickDistractors(subject: QuizSubject, type: QuestionType, difficulty: Difficulty): List<AnswerOption>? {
        for (tier in tiers(subject, type)) {
            val safe = tier.filter { isSafeCandidate(subject, it, type) }
            if (safe.size < 2) continue
            val ranked = rank(subject, safe, type, difficulty)
            val chosen = mutableListOf<QuizSubject>()
            for (candidate in ranked) {
                if (chosen.none { conflicts(it, candidate, type) }) chosen += candidate
                if (chosen.size == 2) break
            }
            if (chosen.size == 2) return chosen.map { AnswerOption(answerFor(it, type), false, it) }
        }
        return null
    }

    private fun answerFor(s: QuizSubject, type: QuestionType) =
        if (type == QuestionType.MEANING) s.primaryMeaning else s.primaryReading!!

    internal fun isSafeCandidate(target: QuizSubject, candidate: QuizSubject, type: QuestionType): Boolean = when (type) {
        QuestionType.MEANING -> {
            val shown = AnswerText.normaliseMeaning(candidate.primaryMeaning)
            shown.isNotEmpty() &&
                shown !in target.allMeaningForms &&
                candidate.allMeaningForms.none { it in target.allMeaningForms } &&
                // Same characters with a different meaning (e.g. kanji vs. vocabulary) would confuse.
                (candidate.characters == null || candidate.characters != target.characters)
        }
        QuestionType.READING -> {
            val shown = candidate.primaryReading?.let(AnswerText::normaliseReading)
            candidate.hasReadingQuestion && shown != null && AnswerText.isKana(shown) &&
                shown !in target.allReadingForms &&
                candidate.acceptedReadings.map(AnswerText::normaliseReading).none { it in target.allReadingForms } &&
                (candidate.characters == null || candidate.characters != target.characters)
        }
    }

    /** Two distractors that mean or read the same would make the question look like it has a twin. */
    private fun conflicts(a: QuizSubject, b: QuizSubject, type: QuestionType): Boolean = when (type) {
        QuestionType.MEANING -> a.allMeaningForms.any { it in b.allMeaningForms }
        QuestionType.READING -> AnswerText.normaliseReading(a.primaryReading!!) == AnswerText.normaliseReading(b.primaryReading!!)
    }

    /**
     * Orders safe candidates by preference for this difficulty. A small random jitter keeps
     * repeated questions from always showing the same pair.
     */
    internal fun rank(target: QuizSubject, candidates: List<QuizSubject>, type: QuestionType, difficulty: Difficulty): List<QuizSubject> {
        // Each candidate's sort key (including its random jitter) is computed exactly once.
        // Drawing random numbers inside the comparator breaks the sort contract on large lists.
        return candidates.map { c ->
            val s = Similarity.score(target, c, type)
            val key = when (difficulty) {
                // Clearly different answers, still from the same kind of subject.
                Difficulty.EASY -> s.confusion + random.nextDouble() * 0.15
                // Similar level and type, mild resemblance.
                Difficulty.NORMAL -> -(s.levelCloseness * 0.6 + s.confusion * 0.4) + random.nextDouble() * 0.35
                // The most confusable valid answers available.
                Difficulty.HARD -> -(s.confusion * 0.8 + s.levelCloseness * 0.2 + random.nextDouble() * 0.1)
            }
            c to key
        }.sortedBy { it.second }.map { it.first }
    }

    private fun selfCheck(subject: QuizSubject, type: QuestionType, difficulty: Difficulty): Question {
        // The answer is only revealed after the user rates themselves.
        return Question(
            subject, type,
            listOf(
                AnswerOption("I know it", true),
                AnswerOption("Not sure", false),
                AnswerOption("I don't know it", false),
            ),
            difficulty,
            isSelfCheck = true,
        )
    }
}

/** Kanji and vocabulary of any kind are compared with their own kind. */
val SubjectType.family: String
    get() = when (this) {
        SubjectType.RADICAL -> "radical"
        SubjectType.KANJI -> "kanji"
        SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY -> "vocabulary"
    }

/** How alike two subjects are, used only to order distractors. */
object Similarity {
    data class Score(val confusion: Double, val levelCloseness: Double)

    fun score(target: QuizSubject, candidate: QuizSubject, type: QuestionType): Score {
        val levelCloseness = 1.0 - (abs(target.level - candidate.level).coerceAtMost(10) / 10.0)
        val visual = if (candidate.id in target.visuallySimilarIds || target.id in candidate.visuallySimilarIds) 1.0 else 0.0
        val sharedComponents = if (target.componentIds.any { it in candidate.componentIds }) 1.0 else 0.0
        val confusion = when (type) {
            QuestionType.MEANING -> {
                val tw = AnswerText.words(target.primaryMeaning)
                val cw = AnswerText.words(candidate.primaryMeaning)
                val overlap = if (tw.isEmpty() || cw.isEmpty()) 0.0 else (tw intersect cw).size.toDouble() / (tw union cw).size
                val samePos = if (target.partsOfSpeech.isNotEmpty() && target.partsOfSpeech.any { it in candidate.partsOfSpeech }) 1.0 else 0.0
                val sameShape = if (AnswerText.isVerbMeaning(target.primaryMeaning) == AnswerText.isVerbMeaning(candidate.primaryMeaning)) 1.0 else 0.0
                val lengthSim = 1.0 - (abs(target.primaryMeaning.length - candidate.primaryMeaning.length).coerceAtMost(12) / 12.0)
                visual * 0.3 + sharedComponents * 0.2 + overlap * 0.15 + samePos * 0.15 + sameShape * 0.1 + lengthSim * 0.1
            }
            QuestionType.READING -> {
                val t = AnswerText.normaliseReading(target.primaryReading!!)
                val c = AnswerText.normaliseReading(candidate.primaryReading!!)
                val edit = 1.0 - AnswerText.levenshtein(t, c).toDouble() / maxOf(t.length, c.length)
                val sameLength = if (t.length == c.length) 1.0 else 0.0
                val sameStart = if (t.first() == c.first()) 1.0 else 0.0
                val sameEnd = if (t.last() == c.last()) 1.0 else 0.0
                val sameReadingType = if (target.primaryReadingType != null && target.primaryReadingType == candidate.primaryReadingType) 1.0 else 0.0
                edit * 0.35 + sameLength * 0.15 + sameStart * 0.15 + sameEnd * 0.1 + sameReadingType * 0.1 + visual * 0.15
            }
        }
        return Score(confusion, levelCloseness)
    }
}

/** Independent check that a generated question can't mislead. Used at runtime and in tests. */
object QuestionSafety {
    fun isSafe(q: Question): Boolean {
        if (q.options.size != 3) return false
        if (q.options.count { it.isCorrect } != 1) return false
        if (q.isSelfCheck) return true
        val norm: (String) -> String = if (q.type == QuestionType.MEANING) AnswerText::normaliseMeaning else AnswerText::normaliseReading
        if (q.options.map { norm(it.text) }.toSet().size != 3) return false
        val forbidden = if (q.type == QuestionType.MEANING) q.subject.allMeaningForms else q.subject.allReadingForms
        val correct = q.options.single { it.isCorrect }
        if (norm(correct.text) !in forbidden) return false
        return q.options.filterNot { it.isCorrect }.none { norm(it.text) in forbidden }
    }
}
