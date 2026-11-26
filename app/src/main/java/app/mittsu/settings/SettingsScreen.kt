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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mittsu.BuildConfig
import app.mittsu.domain.model.ConnectionState
import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.SyncStatus
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.ThemePreference
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onTheme: (ThemePreference) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onReviewMode: (ReviewManagementMode) -> Unit,
    onDailyTarget: (Int) -> Unit,
    onOpenWaniKaniAccount: () -> Unit,
    connection: ConnectionState = ConnectionState.Disconnected,
    syncStatus: SyncStatus = SyncStatus.Idle,
    onConnect: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onDisconnect: () -> Unit = {},
) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect WaniKani?") },
            text = { Text("This removes your token and the cached WaniKani data from this phone. Your WaniKani account and progress are not affected.") },
            confirmButton = { TextButton(onClick = { confirmDisconnect = false; onDisconnect() }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
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
                when (connection) {
                    ConnectionState.Disconnected -> {
                        Text("Not connected. Mittsu is showing sample data.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onConnect, modifier = Modifier.testTag("settings-connect")) { Text("Connect WaniKani") }
                    }
                    is ConnectionState.Connected -> {
                        val user = connection.user
                        Text(
                            user?.let { "${it.username} · level ${it.level}" } ?: "Connected",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("settings-username"),
                        )
                        if (user != null) {
                            Text(
                                subscriptionLabel(user.subscriptionType) +
                                    if (user.vacationStartedAt != null) " · Vacation Mode is on" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (syncStatus) {
                                SyncStatus.Idle -> "Not synced yet this session"
                                is SyncStatus.Running -> syncStatus.step + "…"
                                is SyncStatus.Done -> "Synced at " + DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(syncStatus.at)
                                is SyncStatus.Failed -> syncStatus.message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (syncStatus is SyncStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onSyncNow, enabled = syncStatus !is SyncStatus.Running) { Text("Sync now") }
                            if (syncStatus is SyncStatus.Failed && syncStatus.needsReconnect) {
                                Button(onClick = onConnect) { Text("Reconnect") }
                            }
                            TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.testTag("settings-disconnect")) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Vacation Mode is managed on WaniKani's website.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
