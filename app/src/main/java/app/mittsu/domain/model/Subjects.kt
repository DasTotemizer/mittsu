package app.mittsu.domain.model

/** WaniKani subject types. Radicals have no reading question. */
enum class SubjectType(val apiName: String, val label: String, val hasReading: Boolean) {
    RADICAL("radical", "Radical", hasReading = false),
    KANJI("kanji", "Kanji", hasReading = true),
    VOCABULARY("vocabulary", "Vocabulary", hasReading = true),
    KANA_VOCABULARY("kana_vocabulary", "Kana vocabulary", hasReading = false);

    companion object {
        fun fromApi(name: String): SubjectType? = entries.firstOrNull { it.apiName == name }
    }
}

/**
 * WaniKani SRS stage numbers as returned by the API (`assignment.srs_stage`).
 * 0 = lesson not yet started, 1–4 Apprentice, 5–6 Guru, 7 Master, 8 Enlightened, 9 Burned.
 */
enum class SrsGroup(val label: String, val stages: IntRange) {
    APPRENTICE("Apprentice", 1..4),
    GURU("Guru", 5..6),
    MASTER("Master", 7..7),
    ENLIGHTENED("Enlightened", 8..8),
    BURNED("Burned", 9..9);

    companion object {
        fun forStage(stage: Int): SrsGroup? = entries.firstOrNull { stage in it.stages }
    }
}

/** Count of assignments in each SRS group. */
data class SrsDistribution(val counts: Map<SrsGroup, Int>) {
    val total: Int get() = counts.values.sum()

    fun count(group: SrsGroup): Int = counts[group] ?: 0

    /** Share of the total for a group, 0f..1f. Returns 0 when there is nothing to show. */
    fun fraction(group: SrsGroup): Float = if (total == 0) 0f else count(group).toFloat() / total

    companion object {
        val Empty = SrsDistribution(emptyMap())

        fun fromStages(stages: Iterable<Int>): SrsDistribution =
            SrsDistribution(stages.mapNotNull { SrsGroup.forStage(it) }.groupingBy { it }.eachCount())
    }
}
