package org.ethereumphone.andyclaw.agent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import org.ethereumphone.andyclaw.llm.AnthropicModels
import org.ethereumphone.andyclaw.llm.LlmClient
import org.ethereumphone.andyclaw.llm.ContentBlock
import org.ethereumphone.andyclaw.llm.Message
import org.ethereumphone.andyclaw.llm.ToolResultContent
import org.ethereumphone.andyclaw.llm.MessageContent
import org.ethereumphone.andyclaw.llm.MessagesRequest
import org.ethereumphone.andyclaw.llm.MessagesResponse
import org.ethereumphone.andyclaw.llm.StreamingCallback
import org.ethereumphone.andyclaw.llm.Verbosity
import org.ethereumphone.andyclaw.memory.MemoryManager
import org.ethereumphone.andyclaw.safety.SafetyLayer
import org.ethereumphone.andyclaw.skills.NativeSkillRegistry
import org.ethereumphone.andyclaw.skills.PromptAssembler
import org.ethereumphone.andyclaw.skills.SkillResult
import org.ethereumphone.andyclaw.skills.RoutingResult
import org.ethereumphone.andyclaw.skills.RoutingBudget
import org.ethereumphone.andyclaw.skills.SmartRouter
import org.ethereumphone.andyclaw.skills.Subtask
import org.ethereumphone.andyclaw.skills.Tier

class AgentLoop(
    private val client: LlmClient,
    private val skillRegistry: NativeSkillRegistry,
    private val tier: Tier,
    private val enabledSkillIds: Set<String> = emptySet(),
    private val model: AnthropicModels = AnthropicModels.MINIMAX_M25,
    private val aiName: String? = null,
    private val userStory: String? = null,
    private val memoryManager: MemoryManager? = null,
    private val safetyLayer: SafetyLayer? = null,
    private val smartRouter: SmartRouter? = null,
    private val budgetConfig: BudgetConfig? = null,
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_ITERATIONS = 100
        private const val MAX_SUBTASK_ITERATIONS = 10
        private const val KEEP_RECENT_IMAGES = 2
        private const val MEMORY_CONTEXT_MAX_RESULTS = 3
        private const val MEMORY_CONTEXT_MIN_SCORE = 0.25f

        /**
         * Strip image data from older tool results, keeping only the [keep] most
         * recent image-bearing results. Older images are replaced with a text-only
         * placeholder so the LLM still knows a screenshot was taken.
         *
         * Mirrors Anthropic's `only_n_most_recent_images` strategy from their
         * computer-use reference implementation.
         */
        fun pruneOldImages(messages: MutableList<Message>, keep: Int = KEEP_RECENT_IMAGES) {
            // Collect indices of messages that contain image-bearing tool results.
            data class ImageLocation(val msgIndex: Int, val blockIndex: Int)

            val locations = mutableListOf<ImageLocation>()
            for ((mi, msg) in messages.withIndex()) {
                val blocks = (msg.content as? MessageContent.Blocks)?.blocks ?: continue
                for ((bi, block) in blocks.withIndex()) {
                    if (block is ContentBlock.ToolResult && block.contentBlocks != null) {
                        locations.add(ImageLocation(mi, bi))
                    }
                }
            }

            Log.d("AGENT_VIRTUAL_SCREEN", "pruneOldImages: found ${locations.size} image-bearing tool results, keep=$keep")
            if (locations.size <= keep) {
                Log.d("AGENT_VIRTUAL_SCREEN", "pruneOldImages: nothing to strip (${locations.size} <= $keep)")
                return
            }

            // Keep the last `keep` entries, strip the rest.
            val toStrip = locations.dropLast(keep)
            Log.i("AGENT_VIRTUAL_SCREEN", "pruneOldImages: STRIPPING ${toStrip.size} old image(s), keeping $keep most recent")
            // Group by message index so we rebuild each affected message once.
            for ((msgIndex, locs) in toStrip.groupBy { it.msgIndex }) {
                val msg = messages[msgIndex]
                val blocks = (msg.content as MessageContent.Blocks).blocks.toMutableList()
                val stripSet = locs.map { it.blockIndex }.toSet()
                for (bi in stripSet) {
                    val tr = blocks[bi] as ContentBlock.ToolResult
                    val base64Len = tr.contentBlocks?.filterIsInstance<ToolResultContent.Image>()?.sumOf { it.source.data.length } ?: 0
                    Log.d("AGENT_VIRTUAL_SCREEN", "pruneOldImages: stripping image at msg[$msgIndex] block[$bi] base64Len=$base64Len")
                    blocks[bi] = tr.copy(
                        content = tr.content + " [image removed from history to save bandwidth]",
                        contentBlocks = null,
                    )
                }
                messages[msgIndex] = msg.copy(content = MessageContent.Blocks(blocks))
            }
            Log.d(TAG, "pruneOldImages: stripped ${toStrip.size} old image(s), kept $keep most recent")
        }
    }

    interface Callbacks {
        fun onToken(text: String)
        fun onToolExecution(toolName: String)
        fun onToolResult(toolName: String, result: SkillResult, input: JsonObject? = null)
        fun onSecurityBlock(toolName: String, reason: String) {}
        suspend fun onApprovalNeeded(
            description: String,
            toolName: String? = null,
            toolInput: JsonObject? = null,
        ): Boolean
        suspend fun onPermissionsNeeded(permissions: List<String>): Boolean
        fun onComplete(fullText: String)
        fun onError(error: Throwable)
    }

    suspend fun run(userMessage: String, conversationHistory: List<Message>, callbacks: Callbacks) {
        val safety = safetyLayer

        // Scan inbound message for secrets when safety is enabled
        if (safety != null) {
            val inboundCheck = safety.scanInboundForSecrets(userMessage)
            if (inboundCheck.isFailure) {
                callbacks.onError(inboundCheck.exceptionOrNull()!!)
                return
            }
        }

        // Route to minimal skill set based on user message + conversation context.
        // The runtime tool guard (below) still uses the full enabledSkillIds so
        // multi-turn tool calls from previously-routed skills still work.
        val previousToolNames = conversationHistory
            .filter { it.role == "assistant" }
            .takeLast(3)
            .flatMap { msg ->
                (msg.content as? MessageContent.Blocks)?.blocks
                    ?.filterIsInstance<ContentBlock.ToolUseBlock>()
                    ?.map { it.name }
                    ?: emptyList()
            }.toSet()
        val routingResult = smartRouter?.routeSkillsWithLlm(userMessage, enabledSkillIds, tier, previousToolNames)
        val routedSkillIds = routingResult?.skillIds ?: enabledSkillIds
        val allowedTools = routingResult?.allowedTools
        val routerBudget = routingResult?.budget
        val modelIdOverride = routingResult?.modelIdOverride
        val maxTokensOverride = routingResult?.maxTokensOverride
        val skills = skillRegistry.getEnabled(routedSkillIds)
        Log.d(TAG, "TokenStats | routed ${routedSkillIds.size}/${enabledSkillIds.size} skills" +
            (allowedTools?.let { ", ${it.size} tools filtered" } ?: ", no tool filtering"))

        // Task decomposition: only decompose when subtasks need different skills
        // or have dependencies. When all subtasks share the same skill set (e.g.
        // two web_search calls), the main loop handles it better with parallel
        // tool calls in a single LLM response — no extra LLM overhead.
        val subtasks = routingResult?.subtasks ?: emptyList()
        val shouldDecompose = subtasks.size >= 2 && (
            subtasks.any { it.dependsOn.isNotEmpty() } ||
            subtasks.map { it.skills }.distinct().size > 1
        )
        if (shouldDecompose) {
            Log.i(TAG, "Task decomposition: ${subtasks.size} subtasks with different skills/deps, entering multi-task path")
            try {
                runWithSubtasks(subtasks, userMessage, conversationHistory, callbacks, routingResult!!)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                callbacks.onError(e)
            } finally {
                try { skillRegistry.cleanupAll() } catch (e: Exception) {
                    Log.w(TAG, "cleanupAll failed: ${e.message}", e)
                }
            }
            return
        }
        if (subtasks.size >= 2) {
            Log.i(TAG, "Task decomposition: ${subtasks.size} subtasks share same skills, using main loop instead")
        }

        // Search memory for relevant context to inject into the system prompt.
        val memoryContext = fetchMemoryContext(userMessage)

        val budget = budgetConfig
        val systemPrompt = buildString {
            append(PromptAssembler.assembleSystemPrompt(
                skills, tier, aiName, userStory,
                safetyEnabled = safety?.config?.enabled == true,
                sessionNonce = safety?.sessionNonce,
                concisePrompt = budget?.preset?.concisePrompt == true,
                parallelToolCalls = budget?.preset?.parallelToolCalls == true,
                noPreambleToolCalls = budget?.preset?.noPreambleToolCalls == true,
            ))
            if (memoryContext.isNotBlank()) {
                appendLine()
                appendLine("## Relevant Memories")
                appendLine("The following context was retrieved from long-term memory and may be relevant:")
                appendLine()
                append(memoryContext)
                appendLine()
            }
        }

        val nameResolver: (String, String) -> String = { skillId, name ->
            skillRegistry.getEffectiveName(skillId, name)
        }
        Log.d(TAG, "TokenStats | systemPrompt=${systemPrompt.length} chars, ~${systemPrompt.length / 4} tokens (est)")
        val allToolsJson = PromptAssembler.assembleTools(skills, tier, nameResolver, allowedTools)
        val toolsJson = if (client.maxToolCount > 0 && allToolsJson.size > client.maxToolCount) {
            Log.d(TAG, "Trimming tools from ${allToolsJson.size} to ${client.maxToolCount} for constrained provider")
            allToolsJson.take(client.maxToolCount)
        } else {
            allToolsJson
        }

        val messages = conversationHistory.toMutableList()
        messages.add(Message.user(userMessage))

        var iterations = 0
        val fullText = StringBuilder()
        var totalInputTokens = 0
        var totalOutputTokens = 0
        var totalCacheReadTokens = 0
        var totalCacheWriteTokens = 0
        var totalTokensSavedByMaxTokens = 0
        var totalCharsTruncated = 0
        var truncationCount = 0

        // Resolve effective model ID and max tokens (model routing may override)
        val effectiveModelId = modelIdOverride ?: model.modelId
        val baseMaxTokens = maxTokensOverride ?: model.maxTokens
        if (modelIdOverride != null) {
            Log.i(TAG, "ModelRouting | override active: $modelIdOverride (default was ${model.modelId}), maxTokens=$baseMaxTokens (default was ${model.maxTokens})")
        }

        try {
            Log.i(TAG, "=== AgentLoop.run starting === model=$effectiveModelId" +
                (if (modelIdOverride != null) " [ROUTED from ${model.modelId}]" else "") +
                ", maxTokens=$baseMaxTokens, toolsJson=${toolsJson.size}, historySize=${conversationHistory.size}")
            if (budget != null) {
                val p = budget.preset
                val active = listOfNotNull(
                    if (p.dynamicMaxTokens) "dynamicMaxTokens" else null,
                    if (p.concisePrompt) "concisePrompt" else null,
                    if (p.parallelToolCalls) "parallelToolCalls" else null,
                    if (p.noPreambleToolCalls) "noPreambleToolCalls" else null,
                    if (p.historySummarization) "historySummarization" else null,
                    if (p.thinkingBudget) "thinkingBudget" else null,
                    if (p.toolResultTruncation) "toolResultTruncation(${p.toolResultMaxChars})" else null,
                )
                Log.i(TAG, "BudgetMode | preset=${p.name} (${p.id}), routerBudget=$routerBudget, active=[${active.joinToString()}]")
            } else {
                Log.i(TAG, "BudgetMode | disabled")
            }

            while (iterations < MAX_ITERATIONS) {
                iterations++
                Log.i(TAG, "--- AgentLoop iteration $iterations/$MAX_ITERATIONS ---")

                pruneOldImages(messages)

                val effectiveMaxTokens = budget?.effectiveMaxTokens(
                    modelDefault = baseMaxTokens,
                    iteration = iterations,
                    routerBudget = routerBudget,
                ) ?: baseMaxTokens
                if (effectiveMaxTokens < baseMaxTokens) {
                    totalTokensSavedByMaxTokens += baseMaxTokens - effectiveMaxTokens
                }

                // Map budget concisePrompt to verbosity parameter
                val verbosity = if (budget?.preset?.concisePrompt == true) {
                    Verbosity.LOW
                } else null

                val request = MessagesRequest(
                    model = effectiveModelId,
                    maxTokens = effectiveMaxTokens,
                    system = systemPrompt,
                    messages = messages,
                    tools = toolsJson.takeIf { it.isNotEmpty() },
                    stream = true,
                    parallelToolCalls = budget?.preset?.parallelToolCalls != false,
                    verbosity = verbosity,
                )

                val responseBlocks = mutableListOf<ContentBlock>()
                val streamText = StringBuilder()

                val streamCallback = object : StreamingCallback {
                    override fun onToken(text: String) {
                        streamText.append(text)
                        fullText.append(text)
                        callbacks.onToken(text)
                    }

                    override fun onToolUse(id: String, name: String, input: JsonObject) {
                        // Collected via onComplete
                    }

                    override fun onComplete(response: MessagesResponse) {
                        responseBlocks.addAll(response.content)
                        response.usage?.let { u ->
                            totalInputTokens += u.inputTokens
                            totalOutputTokens += u.outputTokens
                            totalCacheReadTokens += u.cacheReadTokens
                            totalCacheWriteTokens += u.cacheWriteTokens
                            val cacheInfo = if (u.cacheReadTokens > 0 || u.cacheWriteTokens > 0) {
                                " cache_read=${u.cacheReadTokens} cache_write=${u.cacheWriteTokens}"
                            } else ""
                            Log.d(TAG, "TokenStats | input=${u.inputTokens} output=${u.outputTokens} total=${u.inputTokens + u.outputTokens} iteration=$iterations tools=${toolsJson.size} maxTokens=$effectiveMaxTokens$cacheInfo")
                        }
                    }

                    override fun onError(error: Throwable) {
                        callbacks.onError(error)
                    }
                }

                Log.i(TAG, "Sending streaming request to LLM (iteration $iterations, messages=${messages.size})...")
                val iterStartMs = System.currentTimeMillis()
                client.streamMessage(request, streamCallback)
                val iterElapsedMs = System.currentTimeMillis() - iterStartMs
                Log.i(TAG, "LLM stream complete (iteration $iterations): ${iterElapsedMs}ms, ${streamText.length} chars streamed, ${responseBlocks.size} content blocks")

                // Scan LLM response for leaked secrets before displaying
                if (safety != null && streamText.isNotEmpty()) {
                    val responseCheck = safety.scanLlmResponse(streamText.toString())
                    if (responseCheck.isBlocked) {
                        Log.w(TAG, "LLM response blocked by safety: ${responseCheck.blockedReason}")
                    }
                    for (warning in responseCheck.warnings) {
                        Log.w(TAG, "Safety warning in LLM response: $warning")
                    }
                }

                // Add assistant message to conversation
                if (responseBlocks.isNotEmpty()) {
                    messages.add(Message.assistant(responseBlocks))
                }

                // Check for tool_use blocks
                val toolUseBlocks = responseBlocks.filterIsInstance<ContentBlock.ToolUseBlock>()
                if (toolUseBlocks.isEmpty()) {
                    Log.i(TAG, "No tool calls in response, agent loop complete after $iterations iteration(s). Total text: ${fullText.length} chars")
                    logRunSummary(iterations, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalTokensSavedByMaxTokens, totalCharsTruncated, truncationCount, budget)
                    callbacks.onComplete(fullText.toString())
                    return
                }
                Log.i(TAG, "LLM requested ${toolUseBlocks.size} tool call(s): ${toolUseBlocks.joinToString { it.name }}")

                // Execute tool calls in parallel via ExecutionEngine
                val engine = ExecutionEngineFactory.create(
                    skillRegistry = skillRegistry,
                    tier = tier,
                    enabledSkillIds = enabledSkillIds,
                    safetyLayer = safety,
                    agentCallbacks = callbacks,
                    budgetConfig = budget,
                )
                val engineCalls = ExecutionEngineFactory.toToolCalls(toolUseBlocks)
                val batchResult = engine.executeBatch(engineCalls)
                val finalToolResults = ExecutionEngineFactory.toContentBlocks(batchResult.results)

                // Track truncation/budget stats from engine metrics
                val engineMetrics = batchResult.metrics
                Log.i(TAG, "ExecutionEngine | ${engineMetrics.executedCount} executed, " +
                    "${engineMetrics.blockedCount} blocked, ${engineMetrics.errorCount} errors, " +
                    "${engineMetrics.totalDurationMs}ms total (max tool: ${engineMetrics.maxToolDurationMs}ms)")

                // Add tool results as user message
                val imageCount = finalToolResults.count { (it as? ContentBlock.ToolResult)?.contentBlocks != null }
                val totalBase64 = finalToolResults.sumOf { block ->
                    (block as? ContentBlock.ToolResult)?.contentBlocks
                        ?.filterIsInstance<ToolResultContent.Image>()
                        ?.sumOf { it.source.data.length } ?: 0
                }
                Log.i("AGENT_VIRTUAL_SCREEN", "AgentLoop: adding ${finalToolResults.size} tool results as user message, imageCount=$imageCount, totalBase64Chars=$totalBase64")
                messages.add(Message("user", MessageContent.Blocks(finalToolResults)))
            }

            // Max iterations reached
            logRunSummary(iterations, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalTokensSavedByMaxTokens, totalCharsTruncated, truncationCount, budget)
            callbacks.onComplete(fullText.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callbacks.onError(e)
        } finally {
            // Release any resources skills may still hold (e.g. a virtual display
            // that the LLM never destroyed because of a crash or cancellation).
            try {
                skillRegistry.cleanupAll()
            } catch (e: Exception) {
                Log.w(TAG, "cleanupAll failed: ${e.message}", e)
            }
        }
    }

    // ── Task decomposition: multi-subtask execution ────────────────

    /**
     * Executes decomposed subtasks in topological waves (independent subtasks
     * run in parallel, dependent ones wait for predecessors), then synthesizes
     * all results into a single streamed response.
     */
    private suspend fun runWithSubtasks(
        subtasks: List<Subtask>,
        userMessage: String,
        conversationHistory: List<Message>,
        callbacks: Callbacks,
        routingResult: RoutingResult,
    ) {
        val effectiveModelId = routingResult.modelIdOverride ?: model.modelId
        val baseMaxTokens = routingResult.maxTokensOverride ?: model.maxTokens

        val waves = topologicalWaves(subtasks)
        val subtaskResults = mutableMapOf<String, String>()

        for ((waveIndex, wave) in waves.withIndex()) {
            Log.i(TAG, "Subtask wave ${waveIndex + 1}/${waves.size}: ${wave.map { it.id }}")

            // Emit progress indicators (not streamed LLM tokens)
            for (subtask in wave) {
                callbacks.onToken("[${subtask.id}] ${subtask.description}...\n")
            }

            if (wave.size == 1) {
                val subtask = wave[0]
                try {
                    val result = runSubtask(
                        subtask, conversationHistory, subtaskResults,
                        effectiveModelId, baseMaxTokens, callbacks,
                    )
                    subtaskResults[subtask.id] = result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Subtask '${subtask.id}' failed: ${e.message}", e)
                    subtaskResults[subtask.id] = "Error: ${e.message}"
                }
            } else {
                // Multiple independent subtasks — run in parallel
                coroutineScope {
                    val results = wave.map { subtask ->
                        async {
                            try {
                                subtask.id to runSubtask(
                                    subtask, conversationHistory, subtaskResults,
                                    effectiveModelId, baseMaxTokens, callbacks,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Subtask '${subtask.id}' failed: ${e.message}", e)
                                subtask.id to "Error: ${e.message}"
                            }
                        }
                    }.awaitAll()
                    results.forEach { (id, result) -> subtaskResults[id] = result }
                }
            }
        }

        // Stream the synthesis response to the user
        callbacks.onToken("\n")
        val synthesisText = synthesizeResponse(userMessage, subtasks, subtaskResults, effectiveModelId, baseMaxTokens, callbacks)

        // Build the complete text for conversation history: subtask results +
        // synthesis. The synthesis is what the user sees, but the raw results
        // are needed for follow-up turns (the LLM needs the data).
        val completeText = buildString {
            for (subtask in subtasks) {
                val result = subtaskResults[subtask.id] ?: "(failed)"
                append("[${subtask.id}] ${subtask.description}:\n$result\n\n")
            }
            append(synthesisText)
        }
        callbacks.onComplete(completeText)
    }

    /**
     * Topological sort of subtasks into execution waves.
     * Each wave contains subtasks whose dependencies are all in earlier waves.
     * Falls back to a single wave on cycle detection.
     */
    private fun topologicalWaves(subtasks: List<Subtask>): List<List<Subtask>> {
        val remaining = subtasks.toMutableList()
        val completed = mutableSetOf<String>()
        val waves = mutableListOf<List<Subtask>>()

        while (remaining.isNotEmpty()) {
            val wave = remaining.filter { subtask ->
                subtask.dependsOn.all { it in completed }
            }
            if (wave.isEmpty()) {
                Log.w(TAG, "Subtask dependency cycle detected, running remaining as single wave")
                waves.add(remaining.toList())
                break
            }
            waves.add(wave)
            completed.addAll(wave.map { it.id })
            remaining.removeAll(wave.toSet())
        }

        return waves
    }

    /**
     * Runs a single subtask through a focused agent loop: builds a system prompt
     * scoped to the subtask's skills/tools, then iterates LLM → tool execution
     * until the subtask is complete (or hits the iteration cap).
     *
     * Uses [LlmClient.streamMessage] (same path as main loop) to ensure
     * consistent serialization — tool events are forwarded to [callbacks].
     */
    private suspend fun runSubtask(
        subtask: Subtask,
        conversationHistory: List<Message>,
        dependencyResults: Map<String, String>,
        effectiveModelId: String,
        baseMaxTokens: Int,
        callbacks: Callbacks,
    ): String {
        val subtaskSkillIds = subtask.skills.ifEmpty { enabledSkillIds }
        val subtaskSkills = skillRegistry.getEnabled(subtaskSkillIds)

        val nameResolver: (String, String) -> String = { skillId, name ->
            skillRegistry.getEffectiveName(skillId, name)
        }

        val systemPrompt = buildString {
            append(PromptAssembler.assembleSystemPrompt(
                subtaskSkills, tier, aiName, userStory,
                safetyEnabled = safetyLayer?.config?.enabled == true,
                sessionNonce = safetyLayer?.sessionNonce,
                concisePrompt = true,
                parallelToolCalls = true,
                noPreambleToolCalls = true,
            ))
            append("\n\nIMPORTANT: Complete ONLY this specific task. Be brief and direct.")
        }

        val allowedTools = subtask.tools.takeIf { it.isNotEmpty() }
        val toolsJson = PromptAssembler.assembleTools(subtaskSkills, tier, nameResolver, allowedTools)

        // Build the subtask prompt, injecting dependency results when available
        val subtaskPrompt = buildString {
            append(subtask.description)
            val depContext = subtask.dependsOn.mapNotNull { depId ->
                dependencyResults[depId]?.let { result ->
                    "Previous step result ($depId): $result"
                }
            }
            if (depContext.isNotEmpty()) {
                append("\n\nContext from completed steps:\n")
                append(depContext.joinToString("\n"))
            }
        }

        val messages = conversationHistory.toMutableList()
        messages.add(Message.user(subtaskPrompt))

        val fullText = StringBuilder()
        val subtaskMaxTokens = (baseMaxTokens / 4).coerceAtLeast(512)

        for (iteration in 1..MAX_SUBTASK_ITERATIONS) {
            val request = MessagesRequest(
                model = effectiveModelId,
                maxTokens = subtaskMaxTokens,
                system = systemPrompt,
                messages = messages,
                tools = toolsJson.takeIf { it.isNotEmpty() },
                stream = true,
                parallelToolCalls = true,
            )

            // Use streamMessage (same path as main loop) to ensure the
            // assistant response is serialized identically on re-send.
            val responseBlocks = mutableListOf<ContentBlock>()
            var streamError: Throwable? = null
            client.streamMessage(request, object : StreamingCallback {
                override fun onToken(text: String) { /* buffered, not streamed to user */ }
                override fun onToolUse(id: String, name: String, input: JsonObject) {}
                override fun onComplete(response: MessagesResponse) {
                    responseBlocks.addAll(response.content)
                }
                override fun onError(error: Throwable) { streamError = error }
            })
            streamError?.let { throw it }

            if (responseBlocks.isNotEmpty()) {
                messages.add(Message.assistant(responseBlocks))
            }

            // Collect text output
            responseBlocks.filterIsInstance<ContentBlock.TextBlock>().forEach {
                fullText.append(it.text)
            }

            // Check for tool calls
            val toolUseBlocks = responseBlocks.filterIsInstance<ContentBlock.ToolUseBlock>()
            if (toolUseBlocks.isEmpty()) break

            Log.i(TAG, "Subtask '${subtask.id}' iteration $iteration: ${toolUseBlocks.size} tool call(s): ${toolUseBlocks.joinToString { it.name }}")

            // Execute tools via the standard engine
            val engine = ExecutionEngineFactory.create(
                skillRegistry = skillRegistry,
                tier = tier,
                enabledSkillIds = enabledSkillIds,
                safetyLayer = safetyLayer,
                agentCallbacks = callbacks,
                budgetConfig = budgetConfig,
            )
            val engineCalls = ExecutionEngineFactory.toToolCalls(toolUseBlocks)
            val batchResult = engine.executeBatch(engineCalls)
            val toolResults = ExecutionEngineFactory.toContentBlocks(batchResult.results)

            messages.add(Message("user", MessageContent.Blocks(toolResults)))
        }

        Log.i(TAG, "Subtask '${subtask.id}' complete: ${fullText.length} chars")
        return fullText.toString()
    }

    /**
     * Makes a final LLM call to synthesize all subtask results into a single
     * conversational response. Streams tokens to the user via [callbacks.onToken]
     * and returns the synthesis text (caller handles [onComplete]).
     */
    private suspend fun synthesizeResponse(
        userMessage: String,
        subtasks: List<Subtask>,
        results: Map<String, String>,
        effectiveModelId: String,
        baseMaxTokens: Int,
        callbacks: Callbacks,
    ): String {
        val synthesisPrompt = buildString {
            append("The user asked: \"$userMessage\"\n\n")
            append("I completed the following tasks:\n")
            for (subtask in subtasks) {
                val result = results[subtask.id] ?: "(failed)"
                append("- ${subtask.description}: $result\n")
            }
            append("\nProvide a brief, unified response summarizing what was done. Be conversational and concise.")
        }

        val request = MessagesRequest(
            model = effectiveModelId,
            maxTokens = (baseMaxTokens / 4).coerceAtLeast(512),
            system = "You are a helpful phone assistant. Summarize completed tasks conversationally and concisely.",
            messages = listOf(Message.user(synthesisPrompt)),
            stream = true,
        )

        val fullText = StringBuilder()
        val streamCallback = object : StreamingCallback {
            override fun onToken(text: String) {
                fullText.append(text)
                callbacks.onToken(text)
            }
            override fun onToolUse(id: String, name: String, input: JsonObject) {}
            override fun onComplete(response: MessagesResponse) {}
            override fun onError(error: Throwable) { callbacks.onError(error) }
        }

        client.streamMessage(request, streamCallback)
        return fullText.toString()
    }

    private fun logRunSummary(
        iterations: Int,
        totalInput: Int,
        totalOutput: Int,
        cacheRead: Int,
        cacheWrite: Int,
        maxTokensSaved: Int,
        charsTruncated: Int,
        truncations: Int,
        budget: BudgetConfig?,
    ) {
        val total = totalInput + totalOutput
        Log.i(TAG, "=== AgentLoop SUMMARY === iterations=$iterations | input=$totalInput output=$totalOutput total=$total")
        if (cacheRead > 0 || cacheWrite > 0) {
            Log.i(TAG, "  cache: read=$cacheRead write=$cacheWrite (saved ~${(cacheRead * 0.9f).toInt()} input tokens at 90% discount)")
        }
        if (maxTokensSaved > 0) {
            Log.i(TAG, "  dynamicMaxTokens: reduced output budget by $maxTokensSaved tokens across $iterations iteration(s)")
        }
        if (truncations > 0) {
            Log.i(TAG, "  toolResultTruncation: truncated $truncations tool result(s), removed $charsTruncated chars (~${charsTruncated / 4} tokens est)")
        }
        if (budget != null) {
            val p = budget.preset
            val promptExtras = listOfNotNull(
                if (p.concisePrompt) "concisePrompt" else null,
                if (p.parallelToolCalls) "parallelToolCalls" else null,
                if (p.noPreambleToolCalls) "noPreambleToolCalls" else null,
            )
            if (promptExtras.isNotEmpty()) {
                Log.i(TAG, "  promptOptimizations: [${promptExtras.joinToString()}] (active in system prompt)")
            }
        }
    }

    /**
     * Search long-term memory for snippets relevant to the user's message.
     *
     * Returns a formatted string suitable for injection into the system prompt,
     * or blank if no relevant memories are found (or no memory manager is set).
     */
    private suspend fun fetchMemoryContext(userMessage: String): String {
        val manager = memoryManager ?: run {
            Log.d(TAG, "fetchMemoryContext: no MemoryManager set, skipping")
            return ""
        }

        return try {
            Log.d(TAG, "fetchMemoryContext: searching for context, query=\"${userMessage.take(80)}\"")
            val results = manager.search(
                query = userMessage,
                maxResults = MEMORY_CONTEXT_MAX_RESULTS,
                minScore = MEMORY_CONTEXT_MIN_SCORE,
            )
            if (results.isEmpty()) {
                Log.d(TAG, "fetchMemoryContext: no relevant memories found")
                return ""
            }

            Log.i(TAG, "fetchMemoryContext: injecting ${results.size} memory/memories into system prompt")
            results.joinToString("\n") { result ->
                buildString {
                    append("- ${result.snippet}")
                    if (result.tags.isNotEmpty()) {
                        append(" [${result.tags.joinToString(", ")}]")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchMemoryContext: search failed: ${e.message}", e)
            ""
        }
    }
}
