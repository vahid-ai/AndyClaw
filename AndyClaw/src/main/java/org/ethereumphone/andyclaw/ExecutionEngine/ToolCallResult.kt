package org.ethereumphone.andyclaw.ExecutionEngine

/**
 * The final processed result for a single tool call.
 * Contains everything needed to build the response message back to the LLM.
 */
data class ToolCallResult(
    val toolCallId: String,
    val toolName: String,
    val content: String,
    val isError: Boolean,
    val imageData: ImageData? = null,
    val phase: Phase = Phase.EXECUTED,
) {
    data class ImageData(
        val base64: String,
        val mediaType: String,
    )

    enum class Phase {
        BLOCKED_PREFLIGHT,   // failed safety/permission/approval before execution
        EXECUTED,            // ran normally
        EXECUTED_AFTER_APPROVAL, // ran after RequiresApproval flow
    }
}
