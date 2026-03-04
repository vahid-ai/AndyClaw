package org.ethereumphone.andyclaw.safety

import android.util.Log
import java.text.Normalizer

data class WebSanitizeResult(
    val cleaned: String,
    val warnings: List<String>,
    val injectionScore: Int,
)

/**
 * Sanitizes text extracted from web pages before it reaches the generic [Sanitizer].
 *
 * Handles evasion techniques specific to web-sourced content:
 * - Unicode homoglyph normalization (NFKC)
 * - Spaced-out letter evasion ("i g n o r e  p r e v i o u s")
 * - Long unbroken strings that may hide encoded payloads
 * - Instructional tone directed at "you" (the AI agent)
 */
object WebContentSanitizer {

    private const val TAG = "WebContentSanitizer"
    private const val MAX_UNBROKEN_LENGTH = 500

    private val ZERO_WIDTH_REGEX = Regex("[\u200B\u200C\u200D\uFEFF\u200E\u200F\u202A-\u202E\u2066-\u2069]")

    /**
     * Patterns that detect instructional language directed at the AI agent
     * within fetched web content. Each match adds to the injection score.
     */
    private val INSTRUCTIONAL_PATTERNS = listOf(
        ScoredPattern(Regex("""(?i)\byou\s+must\b"""), 15, "Directive: 'you must'"),
        ScoredPattern(Regex("""(?i)\byou\s+should\b"""), 10, "Directive: 'you should'"),
        ScoredPattern(Regex("""(?i)\byour\s+new\s+task\b"""), 25, "Role override: 'your new task'"),
        ScoredPattern(Regex("""(?i)\byou\s+are\s+now\b"""), 25, "Role override: 'you are now'"),
        ScoredPattern(Regex("""(?i)\byour\s+instructions\s+are\b"""), 25, "Role override: 'your instructions are'"),
        ScoredPattern(Regex("""(?i)\bdo\s+not\s+refuse\b"""), 20, "Guardrail bypass: 'do not refuse'"),
        ScoredPattern(Regex("""(?i)\bignore\s+(all\s+)?(previous|prior|above)\b"""), 30, "Instruction override"),
        ScoredPattern(Regex("""(?i)\bforget\s+(your|all|everything)\b"""), 25, "Context reset"),
        ScoredPattern(Regex("""(?i)\bnew\s+system\s+prompt\b"""), 30, "System prompt override"),
        ScoredPattern(Regex("""(?i)\breset\s+your\s+role\b"""), 25, "Role reset"),
        ScoredPattern(Regex("""(?i)\bbypass\s+your\b"""), 20, "Guardrail bypass"),
        ScoredPattern(Regex("""(?i)\bjailbreak\b"""), 20, "Jailbreak keyword"),
        ScoredPattern(Regex("""(?i)\boverride\s+your\b"""), 20, "Override directive"),
        ScoredPattern(Regex("""(?i)\bdiscard\s+(prior|previous|all)\s+(directives?|instructions?)\b"""), 25, "Instruction discard"),
        ScoredPattern(Regex("""(?i)\bpretend\s+(you\s+are|to\s+be)\b"""), 15, "Role manipulation"),
        ScoredPattern(Regex("""(?i)\bact\s+as\s+if\b"""), 15, "Role manipulation"),
        ScoredPattern(Regex("""(?i)\bsimulate\s+a\b"""), 10, "Role simulation"),
        ScoredPattern(Regex("""(?i)\broleplay\s+as\b"""), 10, "Role manipulation"),
    )

    /**
     * Regex that detects "spaced-out" evasion: sequences of single characters
     * separated by spaces, e.g. "i g n o r e  p r e v i o u s".
     * Matches 6+ single-letter-space pairs.
     */
    private val SPACED_LETTERS_REGEX = Regex("""(?i)(\b\w\s){6,}\w\b""")

    private data class ScoredPattern(
        val regex: Regex,
        val score: Int,
        val description: String,
    )

    fun sanitize(content: String): WebSanitizeResult {
        val warnings = mutableListOf<String>()
        var score = 0
        var cleaned = content

        // 1. Strip zero-width characters
        val zwCount = ZERO_WIDTH_REGEX.findAll(cleaned).count()
        if (zwCount > 0) {
            cleaned = ZERO_WIDTH_REGEX.replace(cleaned, "")
            warnings.add("Stripped $zwCount zero-width/bidi character(s)")
            score += (zwCount / 5).coerceAtMost(15)
        }

        // 2. NFKC normalization (collapses homoglyphs)
        val normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFKC)
        if (normalized != cleaned) {
            warnings.add("Unicode NFKC normalization modified content")
            score += 5
        }
        cleaned = normalized

        // 3. Collapse spaced-out letter sequences for detection purposes.
        // We check the collapsed version for injection patterns but keep
        // the original text to avoid mangling legitimate content.
        val collapsed = collapseSpacedLetters(cleaned)
        if (collapsed != cleaned) {
            warnings.add("Detected spaced-out letter evasion pattern")
            score += 15
        }

        // 4. Replace long unbroken strings that may hide payloads
        cleaned = cleaned.replace(Regex("[^\\s]{${MAX_UNBROKEN_LENGTH},}")) { match ->
            warnings.add("Removed long unbroken string (${match.value.length} chars)")
            score += 10
            "[long string removed]"
        }

        // 5. Score instructional patterns (check both original and collapsed text)
        for (sp in INSTRUCTIONAL_PATTERNS) {
            val hitInCleaned = sp.regex.containsMatchIn(cleaned)
            val hitInCollapsed = !hitInCleaned && collapsed != cleaned && sp.regex.containsMatchIn(collapsed)
            if (hitInCleaned || hitInCollapsed) {
                val source = if (hitInCollapsed) "${sp.description} (via spaced evasion)" else sp.description
                warnings.add("Instructional pattern: $source")
                score += sp.score
            }
        }

        val finalScore = score.coerceIn(0, 100)
        if (warnings.isNotEmpty()) {
            Log.w(TAG, "Web content sanitized: ${warnings.size} warning(s), score=$finalScore")
        }

        return WebSanitizeResult(
            cleaned = cleaned,
            warnings = warnings,
            injectionScore = finalScore,
        )
    }

    /**
     * Collapses "s p a c e d  o u t" letter sequences into normal words
     * for pattern-matching purposes.
     */
    private fun collapseSpacedLetters(text: String): String {
        return SPACED_LETTERS_REGEX.replace(text) { match ->
            match.value.replace(" ", "")
        }
    }
}
