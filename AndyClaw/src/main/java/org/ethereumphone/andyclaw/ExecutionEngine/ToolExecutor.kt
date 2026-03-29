package org.ethereumphone.andyclaw.ExecutionEngine

import kotlinx.serialization.json.JsonObject

/**
 * Abstraction over tool execution. The engine doesn't know about
 * NativeSkillRegistry directly — it just needs something that can
 * run a tool by name and return a result.
 */
fun interface ToolExecutor {
    suspend fun execute(toolName: String, params: JsonObject): ToolExecResult
}
