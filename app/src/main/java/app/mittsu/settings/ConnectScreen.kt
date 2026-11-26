package app.mittsu.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.mittsu.BuildConfig
import app.mittsu.ui.components.Kicker
import app.mittsu.ui.components.MittsuCard
import app.mittsu.ui.theme.MittsuTheme

/**
 * Account setup. This is the only place in the app with a text field: the token is pasted
 * once. Study sessions never show a keyboard.
 */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenTokenPage: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var reveal by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Kicker("WaniKani account")
        Text("Connect your account", style = MaterialTheme.typography.displaySmall)
        Text(
            "${BuildConfig.APP_NAME} uses a personal API token. It never asks for your WaniKani password.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.finished && state.connectedUser != null) {
            MittsuCard(Modifier.fillMaxWidth().testTag("connected-card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MittsuTheme.colors.correct)
                    Spacer(Modifier.width(10.dp))
                    Text("Connected as ${state.connectedUser.username}", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Level ${state.connectedUser.level} · ${subscriptionLabel(state.connectedUser.subscriptionType)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Go to today") }
            }
            return@Column
        }

        MittsuCard(Modifier.fillMaxWidth()) {
            Text("1. Create a token", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "On WaniKani, create a personal access token and tick these permissions:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text("•  reviews:create, to submit your reviews", style = MaterialTheme.typography.bodyMedium)
            Text("•  assignments:start, to finish lessons", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenTokenPage) {
                Text("Open WaniKani token settings")
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.padding(start = 8.dp).size(18.dp))
            }
        }

        MittsuCard(Modifier.fillMaxWidth()) {
            Text("2. Paste it here", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.token,
                onValueChange = onTokenChange,
                label = { Text("API token") },
                singleLine = true,
                enabled = !state.busy,
                isError = state.error != null,
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide token" else "Show token")
                    }
                },
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConnect() }),
                modifier = Modifier.fillMaxWidth().testTag("token-field"),
            )
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("connect-error"))
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onConnect,
                enabled = !state.busy && state.token.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("connect-button"),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(state.step ?: "Connecting")
                } else {
                    Text("Connect")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "The token is encrypted with a key kept in your phone's secure hardware and is never included in backups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun subscriptionLabel(type: String): String = when (type) {
    "lifetime" -> "Lifetime member"
    "recurring" -> "Subscriber"
    "free" -> "Free account"
    else -> "Membership unknown"
}
