package app.mittsu.quiz

import app.mittsu.domain.model.SrsGroup
import app.mittsu.domain.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PracticeFiltersTest {
    private fun subject(id: Long, type: SubjectType, level: Int) =
        QuizSubject(id, type, level, "字$id", listOf("Meaning $id"), acceptedReadings = if (type.hasReading) listOf("じ") else emptyList())

    private val subjects = listOf(
        subject(1, SubjectType.RADICAL, 1),
        subject(2, SubjectType.KANJI, 2),
        subject(3, SubjectType.VOCABULARY, 5),
        subject(4, SubjectType.KANA_VOCABULARY, 5),
        subject(5, SubjectType.KANJI, 8),
        subject(6, SubjectType.KANJI, 3), // not learned
    )
    private val info = mapOf(
        1L to PracticeInfo(9, 100, 0),
        2L to PracticeInfo(2, 60, 4),
        3L to PracticeInfo(5, 90, 1),
        4L to PracticeInfo(6, null, 0),
        5L to PracticeInfo(3, 75, 2),
    )

    private fun ids(f: PracticeFilters) = PracticeFilters.select(subjects, info, f.copy(size = 50), Random(1)).map { it.id }.sorted()

    @Test fun defaultsIncludeEveryLearnedSubject() = assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(PracticeFilters()))

    @Test fun filtersByLevelTypeAndSrs() {
        assertEquals(listOf(2L, 3L, 4L), ids(PracticeFilters(minLevel = 2, maxLevel = 5)))
        assertEquals(listOf(2L, 5L), ids(PracticeFilters(types = setOf(SubjectType.KANJI))))
        assertEquals(listOf(2L, 5L), ids(PracticeFilters(srsGroups = setOf(SrsGroup.APPRENTICE))))
        assertEquals(listOf(1L), ids(PracticeFilters(srsGroups = setOf(SrsGroup.BURNED))))
    }

    @Test fun weakOnlyPicksLowAccuracyFirst() {
        assertEquals(listOf(2L, 5L), ids(PracticeFilters(weakOnly = true)))
        val one = PracticeFilters.select(subjects, info, PracticeFilters(weakOnly = true, size = 1), Random(1))
        assertEquals(1, one.size)
        assertTrue(PracticeFilters.isWeak(info.getValue(2)))
        assertFalse(PracticeFilters.isWeak(info.getValue(4)))
    }

    @Test fun sizeLimitsTheSession() =
        assertEquals(2, PracticeFilters.select(subjects, info, PracticeFilters(size = 2), Random(1)).size)
}
