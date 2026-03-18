package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DgenPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    uppercase: Boolean = true,
    minHeight: Dp = 36.dp,
    textStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.sp,
    ),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.primaryContainer,
    disabledTextColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    val alpha = if (enabled) 1f else 0.4f
    val strokeColor = if (enabled) borderColor else disabledTextColor
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(RetroCardShape)
            .background(strokeColor.copy(alpha = 0.1f * alpha))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .drawBehind {
                drawPath(
                    retroPath(size.width, size.height),
                    strokeColor.copy(alpha = alpha),
                    style = Stroke(width = 2f),
                )
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (uppercase) text.uppercase() else text,
            color = if (enabled) containerColor else disabledTextColor,
            style = textStyle.copy(shadow = GlowStyle.button(if (enabled) containerColor else disabledTextColor)),
        )
    }
}
