package app.mittsu.reviews

import app.mittsu.database.AssignmentEntity
import app.mittsu.database.MittsuDatabase
import app.mittsu.database.StoredMeaning
import app.mittsu.database.StoredReading
import app.mittsu.database.SubjectEntity
import app.mittsu.database.UserEntity

/** Builds realistic-looking cached data: distinct kanji with distinct kana readings. */
object ReviewTestData {
    private val kana = listOf("か", "き", "く", "け", "こ", "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と", "な", "に", "ぬ", "ね", "の")
    private val kanjiChars = "山川田日月火水木金土石竹糸貝目耳手足口力"

    fun kanji(id: Long, n: Int) = SubjectEntity(
        id = id, type = "kanji", level = 1 + n % 5,
        characters = (kanjiChars[n % kanjiChars.length].toString() + if (n >= kanjiChars.length) n.toString() else ""),
        slug = null,
        meanings = listOf(StoredMeaning("Word $n", primary = true, accepted = true)),
        auxiliaryMeanings = emptyList(),
        readings = listOf(StoredReading(kana[n % kana.size] + kana[(n / kana.size + 3) % kana.size] + "ん", primary = true, accepted = true, type = "onyomi")),
        meaningMnemonic = null, meaningHint = null, readingMnemonic = null, readingHint = null,
        contextSentences = emptyList(), partsOfSpeech = emptyList(), componentSubjectIds = emptyList(),
        amalgamationSubjectIds = emptyList(), visuallySimilarSubjectIds = emptyList(), characterImages = emptyList(),
        lessonPosition = n, hiddenAt = null, documentUrl = null, srsSystemId = 1,
    )

    fun imageRadical(id: Long) = kanji(id, 0).copy(type = "radical", characters = null, readings = emptyList(),
        meanings = listOf(StoredMeaning("Gun", primary = true, accepted = true)))

    fun assignment(id: Long, subjectId: Long, availableAt: Long?, stage: Int = 4, type: String = "kanji") = AssignmentEntity(
        id = id, subjectId = subjectId, subjectType = type, srsStage = stage,
        unlockedAt = 0, startedAt = 0, passedAt = null, burnedAt = null,
        availableAt = availableAt, resurrectedAt = null, hidden = false,
    )

    /** 40 kanji (ids 1..40); assignments 1001.. for the first [due] are due at [dueAt], others later. */
    suspend fun seed(db: MittsuDatabase, due: Int, dueAt: Long, later: Long = dueAt + 86_400_000L * 30) {
        db.users().upsert(UserEntity(0, "testlearner", 3, 60, "lifetime", true, null, null, null, 0))
        val subjects = (1..40).map { kanji(it.toLong(), it) }
        db.subjects().upsertAll(subjects)
        db.assignments().upsertAll(subjects.mapIndexed { i, s -> assignment(1000L + s.id, s.id, if (i < due) dueAt + i else later) })
    }
}
