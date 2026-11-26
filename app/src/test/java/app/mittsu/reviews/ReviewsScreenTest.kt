package app.mittsu.reviews

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.quiz.QuizPhase
import app.mittsu.quiz.QuizScreen
import app.mittsu.quiz.QuizUiState
import app.mittsu.quiz.SessionStats
import app.mittsu.quiz.SubmissionSummary
import app.mittsu.quiz.realMode
import app.mittsu.ui.theme.MittsuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val ready = ReviewsUiState(
        loading = false, ready = 120, excluded = 3, sizeOptions = SessionSize.options(120), selectedSize = 20,
        completedToday = 30, dailyTarget = 50,
    )

    @Test fun startsTheChosenSessionSize() {
        var started: Int? = null
        compose.setContent { MittsuTheme { ReviewsScreen(ready, ReviewsActions(onStart = { started = it })) } }
        compose.onNodeWithTag("ready-count").assertTextContains("120")
        compose.onNodeWithTag("size-20").assertIsSelected()
        compose.onNodeWithText("20 left to reach today's target of 50.").assertExists()
        compose.onNodeWithTag("start-reviews").assertTextContains("Start 20 reviews").performClick()
        assertEquals(20, started)
    }

    @Test fun explainsLeftOutReviewsAndWaitingUploads() {
        var sent = false
        compose.setContent {
            MittsuTheme { ReviewsScreen(ready.copy(waitingToSend = 2), ReviewsActions(onSendNow = { sent = true })) }
        }
        compose.onNodeWithTag("reviews").performScrollToNode(hasTestTag("excluded-note"))
        compose.onNodeWithTag("excluded-note").assertExists()
        compose.onNodeWithTag("reviews").performScrollToNode(hasTestTag("waiting-notice"))
        compose.onNodeWithText("Send now").performClick()
        assertEquals(true, sent)
    }

    @Test fun offersToResumeASavedSession() {
        var resumed = false
        compose.setContent { MittsuTheme { ReviewsScreen(ready.copy(hasSavedSession = true), ReviewsActions(onResume = { resumed = true })) } }
        compose.onNodeWithTag("resume").performClick()
        assertEquals(true, resumed)
    }

    @Test fun notConnectedAsksToConnect() {
        compose.setContent { MittsuTheme { ReviewsScreen(ReviewsUiState(loading = false, connected = false), ReviewsActions()) } }
        compose.onNodeWithText("Connect WaniKani first").assertExists()
        compose.onNodeWithTag("start-reviews").assertDoesNotExist()
    }

    @Test fun realSummaryReportsWhatWasSent() {
        compose.setContent {
            MittsuTheme {
                QuizScreen(
                    QuizUiState(phase = QuizPhase.Finished, completedItems = 9, totalItems = 10, stats = SessionStats(answered = 20, correct = 17),
                        submission = SubmissionSummary(sent = 7, waiting = 2), excluded = 1),
                    realMode(), {}, {}, {}, {}, {},
                )
            }
        }
        compose.onNodeWithText("Reviews recorded on WaniKani").assertExists()
        compose.onNodeWithText("Saved, waiting to send").assertExists()
        compose.onNodeWithText("Practice results stay on this phone and are never sent to WaniKani.").assertDoesNotExist()
        compose.onNodeWithTag("retry-sending").assertExists()
    }
}
