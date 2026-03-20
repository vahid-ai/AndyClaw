package org.ethereumphone.andyclaw.agent

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.ethereumphone.andyclaw.ExecutionEngine.*
import org.ethereumphone.andyclaw.llm.ContentBlock
import org.ethereumphone.andyclaw.llm.ImageSource
import org.ethereumphone.andyclaw.llm.ToolResultContent
import org.ethereumphone.andyclaw.safety.SafetyLayer
import org.ethereumphone.andyclaw.skills.NativeSkillRegistry
import org.ethereumphone.andyclaw.skills.SkillResult
import org.ethereumphone.andyclaw.skills.Tier

/**
 * Builds a [ParallelExecutionEngine] from AgentLoop's existing dependencies.
 * Bridges between the engine's generic types and the app's concrete types
 * (SafetyLayer, NativeSkillRegistry, AgentLoop.Callbacks, etc.)
 */
object ExecutionEngineFactory {

    private const val TAG = "ExecEngineFactory"

    /**
     * Create an engine wired to the given skill registry, safety layer, and agent callbacks.
     */
    fun create(
        skillRegistry: NativeSkillRegistry,
        tier: Tier,
        enabledSkillIds: Set<String>,
        safetyLayer: SafetyLayer?,
        agentCallbacks: AgentLoop.Callbacks,
        budgetConfig: BudgetConfig?,
    ): ParallelExecutionEngine {
        val builder = EngineBuilder()
            .executor(createExecutor(skillRegistry, tier))
            .callbacks(createCallbacks(agentCallbacks, safetyLayer))

        // Pre-flight checks (order matters — matches original AgentLoop order)
        if (safetyLayer != null) {
            builder.addPreflightCheck(rateLimitCheck(safetyLayer))
            builder.addPreflightCheck(paramValidationCheck(safetyLayer))
        }
        builder.addPreflightCheck(permissionsCheck(skillRegistry, tier))
        builder.addPreflightCheck(approvalCheck(skillRegistry, tier))
        builder.addPreflightCheck(skillEnabledCheck(skillRegistry, tier, enabledSkillIds))

        // Post-processors
        if (safetyLayer != null) {
            builder.addPostProcessor(safetySanitizationProcessor(safetyLayer))
        }
        if (budgetConfig != null) {
            builder.addPostProcessor(truncationProcessor(budgetConfig))
        }

        return builder.build()
    }

    // ═══════════════════════════════════════════
    // ToolExecutor
    // ═══════════════════════════════════════════

    private fun createExecutor(registry: NativeSkillRegistry, tier: Tier): ToolExecutor =
        ToolExecutor { toolName, params ->
            val result = registry.executeTool(toolName, params, tier)
            when (result) {
                is SkillResult.Success -> ToolExecResult.Success(result.data)
                is SkillResult.ImageSuccess -> ToolExecResult.ImageSuccess(result.text, result.base64, result.mediaType)
                is SkillResult.Error -> ToolExecResult.Error(result.message)
                is SkillResult.RequiresApproval -> ToolExecResult.RequiresApproval(result.description)
            }
        }

    // ═══════════════════════════════════════════
    // Pre-flight checks
    // ═══════════════════════════════════════════

    private fun rateLimitCheck(safety: SafetyLayer) = PreflightCheck { call ->
        val msg = safety.checkRateLimit(call.name)
        if (msg != null) PreflightVerdict.Block(msg)
        else PreflightVerdict.Pass
    }

    private fun paramValidationCheck(safety: SafetyLayer) = PreflightCheck { call ->
        val validation = safety.validator.validateToolParams(call.input)
        if (!validation.isValid) {
            val reasons = validation.errors.joinToString("; ") { it.message }
            PreflightVerdict.Block(
                "[Safety] Tool '${call.name}' parameters rejected: $reasons. " +
                    "Disable safety mode in Settings to bypass this check."
            )
        } else {
            PreflightVerdict.Pass
        }
    }

    private fun permissionsCheck(registry: NativeSkillRegistry, tier: Tier) = PreflightCheck { call ->
        val toolDef = registry.getTools(tier).find { it.name == call.name }
        if (toolDef != null && toolDef.requiredPermissions.isNotEmpty()) {
            PreflightVerdict.NeedsPermissions(toolDef.requiredPermissions)
        } else {
            PreflightVerdict.Pass
        }
    }

    private fun approvalCheck(registry: NativeSkillRegistry, tier: Tier) = PreflightCheck { call ->
        val toolDef = registry.getTools(tier).find { it.name == call.name }
        if (toolDef?.requiresApproval == true) {
            PreflightVerdict.NeedsApproval("Tool '${call.name}' requires your approval to execute.")
        } else {
            PreflightVerdict.Pass
        }
    }

    private fun skillEnabledCheck(
        registry: NativeSkillRegistry,
        tier: Tier,
        enabledSkillIds: Set<String>,
    ) = PreflightCheck { call ->
        val owningSkill = registry.findSkillForTool(call.name, tier)
        if (owningSkill != null && owningSkill.id !in enabledSkillIds) {
            PreflightVerdict.Block(
                "Skill '${owningSkill.name}' is disabled. The user must enable it in Settings."
            )
        } else {
            PreflightVerdict.Pass
        }
    }

    // ═══════════════════════════════════════════
    // Post-processors
    // ═══════════════════════════════════════════

    private fun safetySanitizationProcessor(safety: SafetyLayer) = PostProcessor { call, result ->
        val rawContent = when (result) {
            is ToolExecResult.Success -> result.data
            is ToolExecResult.ImageSuccess -> result.text
            is ToolExecResult.Error -> return@PostProcessor PostProcessedResult(
                content = result.message,
                isError = true,
            )
            is ToolExecResult.RequiresApproval -> return@PostProcessor PostProcessedResult(
                content = result.description,
                isError = true,
            )
        }

        val safetyResult = safety.sanitizeToolOutput(call.name, rawContent)

        if (safetyResult.isBlocked) {
            return@PostProcessor PostProcessedResult(
                content = safetyResult.blockedReason ?: safetyResult.output,
                isError = true,
                blocked = true,
                blockedReason = safetyResult.blockedReason ?: safetyResult.output,
            )
        }

        val finalContent = if (safetyResult.wasModified || safety.isUntrustedTool(call.name)) {
            if (safety.isUntrustedTool(call.name)) {
                val sourceUrl = call.input["url"]?.jsonPrimitive?.contentOrNull
                    ?: call.input["query"]?.jsonPrimitive?.contentOrNull
                    ?: "unknown"
                safety.wrapUntrustedForLlm(call.name, safetyResult.output, sourceUrl)
            } else {
                safety.wrapForLlm(call.name, safetyResult.output, safetyResult.wasModified)
            }
        } else {
            safetyResult.output
        }

        val imageData = if (result is ToolExecResult.ImageSuccess) {
            ToolCallResult.ImageData(result.base64, result.mediaType)
        } else null

        PostProcessedResult(
            content = finalContent,
            isError = false,
            imageData = imageData,
            warnings = safetyResult.warnings,
        )
    }

    private fun truncationProcessor(budget: BudgetConfig) = PostProcessor { _, result ->
        val content = when (result) {
            is ToolExecResult.Success -> result.data
            is ToolExecResult.ImageSuccess -> return@PostProcessor PostProcessedResult(
                content = result.text,
                isError = false,
                imageData = ToolCallResult.ImageData(result.base64, result.mediaType),
            )
            is ToolExecResult.Error -> return@PostProcessor PostProcessedResult(
                content = result.message,
                isError = true,
            )
            is ToolExecResult.RequiresApproval -> return@PostProcessor PostProcessedResult(
                content = result.description,
                isError = true,
            )
        }

        val truncated = budget.truncateToolResult(content)
        PostProcessedResult(
            content = truncated,
            isError = false,
        )
    }

    // ═══════════════════════════════════════════
    // Callbacks bridge
    // ═══════════════════════════════════════════

    private fun createCallbacks(
        agentCallbacks: AgentLoop.Callbacks,
        safety: SafetyLayer?,
    ) = object : ExecutionCallbacks {

        override fun onToolStarted(toolName: String) {
            agentCallbacks.onToolExecution(toolName)
        }

        override fun onToolCompleted(toolName: String, result: ToolCallResult) {
            // Map back to SkillResult for the existing callback interface
            val img = result.imageData
            val skillResult = if (result.isError) {
                SkillResult.Error(result.content)
            } else if (img != null) {
                SkillResult.ImageSuccess(result.content, img.base64, img.mediaType)
            } else {
                SkillResult.Success(result.content)
            }
            agentCallbacks.onToolResult(toolName, skillResult)
        }

        override fun onToolBlocked(toolName: String, reason: String) {
            if (reason.startsWith("[Safety]")) {
                agentCallbacks.onSecurityBlock(toolName, reason)
            }
        }

        override suspend fun onApprovalNeeded(
            description: String,
            toolName: String?,
            toolInput: JsonObject?,
        ): Boolean = agentCallbacks.onApprovalNeeded(description, toolName, toolInput)

        override suspend fun onPermissionsNeeded(permissions: List<String>): Boolean =
            agentCallbacks.onPermissionsNeeded(permissions)
    }

    // ═══════════════════════════════════════════
    // Result conversion: ToolCallResult → ContentBlock.ToolResult
    // ═══════════════════════════════════════════

    /**
     * Convert engine results back to LLM-compatible ContentBlocks.
     * Called by AgentLoop to pack results into the conversation.
     */
    fun toContentBlocks(results: List<ToolCallResult>): List<ContentBlock> {
        return results.map { r ->
            val img = r.imageData
            if (img != null && !r.isError) {
                ContentBlock.ToolResult(
                    toolUseId = r.toolCallId,
                    content = r.content,
                    isError = r.isError,
                    contentBlocks = listOf(
                        ToolResultContent.Text(r.content),
                        ToolResultContent.Image(
                            ImageSource(
                                mediaType = img.mediaType,
                                data = img.base64,
                            )
                        ),
                    ),
                )
            } else {
                ContentBlock.ToolResult(
                    toolUseId = r.toolCallId,
                    content = r.content,
                    isError = r.isError,
                )
            }
        }
    }

    /**
     * Convert LLM tool use blocks to engine ToolCalls.
     */
    fun toToolCalls(toolUseBlocks: List<ContentBlock.ToolUseBlock>): List<ToolCall> {
        return toolUseBlocks.map { ToolCall(id = it.id, name = it.name, input = it.input) }
    }
}
