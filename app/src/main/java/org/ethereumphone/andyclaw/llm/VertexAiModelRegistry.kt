package org.ethereumphone.andyclaw.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Discovers available Gemini models on Vertex AI (`aiplatform.googleapis.com`)
 * by probing individual known model IDs.
 */
class VertexAiModelRegistry(
    private val regionProvider: () -> String = { "us-central1" },
) {

    companion object {
        private const val TAG = "VertexAiModelRegistry"
        private const val SCOPES = "https://www.googleapis.com/auth/cloud-platform"

        private val KNOWN_MODELS = listOf(
            "gemini-2.5-pro" to "Gemini 2.5 Pro",
            "gemini-2.5-flash" to "Gemini 2.5 Flash",
            "gemini-2.5-flash-lite" to "Gemini 2.5 Flash Lite",
            "gemini-2.0-flash" to "Gemini 2.0 Flash",
            "gemini-2.0-flash-001" to "Gemini 2.0 Flash 001",
            "gemini-2.0-flash-lite" to "Gemini 2.0 Flash Lite",
            "gemini-1.5-pro" to "Gemini 1.5 Pro",
            "gemini-1.5-flash" to "Gemini 1.5 Flash",
            "gemini-3.1-pro-preview" to "Gemini 3.1 Pro Preview",
            "gemini-3-pro-preview" to "Gemini 3 Pro Preview",
            "gemini-3-flash-preview" to "Gemini 3 Flash Preview",
        )
    }

    data class VertexModel(
        val modelId: String,
        val displayName: String,
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var cachedModels: List<VertexModel> = emptyList()
    private var lastServiceAccountHash: Int = 0

    fun getModels(): List<VertexModel> = cachedModels

    suspend fun refresh(serviceAccountJson: String): List<VertexModel> = mutex.withLock {
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
            val models = withContext(Dispatchers.IO) { probeModels(serviceAccountJson) }
            cachedModels = models
            lastServiceAccountHash = hash
            Log.i(TAG, "Found ${models.size} accessible Gemini models on Vertex AI")
            models
        } catch (e: Exception) {
            Log.w(TAG, "Failed to probe Vertex AI models: ${e.message}")
            cachedModels
        }
    }

    private fun probeModels(serviceAccountJson: String): List<VertexModel> {
        val accessToken = GoogleAuth.fetchAccessTokenSync(serviceAccountJson, SCOPES)
        val region = regionProvider()
        val result = mutableListOf<VertexModel>()

        for ((modelId, displayName) in KNOWN_MODELS) {
            val url = "https://$region-aiplatform.googleapis.com/v1beta1/publishers/google/models/$modelId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    result.add(VertexModel(modelId = "google/$modelId", displayName = displayName))
                }
                response.close()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to probe $modelId: ${e.message}")
            }
        }

        return result
    }
}
