package app.mittsu.lessons

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.mittsu.ui.components.PhasePlaceholder
import app.mittsu.ui.theme.MittsuTheme

@Composable
fun LessonsScreen(onBack: () -> Unit) {
    PhasePlaceholder(
        glyph = "学",
        title = "Lessons",
        body = "Meet new subjects with their mnemonics and context sentences, then confirm them with three-answer quizzes.",
        phase = 5,
        accent = MittsuTheme.colors.highlight,
        accentContainer = MittsuTheme.colors.highlight.copy(alpha = 0.15f),
        pill = "Real WaniKani",
    ) { OutlinedButton(onClick = onBack) { Text("Back to today") } }
}
