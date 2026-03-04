package org.ethereumphone.andyclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatAssessment
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatIndicator
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatLevel

@Composable
fun ApprovalDialog(
    description: String,
    toolName: String?,
    slug: String?,
    threatAssessment: ThreatAssessment?,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    if (threatAssessment != null && slug != null) {
        ThreatApprovalDialog(
            slug = slug,
            assessment = threatAssessment,
            onConfirm = onApprove,
            onDismiss = onDeny,
        )
    } else {
        GenericApprovalDialog(
            description = description,
            onApprove = onApprove,
            onDeny = onDeny,
        )
    }
}

@Composable
private fun GenericApprovalDialog(
    description: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("Approval Required") },
        text = { Text(description) },
        confirmButton = {
            TextButton(onClick = onApprove) {
                Text("Approve")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Deny")
            }
        },
    )
}

@Composable
private fun ThreatApprovalDialog(
    slug: String,
    assessment: ThreatAssessment,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val iconTint = when (assessment.level) {
        ThreatLevel.LOW -> Color(0xFF4CAF50)
        ThreatLevel.MEDIUM -> Color(0xFFFFA000)
        ThreatLevel.HIGH -> Color(0xFFFF6D00)
        ThreatLevel.CRITICAL -> Color(0xFFD50000)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text("Security Warning") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "\"$slug\"",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    ThreatBadge(level = assessment.level)
                }

                Text(
                    text = assessment.summary,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (assessment.indicators.isNotEmpty()) {
                    Text(
                        text = "Detected issues",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    for (indicator in assessment.indicators) {
                        IndicatorRow(indicator)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "By installing this skill you accept the associated risks. " +
                        "Only proceed if you trust the skill author.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Accept & Install",
                    color = if (assessment.level >= ThreatLevel.HIGH)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ThreatBadge(level: ThreatLevel) {
    val (textColor, bgColor) = when (level) {
        ThreatLevel.LOW -> Color(0xFF2E7D32) to Color(0xFF4CAF50).copy(alpha = 0.14f)
        ThreatLevel.MEDIUM -> Color(0xFFF57F17) to Color(0xFFFFA000).copy(alpha = 0.14f)
        ThreatLevel.HIGH -> Color(0xFFE65100) to Color(0xFFFF6D00).copy(alpha = 0.14f)
        ThreatLevel.CRITICAL -> Color(0xFFC62828) to Color(0xFFD50000).copy(alpha = 0.14f)
    }

    Text(
        text = level.displayName,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun IndicatorRow(indicator: ThreatIndicator) {
    val dotColor = when (indicator.severity) {
        ThreatLevel.LOW -> Color(0xFF4CAF50)
        ThreatLevel.MEDIUM -> Color(0xFFFFA000)
        ThreatLevel.HIGH -> Color(0xFFFF6D00)
        ThreatLevel.CRITICAL -> Color(0xFFD50000)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = indicator.category,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = indicator.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
