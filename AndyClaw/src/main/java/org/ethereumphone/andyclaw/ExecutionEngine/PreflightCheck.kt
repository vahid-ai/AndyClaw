package org.ethereumphone.andyclaw.ExecutionEngine

/**
 * Pluggable pre-flight checks that run sequentially before tool execution.
 * Each check can pass, block, or skip (let the next check decide).
 *
 * Pre-flight checks run in order. The first one that returns BLOCK or
 * NEEDS_APPROVAL stops the chain for that tool.
 */
fun interface PreflightCheck {
    suspend fun check(call: ToolCall): PreflightVerdict
}

sealed class PreflightVerdict {
    /** Tool passed this check, continue to next check or execution. */
    data object Pass : PreflightVerdict()

    /** Tool is blocked. Do not execute. Return this error to the LLM. */
    data class Block(val reason: String) : PreflightVerdict()

    /**
     * Tool requires user confirmation before execution.
     * The engine will call [ExecutionCallbacks.onApprovalNeeded].
     * If denied, the tool is blocked.
     */
    data class NeedsApproval(val description: String) : PreflightVerdict()

    /**
     * Tool requires Android runtime permissions.
     * The engine will call [ExecutionCallbacks.onPermissionsNeeded].
     * If denied, the tool is blocked.
     */
    data class NeedsPermissions(val permissions: List<String>) : PreflightVerdict()
}
