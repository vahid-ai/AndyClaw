package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Stable
class CustomTextToolbarState(
    val primaryColor: Color = Color.White
) {
    var isShowing by mutableStateOf(false)
        private set
    var menuRect by mutableStateOf(Rect.Zero)
        private set
    var onCopy: (() -> Unit)? by mutableStateOf(null)
        private set
    var onPaste: (() -> Unit)? by mutableStateOf(null)
        private set
    var onCut: (() -> Unit)? by mutableStateOf(null)
        private set
    var onSelectAll: (() -> Unit)? by mutableStateOf(null)
        private set

    fun show(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        menuRect = rect
        onCopy = onCopyRequested
        onPaste = onPasteRequested
        onCut = onCutRequested
        onSelectAll = onSelectAllRequested
        isShowing = true
    }

    fun hide() {
        isShowing = false
    }
}

class CustomTextToolbar(
    private val state: CustomTextToolbarState
) : TextToolbar {

    override val status: TextToolbarStatus
        get() = if (state.isShowing) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun hide() {
        state.hide()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        if (!state.isShowing) {
            state.show(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
        }
    }
}

@Composable
fun CustomTextSelectionMenuContent(state: CustomTextToolbarState) {
    if (state.isShowing) {
        DisposableEffect(state.isShowing) {
            onDispose { }
        }

        CustomSelectionMenu(
            rect = state.menuRect,
            onCopy = state.onCopy,
            onPaste = state.onPaste,
            onCut = state.onCut,
            onSelectAll = state.onSelectAll,
            onDismiss = { state.hide() },
            primaryColor = state.primaryColor,
        )
    }
}

@Composable
fun CustomSelectionMenu(
    rect: Rect,
    onCopy: (() -> Unit)?,
    onPaste: (() -> Unit)?,
    onCut: (() -> Unit)?,
    onSelectAll: (() -> Unit)?,
    onDismiss: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
) {
    val containerColor = Color(0xFF0A0A0A)

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current

    val menuWidth = 200.dp
    val menuHeight = 50.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }
    val menuHeightPx = with(density) { menuHeight.toPx() }
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val screenHeightPx = with(density) { screenHeight.toPx() }

    var xOffset = if (rect.width > 0) {
        (rect.left + rect.width / 2 - menuWidthPx / 2).toInt()
    } else {
        (rect.left - menuWidthPx / 2).toInt()
    }

    xOffset = xOffset.coerceIn(
        minimumValue = 10,
        maximumValue = (screenWidthPx - menuWidthPx - 10).toInt()
    )

    val spaceAbove = rect.top
    val spaceBelow = screenHeightPx - rect.bottom
    val preferredSpacing = with(density) { 8.dp.toPx() }

    val yOffset: Int

    if (spaceAbove >= menuHeightPx + preferredSpacing) {
        yOffset = (rect.top - menuHeightPx - preferredSpacing).toInt()
    } else if (spaceBelow >= menuHeightPx + preferredSpacing) {
        yOffset = (rect.bottom + preferredSpacing).toInt()
    } else {
        yOffset = if (spaceAbove > spaceBelow) {
            10
        } else {
            (screenHeightPx - menuHeightPx - 10).toInt()
        }
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(xOffset, yOffset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .background(containerColor, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                onCut?.let {
                    TextSelectionMenuButton(
                        text = "CUT",
                        onClick = { it(); onDismiss() },
                        primaryColor = primaryColor,
                    )
                }

                onCopy?.let {
                    TextSelectionMenuButton(
                        text = "COPY",
                        onClick = { it(); onDismiss() },
                        primaryColor = primaryColor,
                    )
                }

                onPaste?.let {
                    TextSelectionMenuButton(
                        text = "PASTE",
                        onClick = { it(); onDismiss() },
                        primaryColor = primaryColor,
                    )
                }

                onSelectAll?.let {
                    TextSelectionMenuButton(
                        text = "ALL",
                        onClick = { it(); onDismiss() },
                        primaryColor = primaryColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSelectionMenuButton(
    text: String,
    onClick: () -> Unit,
    primaryColor: Color,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                shadow = GlowStyle.button(primaryColor),
            ),
            color = primaryColor,
        )
    }
}
