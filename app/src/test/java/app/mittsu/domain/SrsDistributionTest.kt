package app.mittsu.domain

import app.mittsu.domain.model.SrsDistribution
import app.mittsu.domain.model.SrsGroup
import app.mittsu.domain.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsDistributionTest {
    @Test fun stagesMapToWaniKaniGroups() {
        assertNull(SrsGroup.forStage(0)) // lesson not started
        (1..4).forEach { assertEquals(SrsGroup.APPRENTICE, SrsGroup.forStage(it)) }
        (5..6).forEach { assertEquals(SrsGroup.GURU, SrsGroup.forStage(it)) }
        assertEquals(SrsGroup.MASTER, SrsGroup.forStage(7))
        assertEquals(SrsGroup.ENLIGHTENED, SrsGroup.forStage(8))
        assertEquals(SrsGroup.BURNED, SrsGroup.forStage(9))
        assertNull(SrsGroup.forStage(10))
    }

    @Test fun fromStagesIgnoresUnstartedLessons() {
        val d = SrsDistribution.fromStages(listOf(0, 0, 1, 2, 5, 9, 9))
        assertEquals(5, d.total)
        assertEquals(2, d.count(SrsGroup.APPRENTICE))
        assertEquals(2, d.count(SrsGroup.BURNED))
        assertEquals(0.4f, d.fraction(SrsGroup.BURNED), 0.0001f)
    }

    @Test fun emptyDistributionHasZeroFractions() {
        SrsGroup.entries.forEach { assertEquals(0f, SrsDistribution.Empty.fraction(it), 0f) }
    }

    @Test fun radicalsHaveNoReadingQuestion() {
        assertFalse(SubjectType.RADICAL.hasReading)
        assertTrue(SubjectType.KANJI.hasReading)
        assertTrue(SubjectType.VOCABULARY.hasReading)
        assertEquals(SubjectType.KANA_VOCABULARY, SubjectType.fromApi("kana_vocabulary"))
        assertNull(SubjectType.fromApi("unknown"))
    }
}
