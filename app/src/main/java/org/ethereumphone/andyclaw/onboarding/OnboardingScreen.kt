package org.ethereumphone.andyclaw.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.example.dgenlibrary.DgenLoadingMatrix
import com.example.dgenlibrary.ui.theme.dgenOcean
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.ethereumphone.andyclaw.llm.LlmProvider
import org.ethereumphone.andyclaw.skills.AndyClawSkill
import org.ethereumphone.andyclaw.skills.Tier
import org.ethereumphone.andyclaw.skills.tier.OsCapabilities
import org.ethereumphone.andyclaw.ui.components.ChadBackground
import org.ethereumphone.andyclaw.ui.components.RetroButton
import org.ethereumphone.andyclaw.ui.components.RetroCard
import org.ethereumphone.andyclaw.ui.components.RetroTextField

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val error by viewModel.error.collectAsState()

    val isPrivileged = viewModel.isPrivileged
    val walletAddress by viewModel.walletAddress.collectAsState()
    val isSigning by viewModel.isSigning.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val tinfoilApiKey by viewModel.tinfoilApiKey.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val customName by viewModel.customName.collectAsState()
    val values by viewModel.values.collectAsState()
    val yoloMode by viewModel.yoloMode.collectAsState()
    val selectedSkills by viewModel.selectedSkills.collectAsState()

    val totalSteps = viewModel.totalSteps
    val primaryColor = MaterialTheme.colorScheme.primary

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    ChadBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Step counter
            Text(
                text = "[ STEP ${currentStep + 1} / $totalSteps ]",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = primaryColor,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(color = primaryColor.copy(alpha = 0.6f), offset = Offset.Zero, blurRadius = 8f),
                ),
            )

            // Progress bar as a thin accent line
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(primaryColor.copy(alpha = 0.2f))) {
                Box(
                    Modifier
                        .fillMaxWidth((currentStep + 1) / totalSteps.toFloat())
                        .height(2.dp)
                        .background(primaryColor),
                )
            }

            Spacer(Modifier.height(24.dp))

            // Step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn())
                            .togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn())
                            .togetherWith(slideOutHorizontally { it } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_step",
            ) { step ->
                val canAdvance = when (currentStep) {
                    0 -> if (isPrivileged) {
                        walletAddress.isNotBlank()
                    } else {
                        when (selectedProvider) {
                            LlmProvider.OPEN_ROUTER -> apiKey.isNotBlank()
                            LlmProvider.TINFOIL -> tinfoilApiKey.isNotBlank()
                            LlmProvider.LOCAL,
                            LlmProvider.ETHOS_PREMIUM -> true
                        }
                    }
                    1 -> goals.isNotBlank()
                    else -> true
                }
                val onNext = { if (canAdvance) viewModel.nextStep() }

                when (step) {
                    0 -> if (isPrivileged) {
                        StepWalletSign(
                            walletAddress = walletAddress,
                            isSigning = isSigning,
                            onSign = { viewModel.signWithWallet() },
                        )
                    } else {
                        StepProviderSelection(
                            selectedProvider = selectedProvider,
                            apiKey = apiKey,
                            tinfoilApiKey = tinfoilApiKey,
                            onProviderSelected = { viewModel.selectedProvider.value = it },
                            onApiKeyChange = { viewModel.apiKey.value = it },
                            onTinfoilApiKeyChange = { viewModel.tinfoilApiKey.value = it },
                            onNext = onNext,
                        )
                    }
                    1 -> StepGoals(goals, onNext = onNext) { viewModel.goals.value = it }
                    2 -> StepName(customName, onNext = onNext) { viewModel.customName.value = it }
                    3 -> StepValues(values, onNext = onNext) { viewModel.values.value = it }
                    4 -> StepPermissions(
                        skills = viewModel.registeredSkills,
                        yoloMode = yoloMode,
                        selectedSkills = selectedSkills,
                        onYoloModeChange = { viewModel.setYoloMode(it) },
                        onToggleSkill = { id, enabled -> viewModel.toggleSkill(id, enabled) },
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (currentStep > 0) {
                    RetroButton(
                        onClick = { viewModel.previousStep() },
                        enabled = !isSubmitting,
                    ) {
                        Text(
                            text = "< BACK",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = primaryColor,
                        )
                    }
                } else {
                    Spacer(Modifier)
                }

                if (currentStep < totalSteps - 1) {
                    RetroButton(
                        onClick = { viewModel.nextStep() },
                        enabled = when (currentStep) {
                            0 -> if (isPrivileged) {
                                walletAddress.isNotBlank()
                            } else {
                                when (selectedProvider) {
                                    LlmProvider.OPEN_ROUTER -> apiKey.isNotBlank()
                                    LlmProvider.TINFOIL -> tinfoilApiKey.isNotBlank()
                                    LlmProvider.LOCAL,
                                    LlmProvider.ETHOS_PREMIUM -> true
                                }
                            }
                            1 -> goals.isNotBlank()
                            else -> true
                        },
                    ) {
                        Text(
                            text = "NEXT >",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = primaryColor,
                        )
                    }
                } else {
                    RetroButton(
                        onClick = { viewModel.submit(onComplete) },
                        enabled = !isSubmitting,
                    ) {
                        if (isSubmitting) {
                            DgenLoadingMatrix(
                                size = 20.dp,
                                LEDSize = 5.dp,
                                activeLEDColor = primaryColor,
                                unactiveLEDColor = dgenOcean,
                            )
                        } else {
                            Text(
                                text = ">>> LAUNCH <<<",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = primaryColor,
                            )
                        }
                    }
                }
            }
        }

        // Snackbar
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SnackbarHost(snackbarHostState)
        }
    }
}

// --- Step composables ---

@Composable
private fun SectionTitle(text: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Text(
        text = ">>> $text <<<",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = primaryColor,
        style = MaterialTheme.typography.headlineLarge.copy(
            shadow = Shadow(color = primaryColor.copy(alpha = 0.8f), offset = Offset.Zero, blurRadius = 12f),
        ),
    )
}

@Composable
private fun SectionDescription(text: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = primaryColor.copy(alpha = 0.7f),
        lineHeight = 20.sp,
    )
}

@Composable
private fun StepWalletSign(
    walletAddress: String,
    isSigning: Boolean,
    onSign: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column {
        SectionTitle("WALLET SIGN-IN")
        Spacer(Modifier.height(8.dp))
        SectionDescription("Sign a message to verify your identity. This lets us track your usage for billing.")
        Spacer(Modifier.height(24.dp))

        if (walletAddress.isNotBlank()) {
            val truncated = walletAddress.take(6) + "..." + walletAddress.takeLast(4)
            Text(
                text = "SIGNED AS $truncated",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = primaryColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(color = primaryColor.copy(alpha = 0.8f), offset = Offset.Zero, blurRadius = 8f),
                ),
            )
        } else {
            RetroButton(
                onClick = onSign,
                enabled = !isSigning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSigning) {
                    DgenLoadingMatrix(
                        size = 20.dp,
                        LEDSize = 5.dp,
                        activeLEDColor = primaryColor,
                        unactiveLEDColor = dgenOcean,
                    )
                } else {
                    Text(
                        text = "SIGN",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = primaryColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepProviderSelection(
    selectedProvider: LlmProvider,
    apiKey: String,
    tinfoilApiKey: String,
    onProviderSelected: (LlmProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onTinfoilApiKeyChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("CHOOSE AI PROVIDER")
        Spacer(Modifier.height(8.dp))
        SectionDescription("Select how your AI assistant processes requests. You can change this later in Settings.")
        Spacer(Modifier.height(16.dp))

        ProviderCard(
            label = "ON-DEVICE (QWEN2.5-1.5B)",
            description = "Best privacy — runs entirely on your phone. No data leaves the device.",
            isSelected = selectedProvider == LlmProvider.LOCAL,
            onClick = { onProviderSelected(LlmProvider.LOCAL) },
        )
        Spacer(Modifier.height(8.dp))
        ProviderCard(
            label = "TINFOIL TEE",
            description = "Best balance — cloud inference inside a verified Trusted Execution Environment.",
            isSelected = selectedProvider == LlmProvider.TINFOIL,
            onClick = { onProviderSelected(LlmProvider.TINFOIL) },
        )
        Spacer(Modifier.height(8.dp))
        ProviderCard(
            label = "OPENROUTER",
            description = "Best performance — cloud inference via OpenRouter. Fast and capable.",
            isSelected = selectedProvider == LlmProvider.OPEN_ROUTER,
            onClick = { onProviderSelected(LlmProvider.OPEN_ROUTER) },
        )

        Spacer(Modifier.height(16.dp))

        when (selectedProvider) {
            LlmProvider.OPEN_ROUTER -> {
                RetroTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "sk-or-v1-...",
                    singleLine = true,
                )
            }
            LlmProvider.TINFOIL -> {
                RetroTextField(
                    value = tinfoilApiKey,
                    onValueChange = onTinfoilApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "tf-...",
                    singleLine = true,
                )
            }
            LlmProvider.LOCAL,
            LlmProvider.ETHOS_PREMIUM -> {
                Text(
                    text = "No API key needed. The model (~2.5 GB) will be downloaded after setup.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = primaryColor.copy(alpha = 0.6f),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (isSelected) primaryColor.copy(alpha = 0.8f) else primaryColor.copy(alpha = 0.5f),
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun StepGoals(value: String, onNext: () -> Unit, onValueChange: (String) -> Unit) {
    Column {
        SectionTitle("SET YOUR GOALS")
        Spacer(Modifier.height(8.dp))
        SectionDescription("Tell your AI what you'd like help with on your dGEN1.")
        Spacer(Modifier.height(16.dp))
        RetroTextField(
            value = value,
            onValueChange = { onValueChange(it.replace("\n", " ")) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "e.g. Help me manage my crypto portfolio...",
            minLines = 4,
            maxLines = 8,
        )
    }
}

@Composable
private fun StepName(value: String, onNext: () -> Unit, onValueChange: (String) -> Unit) {
    Column {
        SectionTitle("NAME YOUR AI")
        Spacer(Modifier.height(8.dp))
        SectionDescription("Give your AI a custom name, or keep the generated one.")
        Spacer(Modifier.height(16.dp))
        RetroTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun StepValues(value: String, onNext: () -> Unit, onValueChange: (String) -> Unit) {
    Column {
        SectionTitle("YOUR VALUES")
        Spacer(Modifier.height(8.dp))
        SectionDescription("Share your values and priorities so your AI can align with what's important to you.")
        Spacer(Modifier.height(16.dp))
        RetroTextField(
            value = value,
            onValueChange = { onValueChange(it.replace("\n", " ")) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "e.g. Privacy, decentralization, security...",
            minLines = 4,
            maxLines = 8,
        )
    }
}

@Composable
private fun StepPermissions(
    skills: List<AndyClawSkill>,
    yoloMode: Boolean,
    selectedSkills: Set<String>,
    onYoloModeChange: (Boolean) -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
) {
    val tier = OsCapabilities.currentTier()
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("PERMISSIONS")
        Spacer(Modifier.height(8.dp))
        SectionDescription("Choose which capabilities your AI can use. You can change these later in Settings.")

        Spacer(Modifier.height(16.dp))

        // YOLO mode card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (yoloMode) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
                .border(
                    width = if (yoloMode) 2.dp else 1.dp,
                    color = if (yoloMode) primaryColor else primaryColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YOLO MODE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = primaryColor,
                    )
                    Text(
                        text = "Give your AI full access to all device capabilities. Tool usage will be auto-approved.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = primaryColor.copy(alpha = 0.6f),
                        lineHeight = 18.sp,
                    )
                }
                Switch(
                    checked = yoloMode,
                    onCheckedChange = onYoloModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = primaryColor,
                        checkedTrackColor = primaryColor.copy(alpha = 0.3f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "INDIVIDUAL SKILLS",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = primaryColor.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        for (skill in skills) {
            SkillRow(
                skill = skill,
                tier = tier,
                enabled = if (yoloMode) true else skill.id in selectedSkills,
                disabledByYolo = yoloMode,
                onToggle = { onToggleSkill(skill.id, it) },
            )
        }
    }
}

@Composable
private fun SkillRow(
    skill: AndyClawSkill,
    tier: Tier,
    enabled: Boolean,
    disabledByYolo: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = primaryColor,
            )
            val baseToolCount = skill.baseManifest.tools.size
            val privToolCount = skill.privilegedManifest?.tools?.size ?: 0
            val toolText = buildString {
                append("$baseToolCount base tool${if (baseToolCount != 1) "s" else ""}")
                if (privToolCount > 0) {
                    append(", $privToolCount privileged")
                    if (tier != Tier.PRIVILEGED) append(" (locked)")
                }
            }
            Text(
                text = toolText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = primaryColor.copy(alpha = 0.5f),
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = !disabledByYolo,
            colors = SwitchDefaults.colors(
                checkedThumbColor = primaryColor,
                checkedTrackColor = primaryColor.copy(alpha = 0.3f),
            ),
        )
    }
}
