package org.ethereumphone.andyclaw.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Compatibility aliases for values previously imported from the dgen component library.
 * These map old dgen names to Material 3 equivalents used by the new theme.
 */

// Color aliases
val dgenWhite = Blue40
val dgenBlack = Color.Black
val dgenRed = ErrorRed
val dgenGreen = SuccessGreen
val dgenOcean = Blue40
val oceanAbyss = Blue20

// Threat level color aliases
val terminalCore = Color(0xFF4CAF50)    // Green text for low threat
val terminalHack = Color(0xFF4CAF50).copy(alpha = 0.14f) // Green bg for low threat
val orcheCore = Color(0xFFFFA000)       // Amber text
val orcheAsh = Color(0xFFFFA000).copy(alpha = 0.14f)     // Amber bg
val lazerCore = Color(0xFFD50000)       // Red text for critical threat
val lazerBurn = Color(0xFFD50000).copy(alpha = 0.14f)    // Red bg for critical threat
val dgenTurqoise = Blue50               // Turquoise accent

// Font aliases
val SpaceMono = FontFamily.SansSerif
val PitagonsSans = FontFamily.SansSerif

// Font size aliases
val body1_fontSize = 16.sp
val body2_fontSize = 14.sp
val button_fontSize = 14.sp
val label_fontSize = 12.sp
val smalllabel_fontSize = 10.sp

// Animation/opacity aliases
val pulseOpacity = 0.6f
val neonOpacity = 0.85f
