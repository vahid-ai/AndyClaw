package org.ethereumphone.andyclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onInputChanged: (String) -> Unit = {},
    clearInput: Boolean = false,
    onInputCleared: () -> Unit = {},
    backgroundColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(clearInput) {
        if (clearInput) {
            text = ""
            onInputChanged("")
            onInputCleared()
        }
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground

    Box(modifier = modifier.fillMaxWidth().background(backgroundColor).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (isStreaming) {
            Text(
                text = "[CANCEL]",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    shadow = GlowStyle.title(primaryColor),
                ),
                color = primaryColor,
                modifier = Modifier.clickable { onCancel() },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DgenCursorSearchTextfield(
                    value = text,
                    onValueChange = {
                        text = it
                        onInputChanged(it)
                    },
                    modifier = Modifier.weight(1f),
                    keyboardtype = KeyboardType.Text,
                    placeholder = {
                        Text(
                            text = "Type a message...",
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

                if (text.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onSend(text)
                            text = ""
                        },
                        modifier = Modifier.size(32.dp).padding(start = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = primaryColor,
                        )
                    }
                }
            }
        }
    }
}
