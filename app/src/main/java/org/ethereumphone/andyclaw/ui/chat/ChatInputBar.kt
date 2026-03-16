package org.ethereumphone.andyclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ui.theme.dgenWhite
import org.ethereumphone.andyclaw.ui.DgenCursorSearchTextfield
import org.ethereumphone.andyclaw.ui.components.GlowStyle

@Composable
fun ChatInputBar(
    isStreaming: Boolean,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val streamingColor = Color(0xFFFF9800)
    val buttonColor = if (isStreaming) streamingColor else primaryColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
    ) {
        DgenCursorSearchTextfield(
            value = text,
            onValueChange = { newText ->
                // Intercept Enter key: if a newline was just added, treat it as submit
                if (newText.endsWith("\n") && !text.endsWith("\n")) {
                    val trimmed = newText.trimEnd('\n')
                    if (trimmed.isNotBlank() && !isStreaming) {
                        onSend(trimmed)
                        text = ""
                    }
                } else {
                    text = newText
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 52.dp), // leave room for the button
            keyboardtype = KeyboardType.Text,
            placeholder = {
                Text(
                    text = if (isStreaming) "Thinking..." else "Testing...",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = dgenWhite.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Normal,
                        fontSize = 20.sp,
                        shadow = GlowStyle.placeholder(dgenWhite),
                    ),
                )
            },
            leadingContent = {
                Text(
                    text = "> ",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = textColor,
                        shadow = GlowStyle.body(textColor),
                    ),
                )
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
                color = dgenWhite,
                shadow = GlowStyle.body(dgenWhite),
            ),
            cursorColor = dgenWhite,
            cursorWidth = 10.dp,
            cursorHeight = 20.dp,
            maxFieldHeight = 120.dp,
            singleLine = false,
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable {
                    if (isStreaming) {
                        onCancel()
                    } else if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isStreaming) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isStreaming) "Cancel" else "Send",
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
