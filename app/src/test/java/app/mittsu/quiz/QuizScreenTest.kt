package app.mittsu.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.domain.model.Difficulty
import app.mittsu.ui.theme.MittsuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class QuizScreenTest {
    @get:Rule val compose = createComposeRule()

    private val question = QuestionGenerator(TestSubjects.all, Random(4)).generate(TestSubjects.ichi, QuestionType.READING, Difficulty.NORMAL)
    private val asking = QuizUiState(phase = QuizPhase.Asking, question = question, questionNumber = 1, totalItems = 10)

    private fun show(initial: QuizUiState, onSelect: (Int) -> Unit = {}): () -> Unit {
        var state by mutableStateOf(initial)
        compose.setContent {
            MittsuTheme {
                QuizScreen(state, practiceMode(), onSelect = { i ->
                    onSelect(i)
                    state = state.copy(selected = i, explanation = Explanation.after(question, i))
                }, onContinue = {}, onFinishEarly = {}, onExit = {}, onRestart = {})
            }
        }
        return {}
    }

    @Test fun showsSubjectQuestionTypeAndExactlyThreeAnswers() {
        show(asking)
        compose.onNodeWithTag("quiz-characters").assertTextEquals("一")
        compose.onNodeWithTag("quiz-type").assertTextEquals("Choose the reading")
        compose.onNodeWithText("Kanji · Level 1").assertExists()
        assertEquals(3, compose.onAllNodes(SemanticsMatcher("answer card") { n ->
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("answer-") == true
        }).fetchSemanticsNodes().size)
        compose.onNodeWithTag("answer-0").assertExists()
        compose.onNodeWithTag("answer-2").assertExists()
        compose.onNodeWithTag("answer-3").assertDoesNotExist()
    }

    @Test fun neverShowsATextInput() {
        show(asking)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).assertCountEquals(0)
    }

    @Test fun wrongAnswerShowsFeedbackAndTheCorrectAnswer() {
        val wrong = (question.correctIndex + 1) % 3
        var picked = -1
        show(asking, onSelect = { picked = it })
        compose.onNodeWithTag("answer-$wrong").performScrollTo().performClick()
        assertEquals(wrong, picked)
        compose.onNodeWithTag("feedback-title").assertTextEquals("Not quite")
        compose.onNodeWithText("Answer: いち").assertExists()
        compose.onNodeWithTag("continue").assertExists()
    }

    @Test fun correctAnswerShowsCorrect() {
        show(asking)
        compose.onNodeWithTag("answer-${question.correctIndex}").performScrollTo().performClick()
        compose.onNodeWithTag("feedback-title").assertTextEquals("Correct")
    }

    @Test fun answersAreLockedAfterChoosing() {
        var clicks = 0
        show(asking, onSelect = { clicks++ })
        compose.onNodeWithTag("answer-0").performScrollTo().performClick()
        compose.onNodeWithTag("answer-1").performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test fun summaryShowsPracticeStatsSeparately() {
        show(QuizUiState(phase = QuizPhase.Finished, completedItems = 10, totalItems = 10,
            stats = SessionStats(answered = 22, correct = 19, meaningCorrect = 10, meaningIncorrect = 1, readingCorrect = 9, readingIncorrect = 2),
            durationMillis = 185_000))
        compose.onNodeWithText("Practice results stay on this phone and are never sent to WaniKani.").assertExists()
        compose.onNodeWithText("3m 5s").assertExists()
        compose.onNodeWithText("82%").assertExists() // reading 9/11
    }
}
