package app.mittsu.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON settings for WaniKani responses: tolerant of new fields the API may add. */
val WaniKaniJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class ResourceDto<T>(
    val id: Long? = null,
    @SerialName("object") val objectType: String,
    val url: String? = null,
    @SerialName("data_updated_at") val dataUpdatedAt: String? = null,
    val data: T,
)

@Serializable
data class PagesDto(
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("next_url") val nextUrl: String? = null,
    @SerialName("previous_url") val previousUrl: String? = null,
)

@Serializable
data class CollectionDto<T>(
    @SerialName("object") val objectType: String,
    val url: String? = null,
    val pages: PagesDto? = null,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("data_updated_at") val dataUpdatedAt: String? = null,
    val data: List<ResourceDto<T>> = emptyList(),
)

// ---- /user ----

@Serializable
data class SubscriptionDto(
    val active: Boolean = false,
    val type: String = "unknown",
    @SerialName("max_level_granted") val maxLevelGranted: Int = 3,
    @SerialName("period_ends_at") val periodEndsAt: String? = null,
)

@Serializable
data class UserDto(
    val id: String? = null,
    val username: String,
    val level: Int,
    @SerialName("profile_url") val profileUrl: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("current_vacation_started_at") val currentVacationStartedAt: String? = null,
    val subscription: SubscriptionDto = SubscriptionDto(),
)

// ---- /subjects ----

@Serializable
data class MeaningDto(
    val meaning: String,
    val primary: Boolean = false,
    @SerialName("accepted_answer") val acceptedAnswer: Boolean = false,
)

@Serializable
data class AuxiliaryMeaningDto(val meaning: String, val type: String)

@Serializable
data class ReadingDto(
    val reading: String,
    val primary: Boolean = false,
    @SerialName("accepted_answer") val acceptedAnswer: Boolean = false,
    val type: String? = null,
)

@Serializable
data class ContextSentenceDto(val en: String, val ja: String)

@Serializable
data class CharacterImageDto(
    val url: String,
    @SerialName("content_type") val contentType: String? = null,
)

/** One DTO for radicals, kanji, vocabulary and kana vocabulary; type-specific fields are nullable. */
@Serializable
data class SubjectDto(
    val level: Int,
    val slug: String? = null,
    val characters: String? = null,
    @SerialName("character_images") val characterImages: List<CharacterImageDto> = emptyList(),
    val meanings: List<MeaningDto> = emptyList(),
    @SerialName("auxiliary_meanings") val auxiliaryMeanings: List<AuxiliaryMeaningDto> = emptyList(),
    val readings: List<ReadingDto> = emptyList(),
    @SerialName("meaning_mnemonic") val meaningMnemonic: String? = null,
    @SerialName("meaning_hint") val meaningHint: String? = null,
    @SerialName("reading_mnemonic") val readingMnemonic: String? = null,
    @SerialName("reading_hint") val readingHint: String? = null,
    @SerialName("context_sentences") val contextSentences: List<ContextSentenceDto> = emptyList(),
    @SerialName("parts_of_speech") val partsOfSpeech: List<String> = emptyList(),
    @SerialName("component_subject_ids") val componentSubjectIds: List<Long> = emptyList(),
    @SerialName("amalgamation_subject_ids") val amalgamationSubjectIds: List<Long> = emptyList(),
    @SerialName("visually_similar_subject_ids") val visuallySimilarSubjectIds: List<Long> = emptyList(),
    @SerialName("lesson_position") val lessonPosition: Int = 0,
    @SerialName("hidden_at") val hiddenAt: String? = null,
    @SerialName("document_url") val documentUrl: String? = null,
    @SerialName("spaced_repetition_system_id") val spacedRepetitionSystemId: Long? = null,
)

// ---- /assignments ----

@Serializable
data class AssignmentDto(
    @SerialName("subject_id") val subjectId: Long,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("srs_stage") val srsStage: Int,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("passed_at") val passedAt: String? = null,
    @SerialName("burned_at") val burnedAt: String? = null,
    @SerialName("available_at") val availableAt: String? = null,
    @SerialName("resurrected_at") val resurrectedAt: String? = null,
    val hidden: Boolean = false,
)

// ---- /review_statistics ----

@Serializable
data class ReviewStatisticDto(
    @SerialName("subject_id") val subjectId: Long,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("meaning_correct") val meaningCorrect: Int = 0,
    @SerialName("meaning_incorrect") val meaningIncorrect: Int = 0,
    @SerialName("meaning_current_streak") val meaningCurrentStreak: Int = 0,
    @SerialName("reading_correct") val readingCorrect: Int = 0,
    @SerialName("reading_incorrect") val readingIncorrect: Int = 0,
    @SerialName("reading_current_streak") val readingCurrentStreak: Int = 0,
    @SerialName("percentage_correct") val percentageCorrect: Int = 0,
    val hidden: Boolean = false,
)

// ---- /spaced_repetition_systems ----

@Serializable
data class SrsStageDto(
    val position: Int,
    val interval: Long? = null,
    @SerialName("interval_unit") val intervalUnit: String? = null,
)

@Serializable
data class SrsSystemDto(
    val name: String,
    val description: String? = null,
    @SerialName("unlocking_stage_position") val unlockingStagePosition: Int = 0,
    @SerialName("starting_stage_position") val startingStagePosition: Int = 1,
    @SerialName("passing_stage_position") val passingStagePosition: Int = 5,
    @SerialName("burning_stage_position") val burningStagePosition: Int = 9,
    val stages: List<SrsStageDto> = emptyList(),
)

// ---- POST /reviews ----

@Serializable
data class CreateReviewBody(
    @SerialName("assignment_id") val assignmentId: Long,
    @SerialName("incorrect_meaning_answers") val incorrectMeaningAnswers: Int,
    @SerialName("incorrect_reading_answers") val incorrectReadingAnswers: Int,
    /** When the review was actually completed. Omitted only if WaniKani rejects the device clock. */
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateReviewRequest(val review: CreateReviewBody)

@Serializable
data class ReviewDataDto(
    @SerialName("assignment_id") val assignmentId: Long,
    @SerialName("subject_id") val subjectId: Long? = null,
    @SerialName("starting_srs_stage") val startingSrsStage: Int? = null,
    @SerialName("ending_srs_stage") val endingSrsStage: Int? = null,
    @SerialName("incorrect_meaning_answers") val incorrectMeaningAnswers: Int = 0,
    @SerialName("incorrect_reading_answers") val incorrectReadingAnswers: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ResourcesUpdatedDto(
    val assignment: ResourceDto<AssignmentDto>? = null,
    @SerialName("review_statistic") val reviewStatistic: ResourceDto<ReviewStatisticDto>? = null,
)

@Serializable
data class CreateReviewResponse(
    val id: Long? = null,
    @SerialName("object") val objectType: String,
    val data: ReviewDataDto,
    @SerialName("resources_updated") val resourcesUpdated: ResourcesUpdatedDto? = null,
)

@Serializable
data class ApiErrorDto(val error: String? = null, val code: Int? = null)

// ---- PUT /assignments/{id}/start ----

@Serializable
data class StartAssignmentBody(
    /** When the lesson was finished. Omitted only if WaniKani rejects the device clock. */
    @SerialName("started_at") val startedAt: String? = null,
)
