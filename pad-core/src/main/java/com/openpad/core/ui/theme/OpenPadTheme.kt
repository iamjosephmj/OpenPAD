package com.openpad.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.openpad.core.OpenPad

/**
 * Dynamic color palette that reads from [OpenPad.theme] at access time.
 *
 * Every UI composable references these properties. When an integrator sets
 * `OpenPad.theme = OpenPadThemeConfig(...)` before launching the SDK,
 * all colors automatically reflect the custom values.
 */
internal object PadColors {
    val Primary: Color get() = Color(OpenPad.theme.primary)
    val PrimaryLight: Color get() = Color(OpenPad.theme.primaryLight)
    val Success: Color get() = Color(OpenPad.theme.success)
    val Error: Color get() = Color(OpenPad.theme.error)
    val Surface: Color get() = Color(OpenPad.theme.surface)
    val SurfaceVariant: Color get() = Color(OpenPad.theme.surfaceVariant)
    val OnSurface: Color get() = Color(OpenPad.theme.onSurface)
    val OnSurfaceHigh: Color get() = Color(OpenPad.theme.onSurfaceHigh)
    val Scrim: Color get() = Color(OpenPad.theme.scrim)
    val FrostGlass: Color get() = Color(OpenPad.theme.frostGlass)
    val OvalIdle: Color get() = Color(OpenPad.theme.ovalIdle)
    val Divider: Color get() = Color(OpenPad.theme.divider)
}

@Composable
internal fun OpenPadTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = PadColors.Primary,
        secondary = PadColors.PrimaryLight,
        tertiary = PadColors.Success,
        error = PadColors.Error,
        background = PadColors.Surface,
        surface = PadColors.Surface,
        surfaceVariant = PadColors.SurfaceVariant,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = PadColors.OnSurface,
        onSurface = PadColors.OnSurface,
        onError = Color.White
    )

    val typography = Typography(
        headlineLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.5).sp,
            color = PadColors.OnSurfaceHigh
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            color = PadColors.OnSurfaceHigh
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = PadColors.OnSurfaceHigh
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = PadColors.OnSurface
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = PadColors.OnSurface
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = Color.White
        )
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
