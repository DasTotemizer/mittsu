package app.mittsu.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mittsu.domain.model.WaniKaniUser
import app.mittsu.ui.theme.MittsuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConnectScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: ConnectUiState, onConnect: () -> Unit = {}) = compose.setContent {
        MittsuTheme { ConnectScreen(state, {}, onConnect, {}, {}, {}) }
    }

    @Test fun connectDisabledUntilTokenEntered() {
        show(ConnectUiState())
        compose.onNodeWithTag("connect-button").performScrollTo().assertIsNotEnabled()
    }

    @Test fun connectEnabledWithToken() {
        var clicks = 0
        show(ConnectUiState(token = "abc"), onConnect = { clicks++ })
        compose.onNodeWithTag("connect-button").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test fun tokenIsMaskedByDefault() {
        show(ConnectUiState(token = "00000000-1111-2222-3333-444444444444"))
        compose.onNodeWithTag("token-field").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test fun showsErrorAndConnectedAccount() {
        show(ConnectUiState(token = "x", error = "WaniKani did not accept this API token."))
        compose.onNodeWithText("did not accept", substring = true).assertExists()
    }

    @Test fun showsConnectedUser() {
        show(
            ConnectUiState(
                finished = true,
                connectedUser = WaniKaniUser("testlearner", 14, 60, "lifetime", true, null, null, null),
            ),
        )
        compose.onNodeWithTag("connected-card").assertExists()
        compose.onNodeWithText("Connected as testlearner").assertExists()
        compose.onNodeWithText("Level 14 · Lifetime member").assertExists()
    }
}
