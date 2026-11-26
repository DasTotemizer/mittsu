package app.mittsu.holiday

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mittsu.ui.components.PhasePlaceholder
import app.mittsu.ui.theme.MittsuTheme

@Composable
fun HolidayScreen(onOpenVacationSettings: () -> Unit, onBack: () -> Unit) {
    PhasePlaceholder(
        glyph = "休",
        title = "Enjoy your break",
        body = "Your Japanese learning progress will be here when you return. Holiday scheduling and reminder pausing arrive in a later phase.",
        phase = 7,
        accent = MittsuTheme.colors.holiday,
        accentContainer = MittsuTheme.colors.holidayContainer,
    ) {
        Button(
            onClick = onOpenVacationSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("vacation-link"),
            colors = ButtonDefaults.buttonColors(containerColor = MittsuTheme.colors.holiday),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Manage WaniKani Vacation Mode")
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Vacation Mode is switched on and off on WaniKani's own website. Opening this link does not change it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) { Text("Back to today") }
    }
}

