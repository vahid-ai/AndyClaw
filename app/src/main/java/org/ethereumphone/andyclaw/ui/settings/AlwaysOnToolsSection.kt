package org.ethereumphone.andyclaw.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ethereumphone.andyclaw.ui.theme.SpaceMono
import org.ethereumphone.andyclaw.ui.theme.dgenWhite
import org.ethereumphone.andyclaw.ui.theme.label_fontSize
import org.ethereumphone.andyclaw.ui.components.AppTextStyles
import org.ethereumphone.andyclaw.ui.components.DgenSmallPrimaryButton
import org.ethereumphone.andyclaw.ui.components.GlowStyle

/**
 * Settings section for configuring which skills/tools are always available
 * without needing to be discovered via search_available_tools.
 */
@Composable
fun AlwaysOnToolsSection(
    selectedPresetName: String,
    onNavigateToPresetSelection: () -> Unit,
    onNavigateToPresetEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        letterSpacing = 1.sp,
        shadow = GlowStyle.subtitle(primaryColor),
    )
    val contentTitleStyle = AppTextStyles.contentTitle(primaryColor)
    val contentBodyStyle = AppTextStyles.contentBody(primaryColor)
    val rowControlSpacing = 16.dp

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        // ── Section title ──────────────────────────────────────────────
        Text(
            text = "ALWAYS-ON TOOLS",
            color = primaryColor,
            style = sectionTitleStyle,
        )
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Skills and tools in this preset are always available to the AI without needing to be discovered. Everything else can be found via tool search.",
            style = contentBodyStyle,
            color = dgenWhite,
        )

        Spacer(Modifier.height(12.dp))

        // ── Selected preset (clickable to navigate) ───────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToPresetSelection)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PRESET",
                    style = contentTitleStyle,
                    color = primaryColor,
                )
                Text(
                    text = selectedPresetName,
                    style = contentBodyStyle,
                    color = dgenWhite,
                )
            }
            Spacer(Modifier.width(rowControlSpacing))
            Text(
                text = ">",
                style = TextStyle(
                    fontFamily = SpaceMono,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = primaryColor,
            )
        }

        // ── Edit preset button ────────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        DgenSmallPrimaryButton(
            text = "EDIT PRESET",
            primaryColor = primaryColor,
            onClick = onNavigateToPresetEditor,
        )
    }
}
