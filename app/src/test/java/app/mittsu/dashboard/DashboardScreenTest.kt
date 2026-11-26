package app.mittsu.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.repository.SampleDashboardRepository
import app.mittsu.ui.theme.MittsuTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    private val summary = runBlocking { SampleDashboardRepository().summary().first() }

    @Test fun showsLevelReviewsAndSampleNotice() {
        compose.setContent { MittsuTheme { DashboardScreen(DashboardUiState.Ready(summary), DashboardActions()) } }
        compose.onNodeWithTag("level").assertTextEquals("12")
        compose.onNodeWithTag("reviews-due").assertTextEquals("84")
        compose.onNodeWithTag("daily-progress").assertTextEquals("32 / 50")
        compose.onNodeWithTag("sample-notice").assertIsDisplayed()
    }

    @Test fun startReviewsButtonCallsAction() {
        var started = 0
        compose.setContent {
            MittsuTheme {
                DashboardScreen(DashboardUiState.Ready(summary), DashboardActions(onStartReviews = { started++ }))
            }
        }
        compose.onNodeWithTag("dashboard").performScrollToNode(hasText("Start reviews"))
        compose.onNodeWithText("Start reviews").performClick()
        assertEquals(1, started)
    }

    @Test fun noReviewsDueDisablesStart() {
        var started = 0
        compose.setContent {
            MittsuTheme {
                DashboardScreen(
                    DashboardUiState.Ready(summary.copy(reviewsDueNow = 0)),
                    DashboardActions(onStartReviews = { started++ }),
                )
            }
        }
        compose.onNodeWithTag("dashboard").performScrollToNode(hasText("No reviews due"))
        compose.onNodeWithText("No reviews due").assertIsNotEnabled()
        compose.onNodeWithText("No reviews due").performClick()
        assertEquals(0, started)
    }
}
