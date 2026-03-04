package org.ethereumphone.andyclaw.safety

import android.util.Log
import java.text.Normalizer

data class InjectionWarning(
    val pattern: String,
    val severity: Severity,
    val location: IntRange,
    val description: String,
)

data class SanitizedOutput(
    val content: String,
    val warnings: List<InjectionWarning>,
    val wasModified: Boolean,
)

class Sanitizer {

    private data class PatternInfo(
        val pattern: String,
        val severity: Severity,
        val description: String,
    )

    private data class RegexPatternInfo(
        val regex: Regex,
        val name: String,
        val severity: Severity,
        val description: String,
    )

    private val substringPatterns = listOf(
        PatternInfo("ignore previous", Severity.HIGH, "Override previous instructions"),
        PatternInfo("ignore all previous", Severity.CRITICAL, "Override all instructions"),
        PatternInfo("ignore all prior", Severity.CRITICAL, "Override all instructions"),
        PatternInfo("disregard", Severity.MEDIUM, "Instruction override"),
        PatternInfo("forget everything", Severity.HIGH, "Reset context"),
        PatternInfo("forget your instructions", Severity.HIGH, "Instruction override"),
        PatternInfo("discard prior directives", Severity.HIGH, "Instruction override"),
        PatternInfo("you are now", Severity.HIGH, "Role change"),
        PatternInfo("act as", Severity.MEDIUM, "Role manipulation"),
        PatternInfo("pretend to be", Severity.MEDIUM, "Role manipulation"),
        PatternInfo("override your", Severity.HIGH, "Override directive"),
        PatternInfo("do not refuse", Severity.HIGH, "Guardrail bypass"),
        PatternInfo("new system prompt", Severity.CRITICAL, "System prompt override"),
        PatternInfo("reset your role", Severity.HIGH, "Role reset"),
        PatternInfo("bypass your", Severity.HIGH, "Guardrail bypass"),
        PatternInfo("jailbreak", Severity.HIGH, "Jailbreak keyword"),
        PatternInfo("system:", Severity.CRITICAL, "System message injection"),
        PatternInfo("assistant:", Severity.HIGH, "Assistant response injection"),
        PatternInfo("user:", Severity.HIGH, "User message injection"),
        PatternInfo("<|", Severity.CRITICAL, "Special token injection"),
        PatternInfo("|>", Severity.CRITICAL, "Special token injection"),
        PatternInfo("[INST]", Severity.CRITICAL, "Instruction token injection"),
        PatternInfo("[/INST]", Severity.CRITICAL, "Instruction token injection"),
        PatternInfo("new instructions", Severity.HIGH, "New instructions"),
        PatternInfo("updated instructions", Severity.HIGH, "Updated instructions"),
        PatternInfo("```system", Severity.HIGH, "Code block instruction injection"),
        PatternInfo("```bash\nsudo", Severity.MEDIUM, "Dangerous command injection"),
    )

    private val regexPatterns = listOf(
        RegexPatternInfo(
            Regex("""(?i)base64[:\s]+[A-Za-z0-9+/=]{50,}"""),
            "base64_payload", Severity.MEDIUM, "Encoded payload",
        ),
        RegexPatternInfo(
            Regex("""(?i)eval\s*\("""),
            "eval_call", Severity.HIGH, "Code evaluation",
        ),
        RegexPatternInfo(
            Regex("""(?i)exec\s*\("""),
            "exec_call", Severity.HIGH, "Code execution",
        ),
        RegexPatternInfo(
            Regex("""\u0000"""),
            "null_byte", Severity.CRITICAL, "Null byte injection",
        ),
        RegexPatternInfo(
            Regex("""(?i)(human|user|system|assistant)\s*:\s*.{10,}"""),
            "multi_role_injection", Severity.HIGH, "Freeform role-spoofing",
        ),
    )

    fun sanitize(content: String): SanitizedOutput {
        val warnings = mutableListOf<InjectionWarning>()

        // Normalize a comparison copy: NFKC collapses homoglyphs (e.g. Cyrillic
        // look-alikes) and strips zero-width chars so substring patterns can't
        // be trivially bypassed with Unicode tricks.
        val normalized = Normalizer.normalize(content, Normalizer.Form.NFKC)
            .replace(Regex("[\u200B\u200C\u200D\uFEFF]"), "")
        val lower = normalized.lowercase()

        for (p in substringPatterns) {
            var startIndex = 0
            while (true) {
                val idx = lower.indexOf(p.pattern.lowercase(), startIndex)
                if (idx < 0) break
                warnings.add(
                    InjectionWarning(
                        pattern = p.pattern,
                        severity = p.severity,
                        location = idx until (idx + p.pattern.length),
                        description = p.description,
                    )
                )
                startIndex = idx + 1
            }
        }

        for (rp in regexPatterns) {
            for (match in rp.regex.findAll(normalized)) {
                warnings.add(
                    InjectionWarning(
                        pattern = rp.name,
                        severity = rp.severity,
                        location = match.range,
                        description = rp.description,
                    )
                )
            }
        }

        val hasCritical = warnings.any { it.severity == Severity.CRITICAL }

        val escaped = if (hasCritical) escapeContent(content) else content

        if (warnings.isNotEmpty()) {
            Log.w(TAG, "Sanitizer found ${warnings.size} issue(s), critical=$hasCritical")
        }

        return SanitizedOutput(
            content = escaped,
            warnings = warnings,
            wasModified = hasCritical,
        )
    }

    private fun escapeContent(content: String): String {
        var result = content
            .replace("<|", "\\<|")
            .replace("|>", "|\\>")
            .replace("[INST]", "\\[INST]")
            .replace("[/INST]", "\\[/INST]")
            .replace("\u0000", "")

        result = result.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("system:", ignoreCase = true) -> "[ESCAPED] $line"
                trimmed.startsWith("user:", ignoreCase = true) -> "[ESCAPED] $line"
                trimmed.startsWith("assistant:", ignoreCase = true) -> "[ESCAPED] $line"
                else -> line
            }
        }

        return result
    }

    companion object {
        private const val TAG = "Sanitizer"
    }
}
