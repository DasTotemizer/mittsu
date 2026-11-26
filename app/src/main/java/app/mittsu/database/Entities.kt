package app.mittsu.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached WaniKani data. Times are epoch milliseconds (UTC). Lists are JSON (see [Converters]). */

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val key: Int = 0,
    val username: String,
    val level: Int,
    val maxLevelGranted: Int,
    val subscriptionType: String,
    val subscriptionActive: Boolean,
    val vacationStartedAt: Long?,
    val profileUrl: String?,
    val startedAt: Long?,
    val fetchedAt: Long,
)

@Entity(tableName = "subjects", indices = [Index("type"), Index("level")])
data class SubjectEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val level: Int,
    val characters: String?,
    val slug: String?,
    val meanings: List<StoredMeaning>,
    val auxiliaryMeanings: List<StoredAuxMeaning>,
    val readings: List<StoredReading>,
    val meaningMnemonic: String?,
    val meaningHint: String?,
    val readingMnemonic: String?,
    val readingHint: String?,
    val contextSentences: List<StoredSentence>,
    val partsOfSpeech: List<String>,
    val componentSubjectIds: List<Long>,
    val amalgamationSubjectIds: List<Long>,
    val visuallySimilarSubjectIds: List<Long>,
    val characterImages: List<StoredImage>,
    val lessonPosition: Int,
    val hiddenAt: Long?,
    val documentUrl: String?,
    val srsSystemId: Long?,
)

@Entity(tableName = "assignments", indices = [Index(value = ["subjectId"], unique = true), Index("availableAt")])
data class AssignmentEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val srsStage: Int,
    val unlockedAt: Long?,
    val startedAt: Long?,
    val passedAt: Long?,
    val burnedAt: Long?,
    val availableAt: Long?,
    val resurrectedAt: Long?,
    val hidden: Boolean,
)

@Entity(tableName = "review_statistics", indices = [Index(value = ["subjectId"], unique = true)])
data class ReviewStatisticEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val meaningCorrect: Int,
    val meaningIncorrect: Int,
    val readingCorrect: Int,
    val readingIncorrect: Int,
    val percentageCorrect: Int,
    val hidden: Boolean,
)

@Entity(tableName = "srs_systems")
data class SrsSystemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val passingStage: Int,
    val burningStage: Int,
    val stageIntervalsSeconds: List<Long>,
)

/** Incremental sync cursors: the `data_updated_at` of the last full fetch, per collection. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val collection: String,
    val updatedAfter: String?,
    val lastSyncedAt: Long,
)

@kotlinx.serialization.Serializable
data class StoredMeaning(val meaning: String, val primary: Boolean, val accepted: Boolean)

@kotlinx.serialization.Serializable
data class StoredAuxMeaning(val meaning: String, val type: String)

@kotlinx.serialization.Serializable
data class StoredReading(val reading: String, val primary: Boolean, val accepted: Boolean, val type: String? = null)

@kotlinx.serialization.Serializable
data class StoredSentence(val en: String, val ja: String)

@kotlinx.serialization.Serializable
data class StoredImage(val url: String, val contentType: String? = null)
