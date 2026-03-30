package org.ethereumphone.andyclaw.ExecutionEngine

/**
 * Unified result from tool execution.
 * Maps 1:1 to SkillResult variants but lives in the engine's own type system
 * so the engine has no dependency on the skill layer.
 */
sealed class ToolExecResult {
    data class Success(val data: String) : ToolExecResult()
    data class ImageSuccess(
        val text: String,
        val base64: String,
        val mediaType: String = "image/jpeg",
    ) : ToolExecResult()
    data class Error(val message: String) : ToolExecResult()
    data class RequiresApproval(val description: String) : ToolExecResult()
}
