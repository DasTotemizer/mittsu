package app.mittsu.reviews

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BeachAccess
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mittsu.dashboard.NoticeRow
import app.mittsu.dashboard.formatNextReview
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import app.mittsu.ui.components.ModePill
import app.mittsu.ui.theme.JapaneseText
import app.mittsu.ui.theme.MittsuTheme
import java.time.Instant

data class ReviewsActions(
    val onStart: (size: Int) -> Unit = {},
    val onResume: () -> Unit = {},
    val onDiscardSaved: () -> Unit = {},
    val onChooseSize: (Int) -> Unit = {},
    val onSendNow: () -> Unit = {},
    val onDismissRefused: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onOpenVacationSettings: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewsScreen(state: ReviewsUiState, actions: ReviewsActions, now: Instant = Instant.now()) {
    val accent = MittsuTheme.colors.real
    LazyColumn(
        Modifier.fillMaxSize().testTag("reviews"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                ModePill("Real WaniKani", accent, MittsuTheme.colors.realContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text("Reviews", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Answers here are sent to your WaniKani account, the same as reviewing on the website.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!state.connected) {
            item {
                MittsuCard(Modifier.fillMaxWidth()) {
                    Text("Connect WaniKani first", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Real reviews need your API token. Practice works without changing anything on your account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = actions.onConnect, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Connect") }
                }
            }
            return@LazyColumn
        }

        if (state.waitingToSend > 0) {
            item {
                NoticeRow(
                    icon = Icons.Rounded.CloudUpload,
                    tint = MittsuTheme.colors.highlight,
                    text = buildString {
                        append(plural(state.waitingToSend, "finished review is", "finished reviews are"))
                        append(" saved on this phone and waiting to be sent.")
                        state.sendProblem?.let { append(" $it") }
                    },
                    action = if (state.sending) "Sending…" else "Send now",
                    onAction = actions.onSendNow,
                    tag = "waiting-notice",
                )
            }
        }
        if (state.refused.isNotEmpty()) {
            item {
                MittsuCard(Modifier.fillMaxWidth().testTag("refused"), containerColor = MittsuTheme.colors.incorrectContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MittsuTheme.colors.incorrect, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("WaniKani didn't accept ${plural(state.refused.size, "review", "reviews")}", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    state.refused.take(5).forEach {
                        Text("${it.characters}: ${it.message}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "This usually means the review was already done somewhere else. Nothing else was changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = actions.onDismissRefused) { Text("Dismiss") }
                }
            }
        }
        if (state.vacation) {
            item {
                NoticeRow(
                    icon = Icons.Rounded.BeachAccess,
                    tint = MittsuTheme.colors.holiday,
                    text = "WaniKani says Vacation Mode is on, so no reviews come due until you turn it off on WaniKani.",
                    action = "Manage",
                    onAction = actions.onOpenVacationSettings,
                    tag = "reviews-vacation",
                )
            }
        }

        if (state.hasSavedSession) {
            item {
                MittsuCard(Modifier.fillMaxWidth().testTag("saved-session")) {
                    Kicker("Unfinished session", color = accent)
                    Spacer(Modifier.height(6.dp))
                    Text("Pick up where you left off", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Reviews you finished are already saved. Anything done elsewhere since then is skipped.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = actions.onResume,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("resume"),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("Continue session", style = MaterialTheme.typography.titleMedium) }
                    TextButton(onClick = actions.onDiscardSaved) { Text("Start fresh instead") }
                }
            }
        }

        item { StartCard(state, actions, now) }

        if (state.excluded > 0) {
            item {
                Row(Modifier.testTag("excluded-note")) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${plural(state.excluded, "due review is", "due reviews are")} left for the WaniKani website: " +
                            "Mittsu can't yet give ${if (state.excluded == 1) "it" else "them"} three fair answers " +
                            "(image-only radicals, or not enough safe alternatives). Nothing about ${if (state.excluded == 1) "it" else "them"} is changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(state: ReviewsUiState, actions: ReviewsActions, now: Instant) {
    val accent = MittsuTheme.colors.real
    MittsuCard(Modifier.fillMaxWidth().testTag("start-card")) {
        Kicker("Due now", color = accent)
        Spacer(Modifier.height(6.dp))
        if (state.loading) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
            return@MittsuCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${state.ready}", style = JapaneseText.Hero.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.testTag("ready-count"))
            Spacer(Modifier.width(10.dp))
            Text(if (state.ready == 1) "review ready" else "reviews ready", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 14.dp))
        }
        if (state.checking) {
            Text("Checking WaniKani for changes…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.ready == 0) {
            Text(
                if (state.nextReviewAt != null) "Next review in ${formatNextReview(state.nextReviewAt, now)}." else "Nothing is scheduled yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = actions.onBack) { Text("Back to today") }
            return@MittsuCard
        }
        Spacer(Modifier.height(8.dp))
        Text("Session size", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.sizeOptions.forEach { size ->
                FilterChip(
                    selected = size == state.selectedSize,
                    onClick = { actions.onChooseSize(size) },
                    label = { Text(SessionSize.label(size, state.ready)) },
                    modifier = Modifier.testTag("size-$size"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MittsuTheme.colors.realContainer,
                        selectedLabelColor = accent,
                    ),
                )
            }
        }
        targetHint(state)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { actions.onStart(state.selectedSize) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start-reviews"),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = MaterialTheme.shapes.medium,
        ) {
            val n = if (state.selectedSize == SessionSize.ALL) state.ready else state.selectedSize
            Text("Start $n ${if (n == 1) "review" else "reviews"}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun targetHint(state: ReviewsUiState): String? {
    if (state.reviewMode == ReviewManagementMode.STANDARD) return null
    val left = state.dailyTarget - state.completedToday
    return if (left > 0) "$left left to reach today's target of ${state.dailyTarget}."
    else "Today's target of ${state.dailyTarget} is done. Anything more is a bonus."
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
