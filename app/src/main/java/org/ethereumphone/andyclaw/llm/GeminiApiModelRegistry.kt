package org.ethereumphone.andyclaw.llm

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * Queries the Generative Language API for Gemini models that the configured
 * service account has access to. Results are cached in memory.
 *
 * Uses `GET /v1beta/models` on `generativelanguage.googleapis.com`,
 * filtered to Gemini models that support `generateContent`.
 */
class GeminiApiModelRegistry {

    companion object {
        private const val TAG = "GeminiApiModelRegistry"
        private const val TOKEN_LIFETIME_SECONDS = 3600L
        private const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val json = Json { ignoreUnknownKeys = true }
    }

    data class VertexModel(
        val modelId: String,
        val displayName: String,
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var cachedModels: List<VertexModel> = emptyList()
    private var lastServiceAccountHash: Int = 0

    fun getModels(): List<VertexModel> = cachedModels

    /**
     * Fetch available Gemini models using the service account JSON.
     * Only re-fetches if the service account changed since last fetch.
     */
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

    private fun fetchModels(serviceAccountJson: String): List<VertexModel> {
        val accessToken = getAccessToken(serviceAccountJson)

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

    private fun parseModels(jsonBody: String): List<VertexModel> {
        val root = JSONObject(jsonBody)
        val models = root.optJSONArray("models") ?: return emptyList()
        val result = mutableListOf<VertexModel>()

        for (i in 0 until models.length()) {
            val model = models.getJSONObject(i)
            // name is like "models/gemini-2.5-flash"
            val fullName = model.optString("name", "")
            val modelName = fullName.removePrefix("models/")

            // Only include Gemini models
            if (!modelName.startsWith("gemini")) continue

            // Only include models that support generateContent
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

            result.add(VertexModel(
                modelId = modelName,
                displayName = displayName,
            ))
        }

        // Sort: pro models first, then flash, then flash-lite, then others
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

    // ── OAuth2 token exchange ────────────────────────────────────────

    private fun getAccessToken(serviceAccountJson: String): String {
        val obj = json.parseToJsonElement(serviceAccountJson).jsonObject
        val clientEmail = obj["client_email"]?.jsonPrimitive?.content
            ?: error("Missing client_email")
        val privateKeyPem = obj["private_key"]?.jsonPrimitive?.content
            ?: error("Missing private_key")
        val tokenUri = obj["token_uri"]?.jsonPrimitive?.content
            ?: "https://oauth2.googleapis.com/token"

        val nowSeconds = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val scope = "https://www.googleapis.com/auth/generative-language https://www.googleapis.com/auth/cloud-platform"
        val claims = """{"iss":"$clientEmail","scope":"$scope","aud":"$tokenUri","iat":$nowSeconds,"exp":${nowSeconds + TOKEN_LIFETIME_SECONDS}}"""

        val headerB64 = base64UrlEncode(header.toByteArray())
        val claimsB64 = base64UrlEncode(claims.toByteArray())
        val signingInput = "$headerB64.$claimsB64"

        val privateKey = parsePrivateKey(privateKeyPem)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(signingInput.toByteArray())
        val signatureB64 = base64UrlEncode(sig.sign())
        val jwt = "$signingInput.$signatureB64"

        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()

        val request = Request.Builder().url(tokenUri).post(body).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Token exchange failed: ${response.body?.string()}")
        }

        val responseJson = json.parseToJsonElement(response.body!!.string()).jsonObject
        return responseJson["access_token"]?.jsonPrimitive?.content
            ?: error("No access_token in token response")
    }

    private fun parsePrivateKey(pem: String): java.security.PrivateKey {
        val stripped = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
        val keyBytes = Base64.decode(stripped, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    private fun base64UrlEncode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
