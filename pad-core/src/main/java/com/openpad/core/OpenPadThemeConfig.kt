package com.openpad.core

/**
 * Fully customizable theme for the OpenPad SDK UI.
 *
 * Set via [OpenPad.theme] before calling [OpenPad.analyze]. All color values
 * are ARGB hex longs (e.g. `0xFF2962FF`). This keeps the public API free of
 * Compose dependencies so headless integrators are not forced to pull in the
 * UI toolkit.
 *
 * Example:
 * ```
 * OpenPad.theme = OpenPadThemeConfig(
 *     primary = 0xFF1565C0,
 *     success = 0xFF2E7D32,
 *     surface = 0xFF121212
 * )
 * ```
 */
data class OpenPadThemeConfig(
    /** Primary action color (buttons, progress arcs, active indicators). */
    val primary: Long = 0xFF2962FF,

    /** Lighter primary variant (secondary highlights, step dots). */
    val primaryLight: Long = 0xFF448AFF,

    /** Success / live-confirmed accent. */
    val success: Long = 0xFF00C853,

    /** Error / spoof-detected accent. */
    val error: Long = 0xFFD32F2F,

    /** Background surface color (dark base). */
    val surface: Long = 0xFF0D1B2A,

    /** Elevated surface (cards, frosted glass panels). */
    val surfaceVariant: Long = 0xFF1B2838,

    /** Default text / icon color on surfaces. */
    val onSurface: Long = 0xFFE0E6ED,

    /** High-emphasis text color (titles, headings). */
    val onSurfaceHigh: Long = 0xFFFFFFFF,

    /** Scrim color drawn outside the face oval. */
    val scrim: Long = 0xFF0D1B2A,

    /** Frosted-glass panel background (top bar, instruction pill). */
    val frostGlass: Long = 0xFF1B2838,

    /** Idle oval border color before status changes. */
    val ovalIdle: Long = 0xFFE0E6ED,

    /** Divider / separator lines. */
    val divider: Long = 0xFF2A3A4E
) {
    companion object {
        /** Default corporate-trust theme (dark navy + trust blue). */
        val Default = OpenPadThemeConfig()
    }
}
