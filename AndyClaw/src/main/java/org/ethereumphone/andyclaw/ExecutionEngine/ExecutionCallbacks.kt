package org.ethereumphone.andyclaw.ExecutionEngine

import kotlinx.serialization.json.JsonObject

/**
 * Callbacks for the execution engine to communicate with the UI/host.
 * These mirror AgentLoop.Callbacks but are scoped to execution only.
 */
interface ExecutionCallbacks {
    /** Called when a tool starts executing. */
    fun onToolStarted(toolName: String)

    /** Called when a tool completes (success or error). */
    fun onToolCompleted(toolName: String, result: ToolCallResult)

    /** Called when a tool is blocked by a pre-flight check. */
    fun onToolBlocked(toolName: String, reason: String)

    /** User must approve before tool can execute. Returns true if approved. */
    suspend fun onApprovalNeeded(
        description: String,
        toolName: String? = null,
        toolInput: JsonObject? = null,
    ): Boolean

    /** User must grant Android permissions. Returns true if granted. */
    suspend fun onPermissionsNeeded(permissions: List<String>): Boolean
}
