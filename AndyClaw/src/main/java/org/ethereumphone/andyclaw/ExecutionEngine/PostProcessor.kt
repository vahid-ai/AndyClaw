package org.ethereumphone.andyclaw.ExecutionEngine

/**
 * Pluggable post-processing step applied to each tool result after execution.
 * Use for safety sanitization, output truncation, wrapping untrusted content, etc.
 *
 * Post-processors run in order. Each one transforms the result and passes it
 * to the next.
 */
fun interface PostProcessor {
    fun process(call: ToolCall, result: ToolExecResult): PostProcessedResult
}

data class PostProcessedResult(
    val content: String,
    val isError: Boolean,
    val imageData: ToolCallResult.ImageData? = null,
    val blocked: Boolean = false,
    val blockedReason: String? = null,
    val warnings: List<String> = emptyList(),
)
