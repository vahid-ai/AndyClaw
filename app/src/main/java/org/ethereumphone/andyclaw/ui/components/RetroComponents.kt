package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val RetroCardShape = GenericShape { size, _ ->
    moveTo(0f, size.height * 0.1f)
    lineTo(size.width * 0.1f, 0f)
    lineTo(size.width * 0.9f, 0f)
    lineTo(size.width, size.height * 0.1f)
    lineTo(size.width, size.height * 0.9f)
    lineTo(size.width * 0.9f, size.height)
    lineTo(size.width * 0.1f, size.height)
    lineTo(0f, size.height * 0.9f)
    close()
}

private fun retroPath(width: Float, height: Float) = Path().apply {
    moveTo(0f, height * 0.1f)
    lineTo(width * 0.1f, 0f)
    lineTo(width * 0.9f, 0f)
    lineTo(width, height * 0.1f)
    lineTo(width, height * 0.9f)
    lineTo(width * 0.9f, height)
    lineTo(width * 0.1f, height)
    lineTo(0f, height * 0.9f)
    close()
}

@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RetroCardShape)
            .background(primaryColor.copy(alpha = 0.1f))
            .drawBehind {
                drawPath(retroPath(size.width, size.height), primaryColor, style = Stroke(width = 2f))
            }
            .border(2.dp, primaryColor.copy(alpha = 0.8f), RetroCardShape)
            .padding(2.dp),
    ) {
        Box(modifier = Modifier.matchParentSize().padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun RetroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .clip(RetroCardShape)
            .background(primaryColor.copy(alpha = 0.1f * alpha))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                drawPath(retroPath(size.width, size.height), primaryColor.copy(alpha = alpha), style = Stroke(width = 2f))
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RetroCardShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .drawBehind {
                drawPath(retroPath(size.width, size.height), primaryColor, style = Stroke(width = 2f))
            }
            .border(1.dp, primaryColor.copy(alpha = 0.8f), RetroCardShape)
            .padding(12.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = primaryColor.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = GlowStyle.placeholder(primaryColor)),
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = primaryColor, shadow = GlowStyle.textfield(primaryColor)),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                cursorColor = primaryColor,
            ),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ChadAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmButtonText: String = "CONFIRM",
    dismissButtonText: String = "CANCEL",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(2.dp, primaryColor, RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRect(color = primaryColor.copy(alpha = 0.05f), size = size)
                },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            ) {
                Text(
                    text = title,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = primaryColor,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = GlowStyle.title(primaryColor),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(primaryColor.copy(alpha = 0.3f)))
                Spacer(Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f, fill = false).verticalScroll(scrollState)) {
                    Text(
                        text = message,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = primaryColor.copy(alpha = 0.9f),
                        lineHeight = 20.sp,
                        style = MaterialTheme.typography.bodySmall.copy(shadow = GlowStyle.body(primaryColor)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(primaryColor.copy(alpha = 0.3f)))
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChadAlertButton(text = confirmButtonText, onClick = onConfirm, isPrimary = true, modifier = Modifier.fillMaxWidth())
                    ChadAlertButton(text = dismissButtonText, onClick = onDismiss, isPrimary = false, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ChadAlertButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = if (isPrimary) primaryColor.copy(alpha = 0.2f) else Color.Transparent
    val borderAlpha = if (isPrimary) 1f else 0.5f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(
                width = if (isPrimary) 2.dp else 1.dp,
                color = primaryColor.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            color = primaryColor.copy(alpha = if (isPrimary) 1f else 0.8f),
            textAlign = TextAlign.Center,
            style = if (isPrimary) {
                MaterialTheme.typography.bodyMedium.copy(
                    shadow = GlowStyle.button(primaryColor),
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}
