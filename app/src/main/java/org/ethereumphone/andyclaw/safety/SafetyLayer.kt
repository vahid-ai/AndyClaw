package org.ethereumphone.andyclaw.safety

import android.util.Log

data class SafetyConfig(
    val enabled: Boolean = false,
    val maxOutputLength: Int = 100_000,
    val rateLimitEnabled: Boolean = true,
)

/**
 * Central orchestrator for all runtime safety checks.
 *
 * When [config].enabled is false (YOLO mode / default), all methods are
 * transparent pass-throughs — no scanning, no blocking, no wrapping.
 *
 * When enabled, the pipeline is:
 *   length check → leak scan → policy check → sanitize → XML wrap
 */
class SafetyLayer(
    val config: SafetyConfig = SafetyConfig(),
    val sanitizer: Sanitizer = Sanitizer(),
    val validator: Validator = Validator(),
    val policy: Policy = Policy(),
    val leakDetector: LeakDetector = LeakDetector(),
    val rateLimiter: RateLimiter = RateLimiter(),
) {

    /**
     * Result of processing tool output through the safety pipeline.
     * When safety is disabled, [blockedReason] is null and [output] is the raw content.
     */
    data class ToolOutputResult(
        val output: String,
        val wasModified: Boolean,
        val blockedReason: String?,
        val warnings: List<String>,
    ) {
        val isBlocked: Boolean get() = blockedReason != null
    }

    /**
     * Process raw tool output through the full safety pipeline.
     *
     * Returns [ToolOutputResult] which carries the (possibly sanitized) content,
     * whether it was blocked, and human-readable warnings.
     */
    fun sanitizeToolOutput(toolName: String, rawOutput: String): ToolOutputResult {
        if (!config.enabled) {
            return ToolOutputResult(rawOutput, wasModified = false, blockedReason = null, warnings = emptyList())
        }

        val warnings = mutableListOf<String>()
        var content = rawOutput

        // 1. Length check
        if (content.length > config.maxOutputLength) {
            content = content.take(config.maxOutputLength) + "\n[Truncated: output exceeded ${config.maxOutputLength} characters]"
            warnings.add("Output truncated to ${config.maxOutputLength} characters")
        }

        // 2. Leak detection
        val leakResult = leakDetector.scanAndClean(content)
        if (leakResult.isFailure) {
            val reason = "[Safety] Tool '$toolName' output blocked — potential secret leakage detected. " +
                    "Disable safety mode in Settings to bypass this check."
            Log.w(TAG, reason)
            return ToolOutputResult(
                output = reason,
                wasModified = true,
                blockedReason = reason,
                warnings = warnings,
            )
        }
        content = leakResult.getOrThrow()

        // 3. Policy check
        val policyResult = policy.check(content)
        if (policyResult.shouldBlock) {
            val reasons = policyResult.blockReasons.joinToString("; ")
            val reason = "[Safety] Tool '$toolName' output blocked by policy: $reasons. " +
                    "Disable safety mode in Settings to bypass this check."
            Log.w(TAG, reason)
            return ToolOutputResult(
                output = reason,
                wasModified = true,
                blockedReason = reason,
                warnings = warnings,
            )
        }
        policyResult.violations.filter { it.action == PolicyAction.WARN }.forEach {
            warnings.add("Policy warning (${it.ruleId}): ${it.description}")
        }

        // 4. Sanitize (if policy demands it, or always for injection patterns)
        val forceSanitize = policyResult.shouldSanitize
        val sanitized = sanitizer.sanitize(content)
        if (forceSanitize || sanitized.wasModified) {
            content = sanitized.content
        }
        sanitized.warnings.forEach {
            warnings.add("Injection pattern: ${it.description} (${it.severity.displayName})")
        }

        return ToolOutputResult(
            output = content,
            wasModified = sanitized.wasModified || content != rawOutput,
            blockedReason = null,
            warnings = warnings,
        )
    }

    /**
     * Wrap sanitized tool output in XML delimiters for the LLM context.
     * When safety is disabled, returns the content as-is.
     */
    fun wrapForLlm(toolName: String, content: String, wasSanitized: Boolean): String {
        if (!config.enabled) return content

        val escaped = content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return "<tool_output name=\"$toolName\" sanitized=\"$wasSanitized\">\n$escaped\n</tool_output>"
    }

    /**
     * Scan an inbound user message for accidentally included secrets.
     * Returns a failure with a user-friendly message if secrets are found.
     */
    fun scanInboundForSecrets(message: String): Result<Unit> {
        if (!config.enabled) return Result.success(Unit)

        val result = leakDetector.scan(message)
        if (result.shouldBlock) {
            return Result.failure(
                SecurityException(
                    "[Safety] Your message appears to contain sensitive credentials " +
                            "(${result.blockReasons.joinToString()}). Please remove them before sending. " +
                            "Disable safety mode in Settings to bypass this check."
                )
            )
        }
        return Result.success(Unit)
    }

    /**
     * Scan an LLM response before showing it to the user.
     * Redacts secrets or blocks the response entirely.
     */
    fun scanLlmResponse(response: String): ToolOutputResult {
        if (!config.enabled) {
            return ToolOutputResult(response, wasModified = false, blockedReason = null, warnings = emptyList())
        }

        val result = leakDetector.scan(response)
        val warnings = result.matches.map { "Detected ${it.patternName} in response" }

        if (result.shouldBlock) {
            val reason = "[Safety] Response blocked — it contained sensitive credentials " +
                    "(${result.blockReasons.joinToString()}). " +
                    "Disable safety mode in Settings to bypass this check."
            return ToolOutputResult(reason, wasModified = true, blockedReason = reason, warnings = warnings)
        }

        val finalContent = result.redactedContent ?: response
        return ToolOutputResult(
            output = finalContent,
            wasModified = result.redactedContent != null,
            blockedReason = null,
            warnings = warnings,
        )
    }

    /**
     * Validate user input (length, encoding, forbidden patterns).
     */
    fun validateInput(input: String): ValidationResult {
        if (!config.enabled) return ValidationResult.ok()
        return validator.validate(input)
    }

    /**
     * Check rate limit for a tool invocation.
     * Returns null if allowed, or a human-readable denial message if limited.
     */
    fun checkRateLimit(toolName: String, userId: String = "default"): String? {
        if (!config.enabled || !config.rateLimitEnabled) return null

        return when (val result = rateLimiter.checkAndRecord(userId, toolName)) {
            is RateLimitResult.Allowed -> null
            is RateLimitResult.Limited -> {
                val seconds = result.retryAfterMs / 1000
                "[Safety] Tool '$toolName' is rate-limited (${result.limitType.name.lowercase()}). " +
                        "Try again in ${seconds}s. Disable safety mode in Settings to bypass this check."
            }
        }
    }

    companion object {
        private const val TAG = "SafetyLayer"
    }
}
