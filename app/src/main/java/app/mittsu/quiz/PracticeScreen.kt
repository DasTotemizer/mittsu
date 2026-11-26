package app.mittsu.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mittsu.domain.model.SrsGroup
import app.mittsu.domain.model.SubjectType
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import app.mittsu.ui.components.ModePill
import app.mittsu.ui.theme.MittsuTheme
import kotlin.math.roundToInt

data class PracticeActions(
    val onQuickPractice: () -> Unit = {},
    val onCustomPractice: () -> Unit = {},
    val onLevels: (Int, Int) -> Unit = { _, _ -> },
    val onToggleType: (SubjectType) -> Unit = {},
    val onToggleSrs: (SrsGroup) -> Unit = {},
    val onWeakOnly: (Boolean) -> Unit = {},
    val onSize: (Int) -> Unit = {},
)

/** Practice hub: quick practice, or a custom session filtered by level, type, SRS stage and weakness. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(state: PracticeSetupState, actions: PracticeActions) {
    val accent = MittsuTheme.colors.practice
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MittsuTheme.colors.practiceContainer,
        selectedLabelColor = accent,
    )
    LazyColumn(
        Modifier.fillMaxSize().testTag("practice"),
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
                    onClick = actions.onQuickPractice,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("quick-practice"),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
            }
        }
        item {
            val f = state.filters
            MittsuCard(Modifier.fillMaxWidth().testTag("custom-practice")) {
                Kicker("Custom practice", color = accent)
                Spacer(Modifier.height(8.dp))
                Text("Choose what to practise", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(14.dp))

                if (state.maxLevel > 1) {
                    var range by remember(f.minLevel, f.maxLevel, state.maxLevel) {
                        mutableStateOf(f.minLevel.toFloat()..f.maxLevel.coerceAtMost(state.maxLevel).toFloat())
                    }
                    Row {
                        Text("Levels", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val lo = range.start.roundToInt()
                        val hi = range.endInclusive.roundToInt()
                        Text(if (lo == hi) "Level $lo" else "$lo to $hi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("level-range"))
                    }
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        onValueChangeFinished = { actions.onLevels(range.start.roundToInt(), range.endInclusive.roundToInt()) },
                        valueRange = 1f..state.maxLevel.toFloat(),
                        steps = (state.maxLevel - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                    )
                }

                Text("Subject types", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SubjectType.RADICAL to "Radicals", SubjectType.KANJI to "Kanji", SubjectType.VOCABULARY to "Vocabulary").forEach { (type, label) ->
                        FilterChip(selected = type in f.types, onClick = { actions.onToggleType(type) }, label = { Text(label) },
                            colors = chipColors, modifier = Modifier.testTag("type-${type.apiName}"))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("SRS stages", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrsGroup.entries.forEach { g ->
                        FilterChip(selected = g in f.srsGroups, onClick = { actions.onToggleSrs(g) }, label = { Text(g.label) },
                            colors = chipColors, modifier = Modifier.testTag("srs-${g.name}"))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("Weak subjects only", style = MaterialTheme.typography.titleSmall)
                        Text("Below ${PracticeFilters.WEAK_THRESHOLD}% correct on WaniKani, weakest first",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = f.weakOnly, onCheckedChange = actions.onWeakOnly, modifier = Modifier.testTag("weak-only"),
                        colors = SwitchDefaults.colors(checkedTrackColor = accent))
                }
                Spacer(Modifier.height(10.dp))
                Text("Session size", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PracticeFilters.SIZES.forEach { n ->
                        FilterChip(selected = f.size == n, onClick = { actions.onSize(n) }, label = { Text("$n") },
                            colors = chipColors, modifier = Modifier.testTag("practice-size-$n"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (!state.loaded) "Counting your subjects…"
                    else "${state.matching} of ${state.learned} learned subjects match",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("matching-count"),
                )
                Spacer(Modifier.height(12.dp))
                val n = minOf(f.size, state.matching)
                if (state.loaded && state.matching == 0) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Nothing matches") }
                } else {
                    Button(
                        onClick = actions.onCustomPractice,
                        enabled = state.loaded,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start-custom"),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("Practise $n ${if (n == 1) "subject" else "subjects"}", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}
