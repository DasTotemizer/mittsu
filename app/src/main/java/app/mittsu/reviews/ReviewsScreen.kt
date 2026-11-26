package app.mittsu.reviews

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.mittsu.ui.components.PhasePlaceholder
import app.mittsu.ui.theme.MittsuTheme

@Composable
fun ReviewsScreen(onBack: () -> Unit) {
    PhasePlaceholder(
        glyph = "復",
        title = "Real reviews",
        body = "Three-answer reviews that submit to your real WaniKani account, meaning and reading tracked separately.",
        phase = 4,
        accent = MittsuTheme.colors.real,
        accentContainer = MittsuTheme.colors.realContainer,
        pill = "Real WaniKani",
    ) { OutlinedButton(onClick = onBack) { Text("Back to today") } }
}
