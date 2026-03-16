package org.ethereumphone.andyclaw.skills.builtin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.ethereumphone.andyclaw.shizuku.ShizukuManager
import org.ethereumphone.andyclaw.skills.AndyClawSkill
import org.ethereumphone.andyclaw.skills.SkillManifest
import org.ethereumphone.andyclaw.skills.SkillResult
import org.ethereumphone.andyclaw.skills.Tier
import org.ethereumphone.andyclaw.skills.ToolDefinition

class ShizukuSkill(
    private val shizukuManager: ShizukuManager,
) : AndyClawSkill {
    override val id = "shizuku"
    override val name = "Shizuku"

    override val baseManifest = SkillManifest(
        description = buildString {
            appendLine("Elevated device control via Shizuku (ADB-level permissions without root).")
            appendLine("Requires the Shizuku app to be installed and activated on the device.")
            appendLine("Use shizuku_status first to check if Shizuku is available before using other tools.")
            appendLine()
            appendLine("Capabilities (ADB shell level):")
            appendLine("- Execute commands with ADB-level privileges")
            appendLine("- Install/uninstall apps silently")
            appendLine("- Grant/revoke runtime permissions for any app")
            appendLine("- Force stop apps, clear app data")
            appendLine("- Write system/secure/global settings")
            appendLine("- Control device power, connectivity, and more")
        },
        tools = listOf(
            ToolDefinition(
                name = "shizuku_status",
                description = "Check if Shizuku is available and what privilege level it has. Call this before using other Shizuku tools.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(emptyMap()),
                )),
            ),
            ToolDefinition(
                name = "shizuku_shell",
                description = "Execute a shell command with ADB-level privileges via Shizuku. This has more permissions than a normal shell — it can access system services, manage packages, write settings, etc.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "command" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The shell command to execute with ADB-level privileges"),
                        )),
                        "timeout_ms" to JsonObject(mapOf(
                            "type" to JsonPrimitive("integer"),
                            "description" to JsonPrimitive("Timeout in milliseconds (default 30000, max 120000)"),
                        )),
                    )),
                    "required" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("command"))),
                )),
                requiresApproval = true,
            ),
            ToolDefinition(
                name = "shizuku_install_app",
                description = "Install an APK file silently using ADB-level permissions. The APK must already exist on the device filesystem.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "apk_path" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Absolute path to the APK file on the device"),
                        )),
                    )),
                    "required" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("apk_path"))),
                )),
                requiresApproval = true,
            ),
            ToolDefinition(
                name = "shizuku_uninstall_app",
                description = "Uninstall an app silently by package name.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "package_name" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The package name of the app to uninstall (e.g. com.example.app)"),
                        )),
                    )),
                    "required" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("package_name"))),
                )),
                requiresApproval = true,
            ),
            ToolDefinition(
                name = "shizuku_grant_permission",
                description = "Grant a runtime permission to an app.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "package_name" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The target app's package name"),
                        )),
                        "permission" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The Android permission to grant (e.g. android.permission.CAMERA)"),
                        )),
                    )),
                    "required" to kotlinx.serialization.json.JsonArray(listOf(
                        JsonPrimitive("package_name"), JsonPrimitive("permission"),
                    )),
                )),
                requiresApproval = true,
            ),
            ToolDefinition(
                name = "shizuku_revoke_permission",
                description = "Revoke a runtime permission from an app.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "package_name" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The target app's package name"),
                        )),
                        "permission" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The Android permission to revoke (e.g. android.permission.CAMERA)"),
                        )),
                    )),
                    "required" to kotlinx.serialization.json.JsonArray(listOf(
                        JsonPrimitive("package_name"), JsonPrimitive("permission"),
                    )),
                )),
                requiresApproval = true,
            ),
            ToolDefinition(
                name = "shizuku_force_stop",
                description = "Force stop an app by package name.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "package_name" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The package name of the app to force stop"),
                        )),
                    )),
                    "required" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("package_name"))),
                )),
                requiresApproval = true,
            ),
            ToolDefinition(
                name = "shizuku_set_setting",
                description = "Write a system, secure, or global setting. Namespace must be one of: system, secure, global.",
                inputSchema = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "namespace" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The settings namespace: system, secure, or global"),
                        )),
                        "key" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The setting key to write"),
                        )),
                        "value" to JsonObject(mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The value to set"),
                        )),
                    )),
                    "required" to kotlinx.serialization.json.JsonArray(listOf(
                        JsonPrimitive("namespace"), JsonPrimitive("key"), JsonPrimitive("value"),
                    )),
                )),
                requiresApproval = true,
            ),
        ),
    )

    override val privilegedManifest: SkillManifest? = null

    override suspend fun execute(tool: String, params: JsonObject, tier: Tier): SkillResult {
        return when (tool) {
            "shizuku_status" -> getStatus()
            "shizuku_shell" -> executeShell(params)
            "shizuku_install_app" -> installApp(params)
            "shizuku_uninstall_app" -> uninstallApp(params)
            "shizuku_grant_permission" -> grantPermission(params)
            "shizuku_revoke_permission" -> revokePermission(params)
            "shizuku_force_stop" -> forceStop(params)
            "shizuku_set_setting" -> setSetting(params)
            else -> SkillResult.Error("Unknown tool: $tool")
        }
    }

    private fun getStatus(): SkillResult {
        val result = buildJsonObject {
            put("available", shizukuManager.isAvailable.value)
            put("permission_granted", shizukuManager.isPermissionGranted.value)
            put("ready", shizukuManager.isReady)
            put("privilege_level", shizukuManager.privilegeLevel)
            put("uid", shizukuManager.uid.value)
        }
        return SkillResult.Success(result.toString())
    }

    private fun requireReady(): SkillResult? {
        if (!shizukuManager.isAvailable.value) {
            return SkillResult.Error("Shizuku is not available. Make sure the Shizuku app is installed and activated (via ADB or wireless debugging).")
        }
        if (!shizukuManager.isPermissionGranted.value) {
            shizukuManager.requestPermission()
            return SkillResult.Error("Shizuku permission not granted. Permission has been requested — the user needs to approve it in the Shizuku app.")
        }
        return null
    }

    private fun executeShell(params: JsonObject): SkillResult {
        requireReady()?.let { return it }

        val command = params["command"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: command")
        val timeoutMs = params["timeout_ms"]?.jsonPrimitive?.intOrNull?.toLong()?.coerceIn(1000, 120_000)
            ?: 30_000L

        return try {
            val result = shizukuManager.executeCommand(command, timeoutMs)
            val json = buildJsonObject {
                put("exit_code", result.exitCode)
                put("output", result.output)
                if (result.truncated) put("truncated", true)
                put("privilege_level", shizukuManager.privilegeLevel)
            }
            SkillResult.Success(json.toString())
        } catch (e: Exception) {
            SkillResult.Error("Shizuku command failed: ${e.message}")
        }
    }

    private fun installApp(params: JsonObject): SkillResult {
        requireReady()?.let { return it }

        val apkPath = params["apk_path"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: apk_path")

        return try {
            val result = shizukuManager.executeCommand("pm install -r \"$apkPath\"")
            val json = buildJsonObject {
                put("exit_code", result.exitCode)
                put("output", result.output.trim())
                put("success", result.exitCode == 0)
            }
            SkillResult.Success(json.toString())
        } catch (e: Exception) {
            SkillResult.Error("Failed to install app: ${e.message}")
        }
    }

    private fun uninstallApp(params: JsonObject): SkillResult {
        requireReady()?.let { return it }

        val packageName = params["package_name"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: package_name")

        if (!packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) {
            return SkillResult.Error("Invalid package name: $packageName")
        }

        return try {
            val result = shizukuManager.executeCommand("pm uninstall $packageName")
            val json = buildJsonObject {
                put("exit_code", result.exitCode)
                put("output", result.output.trim())
                put("success", result.exitCode == 0)
            }
            SkillResult.Success(json.toString())
        } catch (e: Exception) {
            SkillResult.Error("Failed to uninstall app: ${e.message}")
        }
    }

    private fun grantPermission(params: JsonObject): SkillResult {
        requireReady()?.let { return it }

        val packageName = params["package_name"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: package_name")
        val permission = params["permission"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: permission")

        if (!packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) {
            return SkillResult.Error("Invalid package name: $packageName")
        }
        if (!permission.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) {
            return SkillResult.Error("Invalid permission: $permission")
        }

        return try {
            val result = shizukuManager.executeCommand("pm grant $packageName $permission")
            val json = buildJsonObject {
                put("exit_code", result.exitCode)
                put("output", result.output.trim())
                put("success", result.exitCode == 0)
            }
            SkillResult.Success(json.toString())
        } catch (e: Exception) {
            SkillResult.Error("Failed to grant permission: ${e.message}")
        }
    }

    private fun revokePermission(params: JsonObject): SkillResult {
        requireReady()?.let { return it }

        val packageName = params["package_name"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: package_name")
        val permission = params["permission"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: permission")

        if (!packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) {
            return SkillResult.Error("Invalid package name: $packageName")
        }
        if (!permission.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) {
            return SkillResult.Error("Invalid permission: $permission")
        }

        return try {
            val result = shizukuManager.executeCommand("pm revoke $packageName $permission")
            val json = buildJsonObject {
                put("exit_code", result.exitCode)
                put("output", result.output.trim())
                put("success", result.exitCode == 0)
            }
            SkillResult.Success(json.toString())
        } catch (e: Exception) {
            SkillResult.Error("Failed to revoke permission: ${e.message}")
        }
    }

    private fun forceStop(params: JsonObject): SkillResult {
        requireReady()?.let { return it }

        val packageName = params["package_name"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: package_name")

        if (!packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) {
            return SkillResult.Error("Invalid package name: $packageName")
        }

        return try {
            val result = shizukuManager.executeCommand("am force-stop $packageName")
            val json = buildJsonObject {
                put("exit_code", result.exitCode)
                put("output", result.output.trim())
                put("success", result.exitCode == 0)
            }
            SkillResult.Success(json.toString())
        } catch (e: Exception) {
            SkillResult.Error("Failed to force stop: ${e.message}")
        }
    }

    private fun setSetting(params: JsonObject): SkillResult {
        requireReady()?.let { return it }

        val namespace = params["namespace"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: namespace")
        val key = params["key"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: key")
        val value = params["value"]?.jsonPrimitive?.contentOrNull
            ?: return SkillResult.Error("Missing required parameter: value")

        if (namespace !in listOf("system", "secure", "global")) {
            return SkillResult.Error("Invalid namespace: $namespace (must be system, secure, or global)")
        }
        if (!key.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) {
            return SkillResult.Error("Invalid setting key: $key")
        }

        return try {
            val result = shizukuManager.executeCommand("settings put $namespace $key \"$value\"")
            val json = buildJsonObject {
                put("exit_code", result.exitCode)
                put("output", result.output.trim())
                put("success", result.exitCode == 0)
            }
            SkillResult.Success(json.toString())
        } catch (e: Exception) {
            SkillResult.Error("Failed to set setting: ${e.message}")
        }
    }
}
