package app.mittsu.quiz

import androidx.compose.runtime.Composable
import app.mittsu.ui.components.PhasePlaceholder
import app.mittsu.ui.theme.MittsuTheme

@Composable
fun PracticeScreen() {
    PhasePlaceholder(
        glyph = "練",
        title = "Practice mode",
        body = "Unlimited practice with your cached subjects. Nothing you answer here is ever sent to WaniKani.",
        phase = 5,
        accent = MittsuTheme.colors.practice,
        accentContainer = MittsuTheme.colors.practiceContainer,
        pill = "Practice only",
    )
}
