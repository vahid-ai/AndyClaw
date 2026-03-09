package org.ethereumphone.andyclaw.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dgenlibrary.ui.theme.dgenWhite

@Composable
fun DgenSquareSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = dgenWhite.copy(alpha = 0.5f),
    disabledColor: Color = dgenWhite.copy(alpha = 0.25f),
    trackWidth: Dp = 52.dp,
    trackHeight: Dp = 28.dp,
    thumbSize: Dp = 20.dp,
) {
    val horizontalPadding = 4.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - horizontalPadding else horizontalPadding,
        animationSpec = tween(durationMillis = 120),
        label = "square_switch_thumb_offset",
    )

    val trackColor = when {
        !enabled -> disabledColor
        checked -> activeColor
        else -> inactiveColor
    }

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .alpha(if (enabled) 1f else 0.7f)
            .background(
                color = trackColor.copy(alpha = if (checked) 0.28f else 0.12f),
                shape = RoundedCornerShape(2.dp),
            )
            .border(width = 1.dp, color = trackColor, shape = RoundedCornerShape(2.dp))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .background(color = trackColor, shape = RoundedCornerShape(2.dp)),
        )
    }
}
