package org.ethereumphone.andyclaw.llm

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
 * [LlmClient] for Google Gemini models via the Generative Language API
 * (`generativelanguage.googleapis.com`).
 *
 * Authenticates using a Google service account JSON key via OAuth2 token
 * exchange. Converts between the app's internal Anthropic message format
 * and Gemini's native generateContent / streamGenerateContent format.
 */
class GeminiApiClient(
    private val serviceAccountJsonProvider: () -> String,
) : LlmClient {

    companion object {
        private const val TAG = "GeminiApiClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val TOKEN_LIFETIME_SECONDS = 3600L
        private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
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

    private fun parseServiceAccount(): ServiceAccountInfo {
        val raw = serviceAccountJsonProvider()
        require(raw.isNotBlank()) { "Gemini service account JSON is not configured" }
        val obj = json.parseToJsonElement(raw).jsonObject
        return ServiceAccountInfo(
            clientEmail = obj["client_email"]?.jsonPrimitive?.content
                ?: error("Missing client_email in service account JSON"),
            privateKeyPem = obj["private_key"]?.jsonPrimitive?.content
                ?: error("Missing private_key in service account JSON"),
            tokenUri = obj["token_uri"]?.jsonPrimitive?.content
                ?: "https://oauth2.googleapis.com/token",
        )
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
        val scope = "https://www.googleapis.com/auth/generative-language https://www.googleapis.com/auth/cloud-platform"
        val claims = """{"iss":"${sa.clientEmail}","scope":"$scope","aud":"${sa.tokenUri}","iat":$nowSeconds,"exp":${nowSeconds + TOKEN_LIFETIME_SECONDS}}"""

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
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw AnthropicApiException(response.code, "Token exchange failed: $errorBody")
        }

        val responseJson = json.parseToJsonElement(response.body!!.string()).jsonObject
        return responseJson["access_token"]?.jsonPrimitive?.content
            ?: error("No access_token in token response")
    }

    // ── LlmClient implementation ─────────────────────────────────────

    override suspend fun sendMessage(request: MessagesRequest): MessagesResponse =
        withContext(Dispatchers.IO) {
            val accessToken = getAccessToken()
            val modelName = request.model.removePrefix("google/")
            val url = "$BASE_URL/models/$modelName:generateContent"
            val geminiJson = toGeminiRequestJson(request)
            Log.d(TAG, "sendMessage: model=$modelName, messages=${request.messages.size}")

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(geminiJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "sendMessage: HTTP ${response.code}, error=$errorBody")
                throw AnthropicApiException(response.code, errorBody)
            }

            val responseBody = response.body?.string()
                ?: throw AnthropicApiException(500, "Empty response")
            fromGeminiResponseJson(responseBody, modelName)
        }

    override suspend fun streamMessage(
        request: MessagesRequest,
        callback: StreamingCallback,
    ) = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken()
        val modelName = request.model.removePrefix("google/")
        val url = "$BASE_URL/models/$modelName:streamGenerateContent?alt=sse"
        val geminiJson = toGeminiRequestJson(request)
        Log.d(TAG, "streamMessage: model=$modelName, messages=${request.messages.size}")

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(geminiJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "streamMessage: HTTP ${response.code}, error=$errorBody")
            callback.onError(AnthropicApiException(response.code, errorBody))
            return@withContext
        }

        val textAccumulator = StringBuilder()
        val toolCalls = mutableListOf<ContentBlock.ToolUseBlock>()
        var usageData: Usage? = null

        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        try {
            reader.forEachLine { line ->
                if (!line.startsWith("data: ")) return@forEachLine
                val data = line.removePrefix("data: ").trim()
                if (data.isEmpty() || data == "[DONE]") return@forEachLine

                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    val candidates = chunk["candidates"]?.jsonArray ?: return@forEachLine

                    for (candidate in candidates) {
                        val content = candidate.jsonObject["content"]?.jsonObject ?: continue
                        val parts = content["parts"]?.jsonArray ?: continue

                        for (part in parts) {
                            val partObj = part.jsonObject

                            // Text content
                            val text = partObj["text"]?.jsonPrimitive?.contentOrNull
                            if (text != null) {
                                textAccumulator.append(text)
                                callback.onToken(text)
                            }

                            // Function call
                            val fc = partObj["functionCall"]?.jsonObject
                            if (fc != null) {
                                val name = fc["name"]?.jsonPrimitive?.content ?: ""
                                val args = fc["args"]?.jsonObject ?: JsonObject(emptyMap())
                                val id = "call_${System.nanoTime()}"
                                toolCalls.add(ContentBlock.ToolUseBlock(id, name, args))
                                callback.onToolUse(id, name, args)
                            }
                        }
                    }

                    // Usage metadata
                    val um = chunk["usageMetadata"]?.jsonObject
                    if (um != null) {
                        usageData = Usage(
                            inputTokens = um["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            outputTokens = um["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse stream chunk: ${e.message}")
                }
            }

            // Build final response
            val contentBlocks = mutableListOf<ContentBlock>()
            if (textAccumulator.isNotEmpty()) {
                contentBlocks.add(ContentBlock.TextBlock(textAccumulator.toString()))
            }
            contentBlocks.addAll(toolCalls)

            val stopReason = if (toolCalls.isNotEmpty()) "tool_use" else "end_turn"
            callback.onComplete(MessagesResponse(
                id = "gemini-${System.nanoTime()}",
                type = "message",
                role = "assistant",
                content = contentBlocks,
                model = modelName,
                stopReason = stopReason,
                usage = usageData,
            ))
        } catch (e: Exception) {
            Log.e(TAG, "streamMessage: streaming error", e)
            callback.onError(e)
        } finally {
            reader.close()
            response.close()
        }
    }

    // ── Format conversion: Anthropic → Gemini ────────────────────────

    private fun toGeminiRequestJson(request: MessagesRequest): String {
        return buildJsonObject {
            // System instruction
            request.system?.let { sys ->
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", sys) })
                    })
                })
            }

            // Contents (message history)
            put("contents", buildJsonArray {
                for (msg in request.messages) {
                    val geminiParts = convertMessageParts(msg)
                    if (geminiParts.isNotEmpty()) {
                        add(buildJsonObject {
                            put("role", if (msg.role == "assistant") "model" else "user")
                            put("parts", geminiParts)
                        })
                    }
                }
            })

            // Tools
            request.tools?.let { tools ->
                if (tools.isNotEmpty()) {
                    put("tools", buildJsonArray {
                        add(buildJsonObject {
                            put("functionDeclarations", buildJsonArray {
                                for (tool in tools) {
                                    add(convertToolToGemini(tool))
                                }
                            })
                        })
                    })
                }
            }

            // Generation config
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", request.maxTokens)
                request.temperature?.let { put("temperature", it.toDouble()) }
            })
        }.toString()
    }

    private fun convertMessageParts(msg: Message): JsonArray {
        return buildJsonArray {
            when (val content = msg.content) {
                is MessageContent.Text -> {
                    add(buildJsonObject { put("text", content.value) })
                }
                is MessageContent.Blocks -> {
                    for (block in content.blocks) {
                        when (block) {
                            is ContentBlock.TextBlock -> {
                                add(buildJsonObject { put("text", block.text) })
                            }
                            is ContentBlock.ToolUseBlock -> {
                                add(buildJsonObject {
                                    put("functionCall", buildJsonObject {
                                        put("name", block.name)
                                        put("args", block.input)
                                    })
                                })
                            }
                            is ContentBlock.ToolResult -> {
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", block.toolUseId)
                                        put("response", buildJsonObject {
                                            put("result", block.content)
                                        })
                                    })
                                })
                            }
                            is ContentBlock.ThinkingBlock,
                            is ContentBlock.RedactedThinkingBlock -> {
                                // Skip thinking blocks
                            }
                        }
                    }
                }
            }
        }
    }

    private fun convertToolToGemini(tool: JsonObject): JsonObject {
        val name = tool["name"]?.jsonPrimitive?.content ?: ""
        val description = tool["description"]?.jsonPrimitive?.content ?: ""
        val inputSchema = tool["input_schema"]?.jsonObject

        return buildJsonObject {
            put("name", name)
            put("description", description)
            if (inputSchema != null) {
                put("parameters", inputSchema)
            }
        }
    }

    // ── Format conversion: Gemini → Anthropic ────────────────────────

    private fun fromGeminiResponseJson(jsonBody: String, model: String): MessagesResponse {
        val root = json.parseToJsonElement(jsonBody).jsonObject
        val candidates = root["candidates"]?.jsonArray
        val contentBlocks = mutableListOf<ContentBlock>()
        var stopReason = "end_turn"

        if (candidates != null && candidates.isNotEmpty()) {
            val candidate = candidates[0].jsonObject
            val content = candidate["content"]?.jsonObject
            val parts = content?.get("parts")?.jsonArray

            parts?.forEach { part ->
                val partObj = part.jsonObject
                val text = partObj["text"]?.jsonPrimitive?.contentOrNull
                if (text != null) {
                    contentBlocks.add(ContentBlock.TextBlock(text))
                }

                val fc = partObj["functionCall"]?.jsonObject
                if (fc != null) {
                    val name = fc["name"]?.jsonPrimitive?.content ?: ""
                    val args = fc["args"]?.jsonObject ?: JsonObject(emptyMap())
                    contentBlocks.add(ContentBlock.ToolUseBlock("call_${System.nanoTime()}", name, args))
                    stopReason = "tool_use"
                }
            }

            val finishReason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
            if (finishReason == "STOP" && stopReason != "tool_use") stopReason = "end_turn"
            if (finishReason == "MAX_TOKENS") stopReason = "max_tokens"
        }

        val usageMetadata = root["usageMetadata"]?.jsonObject
        val usage = if (usageMetadata != null) Usage(
            inputTokens = usageMetadata["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            outputTokens = usageMetadata["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        ) else null

        return MessagesResponse(
            id = "gemini-${System.nanoTime()}",
            type = "message",
            role = "assistant",
            content = contentBlocks,
            model = model,
            stopReason = stopReason,
            usage = usage,
        )
    }

    // ── Crypto helpers ───────────────────────────────────────────────

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

    private data class ServiceAccountInfo(
        val clientEmail: String,
        val privateKeyPem: String,
        val tokenUri: String,
    )
}
