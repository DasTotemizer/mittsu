package app.mittsu.lessons

import app.mittsu.database.MittsuDatabase
import app.mittsu.database.PendingLessonEntity
import app.mittsu.database.SubjectEntity
import app.mittsu.domain.model.SubjectType
import app.mittsu.quiz.QuestionGenerator
import app.mittsu.quiz.QuestionType
import app.mittsu.quiz.QuizSubject
import app.mittsu.quiz.toQuizSubject
import app.mittsu.repository.DashboardCalculator
import app.mittsu.reviews.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One component shown on a teaching card, e.g. the radicals of a kanji. */
data class LessonComponent(val characters: String, val meaning: String, val type: SubjectType)

/** Everything needed to teach one new subject. */
data class LessonCard(
    val assignmentId: Long,
    val subject: QuizSubject,
    val alternativeMeanings: List<String>,
    val readings: List<LessonReading>,
    val meaningMnemonic: String?,
    val meaningHint: String?,
    val readingMnemonic: String?,
    val readingHint: String?,
    val contextSentences: List<Pair<String, String>>,
    val partsOfSpeech: List<String>,
    val components: List<LessonComponent>,
) {
    /** What WaniKani asks: meaning always, reading for kanji and vocabulary. */
    val requiredTypes: List<QuestionType>
        get() = if (subject.type.hasReading) listOf(QuestionType.MEANING, QuestionType.READING) else listOf(QuestionType.MEANING)
}

data class LessonReading(val reading: String, val type: String?, val primary: Boolean, val accepted: Boolean)

data class LessonQueue(val ready: List<LessonCard>, val excluded: Int) {
    companion object { val EMPTY = LessonQueue(emptyList(), 0) }
}

/** Available lessons and handing finished ones to [LessonStarter]. */
class LessonRepository(
    private val db: MittsuDatabase,
    private val starter: LessonStarter,
    private val scheduler: UploadScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _lastReport = MutableStateFlow<LessonStarter.Report?>(null)
    val lastReport: StateFlow<LessonStarter.Report?> = _lastReport.asStateFlow()

    /**
     * Lessons WaniKani has unlocked, in WaniKani's order (level, then lesson position). Subjects
     * that can't be quizzed with three real answers are left for the website and counted.
     */
    suspend fun queue(generator: QuestionGenerator): LessonQueue = withContext(Dispatchers.IO) {
        val recorded = db.pendingLessons().all().map { it.assignmentId }.toSet()
        val lessons = db.assignments().visible().filter { DashboardCalculator.isAvailableLesson(it) && it.id !in recorded }
        val subjects = db.subjects().getAll(lessons.map { it.subjectId }).associateBy { it.id }
        val ordered = lessons.sortedWith(compareBy({ subjects[it.subjectId]?.level ?: 99 }, { subjects[it.subjectId]?.lessonPosition ?: 0 }, { it.id }))
        val componentIds = subjects.values.flatMap { it.componentSubjectIds }.distinct()
        val components = if (componentIds.isEmpty()) emptyMap() else db.subjects().getAll(componentIds).associateBy { it.id }
        withContext(Dispatchers.Default) {
            var excluded = 0
            val ready = ordered.mapNotNull { a ->
                val entity = subjects[a.subjectId]
                val card = entity?.let { card(a.id, it, components) }
                if (card == null || card.requiredTypes.any { !generator.canAsk(card.subject, it) }) {
                    excluded++
                    null
                } else card
            }
            LessonQueue(ready, excluded)
        }
    }

    private fun card(assignmentId: Long, e: SubjectEntity, components: Map<Long, SubjectEntity>): LessonCard? {
        val quiz = e.toQuizSubject() ?: return null
        return LessonCard(
            assignmentId = assignmentId,
            subject = quiz,
            alternativeMeanings = quiz.meanings.drop(1),
            readings = e.readings.map { LessonReading(it.reading, it.type, it.primary, it.accepted) },
            meaningMnemonic = e.meaningMnemonic,
            meaningHint = e.meaningHint,
            readingMnemonic = e.readingMnemonic,
            readingHint = e.readingHint,
            contextSentences = e.contextSentences.take(3).map { it.ja to it.en },
            partsOfSpeech = e.partsOfSpeech,
            components = e.componentSubjectIds.mapNotNull { id ->
                val c = components[id] ?: return@mapNotNull null
                val type = SubjectType.fromApi(c.type) ?: return@mapNotNull null
                LessonComponent(c.characters ?: c.slug ?: "?", c.meanings.firstOrNull { it.primary }?.meaning ?: c.meanings.firstOrNull()?.meaning ?: "", type)
            },
        )
    }

    suspend fun recordFinished(assignmentId: Long, subjectId: Long, completedAt: Long): Boolean {
        val stored = starter.record(assignmentId, subjectId, completedAt)
        if (stored) sendSoon()
        return stored
    }

    fun sendSoon() {
        scheduler.schedule()
        scope.launch { _lastReport.value = starter.submitPending() }
    }

    suspend fun sendNow(): LessonStarter.Report = starter.submitPending().also { _lastReport.value = it }

    suspend fun dismissRejected() = starter.dismissRejected()

    fun outstanding(): Flow<List<PendingLessonEntity>> = db.pendingLessons().observeOutstanding()

    fun finishedSince(since: Long): Flow<List<PendingLessonEntity>> = db.pendingLessons().observeSince(since)
}
