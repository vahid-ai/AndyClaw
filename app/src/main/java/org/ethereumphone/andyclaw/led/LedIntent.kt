package org.ethereumphone.andyclaw.led

/**
 * Semantic intent categories that map agent responses to LED patterns.
 *
 * The classifier is intentionally simple — keyword-based with a small
 * dictionary. This avoids over-engineering while covering the most
 * common response types.
 */
enum class LedIntent {
    GREETING,
    SUCCESS,
    ERROR,
    PROCESSING,
    IDLE;

    companion object {
        private val greetingPatterns = listOf(
            "hello", "hi ", "hi!", "hey", "good morning", "good afternoon",
            "good evening", "greetings", "howdy", "what's up", "sup",
        )

        private val successPatterns = listOf(
            "done", "success", "completed", "finished", "all set",
            "here you go", "ready", "confirmed", "approved", "sent",
        )

        private val errorPatterns = listOf(
            "error", "failed", "couldn't", "unable", "sorry",
            "unfortunately", "problem", "issue", "not found", "denied",
        )

        /**
         * Classify the assistant's response text into a semantic intent.
         * Uses the first ~300 characters to determine tone without
         * scanning the entire response.
         */
        fun classifyResponse(text: String): LedIntent {
            val sample = text.take(300).lowercase()
            return when {
                greetingPatterns.any { sample.contains(it) } -> GREETING
                errorPatterns.any { sample.contains(it) } -> ERROR
                successPatterns.any { sample.contains(it) } -> SUCCESS
                else -> IDLE
            }
        }
    }
}
