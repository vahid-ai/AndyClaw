package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ui.headers.NavigationHeaderBar
import org.ethereumphone.andyclaw.ui.theme.SpaceMono
import org.ethereumphone.andyclaw.ui.theme.dgenGreen
import org.ethereumphone.andyclaw.R

@Composable
fun NavigationHeaderBar(
    modifier: Modifier = Modifier,
    text: String = "",
    content: @Composable (() -> Unit)? = null,
    primaryColor: Color,
    onClick: () -> Unit = {}
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceDelay = 500L

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            modifier = Modifier.size(40.dp),
            onClick = {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime >= debounceDelay) {
                    lastClickTime = currentTime
                    onClick()
                }
            }
        ) {
            Icon(
                modifier = Modifier.offset(x = (-8.dp)).size(24.dp),
                painter = painterResource(R.drawable.back_icon),
                contentDescription = "Back",
                tint = primaryColor
            )
        }

        when {
            content != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    content()
                }
            }
            else -> {
                Text(
                    text = text.uppercase(),
                    style = TextStyle(
                        fontFamily = SpaceMono,
                        color = primaryColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        letterSpacing = 0.sp,
                        textDecoration = TextDecoration.None,
                        shadow = GlowStyle.title(primaryColor),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF050505)
@Composable
private fun NavigationHeaderBarTextPreview() {
    NavigationHeaderBar(
        text = "Settings",
        primaryColor = dgenGreen,
        onClick = {}
    )
}