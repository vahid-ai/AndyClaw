package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenOcean
import com.example.dgenlibrary.ui.theme.dgenTurqoise
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.oceanAbyss

@Composable
fun DgenPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    uppercase: Boolean = true,
    cornerRadius: Dp = 2.dp,
    borderWidth: Dp = 1.dp,
    minHeight: Dp = 36.dp,
    textStyle: TextStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.sp,
    ),
    backgroundColor: Color = oceanAbyss,
    containerColor: Color = dgenOcean,
    disabledContainerColor: Color = Color.Transparent,
    borderColor: Color = backgroundColor,
    disabledBorderColor: Color = dgenWhite.copy(alpha = 0.4f),
    disabledTextColor: Color = dgenWhite.copy(alpha = 0.4f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .background(
                color = if (enabled) containerColor else disabledContainerColor,
                shape = shape,
            )
            .border(
                width = borderWidth,
                color = if (enabled) borderColor else disabledBorderColor,
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
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
