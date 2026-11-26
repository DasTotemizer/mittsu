package app.mittsu.lessons

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.domain.model.SubjectType
import app.mittsu.quiz.TestSubjects
import app.mittsu.ui.theme.MittsuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

internal val sampleCard = LessonCard(
    assignmentId = 1, subject = TestSubjects.dai, alternativeMeanings = listOf("Large"),
    readings = listOf(LessonReading("だい", "onyomi", true, true), LessonReading("たい", "onyomi", false, true), LessonReading("おお", "kunyomi", false, false)),
    meaningMnemonic = "A <radical>person</radical> stretching their arms out wide is <kanji>big</kanji>.",
    meaningHint = null, readingMnemonic = "Big things are <reading>dai</reading>.", readingHint = null,
    contextSentences = emptyList(), partsOfSpeech = emptyList(),
    components = listOf(LessonComponent("大", "Big", SubjectType.RADICAL)),
)

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LessonsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overviewStartsABatch() {
        var started = false
        compose.setContent {
            MittsuTheme { LessonsScreen(LessonUiState(step = LessonStep.OVERVIEW, available = 12, batchSize = 5), LessonActions(onStartBatch = { started = true })) }
        }
        compose.onNodeWithTag("lesson-count").assertTextContains("12")
        compose.onNodeWithTag("start-lessons").assertTextContains("Learn 5 new subjects").performClick()
        assertEquals(true, started)
    }

    @Test fun teachingCardShowsMeaningReadingAndMnemonicWithoutMarkup() {
        var next = 0
        compose.setContent {
            MittsuTheme {
                LessonsScreen(LessonUiState(step = LessonStep.TEACHING, batch = listOf(sampleCard), cardIndex = 0), LessonActions(onNextCard = { next++ }))
            }
        }
        compose.onNodeWithTag("lesson-meaning").assertTextContains("Big")
        compose.onNodeWithTag("lesson-reading").assertTextContains("だい、たい")
        compose.onNodeWithText("A person stretching their arms out wide is big.").assertExists()
        compose.onNodeWithTag("lesson-next").assertTextContains("Start the quiz").performClick()
        assertEquals(1, next)
    }

    @Test fun notConnectedAsksToConnect() {
        compose.setContent { MittsuTheme { LessonsScreen(LessonUiState(step = LessonStep.NOT_CONNECTED), LessonActions()) } }
        compose.onNodeWithText("Connect WaniKani first").assertExists()
    }
}
