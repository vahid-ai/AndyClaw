package org.ethereumphone.andyclaw.ui.settings

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.dgenlibrary.DgenLoadingMatrix
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import org.ethereumphone.andyclaw.ui.components.DgenCursorTextfield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.label_fontSize
import kotlinx.coroutines.Job
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ethereumphone.andyclaw.telegram.TelegramBotClient
import org.ethereumphone.andyclaw.ui.components.DgenBackNavigationBackground
import org.ethereumphone.andyclaw.ui.components.DgenSmallPrimaryButton

private const val TAG = "TelegramOnboarding"

private enum class TelegramSubScreen {
    EnterToken,
    VerifyCode,
}

@Composable
fun TelegramOnboardingDialog(
    onComplete: (token: String, ownerChatId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentSubScreen by remember { mutableStateOf(TelegramSubScreen.EnterToken) }
    var token by remember { mutableStateOf("") }

    var pendingChatId by remember { mutableStateOf<Long?>(null) }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var enteredCode by remember { mutableStateOf("") }
    var isPolling by remember { mutableStateOf(false) }
    var waitingForMessage by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    var pollingJob by remember { mutableStateOf<Job?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { SystemColorManager.refresh(context) }
    val primaryColor = SystemColorManager.primaryColor
    val secondaryColor = SystemColorManager.secondaryColor

    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )
    val contentTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )
    val contentBodyStyle = TextStyle(
        fontFamily = PitagonsSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.body(primaryColor),
    )

    DisposableEffect(Unit) {
        onDispose {
            pollingJob?.cancel()
        }
    }

    Dialog(
        onDismissRequest = {
            pollingJob?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DgenBackNavigationBackground(
            title = when (currentSubScreen) {
                TelegramSubScreen.EnterToken -> "Telegram Bot Setup"
                TelegramSubScreen.VerifyCode -> "Verify Ownership"
            },
            primaryColor = primaryColor,
            onNavigateBack = {
                when (currentSubScreen) {
                    TelegramSubScreen.EnterToken -> {
                        pollingJob?.cancel()
                        onDismiss()
                    }
                    TelegramSubScreen.VerifyCode -> {
                        pollingJob?.cancel()
                        isPolling = false
                        waitingForMessage = true
                        errorMessage = null
                        currentSubScreen = TelegramSubScreen.EnterToken
                    }
                }
            },
        ) {
            Crossfade(targetState = currentSubScreen, label = "telegram_crossfade") { screen ->
                when (screen) {
                    TelegramSubScreen.EnterToken -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "STEP 1: ENTER BOT TOKEN",
                                style = sectionTitleStyle,
                                color = primaryColor,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Create a Telegram bot and get its token:",
                                style = contentBodyStyle,
                                color = dgenWhite,
                            )

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "INSTRUCTIONS",
                                style = contentTitleStyle,
                                color = primaryColor,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "1. Open Telegram and search for @BotFather\n" +
                                    "2. Send /newbot and follow the prompts\n" +
                                    "3. Copy the bot token BotFather gives you\n" +
                                    "4. Paste it below",
                                style = contentBodyStyle,
                                color = dgenWhite.copy(alpha = 0.8f),
                            )

                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
                            Spacer(Modifier.height(16.dp))

                            DgenCursorTextfield(
                                value = token,
                                onValueChange = { token = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = "Bot Token",
                                placeholder = { Text("Enter your bot token", color = dgenWhite.copy(alpha = 0.3f), style = contentBodyStyle.copy(shadow = GlowStyle.placeholder(dgenWhite))) },
                                visualTransformation = PasswordVisualTransformation(),
                                primaryColor = primaryColor,
                            )
                            Spacer(Modifier.height(24.dp))
                            DgenSmallPrimaryButton(
                                text = "Next",
                                primaryColor = primaryColor,
                                enabled = token.isNotBlank(),
                                onClick = {
                                    currentSubScreen = TelegramSubScreen.VerifyCode
                                    val client = TelegramBotClient(token = { token.trim() })
                                    isPolling = true
                                    waitingForMessage = true
                                    errorMessage = null
                                    pollingJob = scope.launch {
                                        pollForVerification(
                                            client = client,
                                            onMessageReceived = { chatId, code ->
                                                pendingChatId = chatId
                                                pendingCode = code
                                                waitingForMessage = false
                                            },
                                            onError = { msg ->
                                                errorMessage = msg
                                                isPolling = false
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }

                    TelegramSubScreen.VerifyCode -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "STEP 2: VERIFY OWNERSHIP",
                                style = sectionTitleStyle,
                                color = primaryColor,
                            )
                            Spacer(Modifier.height(16.dp))

                            if (waitingForMessage) {
                                Text(
                                    text = "Open Telegram and send any message to your bot.",
                                    style = contentBodyStyle,
                                    color = dgenWhite,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "WAITING FOR YOUR MESSAGE...",
                                    style = contentTitleStyle,
                                    color = primaryColor,
                                )
                                Spacer(Modifier.height(16.dp))
                                DgenLoadingMatrix(
                                    size = 24.dp,
                                    LEDSize = 6.dp,
                                    activeLEDColor = primaryColor,
                                    unactiveLEDColor = secondaryColor,
                                )
                            } else {
                                Text(
                                    text = "A verification code was sent to your Telegram chat. Enter it below to confirm you own this bot.",
                                    style = contentBodyStyle,
                                    color = dgenWhite,
                                )

                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
                                Spacer(Modifier.height(16.dp))

                                DgenCursorTextfield(
                                    value = enteredCode,
                                    onValueChange = { enteredCode = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "Verification Code",
                                    primaryColor = primaryColor,
                                )
                                Spacer(Modifier.height(24.dp))
                                DgenSmallPrimaryButton(
                                    text = "Verify",
                                    primaryColor = primaryColor,
                                    enabled = enteredCode.isNotBlank(),
                                    onClick = {
                                        if (enteredCode.equals(pendingCode, ignoreCase = true)) {
                                            pollingJob?.cancel()
                                            onComplete(token.trim(), pendingChatId!!)
                                        } else {
                                            errorMessage = "Code does not match. Please try again."
                                        }
                                    },
                                )
                            }

                            if (errorMessage != null) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = contentBodyStyle,
                                    color = Color(0xFFFF6B6B),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars.random() }.joinToString("")
}

private suspend fun pollForVerification(
    client: TelegramBotClient,
    onMessageReceived: (chatId: Long, code: String) -> Unit,
    onError: (String) -> Unit,
) {
    var offset: Long? = null
    try {
        val stale = client.getUpdates(offset = null, timeout = 0)
        if (stale.isNotEmpty()) {
            offset = stale.last().updateId + 1
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to flush stale updates: ${e.message}")
    }

    var attempts = 0
    val maxAttempts = 60
    while (kotlinx.coroutines.currentCoroutineContext().isActive && attempts < maxAttempts) {
        attempts++
        try {
            val updates = client.getUpdates(offset = offset, timeout = 30)
            if (updates.isNotEmpty()) {
                val first = updates.first()
                offset = updates.last().updateId + 1

                val code = generateCode()
                val sent = client.sendMessage(
                    first.chatId,
                    "Your verification code is: *$code*\n\nEnter this code in the AndyClaw app to complete setup.",
                )
                if (sent) {
                    onMessageReceived(first.chatId, code)
                    return
                } else {
                    onError("Failed to send verification code. Check your bot token.")
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Polling error: ${e.message}")
            delay(3000)
        }
    }
    onError("Timed out waiting for a message. Please try again.")
}
