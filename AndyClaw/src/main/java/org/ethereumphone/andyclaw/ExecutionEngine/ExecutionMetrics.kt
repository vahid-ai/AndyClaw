package org.ethereumphone.andyclaw.ExecutionEngine

/**
 * Metrics collected from a single batch execution.
 */
data class ExecutionMetrics(
    val totalTools: Int,
    val executedCount: Int,
    val blockedCount: Int,
    val errorCount: Int,
    val parallelBatches: Int,
    val totalDurationMs: Long,
    val maxToolDurationMs: Long,
    val perToolMs: Map<String, Long>,
)
