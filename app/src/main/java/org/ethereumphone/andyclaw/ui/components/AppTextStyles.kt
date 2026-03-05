package org.ethereumphone.andyclaw.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.label_fontSize

object AppTextStyles {

    fun sectionTitle(primaryColor: Color) = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textDecoration = TextDecoration.None,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )

    fun contentTitle(primaryColor: Color) = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )

    fun contentBody(primaryColor: Color) = TextStyle(
        fontFamily = PitagonsSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )
}
