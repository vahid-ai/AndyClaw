package org.ethereumphone.andyclaw.agent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
import org.ethereumphone.andyclaw.skills.Tier

class AgentLoop(
    private val client: LlmClient,
    private val skillRegistry: NativeSkillRegistry,
    private val tier: Tier,
    private val enabledSkillIds: Set<String> = emptySet(),
    private val model: AnthropicModels = AnthropicModels.MINIMAX_M25,
    private val aiName: String? = null,
    private val userStory: String? = null,
    private val soulContent: String? = null,
    private val memoryManager: MemoryManager? = null,
    private val safetyLayer: SafetyLayer? = null,
    private val smartRouter: SmartRouter? = null,
    private val budgetConfig: BudgetConfig? = null,
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_ITERATIONS = 100
        private const val DEFAULT_SUBAGENT_ITERATIONS = 30
        private const val MAX_SUBAGENT_ITERATIONS = 50
        private const val KEEP_RECENT_IMAGES = 2
        private const val MEMORY_CONTEXT_MAX_RESULTS = 3
        private const val MEMORY_CONTEXT_MIN_SCORE = 0.25f
        internal const val SPAWN_SUBAGENT_TOOL_NAME = "spawn_subagent"

        /**
         * Builds the spawn_subagent tool JSON for the LLM tool list.
         * The model calls this tool when it decides to delegate a subtask to
         * a focused sub-agent with its own routing and tool set.
         */
        fun buildSpawnSubagentToolJson(): JsonObject = buildJsonObject {
            put("name", SPAWN_SUBAGENT_TOOL_NAME)
            put("description", buildString {
                // Core purpose
                append("Delegate a subtask to a focused sub-agent with its own tools. ")
                append("Each sub-agent gets independently routed tools and an isolated context. ")
                append("Call multiple times in one response to run sub-agents in parallel.\n\n")
                // When to use — two valid triggers
                append("USE when EITHER condition is met:\n\n")
                append("1. PARALLEL INDEPENDENT TASKS: The request contains 2+ independent tasks needing ")
                append("different skill domains that can run in parallel (no task depends on another's result) ")
                append("and you do NOT already have the tools needed for both.\n\n")
                append("2. CONTEXT-HEAVY TASKS: A task will generate large intermediate data (screenshots, ")
                append("UI trees, long documents) that would pollute your context window. The sub-agent ")
                append("handles the heavy work and returns only the final result. ")
                append("Prime example: virtual screen / agent_display tasks — navigating apps produces ")
                append("many screenshots and UI dumps that you don't need in your conversation history.\n\n")
                // When NOT to use — critical negative guidance
                append("NEVER use when:\n")
                append("- You can handle it with your current tools in a few calls\n")
                append("- Tasks are sequential steps of one workflow (e.g. look up contact then send SMS)\n")
                append("- Tasks share the same skill set (use parallel tool calls instead)\n")
                append("- The task is simple and won't generate heavy intermediate context\n")
                append("- You are unsure whether to use it (default: do NOT use it)\n\n")
                // Cost awareness
                append("Sub-agents are expensive (extra LLM calls + routing). ")
                append("Parallel tool calls in your own context are always cheaper and faster for lightweight tasks.")
            })
            putJsonObject("input_schema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("task") {
                        put("type", "string")
                        put("description", "Self-contained instruction with all needed context. The sub-agent cannot see your conversation history, so include names, numbers, and details explicitly.")
                    }
                    putJsonObject("max_iterations") {
                        put("type", "integer")
                        put("description", "Max tool-call rounds (default $DEFAULT_SUBAGENT_ITERATIONS, max $MAX_SUBAGENT_ITERATIONS). Increase for tasks that read large documents or require many sequential steps.")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("task")) }
            }
        }

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

        // Search memory for relevant context to inject into the system prompt.
        val memoryContext = fetchMemoryContext(userMessage)

        val budget = budgetConfig
        val systemPrompt = buildString {
            append(PromptAssembler.assembleSystemPrompt(
                skills, tier, aiName, userStory,
                soulContent = soulContent,
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
        val allToolsJson = PromptAssembler.assembleTools(skills, tier, nameResolver, allowedTools).toMutableList()
        // Add the spawn_subagent tool so the model can delegate subtasks
        if (smartRouter != null) {
            allToolsJson.add(buildSpawnSubagentToolJson())
        }
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

                // Separate spawn_subagent calls from regular tool calls
                val subagentCalls = toolUseBlocks.filter { it.name == SPAWN_SUBAGENT_TOOL_NAME }
                val regularCalls = toolUseBlocks.filter { it.name != SPAWN_SUBAGENT_TOOL_NAME }

                // Execute regular tools and sub-agents in parallel
                val allToolResults = mutableListOf<ContentBlock>()

                coroutineScope {
                    // Regular tools via ExecutionEngine
                    val regularResultsDeferred = if (regularCalls.isNotEmpty()) {
                        async {
                            val engine = ExecutionEngineFactory.create(
                                skillRegistry = skillRegistry,
                                tier = tier,
                                enabledSkillIds = enabledSkillIds,
                                safetyLayer = safety,
                                agentCallbacks = callbacks,
                                budgetConfig = budget,
                            )
                            val engineCalls = ExecutionEngineFactory.toToolCalls(regularCalls)
                            val batchResult = engine.executeBatch(engineCalls)
                            val engineMetrics = batchResult.metrics
                            Log.i(TAG, "ExecutionEngine | ${engineMetrics.executedCount} executed, " +
                                "${engineMetrics.blockedCount} blocked, ${engineMetrics.errorCount} errors, " +
                                "${engineMetrics.totalDurationMs}ms total (max tool: ${engineMetrics.maxToolDurationMs}ms)")
                            ExecutionEngineFactory.toContentBlocks(batchResult.results)
                        }
                    } else null

                    // Sub-agent calls — each runs its own routing + mini agent loop
                    val subagentResultsDeferreds = subagentCalls.map { call ->
                        async {
                            val taskDesc = call.input["task"]?.jsonPrimitive?.contentOrNull ?: "unknown task"
                            val iterLimit = call.input["max_iterations"]?.jsonPrimitive?.contentOrNull
                                ?.toIntOrNull()
                                ?.coerceIn(1, MAX_SUBAGENT_ITERATIONS)
                                ?: DEFAULT_SUBAGENT_ITERATIONS
                            Log.i(TAG, "spawn_subagent: '${taskDesc.take(80)}' (id=${call.id}, maxIter=$iterLimit)")
                            try {
                                val result = runSubagent(taskDesc, conversationHistory, callbacks, iterLimit)
                                ContentBlock.ToolResult(
                                    toolUseId = call.id,
                                    content = result,
                                    isError = false,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "spawn_subagent failed for '${taskDesc.take(60)}': ${e.message}", e)
                                ContentBlock.ToolResult(
                                    toolUseId = call.id,
                                    content = "Sub-agent error: ${e.message}",
                                    isError = true,
                                )
                            }
                        }
                    }

                    // Await all results
                    regularResultsDeferred?.await()?.let { allToolResults.addAll(it) }
                    subagentResultsDeferreds.awaitAll().let { allToolResults.addAll(it) }
                }

                // Add tool results as user message
                val imageCount = allToolResults.count { (it as? ContentBlock.ToolResult)?.contentBlocks != null }
                val totalBase64 = allToolResults.sumOf { block ->
                    (block as? ContentBlock.ToolResult)?.contentBlocks
                        ?.filterIsInstance<ToolResultContent.Image>()
                        ?.sumOf { it.source.data.length } ?: 0
                }
                Log.i("AGENT_VIRTUAL_SCREEN", "AgentLoop: adding ${allToolResults.size} tool results as user message, imageCount=$imageCount, totalBase64Chars=$totalBase64")
                messages.add(Message("user", MessageContent.Blocks(allToolResults)))
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

    // ── Sub-agent execution (model-driven delegation) ──────────────

    /**
     * Runs a sub-agent for a delegated task. The sub-agent gets its own routing
     * via [SmartRouter] (so it discovers exactly the tools it needs), its own
     * system prompt scoped to those tools, and a mini agent loop capped at
     * [MAX_SUBAGENT_ITERATIONS].
     *
     * Called when the main model invokes the `spawn_subagent` tool.
     */
    private suspend fun runSubagent(
        taskDescription: String,
        conversationHistory: List<Message>,
        callbacks: Callbacks,
        maxIterations: Int = DEFAULT_SUBAGENT_ITERATIONS,
    ): String {
        // Route skills/tools specifically for this subtask
        val subagentRouting = smartRouter?.routeSkillsWithLlm(
            taskDescription, enabledSkillIds, tier, emptySet(),
        )
        val subagentSkillIds = subagentRouting?.skillIds ?: enabledSkillIds
        val subagentAllowedTools = subagentRouting?.allowedTools
        val subagentSkills = skillRegistry.getEnabled(subagentSkillIds)
        val effectiveModelId = subagentRouting?.modelIdOverride ?: model.modelId
        val baseMaxTokens = subagentRouting?.maxTokensOverride ?: model.maxTokens

        Log.i(TAG, "Subagent routing: '${taskDescription.take(60)}' -> ${subagentSkillIds.size} skills" +
            (subagentAllowedTools?.let { ", ${it.size} tools" } ?: ", all tools"))

        val nameResolver: (String, String) -> String = { skillId, name ->
            skillRegistry.getEffectiveName(skillId, name)
        }

        val systemPrompt = buildString {
            append(PromptAssembler.assembleSystemPrompt(
                subagentSkills, tier, aiName, userStory,
                safetyEnabled = safetyLayer?.config?.enabled == true,
                sessionNonce = safetyLayer?.sessionNonce,
                concisePrompt = true,
                parallelToolCalls = true,
                noPreambleToolCalls = true,
            ))
            append("\n\nIMPORTANT: Complete ONLY this specific task. Be brief and direct.")
        }

        val toolsJson = PromptAssembler.assembleTools(subagentSkills, tier, nameResolver, subagentAllowedTools)

        // Sub-agent gets a copy of conversation history for context + its focused task
        val messages = conversationHistory.toMutableList()
        messages.add(Message.user(taskDescription))

        val fullText = StringBuilder()
        val subagentMaxTokens = (baseMaxTokens / 4).coerceAtLeast(512)

        for (iteration in 1..maxIterations) {
            val request = MessagesRequest(
                model = effectiveModelId,
                maxTokens = subagentMaxTokens,
                system = systemPrompt,
                messages = messages,
                tools = toolsJson.takeIf { it.isNotEmpty() },
                stream = true,
                parallelToolCalls = true,
            )

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

            // Check for tool calls (sub-agents cannot spawn further sub-agents)
            val toolUseBlocks = responseBlocks.filterIsInstance<ContentBlock.ToolUseBlock>()
            if (toolUseBlocks.isEmpty()) break

            Log.i(TAG, "Subagent iteration $iteration: ${toolUseBlocks.size} tool call(s): ${toolUseBlocks.joinToString { it.name }}")

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

        Log.i(TAG, "Subagent complete for '${taskDescription.take(60)}': ${fullText.length} chars")
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
