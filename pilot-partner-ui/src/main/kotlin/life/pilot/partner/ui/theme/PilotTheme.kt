package life.pilot.partner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PilotPrimary = Color(0xFF1B4D3E)
private val PilotPrimaryDark = Color(0xFFA7E2D0)
private val PilotSecondary = Color(0xFF8F4F2A)
private val PilotAccent = Color(0xFFE7B788)

private val LightColors = lightColorScheme(
    primary = PilotPrimary,
    secondary = PilotSecondary,
    tertiary = PilotAccent,
)

private val DarkColors = darkColorScheme(
    primary = PilotPrimaryDark,
    secondary = PilotAccent,
    tertiary = PilotAccent,
)

/**
 * MaterialTheme wrapper with Pilot's default palette. Partners can omit
 * this and use their own MaterialTheme — components only depend on
 * `MaterialTheme.colorScheme` / `typography`.
 */
@Composable
fun PilotPartnerTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}
