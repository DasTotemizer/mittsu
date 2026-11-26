package app.mittsu.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import app.mittsu.ui.components.ModePill
import app.mittsu.ui.theme.MittsuTheme

/** Practice hub. Phase 3 offers quick practice; Phase 5 adds levels, types and SRS filters. */
@Composable
fun PracticeScreen(onQuickPractice: () -> Unit) {
    val accent = MittsuTheme.colors.practice
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ModePill("Practice only", accent, MittsuTheme.colors.practiceContainer)
            Spacer(Modifier.height(12.dp))
            Text("Practice", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Unlimited three-answer practice with subjects you've already learned. Nothing here is ever sent to WaniKani.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            MittsuCard(Modifier.fillMaxWidth()) {
                Kicker("Quick practice", color = accent)
                Spacer(Modifier.height(8.dp))
                Text("10 random subjects", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Uses your question type and difficulty settings. Wrong answers come back until you get them right.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onQuickPractice,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("quick-practice"),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
            }
        }
        item {
            Column {
                Text(
                    "Choosing levels, subject types, SRS stages and weak subjects arrives in build phase 5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
