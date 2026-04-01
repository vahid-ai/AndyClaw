package org.ethereumphone.andyclaw.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Queries the Generative Language API for Gemini models that the configured
 * service account has access to. Filters to models that support `generateContent`.
 */
class GeminiApiModelRegistry {

    companion object {
        private const val TAG = "GeminiApiModelRegistry"
        private const val SCOPES = "https://www.googleapis.com/auth/generative-language https://www.googleapis.com/auth/cloud-platform"
        private const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    data class GeminiModel(
        val modelId: String,
        val displayName: String,
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var cachedModels: List<GeminiModel> = emptyList()
    private var lastServiceAccountHash: Int = 0

    fun getModels(): List<GeminiModel> = cachedModels

    suspend fun refresh(serviceAccountJson: String): List<GeminiModel> = mutex.withLock {
        if (serviceAccountJson.isBlank()) {
            cachedModels = emptyList()
            lastServiceAccountHash = 0
            return@withLock emptyList()
        }

        val hash = serviceAccountJson.hashCode()
        if (hash == lastServiceAccountHash && cachedModels.isNotEmpty()) {
            return@withLock cachedModels
        }

        try {
            val models = withContext(Dispatchers.IO) { fetchModels(serviceAccountJson) }
            cachedModels = models
            lastServiceAccountHash = hash
            Log.i(TAG, "Fetched ${models.size} Gemini models")
            models
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Gemini models: ${e.message}")
            cachedModels
        }
    }

    private fun fetchModels(serviceAccountJson: String): List<GeminiModel> {
        val accessToken = GoogleAuth.fetchAccessTokenSync(serviceAccountJson, SCOPES)

        val request = Request.Builder()
            .url(MODELS_URL)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Gemini models API returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        return parseModels(body)
    }

    private fun parseModels(jsonBody: String): List<GeminiModel> {
        val root = JSONObject(jsonBody)
        val models = root.optJSONArray("models") ?: return emptyList()
        val result = mutableListOf<GeminiModel>()

        for (i in 0 until models.length()) {
            val model = models.getJSONObject(i)
            val fullName = model.optString("name", "")
            val modelName = fullName.removePrefix("models/")

            if (!modelName.startsWith("gemini")) continue

            val methods = model.optJSONArray("supportedGenerationMethods")
            var supportsGenerate = false
            if (methods != null) {
                for (j in 0 until methods.length()) {
                    if (methods.optString(j) == "generateContent") {
                        supportsGenerate = true
                        break
                    }
                }
            }
            if (!supportsGenerate) continue

            val displayName = model.optString("displayName", modelName)
            result.add(GeminiModel(modelId = modelName, displayName = displayName))
        }

        return result.sortedWith(compareBy(
            { when {
                it.modelId.contains("pro") -> 0
                it.modelId.contains("flash-lite") -> 2
                it.modelId.contains("flash") -> 1
                else -> 3
            }},
            { it.modelId },
        ))
    }
}
