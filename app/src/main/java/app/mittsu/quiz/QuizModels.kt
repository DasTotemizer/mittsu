package app.mittsu.quiz

import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.SubjectType

enum class QuestionType(val label: String) { MEANING("Meaning"), READING("Reading") }

/** Which question types a practice session asks. Real reviews always ask what WaniKani requires. */
enum class QuizFocus(val label: String) { MIXED("Mixed"), MEANING_ONLY("Meaning only"), READING_ONLY("Reading only") }

/** Everything the quiz engine needs to know about one WaniKani subject. */
data class QuizSubject(
    val id: Long,
    val type: SubjectType,
    val level: Int,
    val characters: String?,
    /** Accepted meanings in WaniKani order; the primary one first. */
    val meanings: List<String>,
    /** User-facing synonyms WaniKani also accepts (auxiliary "whitelist" meanings). */
    val whitelistMeanings: List<String> = emptyList(),
    /** Readings WaniKani accepts as correct; the primary one first. */
    val acceptedReadings: List<String> = emptyList(),
    /** Readings shown on WaniKani but not accepted (e.g. the other reading type of a kanji). */
    val otherReadings: List<String> = emptyList(),
    val primaryReadingType: String? = null,
    val partsOfSpeech: List<String> = emptyList(),
    val componentIds: List<Long> = emptyList(),
    val visuallySimilarIds: List<Long> = emptyList(),
) {
    val primaryMeaning: String get() = meanings.first()
    val primaryReading: String? get() = acceptedReadings.firstOrNull()
    val hasReadingQuestion: Boolean get() = type.hasReading && acceptedReadings.isNotEmpty()

    /** Every meaning that must never appear as a wrong answer for this subject. */
    val allMeaningForms: Set<String> by lazy {
        (meanings + whitelistMeanings).map(AnswerText::normaliseMeaning).toSet()
    }

    /** Every reading of this subject, accepted or not: none may be shown as a wrong answer. */
    val allReadingForms: Set<String> by lazy {
        (acceptedReadings + otherReadings).map(AnswerText::normaliseReading).toSet()
    }

    fun questionTypes(focus: QuizFocus): List<QuestionType> = when (focus) {
        QuizFocus.MIXED -> if (hasReadingQuestion) listOf(QuestionType.MEANING, QuestionType.READING) else listOf(QuestionType.MEANING)
        QuizFocus.MEANING_ONLY -> listOf(QuestionType.MEANING)
        QuizFocus.READING_ONLY -> if (hasReadingQuestion) listOf(QuestionType.READING) else emptyList()
    }
}

data class AnswerOption(
    val text: String,
    val isCorrect: Boolean,
    /** The subject this wrong answer belongs to, used to explain mistakes. */
    val fromSubject: QuizSubject? = null,
)

data class Question(
    val subject: QuizSubject,
    val type: QuestionType,
    val options: List<AnswerOption>,
    val difficulty: Difficulty,
    /** True when not enough safe distractors existed and the self-check fallback is used. */
    val isSelfCheck: Boolean = false,
) {
    val correctIndex: Int get() = options.indexOfFirst { it.isCorrect }
    val correctAnswer: String get() = options[correctIndex].text

    fun isCorrect(choice: Int): Boolean = options.getOrNull(choice)?.isCorrect == true
}
