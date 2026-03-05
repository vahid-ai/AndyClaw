package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.body1_fontSize
import com.example.dgenlibrary.ui.theme.dgenWhite
import org.ethereumphone.andyclaw.ui.DgenCursorSearchTextfield

@Composable
fun DgenCursorTextfield(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val labelStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
    )

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = labelStyle,
                color = primaryColor,
            )
            Spacer(Modifier.height(8.dp))
        }

        DgenCursorSearchTextfield(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardtype = keyboardType,
            placeholder = {
                CompositionLocalProvider(
                    LocalTextStyle provides TextStyle(fontSize = body1_fontSize)
                ) {
                    placeholder?.invoke()
                }
            },
            singleLine = singleLine,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = body1_fontSize,
                fontWeight = FontWeight.Normal,
                color = dgenWhite,
                shadow = Shadow(
                    color = dgenWhite.copy(alpha = 0.4f),
                    offset = Offset.Zero,
                    blurRadius = 12f,
                ),
            ),
            cursorColor = dgenWhite,
            cursorWidth = 10.dp,
            cursorHeight = 20.dp,
            maxFieldHeight = 56.dp,
            visualTransformation = visualTransformation,
        )
    }
}
