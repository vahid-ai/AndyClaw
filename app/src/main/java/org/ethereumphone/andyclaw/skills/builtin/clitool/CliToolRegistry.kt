package org.ethereumphone.andyclaw.skills.builtin.clitool

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

// ── Data models ─────────────────────────────────────────────────────────

@Serializable
data class CliToolEntry(
    val id: String,
    val name: String,
    val sourceType: String,      // "git", "npm", or "local"
    val sourceValue: String,     // repo URL, npm package name, or local path
    val binaryName: String? = null,
    val installCommand: String? = null,
    val envVarKeys: List<String> = emptyList(),
    val skillMdFiles: List<String> = emptyList(),
    val addedAt: Long = System.currentTimeMillis(),
    val installedAt: Long? = null,
)

// ── Registry persistence ────────────────────────────────────────────────

class CliToolRegistry(private val baseDir: File) {

    companion object {
        private const val TAG = "CliToolRegistry"
        private const val REGISTRY_FILE = "registry.json"
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val registryFile get() = File(baseDir, REGISTRY_FILE)

    init {
        baseDir.mkdirs()
    }

    @Synchronized
    fun getAll(): List<CliToolEntry> {
        if (!registryFile.exists()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(CliToolEntry.serializer()), registryFile.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read registry: ${e.message}")
            emptyList()
        }
    }

    @Synchronized
    fun get(id: String): CliToolEntry? = getAll().find { it.id == id }

    @Synchronized
    fun add(entry: CliToolEntry) {
        val entries = getAll().toMutableList()
        entries.removeAll { it.id == entry.id }
        entries.add(entry)
        save(entries)
        getToolDir(entry.id).mkdirs()
    }

    @Synchronized
    fun update(entry: CliToolEntry) {
        val entries = getAll().toMutableList()
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            entries[index] = entry
            save(entries)
        }
    }

    @Synchronized
    fun remove(id: String) {
        val entries = getAll().toMutableList()
        entries.removeAll { it.id == id }
        save(entries)
        getToolDir(id).deleteRecursively()
    }

    fun getToolDir(id: String): File = File(baseDir, id)

    fun getSkillMdContent(id: String, relativePath: String = "SKILL.md"): String? {
        val file = File(getToolDir(id), relativePath)
        return if (file.exists()) file.readText() else null
    }

    fun saveSkillMd(id: String, relativePath: String, content: String) {
        val file = File(getToolDir(id), relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun save(entries: List<CliToolEntry>) {
        try {
            registryFile.writeText(json.encodeToString(ListSerializer(CliToolEntry.serializer()), entries))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write registry: ${e.message}")
        }
    }
}
