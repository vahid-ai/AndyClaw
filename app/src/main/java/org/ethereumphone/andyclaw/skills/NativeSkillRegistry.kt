package org.ethereumphone.andyclaw.skills

import android.util.Log
import kotlinx.serialization.json.JsonObject
import org.ethereumphone.andyclaw.safety.ToolAttenuation

class NativeSkillRegistry {

    private val skills = mutableListOf<AndyClawSkill>()
    private val builtinToolNames = mutableSetOf<String>()

    fun register(skill: AndyClawSkill) {
        val isExternal = skill.id.startsWith("clawhub:") ||
                skill.id.startsWith("ai:") ||
                skill.id.startsWith("ext:")

        if (isExternal) {
            val shadowedTools = skill.baseManifest.tools.filter { ToolAttenuation.isProtected(it.name) }
            if (shadowedTools.isNotEmpty()) {
                Log.w(TAG, "Rejected skill '${skill.id}': tried to shadow protected tools " +
                        shadowedTools.joinToString { it.name })
                return
            }
        } else {
            skill.baseManifest.tools.forEach { builtinToolNames.add(it.name) }
            skill.privilegedManifest?.tools?.forEach { builtinToolNames.add(it.name) }
        }

        skills.removeAll { it.id == skill.id }
        skills.add(skill)
    }

    companion object {
        private const val TAG = "NativeSkillRegistry"
    }

    fun unregister(skillId: String) {
        skills.removeAll { it.id == skillId }
    }

    fun getAll(): List<AndyClawSkill> = skills.toList()

    fun getEnabled(enabledSkillIds: Set<String>): List<AndyClawSkill> =
        skills.filter { it.id in enabledSkillIds }

    fun getTools(tier: Tier): List<ToolDefinition> {
        val tools = mutableListOf<ToolDefinition>()
        for (skill in skills) {
            tools.addAll(skill.baseManifest.tools)
            if (tier == Tier.PRIVILEGED) {
                skill.privilegedManifest?.tools?.let { tools.addAll(it) }
            }
        }
        return tools
    }

    fun findSkillForTool(toolName: String, tier: Tier): AndyClawSkill? {
        for (skill in skills) {
            if (skill.baseManifest.tools.any { it.name == toolName }) return skill
            if (tier == Tier.PRIVILEGED) {
                if (skill.privilegedManifest?.tools?.any { it.name == toolName } == true) return skill
            }
        }
        return null
    }

    suspend fun executeTool(toolName: String, params: JsonObject, tier: Tier): SkillResult {
        val skill = findSkillForTool(toolName, tier)
            ?: return SkillResult.Error("No skill found for tool: $toolName")
        return skill.execute(toolName, params, tier)
    }

    /** Release any resources held by skills (e.g. a virtual display left open). */
    fun cleanupAll() {
        for (skill in skills) {
            skill.cleanup()
        }
    }
}
