package org.ethereumphone.andyclaw.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

/**
 * Centralised glow presets for the retro-cyberpunk UI.
 *
 * Each function returns a [Shadow] that can be passed directly to a
 * `TextStyle.shadow` or `Typography.copy(shadow = …)` call.
 *
 * Native-canvas glow (BlurMaskFilter for icons / cursors) is intentionally
 * left at the call-site because it requires dp→px conversion at draw-time.
 */
object GlowStyle {

    /** Prominent glow for titles, headings, action labels, and dialog titles. */
    fun title(color: Color) = Shadow(
        color = color.copy(alpha = 0.8f),
        offset = Offset.Zero,
        blurRadius = 12f,
    )

    /** Subtle glow for step counters, badges, and secondary section labels. */
    fun subtitle(color: Color) = Shadow(
        color = color.copy(alpha = 0.6f),
        offset = Offset.Zero,
        blurRadius = 8f,
    )

    /** Glow for user-typed input text and search content. */
    fun body(color: Color) = Shadow(
        color = color.copy(alpha = 0.6f),
        offset = Offset.Zero,
        blurRadius = 16f,
    )

    /** Pulsing glow for processing states and status indicators. */
    fun status(color: Color) = Shadow(
        color = color.copy(alpha = 0.7f),
        offset = Offset.Zero,
        blurRadius = 16f,
    )

    /** Faint glow for placeholder / hint text. */
    fun placeholder(color: Color) = Shadow(
        color = color.copy(alpha = 0.3f),
        offset = Offset.Zero,
        blurRadius = 16f,
    )

    /** Soft glow for generic text-field input text. */
    fun textfield(color: Color) = Shadow(
        color = color.copy(alpha = 0.4f),
        offset = Offset.Zero,
        blurRadius = 12f,
    )

    /** Accent glow for primary buttons. */
    fun button(color: Color) = Shadow(
        color = color.copy(alpha = 0.6f),
        offset = Offset.Zero,
        blurRadius = 4f,
    )
}
