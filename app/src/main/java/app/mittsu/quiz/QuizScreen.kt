package app.mittsu.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mittsu.domain.model.SubjectType
import app.mittsu.ui.components.AccuracyRing
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import app.mittsu.ui.components.MittsuProgressBar
import app.mittsu.ui.components.ModePill
import app.mittsu.ui.theme.JapaneseText
import app.mittsu.ui.theme.MittsuTheme
import kotlin.math.roundToInt

/** Visual identity of a session: practice (indigo) or real WaniKani (vermilion). */
data class QuizMode(val label: String, val accent: Color, val container: Color, val isReal: Boolean = false)

@Composable
fun practiceMode() = QuizMode("Practice only", MittsuTheme.colors.practice, MittsuTheme.colors.practiceContainer)

@Composable
fun realMode() = QuizMode("Real WaniKani", MittsuTheme.colors.real, MittsuTheme.colors.realContainer, isReal = true)

@Composable
fun QuizScreen(
    state: QuizUiState,
    mode: QuizMode,
    onSelect: (Int) -> Unit,
    onContinue: () -> Unit,
    onFinishEarly: () -> Unit,
    onExit: () -> Unit,
    onRestart: () -> Unit,
    onRetrySending: () -> Unit = {},
) {
    when (val phase = state.phase) {
        QuizPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = mode.accent) }
        is QuizPhase.Unavailable -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(phase.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.testTag("quiz-unavailable"))
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onExit) { Text("Back") }
        }
        QuizPhase.Asking -> AskingContent(state, mode, onSelect, onContinue, onFinishEarly)
        QuizPhase.Finished -> SummaryContent(state, mode, onExit, onRestart, onRetrySending)
    }
}

@Composable
private fun AskingContent(state: QuizUiState, mode: QuizMode, onSelect: (Int) -> Unit, onContinue: () -> Unit, onFinishEarly: () -> Unit) {
    val q = state.question ?: return
    Column(Modifier.fillMaxSize().testTag("quiz")) {
        // Header: exit, mode, progress
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFinishEarly) { Icon(Icons.Rounded.Close, contentDescription = "End session") }
            ModePill(mode.label, mode.accent, mode.container)
            Spacer(Modifier.weight(1f))
            Text(
                "${state.difficulty.label}${if (state.adaptive) " · adaptive" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("quiz-difficulty"),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            MittsuProgressBar(state.progress, color = mode.accent, height = 6.dp)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("Question ${state.questionNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${state.completedItems} of ${state.totalItems} done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedContent(
            targetState = q,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "question",
            modifier = Modifier.weight(1f),
        ) { question ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PromptCard(question, mode)
                Spacer(Modifier.height(18.dp))
                question.options.forEachIndexed { i, option ->
                    AnswerCard(
                        index = i,
                        option = option,
                        question = question,
                        selected = state.selected,
                        onClick = { onSelect(i) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(140.dp))
            }
        }

        AnimatedVisibility(state.answered, enter = slideInVertically { it } + fadeIn()) {
            FeedbackPanel(state, q, mode, onContinue)
        }
    }
}

@Composable
private fun PromptCard(q: Question, mode: QuizMode) {
    val isReading = q.type == QuestionType.READING
    val subjectColor = when (q.subject.type) {
        SubjectType.RADICAL -> MittsuTheme.colors.srsMaster
        SubjectType.KANJI -> MittsuTheme.colors.real
        SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY -> MittsuTheme.colors.srsGuru
    }
    MittsuCard(Modifier.fillMaxWidth().padding(top = 16.dp), contentPadding = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${q.subject.type.label} · Level ${q.subject.level}",
                style = MaterialTheme.typography.labelMedium,
                color = subjectColor,
            )
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            val chars = q.subject.characters ?: "?"
            Text(
                chars,
                style = if (chars.length <= 2) JapaneseText.Hero else JapaneseText.Large.copy(fontSize = (220 / chars.length.coerceAtLeast(3)).coerceAtLeast(34).sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("quiz-characters"),
            )
        }
        // Question type banner: clearly different for meaning and reading.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(if (isReading) MaterialTheme.colorScheme.onBackground else mode.container)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                if (isReading) "Choose the reading" else "Choose the meaning",
                style = MaterialTheme.typography.titleMedium,
                color = if (isReading) MaterialTheme.colorScheme.background else mode.accent,
                modifier = Modifier.testTag("quiz-type"),
            )
        }
        if (q.isSelfCheck) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Not enough safe alternatives in your downloaded subjects, so rate yourself honestly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnswerCard(index: Int, option: AnswerOption, question: Question, selected: Int?, onClick: () -> Unit) {
    val answered = selected != null
    val colors = MittsuTheme.colors
    val target = when {
        !answered -> MaterialTheme.colorScheme.surfaceContainer
        option.isCorrect -> colors.correctContainer
        index == selected -> colors.incorrectContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val border = when {
        !answered -> MaterialTheme.colorScheme.outlineVariant
        option.isCorrect -> colors.correct
        index == selected -> colors.incorrect
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bg by animateColorAsState(target, tween(250), label = "answerBg")
    val haptics = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.medium
    val isJapanese = question.type == QuestionType.READING && !question.isSelfCheck
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(bg)
            .border(if (answered && (option.isCorrect || index == selected)) 2.dp else 1.dp, border, shape)
            .clickable(enabled = !answered, role = Role.Button) {
                haptics.performHapticFeedback(if (option.isCorrect) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("answer-$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(14.dp))
        Text(
            option.text,
            style = if (isJapanese) JapaneseText.Answer else MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (answered && option.isCorrect) Icon(Icons.Rounded.CheckCircle, "Correct answer", tint = colors.correct)
        else if (answered && index == selected) Icon(Icons.Rounded.Cancel, "Your answer", tint = colors.incorrect)
    }
}

@Composable
private fun FeedbackPanel(state: QuizUiState, q: Question, mode: QuizMode, onContinue: () -> Unit) {
    val correct = state.selected?.let(q::isCorrect) == true
    val colors = MittsuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("feedback"),
    ) {
        Text(
            if (correct) "Correct" else "Not quite",
            style = MaterialTheme.typography.headlineSmall,
            color = if (correct) colors.correct else colors.incorrect,
            modifier = Modifier.testTag("feedback-title"),
        )
        if (!correct && !q.isSelfCheck) {
            Text("Answer: ${q.correctAnswer}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }
        state.explanation?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("continue"),
            colors = ButtonDefaults.buttonColors(containerColor = mode.accent),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun SummaryContent(state: QuizUiState, mode: QuizMode, onExit: () -> Unit, onRestart: () -> Unit, onRetrySending: () -> Unit) {
    val s = state.stats
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("summary")) {
        ModePill(mode.label, mode.accent, mode.container)
        Spacer(Modifier.height(16.dp))
        Kicker("Session complete", color = mode.accent)
        Spacer(Modifier.height(6.dp))
        val acc = s.accuracy
        Text(
            when {
                acc == null -> "Nothing answered"
                acc >= 0.9f -> "Excellent work"
                acc >= 0.7f -> "Nicely done"
                else -> "Every mistake is practice"
            },
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(20.dp))
        MittsuCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccuracyRing(s.accuracy, color = mode.accent)
                Spacer(Modifier.width(18.dp))
                Column {
                    StatLine("Questions answered", s.answered.toString())
                    StatLine("Correct", s.correct.toString())
                    StatLine("Incorrect attempts", s.incorrect.toString())
                    StatLine("Subjects completed", "${state.completedItems} of ${state.totalItems}")
                }
            }
            Spacer(Modifier.height(14.dp))
            StatLine("Meaning accuracy", s.meaningAccuracy.pct())
            StatLine("Reading accuracy", s.readingAccuracy.pct())
            StatLine("Duration", formatDuration(state.durationMillis))
        }
        Spacer(Modifier.height(10.dp))
        if (mode.isReal) {
            RealSubmission(state, mode, onRetrySending)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("summary-done"),
                colors = ButtonDefaults.buttonColors(containerColor = mode.accent),
                shape = MaterialTheme.shapes.medium,
            ) { Text("Done") }
            return@Column
        }
        Text(
            "Practice results stay on this phone and are never sent to WaniKani.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = mode.accent),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Practise again") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done") }
    }
}

@Composable
private fun RealSubmission(state: QuizUiState, mode: QuizMode, onRetrySending: () -> Unit) {
    val sub = state.submission ?: SubmissionSummary()
    MittsuCard(Modifier.fillMaxWidth().testTag("submission")) {
        Kicker("Sent to WaniKani", color = mode.accent)
        Spacer(Modifier.height(8.dp))
        StatLine("Reviews recorded on WaniKani", sub.sent.toString())
        if (sub.waiting > 0) StatLine("Saved, waiting to send", sub.waiting.toString())
        if (sub.refused > 0) StatLine("Not accepted by WaniKani", sub.refused.toString())
        if (state.excluded > 0) StatLine("Left for the WaniKani website", state.excluded.toString())
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                sub.waiting > 0 -> "Waiting reviews are kept on this phone and sent automatically when you're back online."
                sub.total == 0 -> "No subject was fully answered, so nothing was sent. Partly answered subjects stay due on WaniKani."
                else -> "Only subjects with every answer correct are sent, with the number of wrong tries for meaning and reading."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sub.waiting > 0) {
            OutlinedButton(onClick = onRetrySending, modifier = Modifier.padding(top = 8.dp).testTag("retry-sending")) { Text("Try sending now") }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun Float?.pct() = this?.let { "${(it * 100).roundToInt()}%" } ?: "–"

internal fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m == 0L) "${s}s" else "${m}m ${s}s"
}
