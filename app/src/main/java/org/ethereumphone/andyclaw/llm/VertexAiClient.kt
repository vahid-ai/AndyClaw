package org.ethereumphone.andyclaw.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * [LlmClient] for Google Gemini models via the real Vertex AI
 * (`aiplatform.googleapis.com`) OpenAI-compatible endpoint.
 *
 * Uses [GoogleAuth] for OAuth2 service-account authentication,
 * [OpenAiFormatAdapter] for message format conversion, and
 * [OpenAiStreamAccumulator] for streaming.
 */
class VertexAiClient(
    private val serviceAccountJsonProvider: () -> String,
    private val regionProvider: () -> String = { "us-central1" },
) : LlmClient {

    companion object {
        private const val TAG = "VertexAiClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val auth = GoogleAuth(
        serviceAccountJsonProvider = serviceAccountJsonProvider,
        scopes = "https://www.googleapis.com/auth/cloud-platform",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildEndpointUrl(): String {
        val sa = GoogleAuth.parseServiceAccount(serviceAccountJsonProvider())
        val region = regionProvider()
        return "https://$region-aiplatform.googleapis.com/v1beta1/projects/${sa.projectId}/locations/$region/endpoints/openapi/chat/completions"
    }

    override suspend fun sendMessage(request: MessagesRequest): MessagesResponse =
        withContext(Dispatchers.IO) {
            val accessToken = auth.getAccessToken()
            val url = buildEndpointUrl()
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
        val accessToken = auth.getAccessToken()
        val url = buildEndpointUrl()
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
}
