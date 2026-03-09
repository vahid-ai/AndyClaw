package org.ethereumphone.andyclaw.llm

import android.util.Log

/**
 * LLM client that authenticates via Claude OAuth (Pro/Max subscription tokens).
 *
 * Wraps [AnthropicClient] pointed at the direct Anthropic API, with automatic
 * token refresh via [ClaudeOauthTokenManager].
 */
class ClaudeOauthClient(
    private val tokenManager: ClaudeOauthTokenManager,
) : LlmClient {

    companion object {
        private const val TAG = "ClaudeOauthClient"
        private const val BASE_URL = "https://api.anthropic.com/v1/messages"
    }

    private val inner = AnthropicClient(
        apiKey = { "" }, // placeholder — overridden per-request
        baseUrl = BASE_URL,
    )

    override suspend fun sendMessage(request: MessagesRequest): MessagesResponse {
        return try {
            val token = tokenManager.getValidAccessToken()
            clientWithToken(token).sendMessage(request)
        } catch (e: AnthropicApiException) {
            if (e.statusCode == 401) {
                Log.w(TAG, "Got 401, forcing token refresh...")
                val freshToken = tokenManager.forceRefresh()
                clientWithToken(freshToken).sendMessage(request)
            } else throw e
        }
    }

    override suspend fun streamMessage(request: MessagesRequest, callback: StreamingCallback) {
        try {
            val token = tokenManager.getValidAccessToken()
            clientWithToken(token).streamMessage(request, callback)
        } catch (e: AnthropicApiException) {
            if (e.statusCode == 401) {
                Log.w(TAG, "Got 401 during stream, forcing token refresh...")
                val freshToken = tokenManager.forceRefresh()
                clientWithToken(freshToken).streamMessage(request, callback)
            } else throw e
        }
    }

    private fun clientWithToken(token: String) = AnthropicClient(
        apiKey = { token },
        baseUrl = BASE_URL,
    )
}
