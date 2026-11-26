package app.mittsu.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mittsu.dashboard.NoticeRow
import app.mittsu.domain.model.SubjectType
import app.mittsu.quiz.QuizScreen
import app.mittsu.quiz.lessonMode
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import app.mittsu.ui.components.ModePill
import app.mittsu.ui.theme.JapaneseText
import app.mittsu.ui.theme.MittsuTheme

data class LessonActions(
    val onChooseBatch: (Int) -> Unit = {},
    val onStartBatch: () -> Unit = {},
    val onNextCard: () -> Unit = {},
    val onPreviousCard: () -> Unit = {},
    val onStartQuiz: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onContinue: () -> Unit = {},
    val onEndQuiz: () -> Unit = {},
    val onMoreLessons: () -> Unit = {},
    val onSendNow: () -> Unit = {},
    val onDismissRefused: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onOpenVacationSettings: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun LessonsScreen(state: LessonUiState, actions: LessonActions) {
    when (state.step) {
        LessonStep.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MittsuTheme.colors.highlight)
        }
        LessonStep.NOT_CONNECTED, LessonStep.OVERVIEW, LessonStep.ERROR -> Overview(state, actions)
        LessonStep.TEACHING -> Teaching(state, actions)
        LessonStep.QUIZ -> QuizScreen(
            state = state.quiz,
            mode = lessonMode(),
            onSelect = actions.onSelect,
            onContinue = actions.onContinue,
            onFinishEarly = actions.onEndQuiz,
            onExit = actions.onMoreLessons,
            onRestart = actions.onMoreLessons,
        )
        LessonStep.DONE -> Done(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(state: LessonUiState, actions: LessonActions) {
    val accent = MittsuTheme.colors.highlight
    LazyColumn(
        Modifier.fillMaxSize().testTag("lessons"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                ModePill("Real WaniKani", MittsuTheme.colors.real, MittsuTheme.colors.realContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text("Lessons", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Meet a few new subjects, then confirm them with a three-answer quiz. A lesson is finished on WaniKani only once you get it right.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.step == LessonStep.NOT_CONNECTED) {
            item {
                MittsuCard(Modifier.fillMaxWidth()) {
                    Text("Connect WaniKani first", style = MaterialTheme.typography.headlineSmall)
                    Text("Lessons come from your account, so they need your API token.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = actions.onConnect, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Connect") }
                }
            }
            return@LazyColumn
        }
        state.message?.let { msg -> item { Text(msg, color = MittsuTheme.colors.incorrect, style = MaterialTheme.typography.bodyMedium) } }
        if (state.waitingToSend > 0) {
            item {
                NoticeRow(
                    Icons.Rounded.CloudUpload, MittsuTheme.colors.highlight,
                    "${state.waitingToSend} finished ${if (state.waitingToSend == 1) "lesson is" else "lessons are"} saved on this phone and waiting to be sent.",
                    "Send now", actions.onSendNow, "lessons-waiting",
                )
            }
        }
        if (state.refused.isNotEmpty()) {
            item {
                MittsuCard(Modifier.fillMaxWidth(), containerColor = MittsuTheme.colors.incorrectContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MittsuTheme.colors.incorrect, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("WaniKani didn't accept ${state.refused.size} ${if (state.refused.size == 1) "lesson" else "lessons"}", style = MaterialTheme.typography.titleSmall)
                    }
                    state.refused.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text("Usually the lesson was already done somewhere else.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = actions.onDismissRefused) { Text("Dismiss") }
                }
            }
        }
        if (state.vacation) {
            item {
                NoticeRow(
                    Icons.Rounded.BeachAccess, MittsuTheme.colors.holiday,
                    "WaniKani says Vacation Mode is on. Lessons you finish start reviews once you turn it off on WaniKani.",
                    "Manage", actions.onOpenVacationSettings, "lessons-vacation",
                )
            }
        }
        item {
            MittsuCard(Modifier.fillMaxWidth().testTag("lesson-start-card")) {
                Kicker("Ready to learn", color = accent)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.available}", style = JapaneseText.Hero.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.testTag("lesson-count"))
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.available == 1) "new subject" else "new subjects", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 14.dp))
                }
                if (state.available == 0) {
                    Text("No lessons right now. Keep up with reviews to unlock more.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = actions.onBack) { Text("Back to today") }
                    return@MittsuCard
                }
                Spacer(Modifier.height(8.dp))
                Text("Subjects per batch", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LessonUiState.BATCH_OPTIONS.forEach { size ->
                        FilterChip(
                            selected = size == state.batchSize,
                            onClick = { actions.onChooseBatch(size) },
                            label = { Text("$size") },
                            modifier = Modifier.testTag("batch-$size"),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.18f), selectedLabelColor = accent),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                val n = minOf(state.batchSize, state.available)
                Button(
                    onClick = actions.onStartBatch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start-lessons"),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Learn $n new ${if (n == 1) "subject" else "subjects"}", style = MaterialTheme.typography.titleMedium) }
            }
        }
        if (state.excluded > 0) {
            item {
                Row(Modifier.testTag("lessons-excluded")) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.excluded} ${if (state.excluded == 1) "lesson is" else "lessons are"} left for the WaniKani website, " +
                            "because Mittsu can't yet quiz ${if (state.excluded == 1) "it" else "them"} with three fair answers (for example image-only radicals).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun subjectColor(type: SubjectType): Color = when (type) {
    SubjectType.RADICAL -> MittsuTheme.colors.srsMaster
    SubjectType.KANJI -> MittsuTheme.colors.real
    SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY -> MittsuTheme.colors.srsGuru
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Teaching(state: LessonUiState, actions: LessonActions) {
    val card = state.card ?: return
    val s = card.subject
    val color = subjectColor(s.type)
    Column(Modifier.fillMaxSize().testTag("lesson-card")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onMoreLessons) { Icon(Icons.Rounded.Close, contentDescription = "Stop lessons") }
            Text("New subject ${state.cardIndex + 1} of ${state.batch.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Row(Modifier.padding(end = 12.dp)) {
                state.batch.indices.forEach { i ->
                    Box(Modifier.padding(2.dp).size(8.dp).clip(CircleShape).background(if (i <= state.cardIndex) color else MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            MittsuCard(Modifier.fillMaxWidth(), contentPadding = 22.dp) {
                Text("${s.type.label} · Level ${s.level}", style = MaterialTheme.typography.labelMedium, color = color)
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    val chars = s.characters ?: "?"
                    Text(chars, style = if (chars.length <= 2) JapaneseText.Hero else JapaneseText.Large.copy(fontSize = (220 / chars.length.coerceAtLeast(3)).coerceAtLeast(34).sp), textAlign = TextAlign.Center, modifier = Modifier.testTag("lesson-characters"))
                }
                Text(s.primaryMeaning, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth().testTag("lesson-meaning"), textAlign = TextAlign.Center)
                if (card.alternativeMeanings.isNotEmpty()) {
                    Text("Also: ${card.alternativeMeanings.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                val accepted = card.readings.filter { it.accepted }
                if (s.type.hasReading && accepted.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        accepted.joinToString("、") { it.reading },
                        style = JapaneseText.Answer,
                        modifier = Modifier.fillMaxWidth().testTag("lesson-reading"),
                        textAlign = TextAlign.Center,
                    )
                    val others = card.readings.filterNot { it.accepted }
                    if (others.isNotEmpty() && s.type == SubjectType.KANJI) {
                        Text(
                            "Other readings (not asked yet): ${others.joinToString("、") { it.reading }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (card.partsOfSpeech.isNotEmpty()) {
                    Text(card.partsOfSpeech.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
                }
            }
            if (card.components.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Kicker("Made from", color = color)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.components.forEach { c ->
                        Row(
                            Modifier.clip(MaterialTheme.shapes.small).background(subjectColor(c.type).copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.characters, style = MaterialTheme.typography.titleLarge, color = subjectColor(c.type))
                            Spacer(Modifier.width(8.dp))
                            Text(c.meaning, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            MnemonicSection("Meaning", card.meaningMnemonic, card.meaningHint, color)
            if (s.type.hasReading) MnemonicSection("Reading", card.readingMnemonic, card.readingHint, color)
            if (card.contextSentences.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Kicker("In context", color = color)
                card.contextSentences.forEach { (ja, en) ->
                    Spacer(Modifier.height(8.dp))
                    Text(ja, style = MaterialTheme.typography.titleMedium)
                    Text(en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = actions.onPreviousCard, enabled = state.cardIndex > 0, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Back") }
            val last = state.cardIndex == state.batch.lastIndex
            Button(
                onClick = actions.onNextCard,
                modifier = Modifier.weight(2f).heightIn(min = 56.dp).testTag("lesson-next"),
                colors = ButtonDefaults.buttonColors(containerColor = MittsuTheme.colors.highlight),
                shape = MaterialTheme.shapes.medium,
            ) { Text(if (last) "Start the quiz" else "Next", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun MnemonicSection(title: String, mnemonic: String?, hint: String?, color: Color) {
    if (mnemonic.isNullOrBlank() && hint.isNullOrBlank()) return
    Spacer(Modifier.height(16.dp))
    Kicker("$title mnemonic", color = color)
    Spacer(Modifier.height(6.dp))
    mnemonic?.takeIf { it.isNotBlank() }?.let { Text(mnemonicText(it), style = MaterialTheme.typography.bodyLarge) }
    hint?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(6.dp))
        Text(mnemonicText("Hint: $it"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun mnemonicText(raw: String): AnnotatedString {
    val colors = MittsuTheme.colors
    return buildAnnotatedString {
        MnemonicMarkup.parse(raw).forEach { run ->
            val style = when (run.tag) {
                null -> null
                MnemonicMarkup.Tag.RADICAL -> SpanStyle(color = colors.srsMaster, fontWeight = FontWeight.SemiBold)
                MnemonicMarkup.Tag.KANJI -> SpanStyle(color = colors.real, fontWeight = FontWeight.SemiBold)
                MnemonicMarkup.Tag.VOCABULARY -> SpanStyle(color = colors.srsGuru, fontWeight = FontWeight.SemiBold)
                MnemonicMarkup.Tag.READING, MnemonicMarkup.Tag.MEANING, MnemonicMarkup.Tag.EMPHASIS, MnemonicMarkup.Tag.JA -> SpanStyle(fontWeight = FontWeight.Bold)
            }
            if (style == null) append(run.text) else withStyle(style) { append(run.text) }
        }
    }
}

@Composable
private fun Done(state: LessonUiState, actions: LessonActions) {
    val accent = MittsuTheme.colors.highlight
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("lessons-done")) {
        Kicker("Batch complete", color = accent)
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.learnedThisBatch == 0) "No lessons finished" else "${state.learnedThisBatch} new ${if (state.learnedThisBatch == 1) "subject" else "subjects"} learned",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(16.dp))
        MittsuCard(Modifier.fillMaxWidth().testTag("lesson-result")) {
            Kicker("On WaniKani", color = accent)
            Spacer(Modifier.height(8.dp))
            ResultLine("Lessons finished on WaniKani", state.result.sent)
            if (state.result.waiting > 0) ResultLine("Saved, waiting to send", state.result.waiting)
            if (state.result.refused > 0) ResultLine("Not accepted by WaniKani", state.result.refused)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    state.learnedThisBatch == 0 -> "Subjects you didn't finish stay as lessons on WaniKani."
                    state.result.waiting > 0 -> "Waiting lessons are kept on this phone and sent when you're back online."
                    else -> "Their first reviews come up in about four hours."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = actions.onMoreLessons,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("more-lessons"),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = MaterialTheme.shapes.medium,
        ) { Text("More lessons") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = actions.onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done") }
    }
}

@Composable
private fun ResultLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
