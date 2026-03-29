package org.ethereumphone.andyclaw.skills

import org.ethereumphone.andyclaw.llm.ModelTier

/**
 * Lightweight heuristic classifier for message complexity and expected output length.
 *
 * Replaces the budget/model-tier classification previously embedded in [SmartRouter]'s
 * LLM routing call. Uses simple word-count and keyword heuristics instead of an LLM call.
 */
object MessageClassifier {

    data class Classification(
        val modelTier: ModelTier,
        val budget: RoutingBudget,
    )

    private val CONVERSATIONAL_PATTERNS = listOf(
        Regex("^(hi|hello|hey|howdy|hola|greetings|sup|yo)[.!]?$"),
        Regex("^good (morning|afternoon|evening|night)[.!]?$"),
        Regex("^(thanks|thank you|thx|ty|cheers)[.!]?$"),
        Regex("^(how are you|how's it going|what's up|whats up)[.!?]?$"),
        Regex("^(bye|goodbye|see you|later|goodnight|cya)[.!]?$"),
        Regex("^(ok|okay|sure|alright|got it|understood|cool|nice|great|awesome|perfect|yes|no|yeah|yep|nope|nah)[.!]?$"),
    )

    private val COMPLEX_KEYWORDS = setOf(
        "explain", "analyze", "compare", "write", "create", "build", "implement",
        "debug", "fix", "refactor", "design", "plan", "research", "summarize",
        "describe", "list all", "step by step", "in detail",
    )

    private val MULTI_STEP_INDICATORS = setOf(
        "and then", "after that", "also", "first", "then", "next",
        "multiple", "several", "all of", "each",
    )

    /**
     * Classifies a user message into a model tier and routing budget.
     */
    fun classify(message: String): Classification {
        val lower = message.lowercase().trim()
        val wordCount = lower.split(Regex("\\s+")).size

        // Conversational: greetings, yes/no, thanks
        if (CONVERSATIONAL_PATTERNS.any { it.matches(lower) }) {
            return Classification(ModelTier.LIGHT, RoutingBudget.LOW)
        }

        // Very short messages (likely follow-ups or simple commands)
        if (wordCount <= 5) {
            return Classification(ModelTier.LIGHT, RoutingBudget.LOW)
        }

        // Check for complexity indicators
        val hasComplexKeyword = COMPLEX_KEYWORDS.any { lower.contains(it) }
        val hasMultiStep = MULTI_STEP_INDICATORS.any { lower.contains(it) }

        // Long + complex = POWERFUL/HIGH
        if (wordCount > 30 && (hasComplexKeyword || hasMultiStep)) {
            return Classification(ModelTier.POWERFUL, RoutingBudget.HIGH)
        }

        // Multi-step or complex keyword = STANDARD/MEDIUM
        if (hasComplexKeyword || hasMultiStep || wordCount > 20) {
            return Classification(ModelTier.STANDARD, RoutingBudget.MEDIUM)
        }

        // Default: simple single-action request
        return Classification(ModelTier.LIGHT, RoutingBudget.LOW)
    }
}
