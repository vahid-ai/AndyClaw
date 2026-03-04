package org.ethereumphone.andyclaw.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.pulseOpacity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.LinearProgressIndicator
import org.ethereumphone.andyclaw.llm.AnthropicModels
import org.ethereumphone.andyclaw.ui.components.DgenBackNavigationBackground
import org.ethereumphone.andyclaw.ui.components.DgenSmallPrimaryButton
import org.ethereumphone.andyclaw.ui.components.DgenSquareSwitch
import org.ethereumphone.andyclaw.llm.LlmProvider
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.ui.theme.label_fontSize
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToClawHub: () -> Unit = {},
    onNavigateToHeartbeatLogs: () -> Unit = {},
    onNavigateToAgentDisplayTest: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val tinfoilApiKey by viewModel.tinfoilApiKey.collectAsState()
    val openRouterApiKey by viewModel.apiKey.collectAsState()
    val downloadProgress by viewModel.modelDownloadManager.downloadProgress.collectAsState()
    val isDownloading by viewModel.modelDownloadManager.isDownloading.collectAsState()
    val downloadError by viewModel.modelDownloadManager.downloadError.collectAsState()
    val yoloMode by viewModel.yoloMode.collectAsState()
    val safetyEnabled by viewModel.safetyEnabled.collectAsState()
    val notificationReplyEnabled by viewModel.notificationReplyEnabled.collectAsState()
    val heartbeatOnNotificationEnabled by viewModel.heartbeatOnNotificationEnabled.collectAsState()
    val heartbeatOnXmtpMessageEnabled by viewModel.heartbeatOnXmtpMessageEnabled.collectAsState()
    val heartbeatIntervalMinutes by viewModel.heartbeatIntervalMinutes.collectAsState()
    val memoryCount by viewModel.memoryCount.collectAsState()
    val autoStoreEnabled by viewModel.autoStoreEnabled.collectAsState()
    val isReindexing by viewModel.isReindexing.collectAsState()
    val extensions by viewModel.extensions.collectAsState()
    val isExtensionScanning by viewModel.isExtensionScanning.collectAsState()
    val enabledSkills by viewModel.enabledSkills.collectAsState()
    val paymasterBalance by viewModel.paymasterBalance.collectAsState()
    val telegramBotEnabled by viewModel.telegramBotEnabled.collectAsState()
    val telegramOwnerChatId by viewModel.telegramOwnerChatId.collectAsState()
    val ledMaxBrightness by viewModel.ledMaxBrightness.collectAsState()
    var showTelegramOnboarding by remember { mutableStateOf(false) }
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.Main) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        SystemColorManager.refresh(context)
    }

    val primaryColor = SystemColorManager.primaryColor
    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textDecoration = TextDecoration.None,
        textAlign = TextAlign.Left
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
    val rowControlSpacing = 20.dp

    val providerChoices = if (viewModel.isPrivileged) {
        listOf(LlmProvider.ETHOS_PREMIUM, LlmProvider.OPEN_ROUTER, LlmProvider.TINFOIL)
    } else {
        listOf(LlmProvider.OPEN_ROUTER, LlmProvider.TINFOIL, LlmProvider.LOCAL)
    }

    DgenBackNavigationBackground(
        title = when (currentSubScreen) {
            SettingsSubScreen.Main -> "Settings"
            SettingsSubScreen.ModelSelection -> "Select Model"
            SettingsSubScreen.ProviderSelection -> "Select Provider"
        },
        primaryColor = primaryColor,
        onNavigateBack = {
            when (currentSubScreen) {
                SettingsSubScreen.Main -> onNavigateBack()
                else -> { currentSubScreen = SettingsSubScreen.Main }
            }
        },
    ) {
        Crossfade(targetState = currentSubScreen, label = "settings_crossfade") { screen ->
        when (screen) {
        SettingsSubScreen.Main ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Model Selection
            Text(
                text = "MODEL",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { currentSubScreen = SettingsSubScreen.ModelSelection }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AnthropicModels.fromModelId(selectedModel)?.name ?: selectedModel,
                    style = TextStyle(
                        fontFamily = PitagonsSans,
                        color = dgenWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        lineHeight = 32.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = primaryColor,
                )
            }

            // Paymaster Balance (ethOS privileged only)
            if (viewModel.isPrivileged && paymasterBalance != null) {
                Spacer(Modifier.height(16.dp))
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PAYMASTER BALANCE",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        val formattedBalance = try {
                            val bd = BigDecimal(paymasterBalance!!)
                            "$${bd.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
                        } catch (_: NumberFormatException) {
                            "$0.00"
                        }
                        Text(
                            text = formattedBalance,
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                    }
                    Spacer(Modifier.width(rowControlSpacing))
                    DgenSmallPrimaryButton(
                        text = "Fill up",
                        primaryColor = primaryColor,
                        onClick = {
                        val intent = Intent().apply {
                            setClassName("io.freedomfactory.paymaster", "io.freedomfactory.paymaster.MainActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    })
                }
            }

            // AI Provider
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = "AI PROVIDER",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { currentSubScreen = SettingsSubScreen.ProviderSelection }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedProvider.displayName,
                    style = TextStyle(
                        fontFamily = PitagonsSans,
                        color = dgenWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        lineHeight = 32.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = primaryColor,
                )
            }

            Spacer(Modifier.height(8.dp))

            when (selectedProvider) {
                LlmProvider.ETHOS_PREMIUM -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = "BILLED VIA PAYMASTER BALANCE",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Text(
                            text = "No API key needed. Inference is charged against your ethOS paymaster balance.",
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                    }
                }
                LlmProvider.OPEN_ROUTER -> {
                    var editingOpenRouterKey by remember { mutableStateOf(openRouterApiKey) }
                    OutlinedTextField(
                        value = editingOpenRouterKey,
                        onValueChange = {
                            editingOpenRouterKey = it
                            viewModel.setApiKey(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenRouter API Key") },
                        placeholder = { Text("sk-or-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                LlmProvider.TINFOIL -> {
                    var editingKey by remember { mutableStateOf(tinfoilApiKey) }
                    OutlinedTextField(
                        value = editingKey,
                        onValueChange = {
                            editingKey = it
                            viewModel.setTinfoilApiKey(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tinfoil API Key") },
                        placeholder = { Text("tf-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                LlmProvider.LOCAL -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = "ON-DEVICE MODEL (QWEN2.5-1.5B)",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Text(
                            text = "~2.5 GB Q4_K_M quantization",
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                        Spacer(Modifier.height(12.dp))

                        if (viewModel.modelDownloadManager.isModelDownloaded) {
                            Text(
                                text = "MODEL DOWNLOADED",
                                style = contentTitleStyle,
                                color = primaryColor,
                            )
                            Spacer(Modifier.height(8.dp))
                            DgenSmallPrimaryButton(
                                text = "Delete Model",
                                primaryColor = primaryColor,
                                onClick = { viewModel.deleteLocalModel() },
                            )
                        } else if (isDownloading) {
                            Text(
                                text = "DOWNLOADING... ${(downloadProgress * 100).toInt()}%",
                                style = contentTitleStyle,
                                color = primaryColor,
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            if (downloadError != null) {
                                Text(
                                    text = "Error: $downloadError",
                                    style = contentBodyStyle,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            DgenSmallPrimaryButton(
                                text = "Download Model",
                                primaryColor = primaryColor,
                                onClick = { viewModel.downloadLocalModel() },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Tier Display
            Text(
                text = "DEVICE TIER",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = viewModel.currentTier.uppercase(),
                    style = contentTitleStyle,
                    color = primaryColor,
                )
                Text(
                    text = if (viewModel.isPrivileged) "Full access to all skills and tools"
                    else "Some skills are restricted to privileged OS builds",
                    style = contentBodyStyle,
                    color = dgenWhite,
                )
            }

            // LED Matrix Brightness (ethOS + dGEN1 only)
            if (viewModel.isPrivileged && viewModel.isLedAvailable) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "LED MATRIX",
                    color = primaryColor,
                    style = sectionTitleStyle,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "MAX BRIGHTNESS: $ledMaxBrightness",
                        style = contentTitleStyle,
                        color = primaryColor,
                    )
                    Text(
                        text = "Caps the RGB value sent to the 3×3 LED driver. LEDs get very dim below ~100. Default is 255 (full).",
                        style = contentBodyStyle,
                        color = dgenWhite,
                    )
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = ledMaxBrightness.toFloat(),
                        onValueChange = { viewModel.setLedMaxBrightness(it.toInt()) },
                        valueRange = 100f..255f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("100", style = contentBodyStyle, color = dgenWhite)
                        Text("255", style = contentBodyStyle, color = dgenWhite)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // YOLO Mode
            Text(
                text = "YOLO MODE",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AUTO-APPROVE ALL TOOLS",
                        style = contentTitleStyle,
                        color = primaryColor,
                    )
                    Text(
                        text = "Skip approval prompts for all tool and skill usage, including heartbeat and chat",
                        style = contentBodyStyle,
                        color = dgenWhite,
                    )
                }
                Spacer(Modifier.width(rowControlSpacing))
                DgenSquareSwitch(
                    checked = yoloMode,
                    onCheckedChange = { viewModel.setYoloMode(it) },
                    activeColor = primaryColor,
                )
            }

            // Safety Mode
            Spacer(Modifier.height(12.dp))
            Text(
                text = "SAFETY MODE",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENABLE SAFETY CHECKS",
                        style = contentTitleStyle,
                        color = primaryColor,
                    )
                    Text(
                        text = if (yoloMode) "Disabled while YOLO mode is active"
                        else "Scans tool output for secret leaks, prompt injection, and dangerous patterns. Blocked actions will explain why.",
                        style = contentBodyStyle,
                        color = if (yoloMode) MaterialTheme.colorScheme.error else dgenWhite,
                    )
                }
                Spacer(Modifier.width(rowControlSpacing))
                DgenSquareSwitch(
                    checked = safetyEnabled && !yoloMode,
                    onCheckedChange = { viewModel.setSafetyEnabled(it) },
                    enabled = !yoloMode,
                    activeColor = primaryColor,
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Notification Reply
            Text(
                text = "AUTO-REPLY TO NOTIFICATIONS",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ALLOW REPLYING TO MESSAGES",
                        style = contentTitleStyle,
                        color = primaryColor,
                    )
                    Text(
                        text = "When enabled, the AI can reply to incoming notifications (Telegram, WhatsApp, etc.) on your behalf",
                        style = contentBodyStyle,
                        color = dgenWhite,
                    )
                }
                Spacer(Modifier.width(rowControlSpacing))
                DgenSquareSwitch(
                    checked = notificationReplyEnabled,
                    onCheckedChange = { viewModel.setNotificationReplyEnabled(it) },
                    activeColor = primaryColor,
                )
            }

            // Heartbeat on Notification
            Spacer(Modifier.height(12.dp))
            Text(
                text = "HEARTBEAT ON NOTIFICATION",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TRIGGER HEARTBEAT ON NEW NOTIFICATION",
                        style = contentTitleStyle,
                        color = primaryColor,
                    )
                    Text(
                        text = "When enabled, the AI heartbeat runs whenever a new notification arrives so it can react to messages and alerts",
                        style = contentBodyStyle,
                        color = dgenWhite,
                    )
                }
                Spacer(Modifier.width(rowControlSpacing))
                DgenSquareSwitch(
                    checked = heartbeatOnNotificationEnabled,
                    onCheckedChange = { viewModel.setHeartbeatOnNotificationEnabled(it) },
                    activeColor = primaryColor,
                )
            }

            // Heartbeat on XMTP Message — privileged only
            if (viewModel.isPrivileged) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "HEARTBEAT ON XMTP MESSAGE",
                    color = primaryColor,
                    style = sectionTitleStyle,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TRIGGER HEARTBEAT ON NEW XMTP MESSAGE",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Text(
                            text = "When enabled, the AI heartbeat runs with the new messages as context whenever the background sync detects incoming XMTP messages",
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                    }
                    Spacer(Modifier.width(rowControlSpacing))
                    DgenSquareSwitch(
                        checked = heartbeatOnXmtpMessageEnabled,
                        onCheckedChange = { viewModel.setHeartbeatOnXmtpMessageEnabled(it) },
                        activeColor = primaryColor,
                    )
                }
            }

            // Heartbeat Interval
            Spacer(Modifier.height(12.dp))
            Text(
                text = "HEARTBEAT INTERVAL",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = "EVERY $heartbeatIntervalMinutes MINUTES",
                    style = contentTitleStyle,
                    color = primaryColor,
                )
                Text(
                    text = if (viewModel.isPrivileged) "How often the OS triggers the AI heartbeat check"
                    else "How often the background service runs the AI heartbeat check",
                    style = contentBodyStyle,
                    color = dgenWhite,
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = heartbeatIntervalMinutes.toFloat(),
                    onValueChange = { viewModel.setHeartbeatIntervalMinutes(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("5 MIN", style = contentBodyStyle, color = dgenWhite)
                    Text("60 MIN", style = contentBodyStyle, color = dgenWhite)
                }
            }

            // Heartbeat Logs
            Spacer(Modifier.height(12.dp))
            Text(
                text = "HEARTBEAT LOGS",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RUN HISTORY",
                        style = contentTitleStyle,
                        color = primaryColor,
                    )
                    Text(
                        text = "View past heartbeat runs, tool calls, and responses",
                        style = contentBodyStyle,
                        color = dgenWhite,
                    )
                }
                Spacer(Modifier.width(rowControlSpacing))
                DgenSmallPrimaryButton(
                    text = "Open",
                    primaryColor = primaryColor,
                    onClick = onNavigateToHeartbeatLogs,
                )
            }

            // Telegram Bot
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = "TELEGRAM BOT",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))

            val telegramConfigured = telegramBotEnabled && telegramOwnerChatId != 0L
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (telegramConfigured) {
                        Text(
                            text = "TELEGRAM BOT CONNECTED",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Text(
                            text = "The AI can send you proactive messages via Telegram",
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                    } else {
                        Text(
                            text = "NOT CONFIGURED",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Text(
                            text = "Set up a Telegram bot so the AI can reach you via Telegram",
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                    }
                }
                Spacer(Modifier.width(rowControlSpacing))
                if (telegramConfigured) {
                    DgenSmallPrimaryButton(
                        text = "Disconnect",
                        primaryColor = primaryColor,
                        onClick = { viewModel.clearTelegramSetup() },
                    )
                } else {
                    DgenSmallPrimaryButton(
                        text = "Set up",
                        primaryColor = primaryColor,
                        onClick = { showTelegramOnboarding = true },
                    )
                }
            }

            if (showTelegramOnboarding) {
                TelegramOnboardingDialog(
                    onComplete = { token, ownerChatId ->
                        viewModel.completeTelegramSetup(token, ownerChatId)
                        showTelegramOnboarding = false
                    },
                    onDismiss = { showTelegramOnboarding = false },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Long-term Memory
            MemorySettingsSection(
                memoryCount = memoryCount,
                autoStoreEnabled = autoStoreEnabled,
                isReindexing = isReindexing,
                onAutoStoreToggle = { viewModel.setAutoStoreEnabled(it) },
                onReindex = { viewModel.reindexMemory() },
                onClearMemories = { viewModel.clearAllMemories() },
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Extensions
            ExtensionManagementSection(
                extensions = extensions,
                isScanning = isExtensionScanning,
                onRescan = { viewModel.rescanExtensions() },
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ClawHub
            Text(
                text = "CLAWHUB",
                color = primaryColor,
                style = sectionTitleStyle,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SKILL REGISTRY",
                        style = contentTitleStyle,
                        color = primaryColor,
                    )
                    Text(
                        text = "Browse, install, and manage skills from ClawHub",
                        style = contentBodyStyle,
                        color = dgenWhite,
                    )
                }
                Spacer(Modifier.width(rowControlSpacing))
                DgenSmallPrimaryButton(
                    text = "Open",
                    primaryColor = primaryColor,
                    onClick = onNavigateToClawHub,
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Skills
            SkillManagementSection(
                skills = viewModel.registeredSkills,
                enabledSkills = enabledSkills,
                onToggleSkill = { skillId, enabled -> viewModel.toggleSkill(skillId, enabled) },
            )

            // Hidden Agent Display Test (ethOS only)
            if (viewModel.isPrivileged) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "AGENT DISPLAY",
                    color = primaryColor,
                    style = sectionTitleStyle,
                )
                Spacer(Modifier.height(8.dp))

                // AI toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ALLOW AI TO USE AGENT DISPLAY",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Text(
                            text = "Let the AI create a virtual display, launch apps, take screenshots, and interact with UI elements",
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                    }
                    Spacer(Modifier.width(rowControlSpacing))
                    DgenSquareSwitch(
                        checked = enabledSkills.contains("agent_display"),
                        onCheckedChange = { enabled ->
                            viewModel.toggleSkill("agent_display", enabled)
                        },
                        activeColor = primaryColor,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Test screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VIRTUAL DISPLAY TEST",
                            style = contentTitleStyle,
                            color = primaryColor,
                        )
                        Text(
                            text = "Test the AgentDisplay service (create display, launch apps, capture frames)",
                            style = contentBodyStyle,
                            color = dgenWhite,
                        )
                    }
                    Spacer(Modifier.width(rowControlSpacing))
                    DgenSmallPrimaryButton(
                        text = "Open",
                        primaryColor = primaryColor,
                        onClick = onNavigateToAgentDisplayTest,
                    )
                }
            }
        }

        SettingsSubScreen.ModelSelection -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(viewModel.availableModels) { model ->
                    SelectionRow(
                        text = model.name,
                        subtitle = model.modelId,
                        isSelected = model.modelId == selectedModel,
                        primaryColor = primaryColor,
                        onClick = {
                            viewModel.setSelectedModel(model.modelId)
                            currentSubScreen = SettingsSubScreen.Main
                        },
                    )
                }
            }
        }

        SettingsSubScreen.ProviderSelection -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(providerChoices) { provider ->
                    SelectionRow(
                        text = provider.displayName,
                        isSelected = provider == selectedProvider,
                        primaryColor = primaryColor,
                        onClick = {
                            viewModel.setSelectedProvider(provider)
                            currentSubScreen = SettingsSubScreen.Main
                        },
                    )
                }
            }
        }
        }
        }
    }
}

@Composable
private fun SelectionRow(
    text: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = SpaceMono,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                ),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = PitagonsSans,
                        fontSize = label_fontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = dgenWhite,
                    ),
                )
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(primaryColor, shape = CircleShape)
            )
        }
    }
}

private enum class SettingsSubScreen {
    Main,
    ModelSelection,
    ProviderSelection,
}
