package org.ethereumphone.andyclaw.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

object AppTextStyles {

    fun sectionTitle(primaryColor: Color) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.sp,
        textDecoration = TextDecoration.None,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )

    fun contentTitle(primaryColor: Color) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )

    fun contentBody(primaryColor: Color) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )
}
