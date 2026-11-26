package app.mittsu.stats

import androidx.compose.runtime.Composable
import app.mittsu.ui.components.PhasePlaceholder
import app.mittsu.ui.theme.MittsuTheme

@Composable
fun StatsScreen() {
    PhasePlaceholder(
        glyph = "績",
        title = "Progress",
        body = "Daily, weekly and monthly activity, accuracy by question type, backlog trends and your trickiest subjects.",
        phase = 8,
        accent = MittsuTheme.colors.srsMaster,
        accentContainer = MittsuTheme.colors.practiceContainer,
    )
}
