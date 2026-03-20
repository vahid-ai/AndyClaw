package org.ethereumphone.andyclaw.ExecutionEngine

/**
 * Result of executing a batch of tool calls.
 * [results] are in the same order as the input tool calls.
 */
data class ExecutionBatchResult(
    val results: List<ToolCallResult>,
    val metrics: ExecutionMetrics,
) {
    /** True if all tools completed without errors. */
    val allSucceeded: Boolean get() = results.none { it.isError }

    /** True if any tool was actually executed (not just blocked). */
    val hadExecutions: Boolean get() = results.any {
        it.phase == ToolCallResult.Phase.EXECUTED ||
        it.phase == ToolCallResult.Phase.EXECUTED_AFTER_APPROVAL
    }
}
