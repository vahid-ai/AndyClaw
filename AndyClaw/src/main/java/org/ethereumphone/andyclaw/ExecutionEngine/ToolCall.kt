package org.ethereumphone.andyclaw.ExecutionEngine

import kotlinx.serialization.json.JsonObject

/**
 * A single tool invocation request.
 * This is the engine's input — provider-agnostic, no LLM types.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val input: JsonObject,
)
