package org.ethereumphone.andyclaw.skills.builtin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.ethereumphone.andyclaw.skills.AndyClawSkill
import org.ethereumphone.andyclaw.skills.NativeSkillRegistry
import org.ethereumphone.andyclaw.skills.SkillManifest
import org.ethereumphone.andyclaw.skills.SkillResult
import org.ethereumphone.andyclaw.skills.Tier
import org.ethereumphone.andyclaw.skills.ToolDefinition
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolBridgeTest {

    private lateinit var registry: NativeSkillRegistry
    private lateinit var bridge: ToolBridge

    /** Minimal tool definition builder. */
    private fun tool(name: String, description: String, requiresApproval: Boolean = false) =
        ToolDefinition(
            name = name,
            description = description,
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            },
            requiresApproval = requiresApproval,
        )

    /** Skill that echoes params back as JSON. */
    private fun echoSkill(id: String, vararg tools: ToolDefinition) = object : AndyClawSkill {
        override val id = id
        override val name = id.replaceFirstChar { it.uppercase() }
        override val baseManifest = SkillManifest(
            description = "$id skill",
            tools = tools.toList(),
        )
        override val privilegedManifest: SkillManifest? = null
        override suspend fun execute(tool: String, params: JsonObject, tier: Tier): SkillResult {
            return SkillResult.Success("echo:$tool:$params")
        }
    }

    /** Skill that always returns an error. */
    private fun errorSkill() = object : AndyClawSkill {
        override val id = "error_skill"
        override val name = "Error"
        override val baseManifest = SkillManifest(
            description = "always fails",
            tools = listOf(tool("fail_tool", "always fails")),
        )
        override val privilegedManifest: SkillManifest? = null
        override suspend fun execute(tool: String, params: JsonObject, tier: Tier): SkillResult {
            return SkillResult.Error("intentional failure")
        }
    }

    @Before
    fun setup() {
        registry = NativeSkillRegistry()
        registry.register(echoSkill("ens", tool("resolve_ens", "Resolve ENS name")))
        registry.register(echoSkill("wallet", tool("get_balance", "Get balance")))
        registry.register(echoSkill("protected",
            tool("protected_tool", "Needs approval", requiresApproval = true)))
        registry.register(errorSkill())

        val allSkillIds = registry.getAll().map { it.id }.toSet()
        bridge = ToolBridge(registry, Tier.OPEN, allSkillIds)
    }

    // ── Basic call tests ─────────────────────────────────────────────

    @Test
    fun `call returns tool result as string`() {
        val result = bridge.call("resolve_ens", mapOf("name" to "alice.eth"))
        assertTrue("Should contain echo of tool name", result.contains("resolve_ens"))
        assertTrue("Should contain param", result.contains("alice.eth"))
    }

    @Test
    fun `call with multiple params`() {
        val result = bridge.call("get_balance", mapOf("address" to "0x123", "chain" to "ethereum"))
        assertTrue(result.contains("0x123"))
        assertTrue(result.contains("ethereum"))
    }

    // ── Error handling ───────────────────────────────────────────────

    @Test(expected = RuntimeException::class)
    fun `call throws on unknown tool`() {
        bridge.call("nonexistent_tool", emptyMap())
    }

    @Test
    fun `call throws with descriptive message on unknown tool`() {
        try {
            bridge.call("nonexistent_tool", emptyMap())
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("not found"))
        }
    }

    @Test(expected = RuntimeException::class)
    fun `call throws on tool error`() {
        bridge.call("fail_tool", emptyMap())
    }

    @Test
    fun `call throws with tool error message`() {
        try {
            bridge.call("fail_tool", emptyMap())
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("intentional failure"))
        }
    }

    // ── Approval-required tools ──────────────────────────────────────

    @Test(expected = RuntimeException::class)
    fun `call throws on approval-required tool`() {
        bridge.call("protected_tool", emptyMap())
    }

    @Test
    fun `call throws with approval message`() {
        try {
            bridge.call("protected_tool", emptyMap())
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("requires user approval"))
            assertTrue(e.message!!.contains("direct tool call"))
        }
    }

    // ── Disabled skill ───────────────────────────────────────────────

    @Test(expected = RuntimeException::class)
    fun `call throws when skill is disabled`() {
        val limitedBridge = ToolBridge(registry, Tier.OPEN, setOf("ens")) // only ens enabled
        limitedBridge.call("get_balance", emptyMap()) // wallet skill not enabled
    }

    // ── Call log tracking ────────────────────────────────────────────

    @Test
    fun `call log tracks successful calls`() {
        bridge.call("resolve_ens", mapOf("name" to "test.eth"))
        assertEquals(1, bridge.callLog.size)
        assertEquals("resolve_ens", bridge.callLog[0].toolName)
        assertTrue(bridge.callLog[0].success)
        assertTrue(bridge.callLog[0].durationMs >= 0)
    }

    @Test
    fun `call log tracks failed calls`() {
        try { bridge.call("fail_tool", emptyMap()) } catch (_: RuntimeException) {}
        assertEquals(1, bridge.callLog.size)
        assertFalse(bridge.callLog[0].success)
    }

    @Test
    fun `call log accumulates across multiple calls`() {
        bridge.call("resolve_ens", mapOf("name" to "a.eth"))
        bridge.call("get_balance", mapOf("address" to "0x1"))
        assertEquals(2, bridge.callLog.size)
    }

    // ── Call summary ─────────────────────────────────────────────────

    @Test
    fun `buildCallSummary returns null when no calls made`() {
        assertNull(bridge.buildCallSummary())
    }

    @Test
    fun `buildCallSummary returns summary after calls`() {
        bridge.call("resolve_ens", mapOf("name" to "a.eth"))
        bridge.call("get_balance", emptyMap())
        val summary = bridge.buildCallSummary()
        assertNotNull(summary)
        assertTrue(summary!!.contains("resolve_ens"))
        assertTrue(summary.contains("get_balance"))
        assertTrue(summary.contains("2 call(s)"))
    }

    // ── Param conversion ─────────────────────────────────────────────

    @Test
    fun `call handles string params`() {
        val result = bridge.call("resolve_ens", mapOf("name" to "test.eth"))
        assertTrue(result.contains("test.eth"))
    }

    @Test
    fun `call handles numeric params`() {
        val result = bridge.call("get_balance", mapOf("amount" to 42, "price" to 3.14))
        assertTrue(result.contains("42"))
        assertTrue(result.contains("3.14"))
    }

    @Test
    fun `call handles boolean params`() {
        val result = bridge.call("get_balance", mapOf("verbose" to true))
        assertTrue(result.contains("true"))
    }

    @Test
    fun `call handles null params`() {
        val result = bridge.call("get_balance", mapOf("optional" to null))
        assertTrue(result.contains("null"))
    }

    @Test
    fun `call handles empty params`() {
        val result = bridge.call("get_balance", emptyMap())
        assertNotNull(result)
    }

    @Test
    fun `call handles nested map params`() {
        val result = bridge.call("get_balance", mapOf(
            "filter" to mapOf("chain" to "ethereum", "minBalance" to 100)
        ))
        assertTrue(result.contains("ethereum"))
        assertTrue(result.contains("100"))
    }

    @Test
    fun `call handles list params`() {
        val result = bridge.call("get_balance", mapOf(
            "addresses" to listOf("0x1", "0x2", "0x3")
        ))
        assertTrue(result.contains("0x1"))
        assertTrue(result.contains("0x2"))
    }

    // ── callParallel tests ───────────────────────────────────────────

    @Test
    fun `callParallel returns results in order`() {
        val results = bridge.callParallel("resolve_ens", listOf(
            mapOf("name" to "alice.eth"),
            mapOf("name" to "bob.eth"),
            mapOf("name" to "carol.eth"),
        ))
        assertEquals(3, results.size)
        assertTrue(results[0].contains("alice.eth"))
        assertTrue(results[1].contains("bob.eth"))
        assertTrue(results[2].contains("carol.eth"))
    }

    @Test
    fun `callParallel with empty list returns empty`() {
        val results = bridge.callParallel("resolve_ens", emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `callParallel with single item works`() {
        val results = bridge.callParallel("resolve_ens", listOf(
            mapOf("name" to "test.eth"),
        ))
        assertEquals(1, results.size)
        assertTrue(results[0].contains("test.eth"))
    }

    @Test(expected = RuntimeException::class)
    fun `callParallel throws on unknown tool`() {
        bridge.callParallel("nonexistent", listOf(mapOf("x" to "y")))
    }

    @Test(expected = RuntimeException::class)
    fun `callParallel throws on approval-required tool`() {
        bridge.callParallel("protected_tool", listOf(emptyMap()))
    }

    @Test(expected = RuntimeException::class)
    fun `callParallel throws on disabled skill`() {
        val limitedBridge = ToolBridge(registry, Tier.OPEN, setOf("ens"))
        limitedBridge.callParallel("get_balance", listOf(emptyMap()))
    }

    @Test
    fun `callParallel tracks calls in log`() {
        bridge.callParallel("resolve_ens", listOf(
            mapOf("name" to "a.eth"),
            mapOf("name" to "b.eth"),
        ))
        assertEquals(2, bridge.callLog.size)
        assertTrue(bridge.callLog.all { it.toolName == "resolve_ens" })
        assertTrue(bridge.callLog.all { it.success })
    }

    @Test
    fun `callParallel throws on error tool`() {
        try {
            bridge.callParallel("fail_tool", listOf(emptyMap(), emptyMap()))
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("intentional failure"))
        }
    }

    // ── Mixed sequential + parallel ──────────────────────────────────

    @Test
    fun `sequential call then parallel works together`() {
        // Simulate: resolve one ENS → then batch balance check
        val addr = bridge.call("resolve_ens", mapOf("name" to "alice.eth"))
        assertTrue(addr.contains("alice.eth"))

        val balances = bridge.callParallel("get_balance", listOf(
            mapOf("address" to "0x1"),
            mapOf("address" to "0x2"),
        ))
        assertEquals(2, balances.size)

        // Total: 3 calls in log (1 sequential + 2 parallel)
        assertEquals(3, bridge.callLog.size)
    }
}
