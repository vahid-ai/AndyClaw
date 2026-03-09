package org.ethereumphone.andyclaw.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenWhite

@Composable
fun DgenSmallPrimaryButton(
    text: String,
    primaryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
) {
    val alpha = if (enabled) 1f else 0.4f
    val shape = RoundedCornerShape(4.dp)
    val view = LocalView.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(primaryColor.copy(alpha = 0.1f * alpha))
            .border(width = 1.dp, color = primaryColor.copy(alpha = alpha), shape = shape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = if (enabled) primaryColor else primaryColor.copy(alpha = 0.4f),
            style = TextStyle(
                fontFamily = SpaceMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.sp,
                shadow = GlowStyle.button(primaryColor),
            ),
        )
    }
}
