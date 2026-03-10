package org.ethereumphone.andyclaw.llm

/**
 * LLM client for Claude setup-tokens (sk-ant-oat01-...).
 *
 * Matches OpenClaw behavior:
 * - Use the setup-token directly as Bearer auth
 * - Call Anthropic's native Messages API
 * - Include Anthropic OAuth beta headers required for setup-token auth
 */
class ClaudeOauthClient(
    private val setupTokenProvider: () -> String,
) : LlmClient {

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val OAUTH_BETAS =
            "claude-code-20250219,oauth-2025-04-20,fine-grained-tool-streaming-2025-05-14,interleaved-thinking-2025-05-14"
    }

    override suspend fun sendMessage(request: MessagesRequest): MessagesResponse {
        val token = setupTokenProvider().trim()
        if (token.isBlank()) {
            throw ClaudeOauthException("No setup-token configured. Paste your Claude setup-token in Settings.")
        }
        return clientWithToken(token).sendMessage(request)
    }

    override suspend fun streamMessage(request: MessagesRequest, callback: StreamingCallback) {
        val token = setupTokenProvider().trim()
        if (token.isBlank()) {
            callback.onError(
                ClaudeOauthException("No setup-token configured. Paste your Claude setup-token in Settings."),
            )
            return
        }
        clientWithToken(token).streamMessage(request, callback)
    }

    private fun clientWithToken(token: String) = AnthropicClient(
        apiKey = { token },
        extraHeaders = {
            mapOf(
                "anthropic-beta" to OAUTH_BETAS,
            )
        },
        baseUrl = BASE_URL,
    )
}
