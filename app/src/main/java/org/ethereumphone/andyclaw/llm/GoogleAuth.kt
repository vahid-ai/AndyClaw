package org.ethereumphone.andyclaw.llm

import android.util.Base64
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
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * Shared OAuth2 service-account authentication for Google APIs.
 *
 * Creates a signed JWT from the service account credentials, exchanges it
 * for an access token at the token URI, and caches the result.
 *
 * @param serviceAccountJsonProvider Returns the raw service account JSON string.
 * @param scopes OAuth2 scopes to request (space-separated).
 */
class GoogleAuth(
    private val serviceAccountJsonProvider: () -> String,
    private val scopes: String,
) {
    companion object {
        private const val TOKEN_LIFETIME_SECONDS = 3600L
        private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L
        private val json = Json { ignoreUnknownKeys = true }

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** Parse service account JSON and return key fields. */
        fun parseServiceAccount(raw: String): ServiceAccountInfo {
            require(raw.isNotBlank()) { "Service account JSON is not configured" }
            val obj = json.parseToJsonElement(raw).jsonObject
            return ServiceAccountInfo(
                projectId = obj["project_id"]?.jsonPrimitive?.content ?: "",
                clientEmail = obj["client_email"]?.jsonPrimitive?.content
                    ?: error("Missing client_email in service account JSON"),
                privateKeyPem = obj["private_key"]?.jsonPrimitive?.content
                    ?: error("Missing private_key in service account JSON"),
                tokenUri = obj["token_uri"]?.jsonPrimitive?.content
                    ?: "https://oauth2.googleapis.com/token",
            )
        }

        /**
         * One-shot token fetch without caching. Useful for model registries
         * that run infrequently.
         */
        fun fetchAccessTokenSync(serviceAccountJson: String, scopes: String): String {
            val sa = parseServiceAccount(serviceAccountJson)
            return exchangeJwtForToken(sa, scopes)
        }

        private fun exchangeJwtForToken(sa: ServiceAccountInfo, scopes: String): String {
            val nowSeconds = System.currentTimeMillis() / 1000
            val header = """{"alg":"RS256","typ":"JWT"}"""
            val claims = """{"iss":"${sa.clientEmail}","scope":"$scopes","aud":"${sa.tokenUri}","iat":$nowSeconds,"exp":${nowSeconds + TOKEN_LIFETIME_SECONDS}}"""

            val headerB64 = base64UrlEncode(header.toByteArray())
            val claimsB64 = base64UrlEncode(claims.toByteArray())
            val signingInput = "$headerB64.$claimsB64"

            val privateKey = parsePrivateKey(sa.privateKeyPem)
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(privateKey)
            sig.update(signingInput.toByteArray())
            val signatureB64 = base64UrlEncode(sig.sign())
            val jwt = "$signingInput.$signatureB64"

            val body = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", jwt)
                .build()

            val request = Request.Builder().url(sa.tokenUri).post(body).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw AnthropicApiException(response.code, "Token exchange failed: $errorBody")
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
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        }

        private fun base64UrlEncode(data: ByteArray): String =
            Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    data class ServiceAccountInfo(
        val projectId: String,
        val clientEmail: String,
        val privateKeyPem: String,
        val tokenUri: String,
    )

    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAtMs: Long = 0L

    /** Get a cached or fresh access token. Thread-safe. */
    suspend fun getAccessToken(): String = mutex.withLock {
        val now = System.currentTimeMillis()
        cachedToken?.let { token ->
            if (now < expiresAtMs - REFRESH_BUFFER_MS) return token
        }
        val sa = parseServiceAccount(serviceAccountJsonProvider())
        val token = withContext(Dispatchers.IO) { exchangeJwtForToken(sa, scopes) }
        cachedToken = token
        expiresAtMs = now + TOKEN_LIFETIME_SECONDS * 1000
        token
    }
}
