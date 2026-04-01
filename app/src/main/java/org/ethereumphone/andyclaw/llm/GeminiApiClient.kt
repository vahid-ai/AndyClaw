package org.ethereumphone.andyclaw.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * [LlmClient] for Google Gemini models via the Generative Language API
 * (`generativelanguage.googleapis.com`).
 *
 * Uses [GoogleAuth] for OAuth2 service-account authentication.
 * Converts between the app's internal Anthropic message format and
 * Gemini's native generateContent / streamGenerateContent format.
 */
class GeminiApiClient(
    private val serviceAccountJsonProvider: () -> String,
) : LlmClient {

    companion object {
        private const val TAG = "GeminiApiClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val auth = GoogleAuth(
        serviceAccountJsonProvider = serviceAccountJsonProvider,
        scopes = "https://www.googleapis.com/auth/generative-language https://www.googleapis.com/auth/cloud-platform",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── LlmClient implementation ─────────────────────────────────────

    override suspend fun sendMessage(request: MessagesRequest): MessagesResponse =
        withContext(Dispatchers.IO) {
            val accessToken = auth.getAccessToken()
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
        val accessToken = auth.getAccessToken()
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
                            val text = partObj["text"]?.jsonPrimitive?.contentOrNull
                            if (text != null) {
                                textAccumulator.append(text)
                                callback.onToken(text)
                            }
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
            request.system?.let { sys ->
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", sys) })
                    })
                })
            }

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
                            is ContentBlock.TextBlock ->
                                add(buildJsonObject { put("text", block.text) })
                            is ContentBlock.ToolUseBlock ->
                                add(buildJsonObject {
                                    put("functionCall", buildJsonObject {
                                        put("name", block.name)
                                        put("args", block.input)
                                    })
                                })
                            is ContentBlock.ToolResult ->
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", block.toolUseId)
                                        put("response", buildJsonObject {
                                            put("result", block.content)
                                        })
                                    })
                                })
                            is ContentBlock.ThinkingBlock,
                            is ContentBlock.RedactedThinkingBlock -> { }
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
            if (inputSchema != null) put("parameters", inputSchema)
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
                if (text != null) contentBlocks.add(ContentBlock.TextBlock(text))

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
}
