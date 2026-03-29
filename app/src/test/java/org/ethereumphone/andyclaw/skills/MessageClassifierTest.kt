package org.ethereumphone.andyclaw.skills

import org.ethereumphone.andyclaw.llm.ModelTier
import org.junit.Assert.*
import org.junit.Test

class MessageClassifierTest {

    // ── Conversational / LIGHT ───────────────────────────────────────

    @Test
    fun `greetings classify as LIGHT LOW`() {
        for (msg in listOf("hi", "hello", "hey", "yo", "sup")) {
            val result = MessageClassifier.classify(msg)
            assertEquals("'$msg' should be LIGHT", ModelTier.LIGHT, result.modelTier)
            assertEquals("'$msg' should be LOW", RoutingBudget.LOW, result.budget)
        }
    }

    @Test
    fun `yes no classify as LIGHT LOW`() {
        for (msg in listOf("yes", "no", "yeah", "nope", "ok", "sure", "alright")) {
            val result = MessageClassifier.classify(msg)
            assertEquals("'$msg' should be LIGHT", ModelTier.LIGHT, result.modelTier)
            assertEquals("'$msg' should be LOW", RoutingBudget.LOW, result.budget)
        }
    }

    @Test
    fun `thanks classify as LIGHT LOW`() {
        for (msg in listOf("thanks", "thank you", "ty", "cheers")) {
            val result = MessageClassifier.classify(msg)
            assertEquals("'$msg' should be LIGHT", ModelTier.LIGHT, result.modelTier)
            assertEquals("'$msg' should be LOW", RoutingBudget.LOW, result.budget)
        }
    }

    @Test
    fun `bye classify as LIGHT LOW`() {
        val result = MessageClassifier.classify("bye")
        assertEquals(ModelTier.LIGHT, result.modelTier)
        assertEquals(RoutingBudget.LOW, result.budget)
    }

    // ── Short messages / simple commands ──────────────────────────────

    @Test
    fun `short messages classify as LIGHT LOW`() {
        for (msg in listOf("turn on wifi", "send eth", "now mhaas.eth")) {
            val result = MessageClassifier.classify(msg)
            assertEquals("'$msg' should be LIGHT", ModelTier.LIGHT, result.modelTier)
            assertEquals("'$msg' should be LOW", RoutingBudget.LOW, result.budget)
        }
    }

    // ── Medium complexity ────────────────────────────────────────────

    @Test
    fun `complex keyword triggers STANDARD`() {
        val result = MessageClassifier.classify("explain how the wallet works on this device")
        assertEquals(ModelTier.STANDARD, result.modelTier)
        assertEquals(RoutingBudget.MEDIUM, result.budget)
    }

    @Test
    fun `multi-step indicator triggers STANDARD`() {
        val result = MessageClassifier.classify("search for Mark in contacts and then send him a message")
        assertEquals(ModelTier.STANDARD, result.modelTier)
        assertEquals(RoutingBudget.MEDIUM, result.budget)
    }

    @Test
    fun `longer message without complex keywords triggers STANDARD`() {
        // 21+ words but no complex keywords → STANDARD/MEDIUM
        val msg = "I want to know the current weather in San Francisco and also in New York City and London and maybe Tokyo too"
        val result = MessageClassifier.classify(msg)
        assertEquals(ModelTier.STANDARD, result.modelTier)
        assertEquals(RoutingBudget.MEDIUM, result.budget)
    }

    // ── High complexity ──────────────────────────────────────────────

    @Test
    fun `long complex message triggers POWERFUL`() {
        val msg = "Please analyze the transaction history from my wallet over the last month, " +
            "compare the gas fees across different chains, and then create a detailed summary " +
            "of the spending patterns including a step by step breakdown of each category"
        val result = MessageClassifier.classify(msg)
        assertEquals(ModelTier.POWERFUL, result.modelTier)
        assertEquals(RoutingBudget.HIGH, result.budget)
    }

    // ── Edge cases ───────────────────────────────────────────────────

    @Test
    fun `empty message classifies as LIGHT`() {
        val result = MessageClassifier.classify("")
        assertEquals(ModelTier.LIGHT, result.modelTier)
        assertEquals(RoutingBudget.LOW, result.budget)
    }

    @Test
    fun `single word non-conversational classifies as LIGHT`() {
        val result = MessageClassifier.classify("weather")
        assertEquals(ModelTier.LIGHT, result.modelTier)
        assertEquals(RoutingBudget.LOW, result.budget)
    }

    @Test
    fun `case insensitive classification`() {
        val result = MessageClassifier.classify("HELLO")
        assertEquals(ModelTier.LIGHT, result.modelTier)
    }
}
