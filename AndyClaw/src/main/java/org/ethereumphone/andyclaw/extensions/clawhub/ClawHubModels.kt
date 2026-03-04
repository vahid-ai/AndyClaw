package org.ethereumphone.andyclaw.extensions.clawhub

import kotlinx.serialization.Serializable

/**
 * Data models for the ClawHub public skill registry API (v1).
 *
 * These mirror the API response schemas from clawhub.ai's v1 endpoints.
 * The registry stores versioned SKILL.md bundles and exposes them via
 * REST search, browse, resolve, and download endpoints.
 */

// ── Moderation (skill-level flags from /api/v1/skills/{slug}) ────────

@Serializable
data class ClawHubModeration(
    val isSuspicious: Boolean = false,
    val isMalwareBlocked: Boolean = false,
)

// ── Version security (from /api/v1/skills/{slug}/versions/{v}) ──────

/**
 * LLM-based security analysis status returned by the version detail
 * endpoint. This is separate from skill-level moderation — it reflects
 * the automated code analysis verdict for a specific version.
 */
@Serializable
data class ClawHubVersionSecurity(
    val status: String? = null,
    val hasWarnings: Boolean = false,
    val checkedAt: Long? = null,
    val model: String? = null,
) {
    val isSuspicious: Boolean
        get() = status.equals("suspicious", ignoreCase = true)

    val isMalicious: Boolean
        get() = status.equals("malicious", ignoreCase = true)
}

@Serializable
data class ClawHubVersionInfo(
    val version: String? = null,
    val security: ClawHubVersionSecurity? = null,
)

@Serializable
data class ClawHubVersionDetail(
    val version: ClawHubVersionInfo? = null,
)

/**
 * Merged risk data combining skill-level moderation and version-level
 * security analysis. Used by [SkillThreatAnalyzer] for risk badges.
 */
data class ClawHubRiskData(
    val moderation: ClawHubModeration?,
    val versionSecurity: ClawHubVersionSecurity?,
)

// ── Search ──────────────────────────────────────────────────────────

@Serializable
data class ClawHubSearchResult(
    val slug: String? = null,
    val displayName: String? = null,
    val summary: String? = null,
    val version: String? = null,
    val score: Double = 0.0,
    val updatedAt: Long? = null,
    val moderation: ClawHubModeration? = null,
)

@Serializable
data class ClawHubSearchResponse(
    val results: List<ClawHubSearchResult> = emptyList(),
)

// ── Skill listing / detail ──────────────────────────────────────────

@Serializable
data class ClawHubSkillVersion(
    val version: String,
    val createdAt: Long = 0L,
    val changelog: String = "",
    val changelogSource: String? = null,
)

@Serializable
data class ClawHubSkillStats(
    val downloads: Long = 0L,
    val stars: Long = 0L,
)

@Serializable
data class ClawHubSkillSummary(
    val slug: String,
    val displayName: String,
    val summary: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val latestVersion: ClawHubSkillVersion? = null,
    val moderation: ClawHubModeration? = null,
)

@Serializable
data class ClawHubSkillOwner(
    val handle: String? = null,
    val displayName: String? = null,
    val image: String? = null,
)

@Serializable
data class ClawHubSkillDetail(
    val skill: ClawHubSkillSummary? = null,
    val latestVersion: ClawHubSkillVersion? = null,
    val owner: ClawHubSkillOwner? = null,
    val moderation: ClawHubModeration? = null,
)

@Serializable
data class ClawHubSkillListResponse(
    val items: List<ClawHubSkillSummary> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ClawHubVersionListResponse(
    val items: List<ClawHubSkillVersion> = emptyList(),
    val nextCursor: String? = null,
)

// ── Lockfile (.clawhub/lock.json) ───────────────────────────────────

@Serializable
data class ClawHubLockEntry(
    val version: String? = null,
    val installedAt: Long = 0L,
)

@Serializable
data class ClawHubLockData(
    val version: Int = 1,
    val skills: MutableMap<String, ClawHubLockEntry> = mutableMapOf(),
)

// ── Installed skill info (local tracking) ───────────────────────────

/**
 * Represents a ClawHub skill that has been installed locally.
 * Tracks the slug, installed version, and local directory path.
 */
data class InstalledClawHubSkill(
    val slug: String,
    val displayName: String,
    val version: String?,
    val installedAt: Long,
    val localDir: String,
)
