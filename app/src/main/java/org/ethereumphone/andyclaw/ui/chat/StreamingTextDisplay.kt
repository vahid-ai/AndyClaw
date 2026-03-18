package org.ethereumphone.andyclaw.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StreamingTextDisplay(
    text: String,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (text.isNotEmpty()) {
            SelectionContainer {
                MarkdownText(
                    text = text + "\u2588",
                    color = primaryColor,
                )
            }
        } else {
            Text(
                text = "Processing...",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    shadow = GlowStyle.status(primaryColor),
                ),
                color = primaryColor,
            )
        }
    }
}
