package org.ethereumphone.andyclaw.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.label_fontSize
import org.ethereumphone.andyclaw.ui.components.DgenPrimaryButton
import org.ethereumphone.andyclaw.ui.components.DgenSquareSwitch

@Composable
fun MemorySettingsSection(
    memoryCount: Int,
    autoStoreEnabled: Boolean,
    isReindexing: Boolean,
    onAutoStoreToggle: (Boolean) -> Unit,
    onReindex: () -> Unit,
    onClearMemories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = SystemColorManager.primaryColor
    val secondaryColor = SystemColorManager.secondaryColor

    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
    )
    val titleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textDecoration = TextDecoration.None,
        textAlign = TextAlign.Left
    )
    val bodyStyle = TextStyle(
        fontFamily = PitagonsSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
    )

    Column(modifier = modifier) {
        Text(
            text = "LONG-TERM MEMORY",
            style = titleStyle,
            color = primaryColor,
        )
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "$memoryCount MEMOR${if (memoryCount == 1) "Y" else "IES"} STORED",
                style = sectionTitleStyle,
                color = primaryColor,
            )
            Text(
                text = "Memories persist across conversations and are searchable by the agent. " +
                    "Hybrid keyword + semantic search is used when an embedding provider is configured.",
                style = bodyStyle,
                color = dgenWhite,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AUTO-STORE CONVERSATIONS",
                    style = sectionTitleStyle,
                    color = primaryColor,
                )
                Text(
                    text = "Automatically save conversation turns to memory for future recall",
                    style = bodyStyle,
                    color = dgenWhite,
                )
            }
            Spacer(Modifier.width(20.dp))
            DgenSquareSwitch(
                checked = autoStoreEnabled,
                onCheckedChange = onAutoStoreToggle,
                activeColor = primaryColor,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DgenPrimaryButton(
                text = if (isReindexing) "Reindexing..." else "Reindex",
                borderColor = primaryColor,
                containerColor = secondaryColor,
                onClick = onReindex,
                enabled = !isReindexing && memoryCount > 0,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
            DgenPrimaryButton(
                text = "Clear All",
                borderColor = primaryColor,
                containerColor = secondaryColor,
                onClick = onClearMemories,
                enabled = memoryCount > 0,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
        }

        if (isReindexing) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = primaryColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "REINDEX IN PROGRESS",
                    style = sectionTitleStyle,
                    color = primaryColor,
                )
            }
        }
    }
}
