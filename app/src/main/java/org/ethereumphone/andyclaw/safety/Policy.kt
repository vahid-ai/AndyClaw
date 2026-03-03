package org.ethereumphone.andyclaw.safety

import android.util.Log

enum class PolicyAction {
    WARN, BLOCK, REVIEW, SANITIZE;
}

data class PolicyRule(
    val id: String,
    val description: String,
    val severity: Severity,
    val pattern: Regex,
    val action: PolicyAction,
)

data class PolicyViolation(
    val ruleId: String,
    val description: String,
    val severity: Severity,
    val action: PolicyAction,
)

data class PolicyCheckResult(
    val violations: List<PolicyViolation>,
    val shouldBlock: Boolean,
    val shouldSanitize: Boolean,
) {
    val blockReasons: List<String>
        get() = violations.filter { it.action == PolicyAction.BLOCK }.map { it.description }
}

class Policy(
    private val rules: List<PolicyRule> = defaultRules(),
) {

    fun check(content: String): PolicyCheckResult {
        val violations = mutableListOf<PolicyViolation>()

        for (rule in rules) {
            if (rule.pattern.containsMatchIn(content)) {
                violations.add(
                    PolicyViolation(
                        ruleId = rule.id,
                        description = rule.description,
                        severity = rule.severity,
                        action = rule.action,
                    )
                )
            }
        }

        if (violations.isNotEmpty()) {
            Log.w(TAG, "Policy check found ${violations.size} violation(s): " +
                    violations.joinToString { "${it.ruleId}(${it.action})" })
        }

        return PolicyCheckResult(
            violations = violations,
            shouldBlock = violations.any { it.action == PolicyAction.BLOCK },
            shouldSanitize = violations.any { it.action == PolicyAction.SANITIZE },
        )
    }

    companion object {
        private const val TAG = "Policy"

        fun defaultRules(): List<PolicyRule> = listOf(
            PolicyRule(
                id = "system_file_access",
                description = "Attempted access to sensitive system files",
                severity = Severity.CRITICAL,
                pattern = Regex("""(?i)(/etc/passwd|/etc/shadow|\.ssh/|\.aws/credentials)"""),
                action = PolicyAction.BLOCK,
            ),
            PolicyRule(
                id = "crypto_private_key",
                description = "Possible private key or seed phrase exposure",
                severity = Severity.CRITICAL,
                pattern = Regex("""(?i)(private.?key|seed.?phrase|mnemonic).{0,20}[0-9a-f]{64}"""),
                action = PolicyAction.BLOCK,
            ),
            PolicyRule(
                id = "sql_pattern",
                description = "SQL injection pattern detected",
                severity = Severity.MEDIUM,
                pattern = Regex("""(?i)(DROP\s+TABLE|DELETE\s+FROM|INSERT\s+INTO|UPDATE\s+\w+\s+SET)"""),
                action = PolicyAction.WARN,
            ),
            PolicyRule(
                id = "shell_injection",
                description = "Shell injection pattern detected",
                severity = Severity.CRITICAL,
                pattern = Regex("""(?i)(;\s*rm\s+-rf|;\s*curl\s+.*\|\s*sh)"""),
                action = PolicyAction.BLOCK,
            ),
            PolicyRule(
                id = "excessive_urls",
                description = "Excessive URL count detected",
                severity = Severity.LOW,
                pattern = Regex("""(https?://[^\s]+\s*){10,}"""),
                action = PolicyAction.WARN,
            ),
            PolicyRule(
                id = "encoded_exploit",
                description = "Encoded exploit attempt detected",
                severity = Severity.HIGH,
                pattern = Regex("""(?i)(base64_decode|eval\s*\(\s*base64|atob\s*\()"""),
                action = PolicyAction.SANITIZE,
            ),
            PolicyRule(
                id = "obfuscated_string",
                description = "Suspiciously long unbroken string",
                severity = Severity.MEDIUM,
                pattern = Regex("""[^\s]{500,}"""),
                action = PolicyAction.WARN,
            ),
        )
    }
}
