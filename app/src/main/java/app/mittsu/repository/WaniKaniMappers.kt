package app.mittsu.repository

import app.mittsu.database.AssignmentEntity
import app.mittsu.database.ReviewStatisticEntity
import app.mittsu.database.SrsSystemEntity
import app.mittsu.database.StoredAuxMeaning
import app.mittsu.database.StoredImage
import app.mittsu.database.StoredMeaning
import app.mittsu.database.StoredReading
import app.mittsu.database.StoredSentence
import app.mittsu.database.SubjectEntity
import app.mittsu.database.UserEntity
import app.mittsu.domain.model.SubjectType
import app.mittsu.domain.model.WaniKaniUser
import app.mittsu.network.AssignmentDto
import app.mittsu.network.ResourceDto
import app.mittsu.network.ReviewStatisticDto
import app.mittsu.network.SrsSystemDto
import app.mittsu.network.SubjectDto
import app.mittsu.network.UserDto
import java.time.Instant

/**
 * Converts API resources to cache rows. Each mapper validates the resource and returns null
 * for anything that would be unsafe to quiz on (for example a kanji with no accepted reading),
 * so bad data is skipped rather than turned into a misleading question.
 */
object WaniKaniMappers {

    internal fun time(value: String?): Long? = value?.let {
        runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
    }

    fun user(dto: UserDto, fetchedAt: Long) = UserEntity(
        username = dto.username,
        level = dto.level,
        maxLevelGranted = dto.subscription.maxLevelGranted,
        subscriptionType = dto.subscription.type,
        subscriptionActive = dto.subscription.active,
        vacationStartedAt = time(dto.currentVacationStartedAt),
        profileUrl = dto.profileUrl,
        startedAt = time(dto.startedAt),
        fetchedAt = fetchedAt,
    )

    fun user(entity: UserEntity) = WaniKaniUser(
        username = entity.username,
        level = entity.level,
        maxLevelGranted = entity.maxLevelGranted,
        subscriptionType = entity.subscriptionType,
        subscriptionActive = entity.subscriptionActive,
        vacationStartedAt = entity.vacationStartedAt?.let(Instant::ofEpochMilli),
        profileUrl = entity.profileUrl,
        startedAt = entity.startedAt?.let(Instant::ofEpochMilli),
    )

    fun subject(resource: ResourceDto<SubjectDto>): SubjectEntity? {
        val id = resource.id ?: return null
        val type = SubjectType.fromApi(resource.objectType) ?: return null
        val d = resource.data
        val meanings = d.meanings.filter { it.meaning.isNotBlank() }
        if (meanings.none { it.acceptedAnswer }) return null
        val readings = d.readings.filter { it.reading.isNotBlank() }
        if (type.hasReading && readings.none { it.acceptedAnswer }) return null
        if (d.characters.isNullOrBlank() && d.characterImages.isEmpty()) return null
        if (d.level !in 1..60) return null

        return SubjectEntity(
            id = id,
            type = type.apiName,
            level = d.level,
            characters = d.characters?.takeIf { it.isNotBlank() },
            slug = d.slug,
            meanings = meanings.map { StoredMeaning(it.meaning.trim(), it.primary, it.acceptedAnswer) },
            auxiliaryMeanings = d.auxiliaryMeanings.map { StoredAuxMeaning(it.meaning.trim(), it.type) },
            readings = if (type.hasReading) readings.map { StoredReading(it.reading.trim(), it.primary, it.acceptedAnswer, it.type) } else emptyList(),
            meaningMnemonic = d.meaningMnemonic,
            meaningHint = d.meaningHint,
            readingMnemonic = d.readingMnemonic,
            readingHint = d.readingHint,
            contextSentences = d.contextSentences.map { StoredSentence(it.en, it.ja) },
            partsOfSpeech = d.partsOfSpeech,
            componentSubjectIds = d.componentSubjectIds,
            amalgamationSubjectIds = d.amalgamationSubjectIds,
            visuallySimilarSubjectIds = d.visuallySimilarSubjectIds,
            characterImages = d.characterImages.map { StoredImage(it.url, it.contentType) },
            lessonPosition = d.lessonPosition,
            hiddenAt = time(d.hiddenAt),
            documentUrl = d.documentUrl,
            srsSystemId = d.spacedRepetitionSystemId,
        )
    }

    fun assignment(resource: ResourceDto<AssignmentDto>): AssignmentEntity? {
        val id = resource.id ?: return null
        if (resource.objectType != "assignment") return null
        val d = resource.data
        if (d.srsStage !in 0..9) return null
        return AssignmentEntity(
            id = id,
            subjectId = d.subjectId,
            subjectType = d.subjectType,
            srsStage = d.srsStage,
            unlockedAt = time(d.unlockedAt),
            startedAt = time(d.startedAt),
            passedAt = time(d.passedAt),
            burnedAt = time(d.burnedAt),
            availableAt = time(d.availableAt),
            resurrectedAt = time(d.resurrectedAt),
            hidden = d.hidden,
        )
    }

    fun reviewStatistic(resource: ResourceDto<ReviewStatisticDto>): ReviewStatisticEntity? {
        val id = resource.id ?: return null
        if (resource.objectType != "review_statistic") return null
        val d = resource.data
        if (listOf(d.meaningCorrect, d.meaningIncorrect, d.readingCorrect, d.readingIncorrect).any { it < 0 }) return null
        return ReviewStatisticEntity(
            id = id,
            subjectId = d.subjectId,
            subjectType = d.subjectType,
            meaningCorrect = d.meaningCorrect,
            meaningIncorrect = d.meaningIncorrect,
            readingCorrect = d.readingCorrect,
            readingIncorrect = d.readingIncorrect,
            percentageCorrect = d.percentageCorrect,
            hidden = d.hidden,
        )
    }

    fun srsSystem(resource: ResourceDto<SrsSystemDto>): SrsSystemEntity? {
        val id = resource.id ?: return null
        val d = resource.data
        return SrsSystemEntity(
            id = id,
            name = d.name,
            passingStage = d.passingStagePosition,
            burningStage = d.burningStagePosition,
            stageIntervalsSeconds = d.stages.sortedBy { it.position }.map { stage ->
                val n = stage.interval ?: 0
                when (stage.intervalUnit) {
                    "milliseconds" -> n / 1000
                    "minutes" -> n * 60
                    "hours" -> n * 3600
                    "days" -> n * 86_400
                    "weeks" -> n * 604_800
                    else -> n
                }
            },
        )
    }
}
