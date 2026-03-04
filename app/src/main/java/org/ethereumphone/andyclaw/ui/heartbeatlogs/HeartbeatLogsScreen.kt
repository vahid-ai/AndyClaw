package org.ethereumphone.andyclaw.ui.heartbeatlogs

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.label_fontSize
import org.ethereumphone.andyclaw.heartbeat.HeartbeatLogEntry
import org.ethereumphone.andyclaw.ui.components.DgenBackNavigationBackground
import org.ethereumphone.andyclaw.ui.components.DgenSmallPrimaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HeartbeatLogsSubScreen {
    Main,
    Detail,
}

@Composable
fun HeartbeatLogsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HeartbeatLogsViewModel = viewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    var currentSubScreen by remember { mutableStateOf(HeartbeatLogsSubScreen.Main) }
    var selectedEntry by remember { mutableStateOf<HeartbeatLogEntry?>(null) }

    val context = LocalContext.current
    LaunchedEffect(Unit) { SystemColorManager.refresh(context) }
    val primaryColor = SystemColorManager.primaryColor

    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
    )
    val contentTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
    )
    val contentBodyStyle = TextStyle(
        fontFamily = PitagonsSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
    )

    DgenBackNavigationBackground(
        title = when (currentSubScreen) {
            HeartbeatLogsSubScreen.Main -> "Heartbeat Logs"
            HeartbeatLogsSubScreen.Detail -> "Log Detail"
        },
        primaryColor = primaryColor,
        onNavigateBack = {
            when (currentSubScreen) {
                HeartbeatLogsSubScreen.Main -> onNavigateBack()
                else -> { currentSubScreen = HeartbeatLogsSubScreen.Main }
            }
        },
    ) {
        Crossfade(targetState = currentSubScreen, label = "heartbeat_crossfade") { screen ->
            when (screen) {
                HeartbeatLogsSubScreen.Main -> {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "NO HEARTBEAT LOGS YET",
                                    style = contentTitleStyle,
                                    color = primaryColor,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Logs will appear after the next heartbeat run",
                                    style = contentBodyStyle,
                                    color = dgenWhite,
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "RUN HISTORY",
                                    style = sectionTitleStyle,
                                    color = primaryColor,
                                )
                                DgenSmallPrimaryButton(
                                    text = "Clear",
                                    primaryColor = primaryColor,
                                    onClick = { viewModel.clearLogs() },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(
                                    items = logs,
                                    key = { it.timestampMs },
                                ) { entry ->
                                    HeartbeatLogRow(
                                        entry = entry,
                                        primaryColor = primaryColor,
                                        contentTitleStyle = contentTitleStyle,
                                        contentBodyStyle = contentBodyStyle,
                                        onClick = {
                                            selectedEntry = entry
                                            currentSubScreen = HeartbeatLogsSubScreen.Detail
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                HeartbeatLogsSubScreen.Detail -> {
                    val entry = selectedEntry ?: return@Crossfade
                    HeartbeatLogDetail(
                        entry = entry,
                        primaryColor = primaryColor,
                        sectionTitleStyle = sectionTitleStyle,
                        contentTitleStyle = contentTitleStyle,
                        contentBodyStyle = contentBodyStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeartbeatLogRow(
    entry: HeartbeatLogEntry,
    primaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
    onClick: () -> Unit,
) {
    val timeText = remember(entry.timestampMs) {
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMs))
    }
    val isError = entry.outcome == "error"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeText,
                style = contentTitleStyle,
                color = primaryColor,
            )
            Text(
                text = entry.outcome.uppercase(),
                style = contentTitleStyle,
                color = if (isError) Color(0xFFFF6B6B) else primaryColor,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append("${entry.durationMs}ms")
                if (entry.toolCalls.isNotEmpty()) {
                    append(" · ${entry.toolCalls.size} tool call(s)")
                }
            },
            style = contentBodyStyle,
            color = dgenWhite.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.responseText.ifBlank { "(no response)" },
            style = contentBodyStyle,
            color = dgenWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
    }
}

@Composable
private fun HeartbeatLogDetail(
    entry: HeartbeatLogEntry,
    primaryColor: Color,
    sectionTitleStyle: TextStyle,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    val timeText = remember(entry.timestampMs) {
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMs))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(text = "TIMESTAMP", style = sectionTitleStyle, color = primaryColor)
        Spacer(Modifier.height(4.dp))
        Text(text = timeText, style = contentBodyStyle, color = dgenWhite)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Text(text = "OUTCOME", style = sectionTitleStyle, color = primaryColor)
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.outcome.uppercase(),
            style = contentBodyStyle,
            color = if (entry.outcome == "error") Color(0xFFFF6B6B) else dgenWhite,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Text(text = "DURATION", style = sectionTitleStyle, color = primaryColor)
        Spacer(Modifier.height(4.dp))
        Text(text = "${entry.durationMs}ms", style = contentBodyStyle, color = dgenWhite)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Text(text = "PROMPT", style = sectionTitleStyle, color = primaryColor)
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.prompt.ifBlank { "(empty)" },
            style = contentBodyStyle,
            color = dgenWhite,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))

        Text(text = "RESPONSE", style = sectionTitleStyle, color = primaryColor)
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.responseText.ifBlank { "(no response)" },
            style = contentBodyStyle,
            color = dgenWhite,
        )

        if (!entry.error.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            Text(text = "ERROR", style = sectionTitleStyle, color = Color(0xFFFF6B6B))
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.error,
                style = contentBodyStyle,
                color = Color(0xFFFF6B6B),
            )
        }

        if (entry.toolCalls.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            Text(text = "TOOL CALLS", style = sectionTitleStyle, color = primaryColor)
            Spacer(Modifier.height(8.dp))
            for (tool in entry.toolCalls) {
                Text(
                    text = tool.toolName.uppercase(),
                    style = contentTitleStyle,
                    color = primaryColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tool.result,
                    style = contentBodyStyle,
                    color = dgenWhite,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
