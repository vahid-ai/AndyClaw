package org.ethereumphone.andyclaw.ui.chat

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dgenlibrary.ConfirmationOverlay
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.showDgenToast
import com.example.dgenlibrary.ui.theme.body1_fontSize
import org.ethereumphone.andyclaw.NodeApp
import org.ethereumphone.andyclaw.commands.SlashCommandResult
import org.ethereumphone.andyclaw.ui.components.ChadBackground
import org.ethereumphone.andyclaw.ui.components.ThreatConfirmationDialog

@Composable
fun ChatScreen(
    sessionId: String?,
    onNavigateToSessions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRoute: (String) -> Unit = { onNavigateToSettings() },
    viewModel: ChatViewModel = viewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val currentTool by viewModel.currentToolExecution.collectAsState()
    val error by viewModel.error.collectAsState()
    val insufficientBalance by viewModel.insufficientBalance.collectAsState()
    val approvalRequest by viewModel.approvalRequest.collectAsState()
    val displayBitmap by viewModel.agentDisplayBitmap.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as NodeApp
    val aiName by app.securePrefs.aiName.collectAsState()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var autoScroll by remember { mutableStateOf(true) }
    val expandedToolResults = remember { mutableStateListOf<String>() }
    var showSlashOverlay by remember { mutableStateOf(false) }
    var slashQuery by remember { mutableStateOf("") }
    var clearInput by remember { mutableStateOf(false) }

    // Disable auto-scroll when the user touches/drags the list.
    // NestedScrollConnection.onPreScroll only fires for user gestures,
    // never for programmatic scrollToItem calls.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag) {
                    autoScroll = false
                }
                return Offset.Zero
            }
        }
    }

    val primaryColor = SystemColorManager.primaryColor
    val secondaryColor = SystemColorManager.secondaryColor

    LaunchedEffect(Unit) {
        SystemColorManager.refresh(context)
    }

    // Re-enable auto-scroll when a new streaming response begins
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            autoScroll = true
        }
    }

    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            viewModel.loadSession(sessionId)
        } else if (viewModel.sessionId.value == null) {
            viewModel.newSession()
        }
    }

    LaunchedEffect(messages.size, streamingText) {
        if ((messages.isNotEmpty() || streamingText.isNotEmpty()) && autoScroll) {
            val targetIndex = listState.layoutInfo.totalItemsCount - 1
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    LaunchedEffect(error) {
        error?.let {
            showDgenToast(context, it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(approvalRequest) {
        if (approvalRequest != null) keyboardController?.hide()
    }

    LaunchedEffect(insufficientBalance) {
        if (insufficientBalance) keyboardController?.hide()
    }

    // Handle navigation events from slash commands
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { route ->
            viewModel.consumeNavigationEvent()
            onNavigateToRoute(route)
        }
    }

    ChadBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .imePadding()) {
            // Top bar overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateToSessions) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Sessions",
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = aiName.uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = body1_fontSize,
                        shadow = GlowStyle.title(primaryColor),
                    ),
                    color = primaryColor,
                )

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Messages area
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            isExpanded = expandedToolResults.contains(message.id),
                            onToggleExpand = if (message.role == "tool") {
                                {
                                    if (expandedToolResults.contains(message.id)) {
                                        expandedToolResults.remove(message.id)
                                    } else {
                                        expandedToolResults.add(message.id)
                                    }
                                }
                            } else null,
                        )
                    }

                    if (isStreaming && streamingText.isNotEmpty()) {
                        item {
                            StreamingTextDisplay(
                                text = streamingText,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (currentTool != null) {
                        item {
                            ToolExecutionIndicator(toolName = currentTool!!)
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Agent display live preview
                displayBitmap?.let { bitmap ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .width(150.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Agent display preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            ChatInputBar(
                isStreaming = isStreaming,
                onSend = { text ->
                    showSlashOverlay = false
                    slashQuery = ""
                    viewModel.sendMessage(text)
                },
                onCancel = { viewModel.cancel() },
                onInputChanged = { text ->
                    if (text.startsWith("/")) {
                        showSlashOverlay = true
                        slashQuery = text.removePrefix("/").lowercase()
                    } else {
                        showSlashOverlay = false
                        slashQuery = ""
                    }
                },
                clearInput = clearInput,
                onInputCleared = { clearInput = false },
            )
        }

        // Slash command overlay — absolutely positioned, floats above the input bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(bottom = 52.dp), // offset above ChatInputBar
            contentAlignment = Alignment.BottomCenter,
        ) {
            SlashCommandOverlay(
                visible = showSlashOverlay,
                query = slashQuery,
                accentColor = primaryColor,
                prefs = app.securePrefs,
                executor = viewModel.slashExecutor,
                onCommandResult = { result ->
                    when (result) {
                        is SlashCommandResult.Toggled -> {
                            showDgenToast(context, result.message)
                        }
                        is SlashCommandResult.CycleSelected -> {
                            showDgenToast(context, result.message)
                        }
                        is SlashCommandResult.ActionDone -> {
                            showSlashOverlay = false
                            slashQuery = ""
                            clearInput = true
                            when (result.message) {
                                "Conversation cleared." -> viewModel.newSession()
                                "Memory reindex started." -> viewModel.triggerReindex()
                            }
                            showDgenToast(context, result.message)
                        }
                        is SlashCommandResult.Navigate -> {
                            showSlashOverlay = false
                            slashQuery = ""
                            clearInput = true
                            viewModel.consumeNavigationEvent()
                            onNavigateToRoute(result.route)
                        }
                        is SlashCommandResult.HelpList -> {
                            // Show all commands by clearing the query filter
                            slashQuery = ""
                        }
                        else -> {}
                    }
                },
                onDismiss = {
                    showSlashOverlay = false
                    slashQuery = ""
                    clearInput = true
                },
            )
        }

        approvalRequest?.let { request ->
            if (request.threatAssessment != null && request.slug != null) {
                ThreatConfirmationDialog(
                    slug = request.slug,
                    assessment = request.threatAssessment,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    confirmButtonText = "APPROVE",
                    cancelButtonText = "DENY",
                    onConfirm = { viewModel.respondToApproval(true) },
                    onDismiss = { viewModel.respondToApproval(false) },
                )
            } else {
                ConfirmationOverlay(
                    visible = true,
                    description = "APPROVAL REQUIRED",
                    extraDescription = request.description,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    cancelButtonText = "DENY",
                    confirmButtonText = "APPROVE",
                    onCancel = { viewModel.respondToApproval(false) },
                    onConfirm = { viewModel.respondToApproval(true) }
                )
            }
        }

        if (insufficientBalance) {
            ConfirmationOverlay(
                visible = true,
                description = "PAYMASTER DEPLETED",
                extraDescription = "The paymaster that covers your AI usage has been depleted. Please fill it up to continue using $aiName.",
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                confirmButtonText = "DISMISS",
                cancelButtonText = "TOP UP",
                onConfirm = { viewModel.clearInsufficientBalance() },
                onCancel = {
                    viewModel.clearInsufficientBalance()
                    val intent = Intent().apply {
                        setClassName("io.freedomfactory.paymaster", "io.freedomfactory.paymaster.MainActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}
