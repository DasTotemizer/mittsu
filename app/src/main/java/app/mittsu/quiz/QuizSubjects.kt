package app.mittsu.quiz

import app.mittsu.database.SubjectEntity
import app.mittsu.domain.model.SubjectType

/** Builds the quiz view of a cached subject, or null if it can't be quizzed safely. */
fun SubjectEntity.toQuizSubject(): QuizSubject? {
    val type = SubjectType.fromApi(type) ?: return null
    val chars = characters ?: return null // Image-only radicals are left out for now.
    val accepted = meanings.filter { it.accepted }.sortedByDescending { it.primary }.map { it.meaning }
    if (accepted.isEmpty()) return null
    val acceptedReadings = readings.filter { it.accepted }.sortedByDescending { it.primary }.map { it.reading }
    if (type.hasReading && acceptedReadings.isEmpty()) return null
    return QuizSubject(
        id = id,
        type = type,
        level = level,
        characters = chars,
        meanings = accepted,
        whitelistMeanings = auxiliaryMeanings.filter { it.type == "whitelist" }.map { it.meaning } +
            meanings.filterNot { it.accepted }.map { it.meaning },
        acceptedReadings = acceptedReadings,
        otherReadings = readings.filterNot { it.accepted }.map { it.reading },
        primaryReadingType = readings.firstOrNull { it.primary }?.type,
        partsOfSpeech = partsOfSpeech,
        componentIds = componentSubjectIds,
        visuallySimilarIds = visuallySimilarSubjectIds,
    )
}
