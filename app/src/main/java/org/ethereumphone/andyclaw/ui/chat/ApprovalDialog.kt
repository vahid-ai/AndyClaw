package org.ethereumphone.andyclaw.ui.chat

import androidx.compose.runtime.Composable
import org.ethereumphone.andyclaw.ui.components.ChadAlertDialog

@Composable
fun ApprovalDialog(
    description: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    ChadAlertDialog(
        onDismissRequest = onDeny,
        title = "APPROVAL REQUIRED",
        message = description,
        confirmButtonText = "APPROVE",
        dismissButtonText = "DENY",
        onConfirm = onApprove,
        onDismiss = onDeny,
    )
}
