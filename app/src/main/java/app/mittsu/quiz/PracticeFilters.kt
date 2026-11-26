package app.mittsu.quiz

import app.mittsu.domain.model.SrsGroup
import app.mittsu.domain.model.SubjectType
import kotlin.random.Random

/** What the user has done with a subject on WaniKani, for practice filters. */
data class PracticeInfo(val srsStage: Int, val percentageCorrect: Int?, val incorrect: Int)

/** Which learned subjects a custom practice session draws from. */
data class PracticeFilters(
    val minLevel: Int = 1,
    val maxLevel: Int = 60,
    val types: Set<SubjectType> = SubjectType.entries.toSet(),
    val srsGroups: Set<SrsGroup> = SrsGroup.entries.toSet(),
    /** Only subjects answered wrong at least once with under [WEAK_THRESHOLD]% correct overall. */
    val weakOnly: Boolean = false,
    val size: Int = 20,
) {
    fun matches(subject: QuizSubject, info: PracticeInfo?): Boolean {
        if (info == null) return false // not learned yet
        if (subject.level !in minLevel..maxLevel) return false
        if (subject.type !in types) return false
        val group = SrsGroup.forStage(info.srsStage) ?: return false
        if (group !in srsGroups) return false
        if (weakOnly && !isWeak(info)) return false
        return true
    }

    companion object {
        const val WEAK_THRESHOLD = 80
        val SIZES = listOf(10, 20, 50)

        fun isWeak(info: PracticeInfo) = info.incorrect > 0 && (info.percentageCorrect ?: 100) < WEAK_THRESHOLD

        /**
         * Picks a session. Weak-only sessions favour the weakest subjects (drawn at random from the
         * weakest twice-the-size, so repeated sessions still vary); others are a random sample.
         */
        fun select(candidates: List<QuizSubject>, info: Map<Long, PracticeInfo>, filters: PracticeFilters, random: Random): List<QuizSubject> {
            val matching = candidates.filter { filters.matches(it, info[it.id]) }
            return if (filters.weakOnly) {
                matching.sortedBy { info[it.id]?.percentageCorrect ?: 100 }.take(filters.size * 2).shuffled(random).take(filters.size)
            } else matching.shuffled(random).take(filters.size)
        }
    }
}
