package app.mittsu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mittsu.domain.model.SrsDistribution
import app.mittsu.domain.model.SrsGroup
import app.mittsu.ui.theme.MittsuTheme
import kotlin.math.roundToInt

/** Rounded editorial card with a hairline border. */
@Composable
fun MittsuCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Small uppercase "kicker" label above a headline, as in a newspaper. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Animated horizontal progress bar with a rounded track. */
@Composable
fun MittsuProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 10.dp,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        animated.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "${(fraction * 100).roundToInt()} percent" },
    ) {
        val r = size.height / 2
        drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        if (animated.value > 0f) {
            drawRoundRect(
                color,
                size = Size(size.width * animated.value, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            )
        }
    }
}

/** Circular accuracy ring with the percentage in the centre. */
@Composable
fun AccuracyRing(
    accuracy: Float?,
    modifier: Modifier = Modifier,
    diameter: Dp = 88.dp,
    color: Color = MittsuTheme.colors.correct,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(accuracy) {
        animated.animateTo((accuracy ?: 0f).coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * animated.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            text = accuracy?.let { "${(it * 100).roundToInt()}%" } ?: "–",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun srsColor(group: SrsGroup): Color = when (group) {
    SrsGroup.APPRENTICE -> MittsuTheme.colors.srsApprentice
    SrsGroup.GURU -> MittsuTheme.colors.srsGuru
    SrsGroup.MASTER -> MittsuTheme.colors.srsMaster
    SrsGroup.ENLIGHTENED -> MittsuTheme.colors.srsEnlightened
    SrsGroup.BURNED -> MittsuTheme.colors.srsBurned
}

/** Single stacked bar showing the share of items in each SRS group, with a legend. */
@Composable
fun SrsDistributionBar(distribution: SrsDistribution, modifier: Modifier = Modifier) {
    val groups = SrsGroup.entries
    val colors = groups.map { srsColor(it) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            drawRect(track)
            var x = 0f
            val gap = 2.dp.toPx()
            groups.forEachIndexed { i, g ->
                val w = size.width * distribution.fraction(g)
                if (w > 0f) {
                    drawRect(colors[i], topLeft = Offset(x, 0f), size = Size((w - gap).coerceAtLeast(1f), size.height))
                    x += w
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            groups.forEachIndexed { i, g ->
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(colors[i], CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(distribution.count(g).toString(), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        g.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Compact number + label tile. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface,
) {
    MittsuCard(modifier = modifier, contentPadding = 16.dp) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(10.dp))
        }
        Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pill showing whether a screen touches the real account or is practice only. */
@Composable
fun ModePill(text: String, color: Color, container: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
