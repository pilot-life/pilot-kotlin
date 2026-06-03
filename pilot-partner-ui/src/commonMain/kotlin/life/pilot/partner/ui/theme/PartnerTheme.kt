package life.pilot.partner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-coverage theming for the partner UI. Pass to [PilotPartnerTheme]
 * on Android (Kotlin) or to `PilotPartnerUi.shared.eventsScreen(theme:)`
 * on iOS (Swift). Every field is nullable / defaulted to "use Pilot's
 * default," so a partner only sets the slots they care about.
 *
 * Coverage:
 *   - 32 Material 3 color tokens for [light] and 32 for [dark]
 *   - All 15 Material 3 text styles via [typography]
 *   - All 3 shape sizes via [shapes]
 *   - [useDarkTheme] override (null = follow system on Android, follow
 *     `UITraitCollection.userInterfaceStyle` on iOS)
 *
 * Color values are passed as `Long` holding the 32-bit ARGB packed
 * pattern (e.g. `0xFF0A66C2` for opaque LinkedIn-blue). Use `Long?` so
 * Swift can pass `KotlinLong(value:)` or `nil` for "inherit default."
 *
 * Bespoke fonts: this config only covers per-style metrics (size,
 * weight, line height, letter spacing). To override the font *family*
 * (a custom brand typeface), wrap the components in your own
 * `MaterialTheme(typography = …)` on Android. Cross-platform font
 * loading from Swift requires registering the TTF in your iOS app
 * bundle and is out of scope for this config — file a request if you
 * need a Compose-Multiplatform font-resource pathway.
 */
data class PartnerTheme(
    val light: PartnerColorScheme = PartnerColorScheme(),
    val dark: PartnerColorScheme = PartnerColorScheme(),
    val typography: PartnerTypography = PartnerTypography(),
    val shapes: PartnerShapes = PartnerShapes(),
    /** `null` = follow system; `true`/`false` = force light or dark. */
    val useDarkTheme: Boolean? = null,
)

/**
 * All 32 Material 3 color tokens. `null` means "inherit Pilot's
 * default for this slot." Values are 32-bit ARGB longs
 * (e.g. `0xFFE74C3C` for an opaque red).
 */
data class PartnerColorScheme(
    val primary: Long? = null,
    val onPrimary: Long? = null,
    val primaryContainer: Long? = null,
    val onPrimaryContainer: Long? = null,
    val inversePrimary: Long? = null,
    val secondary: Long? = null,
    val onSecondary: Long? = null,
    val secondaryContainer: Long? = null,
    val onSecondaryContainer: Long? = null,
    val tertiary: Long? = null,
    val onTertiary: Long? = null,
    val tertiaryContainer: Long? = null,
    val onTertiaryContainer: Long? = null,
    val background: Long? = null,
    val onBackground: Long? = null,
    val surface: Long? = null,
    val onSurface: Long? = null,
    val surfaceVariant: Long? = null,
    val onSurfaceVariant: Long? = null,
    val surfaceTint: Long? = null,
    val inverseSurface: Long? = null,
    val inverseOnSurface: Long? = null,
    val error: Long? = null,
    val onError: Long? = null,
    val errorContainer: Long? = null,
    val onErrorContainer: Long? = null,
    val outline: Long? = null,
    val outlineVariant: Long? = null,
    val scrim: Long? = null,
    val surfaceBright: Long? = null,
    val surfaceDim: Long? = null,
    val surfaceContainerLowest: Long? = null,
    val surfaceContainerLow: Long? = null,
    val surfaceContainer: Long? = null,
    val surfaceContainerHigh: Long? = null,
    val surfaceContainerHighest: Long? = null,
)

/**
 * Per-style typography overrides. `null` means "inherit Material 3
 * default for this style." See [PartnerTextStyle] for what you can
 * customize per style.
 */
data class PartnerTypography(
    val displayLarge: PartnerTextStyle? = null,
    val displayMedium: PartnerTextStyle? = null,
    val displaySmall: PartnerTextStyle? = null,
    val headlineLarge: PartnerTextStyle? = null,
    val headlineMedium: PartnerTextStyle? = null,
    val headlineSmall: PartnerTextStyle? = null,
    val titleLarge: PartnerTextStyle? = null,
    val titleMedium: PartnerTextStyle? = null,
    val titleSmall: PartnerTextStyle? = null,
    val bodyLarge: PartnerTextStyle? = null,
    val bodyMedium: PartnerTextStyle? = null,
    val bodySmall: PartnerTextStyle? = null,
    val labelLarge: PartnerTextStyle? = null,
    val labelMedium: PartnerTextStyle? = null,
    val labelSmall: PartnerTextStyle? = null,
)

/**
 * Per-style text metrics. All fields nullable so partners can override
 * one slot (e.g. just `fontWeight`) without restating the others.
 *
 * - [fontSizeSp] / [lineHeightSp] / [letterSpacingSp] in sp
 * - [fontWeight] is a 100..900 integer (Material 3 uses 400 = Regular,
 *   500 = Medium, 700 = Bold, etc.)
 */
data class PartnerTextStyle(
    val fontSizeSp: Float? = null,
    val fontWeight: Int? = null,
    val lineHeightSp: Float? = null,
    val letterSpacingSp: Float? = null,
)

/**
 * Corner radii (in dp) for the three Material 3 shape buckets. `null`
 * = inherit default (4dp small, 12dp medium, 16dp large).
 */
data class PartnerShapes(
    val smallCornerDp: Float? = null,
    val mediumCornerDp: Float? = null,
    val largeCornerDp: Float? = null,
)

// ─── internal helpers: merge into Material objects ────────────────────

/** ARGB Long → Compose [Color]. Mask to 32 bits in case Swift sign-extended. */
private fun Long.toComposeColor(): Color = Color((this and 0xFFFFFFFFL).toULong())

internal fun PartnerColorScheme.mergeInto(base: ColorScheme): ColorScheme = base.copy(
    primary = primary?.toComposeColor() ?: base.primary,
    onPrimary = onPrimary?.toComposeColor() ?: base.onPrimary,
    primaryContainer = primaryContainer?.toComposeColor() ?: base.primaryContainer,
    onPrimaryContainer = onPrimaryContainer?.toComposeColor() ?: base.onPrimaryContainer,
    inversePrimary = inversePrimary?.toComposeColor() ?: base.inversePrimary,
    secondary = secondary?.toComposeColor() ?: base.secondary,
    onSecondary = onSecondary?.toComposeColor() ?: base.onSecondary,
    secondaryContainer = secondaryContainer?.toComposeColor() ?: base.secondaryContainer,
    onSecondaryContainer = onSecondaryContainer?.toComposeColor() ?: base.onSecondaryContainer,
    tertiary = tertiary?.toComposeColor() ?: base.tertiary,
    onTertiary = onTertiary?.toComposeColor() ?: base.onTertiary,
    tertiaryContainer = tertiaryContainer?.toComposeColor() ?: base.tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer?.toComposeColor() ?: base.onTertiaryContainer,
    background = background?.toComposeColor() ?: base.background,
    onBackground = onBackground?.toComposeColor() ?: base.onBackground,
    surface = surface?.toComposeColor() ?: base.surface,
    onSurface = onSurface?.toComposeColor() ?: base.onSurface,
    surfaceVariant = surfaceVariant?.toComposeColor() ?: base.surfaceVariant,
    onSurfaceVariant = onSurfaceVariant?.toComposeColor() ?: base.onSurfaceVariant,
    surfaceTint = surfaceTint?.toComposeColor() ?: base.surfaceTint,
    inverseSurface = inverseSurface?.toComposeColor() ?: base.inverseSurface,
    inverseOnSurface = inverseOnSurface?.toComposeColor() ?: base.inverseOnSurface,
    error = error?.toComposeColor() ?: base.error,
    onError = onError?.toComposeColor() ?: base.onError,
    errorContainer = errorContainer?.toComposeColor() ?: base.errorContainer,
    onErrorContainer = onErrorContainer?.toComposeColor() ?: base.onErrorContainer,
    outline = outline?.toComposeColor() ?: base.outline,
    outlineVariant = outlineVariant?.toComposeColor() ?: base.outlineVariant,
    scrim = scrim?.toComposeColor() ?: base.scrim,
    surfaceBright = surfaceBright?.toComposeColor() ?: base.surfaceBright,
    surfaceDim = surfaceDim?.toComposeColor() ?: base.surfaceDim,
    surfaceContainerLowest = surfaceContainerLowest?.toComposeColor() ?: base.surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow?.toComposeColor() ?: base.surfaceContainerLow,
    surfaceContainer = surfaceContainer?.toComposeColor() ?: base.surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh?.toComposeColor() ?: base.surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest?.toComposeColor() ?: base.surfaceContainerHighest,
)

private fun PartnerTextStyle.mergeInto(base: TextStyle): TextStyle = base.copy(
    fontSize = fontSizeSp?.sp ?: base.fontSize,
    fontWeight = fontWeight?.let { FontWeight(it) } ?: base.fontWeight,
    lineHeight = lineHeightSp?.sp ?: base.lineHeight,
    letterSpacing = letterSpacingSp?.sp ?: base.letterSpacing,
)

internal fun PartnerTypography.mergeInto(base: Typography): Typography = Typography(
    displayLarge = displayLarge?.mergeInto(base.displayLarge) ?: base.displayLarge,
    displayMedium = displayMedium?.mergeInto(base.displayMedium) ?: base.displayMedium,
    displaySmall = displaySmall?.mergeInto(base.displaySmall) ?: base.displaySmall,
    headlineLarge = headlineLarge?.mergeInto(base.headlineLarge) ?: base.headlineLarge,
    headlineMedium = headlineMedium?.mergeInto(base.headlineMedium) ?: base.headlineMedium,
    headlineSmall = headlineSmall?.mergeInto(base.headlineSmall) ?: base.headlineSmall,
    titleLarge = titleLarge?.mergeInto(base.titleLarge) ?: base.titleLarge,
    titleMedium = titleMedium?.mergeInto(base.titleMedium) ?: base.titleMedium,
    titleSmall = titleSmall?.mergeInto(base.titleSmall) ?: base.titleSmall,
    bodyLarge = bodyLarge?.mergeInto(base.bodyLarge) ?: base.bodyLarge,
    bodyMedium = bodyMedium?.mergeInto(base.bodyMedium) ?: base.bodyMedium,
    bodySmall = bodySmall?.mergeInto(base.bodySmall) ?: base.bodySmall,
    labelLarge = labelLarge?.mergeInto(base.labelLarge) ?: base.labelLarge,
    labelMedium = labelMedium?.mergeInto(base.labelMedium) ?: base.labelMedium,
    labelSmall = labelSmall?.mergeInto(base.labelSmall) ?: base.labelSmall,
)

internal fun PartnerShapes.mergeInto(base: Shapes): Shapes = base.copy(
    small = smallCornerDp?.let { RoundedCornerShape(it.dp) } ?: base.small,
    medium = mediumCornerDp?.let { RoundedCornerShape(it.dp) } ?: base.medium,
    large = largeCornerDp?.let { RoundedCornerShape(it.dp) } ?: base.large,
)
