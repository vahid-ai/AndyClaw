package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ethereumphone.andyclaw.ui.theme.PitagonsSans
import org.ethereumphone.andyclaw.ui.theme.SpaceMono
import org.ethereumphone.andyclaw.ui.theme.body1_fontSize
import org.ethereumphone.andyclaw.ui.theme.button_fontSize
import org.ethereumphone.andyclaw.ui.theme.dgenWhite
import org.ethereumphone.andyclaw.ui.theme.label_fontSize

@Composable
fun SmallDetailItem(
    label: String,
    value: String,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(end=24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = primaryColor,
            style = TextStyle(
                fontFamily = SpaceMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Left,
                shadow = GlowStyle.subtitle(primaryColor),
            )
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = PitagonsSans,
                color = dgenWhite,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                shadow = GlowStyle.body(dgenWhite),
            )
        )
    }
}