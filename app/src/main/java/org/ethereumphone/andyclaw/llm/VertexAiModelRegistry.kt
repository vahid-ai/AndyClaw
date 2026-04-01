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
 * Discovers available Gemini models on Vertex AI (`aiplatform.googleapis.com`)
 * by probing individual known model IDs. The Vertex AI list endpoint may
 * return empty, so we check each model individually.
 */
class VertexAiModelRegistry(
    private val regionProvider: () -> String = { "us-central1" },
) {

    companion object {
        private const val TAG = "VertexAiModelRegistry"
        private const val TOKEN_LIFETIME_SECONDS = 3600L
        private val json = Json { ignoreUnknownKeys = true }

        /** Known Gemini model IDs to probe on Vertex AI. */
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

    /**
     * Probe each known model by GETting its metadata endpoint.
     * 200 = accessible, 403/404 = not available for this project.
     */
    private fun probeModels(serviceAccountJson: String): List<VertexModel> {
        val accessToken = getAccessToken(serviceAccountJson)
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
                    result.add(VertexModel(
                        modelId = "google/$modelId",
                        displayName = displayName,
                    ))
                }
                response.close()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to probe $modelId: ${e.message}")
            }
        }

        return result
    }

    // ── OAuth2 token exchange ────────────────────────────────────────

    private fun getAccessToken(serviceAccountJson: String): String {
        val obj = json.parseToJsonElement(serviceAccountJson).jsonObject
        val clientEmail = obj["client_email"]?.jsonPrimitive?.content ?: error("Missing client_email")
        val privateKeyPem = obj["private_key"]?.jsonPrimitive?.content ?: error("Missing private_key")
        val tokenUri = obj["token_uri"]?.jsonPrimitive?.content ?: "https://oauth2.googleapis.com/token"

        val nowSeconds = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = """{"iss":"$clientEmail","scope":"https://www.googleapis.com/auth/cloud-platform","aud":"$tokenUri","iat":$nowSeconds,"exp":${nowSeconds + TOKEN_LIFETIME_SECONDS}}"""

        val headerB64 = base64UrlEncode(header.toByteArray())
        val claimsB64 = base64UrlEncode(claims.toByteArray())
        val signingInput = "$headerB64.$claimsB64"

        val privateKey = parsePrivateKey(privateKeyPem)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(signingInput.toByteArray())
        val jwt = "$signingInput.${base64UrlEncode(sig.sign())}"

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
        return responseJson["access_token"]?.jsonPrimitive?.content ?: error("No access_token")
    }

    private fun parsePrivateKey(pem: String): java.security.PrivateKey {
        val stripped = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "").replace("\n", "").replace("\r", "").replace(" ", "")
        val keyBytes = Base64.decode(stripped, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    private fun base64UrlEncode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
