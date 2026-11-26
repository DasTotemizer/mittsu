package app.mittsu.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mittsu.BuildConfig
import app.mittsu.domain.model.DashboardSummary
import app.mittsu.domain.model.NextAction
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.SyncStatus
import app.mittsu.domain.model.dailyProgress
import app.mittsu.domain.model.nextAction
import app.mittsu.ui.components.AccuracyRing
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import app.mittsu.ui.components.MittsuProgressBar
import app.mittsu.ui.components.SrsDistributionBar
import app.mittsu.ui.components.StatTile
import app.mittsu.ui.theme.MittsuTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DashboardActions(
    val onStartReviews: () -> Unit = {},
    val onStartLessons: () -> Unit = {},
    val onPractice: () -> Unit = {},
    val onHoliday: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenVacationSettings: () -> Unit = {},
)

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    now: Instant = Instant.now(),
) {
    when (state) {
        DashboardUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DashboardUiState.Ready -> DashboardContent(state.summary, state.sync, actions, modifier, today, now)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    s: DashboardSummary,
    sync: SyncStatus,
    actions: DashboardActions,
    modifier: Modifier,
    today: LocalDate,
    now: Instant,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    PullToRefreshBox(
        isRefreshing = sync is SyncStatus.Running,
        onRefresh = actions.onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Masthead(s, today) }
        if (s.isSampleData) item { SampleDataNotice(actions.onConnect) }
        if (!s.isSampleData) syncNotice(sync, actions)?.let { notice -> item { notice() } }
        if (s.vacationActive) item { VacationNotice(actions.onOpenVacationSettings) }
        item {
            AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 6 }) {
                HeroCard(s, now, actions)
            }
        }
        item { QuickStats(s, now) }
        item { ActionGrid(s, actions) }
        item { SrsCard(s) }
        item { AccuracyCard(s) }
        item { Footnote() }
    }
    }
}

private fun syncNotice(sync: SyncStatus, actions: DashboardActions): (@Composable () -> Unit)? = when (sync) {
    is SyncStatus.Failed -> {
        {
            NoticeRow(
                icon = Icons.Rounded.CloudOff,
                tint = MaterialTheme.colorScheme.error,
                text = if (sync.needsReconnect) "WaniKani no longer accepts your token. Showing saved data." else "${sync.message} Showing saved data.",
                action = if (sync.needsReconnect) "Reconnect" else "Retry",
                onAction = if (sync.needsReconnect) actions.onConnect else actions.onRefresh,
                tag = "sync-error",
            )
        }
    }
    else -> null
}

@Composable
private fun VacationNotice(onOpen: () -> Unit) {
    NoticeRow(
        icon = Icons.Rounded.BeachAccess,
        tint = MittsuTheme.colors.holiday,
        text = "WaniKani says Vacation Mode is on for your account.",
        action = "Manage",
        onAction = onOpen,
        tag = "vacation-notice",
    )
}

@Composable
internal fun NoticeRow(icon: ImageVector, tint: Color, text: String, action: String?, onAction: () -> Unit, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Masthead(s: DashboardSummary, today: LocalDate) {
    val date = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)).uppercase()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(BuildConfig.APP_NAME, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    "三",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        LevelBadge(s.level)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)))
}

@Composable
private fun LevelBadge(level: Int) {
    Column(
        Modifier.size(60.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.background)
        Text(
            level.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.testTag("level"),
        )
    }
}

@Composable
private fun SampleDataNotice(onConnect: () -> Unit) {
    NoticeRow(
        icon = Icons.Rounded.Info,
        tint = MaterialTheme.colorScheme.tertiary,
        text = "Sample data. Connect your WaniKani account to see your real numbers.",
        action = "Connect",
        onAction = onConnect,
        tag = "sample-notice",
    )
}

@Composable
private fun HeroCard(s: DashboardSummary, now: Instant, actions: DashboardActions) {
    val progress = s.dailyProgress()
    val usesTarget = s.reviewMode != ReviewManagementMode.STANDARD
    val action = s.nextAction()
    val real = MittsuTheme.colors.real

    MittsuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentPadding = 22.dp) {
        Kicker(
            when (action) {
                NextAction.REVIEWS -> "Up next"
                NextAction.LESSONS -> "Ready to learn"
                NextAction.TARGET_MET -> "Target reached"
                NextAction.ALL_CLEAR -> "All clear"
                NextAction.HOLIDAY -> "On holiday"
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                s.reviewsDueNow.toString(),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.testTag("reviews-due"),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "reviews\ndue now",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(
                    if (s.totalOutstandingReviews > s.reviewsDueNow) "${s.totalOutstandingReviews} outstanding in total"
                    else "Your whole backlog right now",
                )
                if (usesTarget) {
                    if (progress.targetReached) append(". Today's target is done, extra reviews are a bonus.")
                    else append(". ${progress.remaining} more to reach today's target.")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (usesTarget) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    "${progress.completed} / ${progress.target}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("daily-progress"),
                )
            }
            Spacer(Modifier.height(8.dp))
            MittsuProgressBar(progress.fraction, color = if (progress.targetReached) MittsuTheme.colors.correct else real)
            Spacer(Modifier.height(6.dp))
            Text(
                "${s.reviewMode.label} mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = actions.onStartReviews,
            enabled = s.reviewsDueNow > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = real),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                when {
                    s.reviewsDueNow == 0 -> "No reviews due"
                    usesTarget && progress.targetReached -> "Keep going"
                    else -> "Start reviews"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
        }
    }
}

@Composable
private fun QuickStats(s: DashboardSummary, now: Instant) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(s.lessonsAvailable.toString(), "Lessons available", Modifier.weight(1f), Icons.Rounded.MenuBook, MittsuTheme.colors.highlight)
        StatTile(formatNextReview(s.nextReviewAt, now), "Next review", Modifier.weight(1f), Icons.Rounded.Schedule)
        StatTile(s.reviewsInNextDay.toString(), "Due in 24 h", Modifier.weight(1f), Icons.Rounded.WbSunny)
    }
}

@Composable
private fun ActionGrid(s: DashboardSummary, actions: DashboardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionRow(
            title = "Start lessons",
            subtitle = if (s.lessonsAvailable > 0) "${s.lessonsAvailable} new subjects ready" else "No lessons right now",
            icon = Icons.Rounded.School,
            accent = MittsuTheme.colors.highlight,
            onClick = actions.onStartLessons,
        )
        ActionRow(
            title = "Practice mode",
            subtitle = "Unlimited, never touches your WaniKani account",
            icon = Icons.Rounded.SelfImprovement,
            accent = MittsuTheme.colors.practice,
            onClick = actions.onPractice,
        )
        ActionRow(
            title = "Holiday mode",
            subtitle = "Take a break without the guilt",
            icon = Icons.Rounded.BeachAccess,
            accent = MittsuTheme.colors.holiday,
            onClick = actions.onHoliday,
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    MittsuCard(onClick = onClick, contentPadding = 16.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(accent.copy(alpha = 0.15f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SrsCard(s: DashboardSummary) {
    MittsuCard(Modifier.fillMaxWidth()) {
        Kicker("Your progress", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        Text("${s.srsDistribution.total} items in the SRS", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        SrsDistributionBar(s.srsDistribution)
    }
}

@Composable
private fun AccuracyCard(s: DashboardSummary) {
    MittsuCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("Recent accuracy", color = MittsuTheme.colors.correct)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        s.recentAccuracy == null -> "No reviews yet"
                        s.recentAccuracy >= 0.9f -> "Superb recall"
                        s.recentAccuracy >= 0.75f -> "Steady and strong"
                        else -> "Room to grow"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    s.accuracyCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AccuracyRing(s.recentAccuracy)
        }
    }
}

@Composable
private fun Footnote() {
    Text(
        "Daily targets help you manage your workload. They never change WaniKani's official review schedule. " +
            "${BuildConfig.APP_NAME} is an unofficial app and is not affiliated with WaniKani.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}

internal fun formatNextReview(next: Instant?, now: Instant): String {
    if (next == null) return "–"
    val d = Duration.between(now, next)
    return when {
        !d.isPositive -> "Now"
        d.toMinutes() < 60 -> "${d.toMinutes().coerceAtLeast(1)} min"
        d.toHours() < 24 -> "${d.toHours()} h"
        else -> "${d.toDays()} d"
    }
}
