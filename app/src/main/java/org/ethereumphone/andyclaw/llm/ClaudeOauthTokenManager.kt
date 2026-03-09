package org.ethereumphone.andyclaw.llm

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Manages Claude OAuth access/refresh tokens.
 *
 * Users paste their refresh token (sk-ant-ort01-...) obtained via `claude setup-token`.
 * This manager automatically obtains and refreshes access tokens as needed.
 */
class ClaudeOauthTokenManager(
    private val refreshTokenProvider: () -> String,
    private val onTokensUpdated: (accessToken: String, expiresAtMillis: Long) -> Unit,
    private val accessTokenProvider: () -> String,
    private val expiresAtProvider: () -> Long,
) {
    companion object {
        private const val TAG = "ClaudeOauthTokenMgr"
        private const val TOKEN_ENDPOINT = "https://console.anthropic.com/api/oauth/token"
        private const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        /** Refresh 5 minutes before expiry to avoid mid-request failures. */
        private const val EXPIRY_BUFFER_MS = 5 * 60 * 1000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Returns a valid access token, refreshing if necessary.
     * Thread-safe — concurrent callers will wait for the same refresh.
     */
    suspend fun getValidAccessToken(): String {
        val current = accessTokenProvider()
        val expiresAt = expiresAtProvider()

        if (current.isNotBlank() && System.currentTimeMillis() < expiresAt - EXPIRY_BUFFER_MS) {
            return current
        }

        return mutex.withLock {
            // Double-check after acquiring lock — another coroutine may have refreshed already.
            val rechecked = accessTokenProvider()
            val recheckExpiry = expiresAtProvider()
            if (rechecked.isNotBlank() && System.currentTimeMillis() < recheckExpiry - EXPIRY_BUFFER_MS) {
                return@withLock rechecked
            }
            refresh()
        }
    }

    /**
     * Force a refresh (e.g. after a 401). Returns the new access token.
     */
    suspend fun forceRefresh(): String = mutex.withLock { refresh() }

    private fun refresh(): String {
        val refreshToken = refreshTokenProvider()
        if (refreshToken.isBlank()) {
            throw ClaudeOauthException("No refresh token configured. Paste your setup-token in Settings.")
        }

        Log.d(TAG, "Refreshing Claude OAuth access token...")

        val body = json.encodeToString(
            TokenRequest.serializer(),
            TokenRequest(refreshToken = refreshToken),
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Token refresh failed: HTTP ${response.code} — $responseBody")
            throw ClaudeOauthException("Token refresh failed (${response.code}). Your setup-token may be expired — run `claude setup-token` again.")
        }

        val tokenResponse = json.decodeFromString<TokenResponse>(responseBody)
        val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)

        onTokensUpdated(tokenResponse.accessToken, expiresAt)
        Log.i(TAG, "Claude OAuth token refreshed, expires in ${tokenResponse.expiresIn}s")

        return tokenResponse.accessToken
    }

    @Serializable
    private data class TokenRequest(
        val grant_type: String = "refresh_token",
        val refresh_token: String,
        val client_id: String = CLIENT_ID,
    ) {
        // Constructor with named parameter for clarity
        constructor(refreshToken: String) : this(
            refresh_token = refreshToken,
        )
    }

    @Serializable
    private data class TokenResponse(
        val token_type: String = "",
        val access_token: String,
        val expires_in: Long = 28800,
        val refresh_token: String = "",
    ) {
        val accessToken get() = access_token
        val expiresIn get() = expires_in
    }
}

class ClaudeOauthException(message: String) : Exception(message)
