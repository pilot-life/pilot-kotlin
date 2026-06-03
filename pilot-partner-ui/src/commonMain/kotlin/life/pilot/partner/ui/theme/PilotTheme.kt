package life.pilot.partner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * MaterialTheme wrapper with Pilot's default palette and the
 * partner-supplied [theme] overrides applied on top.
 *
 * For zero customization: call without arguments to get Pilot's
 * defaults. For brand colors, typography, shapes, or a forced
 * light/dark mode, build a [PartnerTheme] and pass it.
 *
 * Partners with a design system already in Compose can skip this and
 * wrap components in their own `MaterialTheme(...)` directly —
 * components only read from `MaterialTheme.colorScheme` /
 * `typography` / `shapes`, so they'll inherit your tokens.
 */
@Composable
fun PilotPartnerTheme(
    theme: PartnerTheme = PartnerTheme(),
    useDarkTheme: Boolean = theme.useDarkTheme ?: isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val baseScheme = if (useDarkTheme) DarkColors else LightColors
    val schemeOverrides = if (useDarkTheme) theme.dark else theme.light
    val mergedScheme = remember(schemeOverrides, useDarkTheme) {
        schemeOverrides.mergeInto(baseScheme)
    }
    val mergedTypography: Typography = remember(theme.typography) {
        theme.typography.mergeInto(Typography())
    }
    val mergedShapes: Shapes = remember(theme.shapes) {
        theme.shapes.mergeInto(Shapes())
    }
    MaterialTheme(
        colorScheme = mergedScheme,
        typography = mergedTypography,
        shapes = mergedShapes,
        content = content,
    )
}
