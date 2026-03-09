package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun GlitchEffect(
    modifier: Modifier = Modifier,
    intensity: Float = 0.5f,
    content: @Composable () -> Unit,
) {
    var glitchOffset by remember { mutableStateOf(0f) }
    var isGlitching by remember { mutableStateOf(false) }
    val themeColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(800, 2000))
            isGlitching = true
            repeat(5) {
                glitchOffset = Random.nextFloat() * 12f * intensity
                delay(40)
                glitchOffset = -Random.nextFloat() * 12f * intensity
                delay(40)
            }
            glitchOffset = 0f
            isGlitching = false
        }
    }

    Box(
        modifier = modifier.drawWithContent {
            this@drawWithContent.drawContent()
            if (isGlitching) {
                drawGlitchLines(intensity, themeColor)
                translate(left = glitchOffset, top = 0f) {
                    this@drawWithContent.drawContent()
                }
            }
        },
    ) {
        content()
    }
}

private fun DrawScope.drawGlitchLines(intensity: Float, themeColor: Color) {
    val numLines = (8 * intensity).toInt()
    val lineColor = themeColor.copy(alpha = 0.4f)

    repeat(numLines) {
        val y = Random.nextFloat() * size.height
        val width = Random.nextFloat() * 60f * intensity
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y + width),
            strokeWidth = Random.nextFloat() * 4f * intensity,
        )
    }
}
