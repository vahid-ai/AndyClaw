package org.ethereumphone.andyclaw.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun ChadBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    var matrixChars by remember { mutableStateOf(List(15) { generateMatrixChar() }) }

    val infiniteTransition = rememberInfiniteTransition(label = "chad_bg")

    val scanlineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "scanlines",
    )

    val phosphorGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "phosphor_glow",
    )

    val matrixAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "matrix_alpha",
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(200, 800))
            val index = Random.nextInt(matrixChars.size)
            matrixChars = matrixChars.toMutableList().also { it[index] = generateMatrixChar() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawWithContent {
                drawContent()
                drawScanlines(scanlineOffset, size.height, primaryColor, alpha = 0.07f)
                drawRect(color = primaryColor.copy(alpha = 0.05f * phosphorGlow), size = size)
            },
    ) {
        MatrixRainBackground(
            chars = matrixChars,
            alpha = matrixAlpha,
            modifier = Modifier.fillMaxSize(),
        )

        GridOverlay(
            primaryColor = primaryColor,
            modifier = Modifier.fillMaxSize(),
        )

        content()
    }
}

@Composable
private fun MatrixRainBackground(
    chars: List<String>,
    alpha: Float,
    modifier: Modifier = Modifier,
    density: Float = 0.08f,
    radius: Float = 2f,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val charSize = 20.dp.toPx()
        val columns = (size.width / charSize).toInt()
        val rows = (size.height / charSize).toInt()
        for (col in 0 until columns) {
            for (row in 0 until rows) {
                if (Random.nextFloat() < density) {
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha * 0.4f),
                        radius = radius,
                        center = Offset(col * charSize, row * charSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun GridOverlay(
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val gridLineColor = primaryColor.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        val gridSize = 50.dp.toPx()
        val horizontalLines = (size.height / gridSize).toInt()
        val verticalLines = (size.width / gridSize).toInt()

        for (i in 0..horizontalLines) {
            drawLine(gridLineColor, Offset(0f, i * gridSize), Offset(size.width, i * gridSize), strokeWidth = 1f)
        }
        for (i in 0..verticalLines) {
            drawLine(gridLineColor, Offset(i * gridSize, 0f), Offset(i * gridSize, size.height), strokeWidth = 1f)
        }
    }
}

private fun DrawScope.drawScanlines(offset: Float, height: Float, primaryColor: Color, alpha: Float = 0.05f) {
    val lineSpacing = 4.dp.toPx()
    val numLines = (height / lineSpacing).toInt()
    for (i in 0..numLines) {
        val y = (i * lineSpacing + offset * height) % height
        drawLine(
            color = primaryColor.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

private fun generateMatrixChar(): String {
    val chars = "0123456789ABCDEF"
    return chars.random().toString()
}
