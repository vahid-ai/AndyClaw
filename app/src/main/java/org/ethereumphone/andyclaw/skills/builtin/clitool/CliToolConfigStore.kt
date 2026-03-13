@file:Suppress("DEPRECATION")

package org.ethereumphone.andyclaw.skills.builtin.clitool

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted key-value store for CLI tool configuration (API keys, env vars).
 * Each entry is stored as "cli.<toolId>.<KEY>" to avoid collisions.
 */
class CliToolConfigStore(context: Context) {

    companion object {
        private const val TAG = "CliToolConfigStore"
        private const val PREFS_NAME = "cli-tool-config.secure"
        private const val KEY_PREFIX = "cli."
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun setEnvVar(toolId: String, key: String, value: String) {
        prefs.edit { putString("$KEY_PREFIX$toolId.$key", value) }
    }

    fun getEnvVar(toolId: String, key: String): String? {
        return prefs.getString("$KEY_PREFIX$toolId.$key", null)
    }

    fun getAllEnvVars(toolId: String): Map<String, String> {
        val prefix = "$KEY_PREFIX$toolId."
        val result = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(prefix) && value is String) {
                result[key.removePrefix(prefix)] = value
            }
        }
        return result
    }

    fun removeAllForTool(toolId: String) {
        val prefix = "$KEY_PREFIX$toolId."
        prefs.edit {
            for (key in prefs.all.keys) {
                if (key.startsWith(prefix)) remove(key)
            }
        }
    }

    fun isConfigured(toolId: String, requiredKeys: List<String>): Boolean {
        if (requiredKeys.isEmpty()) return true
        return requiredKeys.all { key ->
            !prefs.getString("$KEY_PREFIX$toolId.$key", null).isNullOrBlank()
        }
    }
}
