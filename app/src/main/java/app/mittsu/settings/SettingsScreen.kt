package app.mittsu.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mittsu.BuildConfig
import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.ThemePreference
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onTheme: (ThemePreference) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onReviewMode: (ReviewManagementMode) -> Unit,
    onDailyTarget: (Int) -> Unit,
    onOpenWaniKaniAccount: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.displaySmall) }

        item {
            Section("Appearance") {
                Segmented(
                    options = ThemePreference.entries,
                    selected = settings.theme,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    onSelect = onTheme,
                )
            }
        }

        item {
            Section("Review difficulty") {
                Segmented(Difficulty.entries, settings.difficulty, { it.label }, onDifficulty)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Difficulty only changes which three answers you see. It never affects WaniKani scoring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Section("Review management") {
                Segmented(ReviewManagementMode.entries, settings.reviewMode, { it.label.substringBefore(' ') }, onReviewMode)
                Spacer(Modifier.height(8.dp))
                Text(settings.reviewMode.description, style = MaterialTheme.typography.bodyMedium)
                if (settings.reviewMode != ReviewManagementMode.STANDARD) {
                    Spacer(Modifier.height(16.dp))
                    var target by remember(settings.dailyTarget) { mutableFloatStateOf(settings.dailyTarget.toFloat()) }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Daily review target", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(target.roundToInt().toString(), style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = target,
                        onValueChange = { target = it },
                        onValueChangeFinished = { onDailyTarget(target.roundToInt()) },
                        valueRange = AppSettings.TARGET_RANGE.first.toFloat()..AppSettings.TARGET_RANGE.last.toFloat(),
                        steps = (AppSettings.TARGET_RANGE.last - AppSettings.TARGET_RANGE.first) / 5 - 1,
                    )
                    Text(
                        "Targets manage your workload. They do not change WaniKani's official review schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Section("WaniKani account") {
                Text(
                    "Account connection arrives in build phase 2. Vacation Mode is managed on WaniKani's website.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onOpenWaniKaniAccount, contentPadding = PaddingValues(0.dp)) {
                    Text("Open WaniKani account settings")
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.padding(start = 6.dp))
                }
            }
        }

        item {
            Text(
                "${BuildConfig.APP_NAME} ${BuildConfig.VERSION_NAME} · unofficial, not affiliated with WaniKani",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    MittsuCard(Modifier.fillMaxWidth()) {
        Kicker(title)
        Spacer(Modifier.height(12.dp))
        Column { content() }
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}
