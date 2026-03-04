package org.ethereumphone.andyclaw.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.ethereumphone.andyclaw.extensions.ExtensionDescriptor
import org.ethereumphone.andyclaw.extensions.ExtensionType
import org.ethereumphone.andyclaw.ui.components.DgenPrimaryButton

@Composable
fun ExtensionManagementSection(
    extensions: List<ExtensionDescriptor>,
    isScanning: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = SystemColorManager.primaryColor
    val secondaryColor = SystemColorManager.secondaryColor
    val titleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textDecoration = TextDecoration.None,
        textAlign = TextAlign.Left,
    )
    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
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
            text = "EXTENSIONS",
            style = titleStyle,
            color = primaryColor,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanning) "SCANNING EXTENSIONS..." else "DISCOVER INSTALLED EXTENSIONS",
                    style = sectionTitleStyle,
                    color = primaryColor,
                )
                Text(
                    text = "Extensions are discovered from installed APKs with AndyClaw metadata",
                    style = bodyStyle,
                    color = dgenWhite,
                )
            }
            Spacer(Modifier.width(20.dp))
            DgenPrimaryButton(
                text = if (isScanning) "Scanning..." else "Rescan",
                borderColor = primaryColor,
                containerColor = secondaryColor,
                onClick = onRescan,
                enabled = !isScanning,
                minHeight = 30.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (extensions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = if (isScanning) "SCANNING FOR EXTENSIONS..." else "NO EXTENSIONS FOUND",
                    style = sectionTitleStyle,
                    color = primaryColor,
                )
                Text(
                    text = "Install an extension-enabled APK to add functions here.",
                    style = bodyStyle,
                    color = dgenWhite,
                )
            }
        } else {
            for (ext in extensions) {
                ExtensionRow(
                    descriptor = ext,
                    titleStyle = sectionTitleStyle,
                    bodyStyle = bodyStyle,
                    titleColor = primaryColor,
                )
            }
        }
    }
}

@Composable
private fun ExtensionRow(
    descriptor: ExtensionDescriptor,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    titleColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = descriptor.name.uppercase(),
                style = titleStyle,
                color = titleColor,
            )
            val detail = buildString {
                append(
                    when (descriptor.type) {
                        ExtensionType.APK -> "APK"
                    }
                )
                append(" · ${descriptor.functions.size} function")
                if (descriptor.functions.size != 1) append("s")
                if (descriptor.version > 1) append(" · v${descriptor.version}")
                if (descriptor.trusted) append(" · Trusted")
            }
            Text(
                text = detail,
                style = bodyStyle,
                color = dgenWhite,
            )
        }
    }
}
