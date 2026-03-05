package org.ethereumphone.andyclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatMessageItem(
    message: ChatUiMessage,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"
    val isSecurity = message.isSecurityBlock

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (isSecurity) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Security",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Safety Blocked" + if (message.toolName != null) " — ${message.toolName}" else "",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                shadow = GlowStyle.status(MaterialTheme.colorScheme.error),
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        text = message.content
                            .removePrefix("[Safety] ")
                            .replace(". Disable safety mode in Settings to bypass this check.", ".")
                            .replace(". Disable safety mode in Settings to allow this command.", "."),
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = GlowStyle.body(MaterialTheme.colorScheme.onErrorContainer),
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "You can disable safety mode in Settings to bypass this.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            shadow = GlowStyle.body(MaterialTheme.colorScheme.onErrorContainer),
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else if (isTool) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
            ) {
                Column {
                    Text(
                        text = message.toolName ?: "Tool",
                        style = MaterialTheme.typography.labelSmall.copy(
                            shadow = GlowStyle.subtitle(MaterialTheme.colorScheme.primary),
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = message.content.take(500),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            shadow = GlowStyle.body(MaterialTheme.colorScheme.onSurfaceVariant),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isUser) 0.8f else 0.9f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp,
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(12.dp),
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            shadow = GlowStyle.body(MaterialTheme.colorScheme.onPrimary),
                        ),
                    )
                } else {
                    MarkdownText(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
