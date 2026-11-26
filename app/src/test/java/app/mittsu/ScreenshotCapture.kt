package app.mittsu

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.dashboard.DashboardActions
import app.mittsu.dashboard.DashboardScreen
import app.mittsu.dashboard.DashboardUiState
import app.mittsu.domain.model.ThemePreference
import app.mittsu.repository.SampleDashboardRepository
import app.mittsu.ui.theme.MittsuTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the dashboard to PNG for design review. Skipped unless -Pmittsu.screenshots=<dir> is given:
 *   ./gradlew :app:testDebugUnitTest --tests '*ScreenshotCapture*' -Pmittsu.screenshots=/tmp/shots
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi")
class ScreenshotCapture {
    @get:Rule val compose = createComposeRule()
    private val outDir = System.getProperty("mittsu.screenshots")

    private fun shoot(name: String, theme: ThemePreference) {
        assumeTrue(outDir != null)
        val summary = runBlocking { SampleDashboardRepository().summary().first() }
        compose.mainClock.autoAdvance = true
        compose.setContent {
            MittsuTheme(theme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    DashboardScreen(DashboardUiState.Ready(summary), DashboardActions())
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir!!).mkdirs()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dashboardLight() = shoot("dashboard-light", ThemePreference.LIGHT)
    @Test fun dashboardDark() = shoot("dashboard-dark", ThemePreference.DARK)
}
