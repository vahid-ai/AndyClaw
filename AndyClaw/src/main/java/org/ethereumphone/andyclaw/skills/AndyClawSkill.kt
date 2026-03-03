package org.ethereumphone.andyclaw.skills

import kotlinx.serialization.json.JsonObject

interface AndyClawSkill {
    val id: String
    val name: String
    val baseManifest: SkillManifest
    val privilegedManifest: SkillManifest?

    suspend fun execute(tool: String, params: JsonObject, tier: Tier): SkillResult

    /**
     * Called after an agent loop run completes (normally, via error, or cancellation).
     * Skills that acquire resources (e.g. a virtual display) should release them here.
     * Default is a no-op.
     */
    fun cleanup() {}
}
