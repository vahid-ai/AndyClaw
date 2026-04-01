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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * [LlmClient] for Google Gemini models via the real Vertex AI
 * (`aiplatform.googleapis.com`) OpenAI-compatible endpoint.
 *
 * Authenticates via OAuth2 token exchange using a Google Cloud service account.
 * Requires the Vertex AI API to be enabled in the GCP project and the service
 * account to have the Vertex AI User role.
 *
 * Uses [OpenAiFormatAdapter] for message format conversion and
 * [OpenAiStreamAccumulator] for streaming.
 */
class VertexAiClient(
    private val serviceAccountJsonProvider: () -> String,
    private val regionProvider: () -> String = { "us-central1" },
) : LlmClient {

    companion object {
        private const val TAG = "VertexAiClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val TOKEN_LIFETIME_SECONDS = 3600L
        private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val tokenMutex = Mutex()
    private var cachedAccessToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    // ── Auth ──────────────────────────────────────────────────────────

    private data class ServiceAccountInfo(
        val projectId: String,
        val clientEmail: String,
        val privateKeyPem: String,
        val tokenUri: String,
    )

    private fun parseServiceAccount(): ServiceAccountInfo {
        val raw = serviceAccountJsonProvider()
        require(raw.isNotBlank()) { "Vertex AI service account JSON is not configured" }
        val obj = json.parseToJsonElement(raw).jsonObject
        return ServiceAccountInfo(
            projectId = obj["project_id"]?.jsonPrimitive?.content
                ?: error("Missing project_id in service account JSON"),
            clientEmail = obj["client_email"]?.jsonPrimitive?.content
                ?: error("Missing client_email in service account JSON"),
            privateKeyPem = obj["private_key"]?.jsonPrimitive?.content
                ?: error("Missing private_key in service account JSON"),
            tokenUri = obj["token_uri"]?.jsonPrimitive?.content
                ?: "https://oauth2.googleapis.com/token",
        )
    }

    private fun buildEndpointUrl(projectId: String): String {
        val region = regionProvider()
        return "https://$region-aiplatform.googleapis.com/v1beta1/projects/$projectId/locations/$region/endpoints/openapi/chat/completions"
    }

    private suspend fun getAccessToken(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        cachedAccessToken?.let { token ->
            if (now < tokenExpiresAtMs - REFRESH_BUFFER_MS) return token
        }
        val sa = parseServiceAccount()
        val token = withContext(Dispatchers.IO) { fetchAccessToken(sa) }
        cachedAccessToken = token
        tokenExpiresAtMs = now + TOKEN_LIFETIME_SECONDS * 1000
        token
    }

    private fun fetchAccessToken(sa: ServiceAccountInfo): String {
        val nowSeconds = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = """{"iss":"${sa.clientEmail}","scope":"https://www.googleapis.com/auth/cloud-platform","aud":"${sa.tokenUri}","iat":$nowSeconds,"exp":${nowSeconds + TOKEN_LIFETIME_SECONDS}}"""

        val headerB64 = base64UrlEncode(header.toByteArray())
        val claimsB64 = base64UrlEncode(claims.toByteArray())
        val signingInput = "$headerB64.$claimsB64"

        val privateKey = parsePrivateKey(sa.privateKeyPem)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(signingInput.toByteArray())
        val jwt = "$signingInput.${base64UrlEncode(sig.sign())}"

        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()

        val request = Request.Builder().url(sa.tokenUri).post(body).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw AnthropicApiException(response.code, "Token exchange failed: $errorBody")
        }

        val responseJson = json.parseToJsonElement(response.body!!.string()).jsonObject
        return responseJson["access_token"]?.jsonPrimitive?.content
            ?: error("No access_token in token response")
    }

    // ── LlmClient ────────────────────────────────────────────────────

    override suspend fun sendMessage(request: MessagesRequest): MessagesResponse =
        withContext(Dispatchers.IO) {
            val sa = parseServiceAccount()
            val accessToken = getAccessToken()
            val url = buildEndpointUrl(sa.projectId)
            val openAiJson = OpenAiFormatAdapter.toOpenAiRequestJson(request.copy(stream = false))
            Log.d(TAG, "sendMessage: model=${request.model}, messages=${request.messages.size}")

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(openAiJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "sendMessage: HTTP ${response.code}, error=$errorBody")
                throw AnthropicApiException(response.code, errorBody)
            }

            val responseBody = response.body?.string()
                ?: throw AnthropicApiException(500, "Empty response")
            OpenAiFormatAdapter.fromOpenAiResponseJson(responseBody)
        }

    override suspend fun streamMessage(
        request: MessagesRequest,
        callback: StreamingCallback,
    ) = withContext(Dispatchers.IO) {
        val sa = parseServiceAccount()
        val accessToken = getAccessToken()
        val url = buildEndpointUrl(sa.projectId)
        val openAiJson = OpenAiFormatAdapter.toOpenAiRequestJson(request.copy(stream = true))
        Log.d(TAG, "streamMessage: model=${request.model}, messages=${request.messages.size}")

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(openAiJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "streamMessage: HTTP ${response.code}, error=$errorBody")
            callback.onError(AnthropicApiException(response.code, errorBody))
            return@withContext
        }

        val accumulator = OpenAiStreamAccumulator(callback)
        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        try {
            reader.forEachLine { line ->
                if (line.startsWith("data: ")) {
                    accumulator.onData(line.removePrefix("data: "))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "streamMessage: streaming error", e)
            callback.onError(e)
        } finally {
            reader.close()
            response.close()
        }
    }

    // ── Crypto helpers ───────────────────────────────────────────────

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
