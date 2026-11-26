package app.mittsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.mittsu.domain.model.ThemePreference

private val LightColors = lightColorScheme(
    primary = Washi.Shu,
    onPrimary = Color.White,
    primaryContainer = Washi.ShuMist,
    onPrimaryContainer = Color(0xFF4A140A),
    secondary = Washi.Ai,
    onSecondary = Color.White,
    secondaryContainer = Washi.AiMist,
    onSecondaryContainer = Color(0xFF0E1E38),
    tertiary = Washi.Kincha,
    onTertiary = Color.White,
    background = Washi.Paper,
    onBackground = Washi.Ink,
    surface = Washi.Paper,
    onSurface = Washi.Ink,
    surfaceVariant = Washi.PaperDeep,
    onSurfaceVariant = Washi.Stone,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Washi.Card,
    surfaceContainer = Washi.Card,
    surfaceContainerHigh = Washi.PaperDeep,
    surfaceContainerHighest = Color(0xFFE5DCCB),
    outline = Washi.StoneLight,
    outlineVariant = Washi.Line,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Washi.ShuBright,
    onPrimary = Color(0xFF2B0A04),
    primaryContainer = Color(0xFF5A1E12),
    onPrimaryContainer = Washi.ShuMist,
    secondary = Washi.AiBright,
    onSecondary = Color(0xFF0B1A33),
    secondaryContainer = Color(0xFF1F3457),
    onSecondaryContainer = Washi.AiMist,
    tertiary = Washi.KinchaBright,
    onTertiary = Color(0xFF2E2003),
    background = Washi.Ink,
    onBackground = Color(0xFFEDE7DC),
    surface = Washi.Ink,
    onSurface = Color(0xFFEDE7DC),
    surfaceVariant = Washi.InkSoft,
    onSurfaceVariant = Color(0xFFA9A398),
    surfaceContainerLowest = Color(0xFF101216),
    surfaceContainerLow = Washi.InkCard,
    surfaceContainer = Washi.InkCard,
    surfaceContainerHigh = Washi.InkSoft,
    surfaceContainerHighest = Color(0xFF33363F),
    outline = Color(0xFF5C5F69),
    outlineVariant = Washi.InkLine,
    error = Color(0xFFF2B8B5),
)

/** Colours outside the Material scheme that the app needs in both themes. */
@Immutable
data class MittsuExtraColors(
    val real: Color,
    val realContainer: Color,
    val practice: Color,
    val practiceContainer: Color,
    val correct: Color,
    val correctContainer: Color,
    val incorrect: Color,
    val incorrectContainer: Color,
    val holiday: Color,
    val holidayContainer: Color,
    val highlight: Color,
    val srsApprentice: Color,
    val srsGuru: Color,
    val srsMaster: Color,
    val srsEnlightened: Color,
    val srsBurned: Color,
)

private val LightExtras = MittsuExtraColors(
    real = Washi.Shu, realContainer = Washi.ShuMist,
    practice = Washi.Ai, practiceContainer = Washi.AiMist,
    correct = Washi.Matcha, correctContainer = Color(0xFFE1EBD6),
    incorrect = Color(0xFFB3261E), incorrectContainer = Color(0xFFF7DCD8),
    holiday = Washi.Fuji, holidayContainer = Color(0xFFEDE3F4),
    highlight = Washi.Kincha,
    srsApprentice = Washi.SrsApprentice, srsGuru = Washi.SrsGuru, srsMaster = Washi.SrsMaster,
    srsEnlightened = Washi.SrsEnlightened, srsBurned = Washi.SrsBurned,
)

private val DarkExtras = MittsuExtraColors(
    real = Washi.ShuBright, realContainer = Color(0xFF4A1A10),
    practice = Washi.AiBright, practiceContainer = Color(0xFF1C2E4D),
    correct = Washi.MatchaBright, correctContainer = Color(0xFF243520),
    incorrect = Color(0xFFF2A49C), incorrectContainer = Color(0xFF4A1C18),
    holiday = Color(0xFFC3A6DD), holidayContainer = Color(0xFF33284A),
    highlight = Washi.KinchaBright,
    srsApprentice = Color(0xFFE8927C), srsGuru = Color(0xFFB394D0), srsMaster = Color(0xFF7FA3DB),
    srsEnlightened = Color(0xFF6CC0D4), srsBurned = Color(0xFF8E897E),
)

val LocalMittsuColors = staticCompositionLocalOf { LightExtras }

val MittsuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

object MittsuTheme {
    val colors: MittsuExtraColors
        @Composable get() = LocalMittsuColors.current
    val scheme: ColorScheme
        @Composable get() = MaterialTheme.colorScheme
}

@Composable
fun isDarkTheme(preference: ThemePreference): Boolean = when (preference) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

@Composable
fun MittsuTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themePreference)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMittsuColors provides if (dark) DarkExtras else LightExtras,
    ) {
        val scheme = if (dark) DarkColors else LightColors
        MaterialTheme(colorScheme = scheme, typography = MittsuTypography, shapes = MittsuShapes) {
            // Default text colour follows the theme even outside a Surface (fixes black text in dark mode).
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides scheme.onBackground,
                content = content,
            )
        }
    }
}
