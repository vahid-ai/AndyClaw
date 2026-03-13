package org.ethereumphone.andyclaw.summary

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import org.ethereumphone.andyclaw.NodeApp
import org.ethereumphone.andyclaw.llm.AnthropicModels
import org.ethereumphone.andyclaw.llm.ContentBlock
import org.ethereumphone.andyclaw.llm.Message
import org.ethereumphone.andyclaw.llm.MessagesRequest

/**
 * Generates and stores an executive summary via the system-level
 * AndyClawHeartbeatService binder.
 *
 * The summary is a 2-3 sentence snapshot written by the LLM after
 * every heartbeat, notification, or lockscreen voice prompt.
 */
class ExecutiveSummaryManager(private val app: NodeApp) {

    companion object {
        private const val TAG = "ExecSummaryMgr"
        private const val OS_BINDER_DESCRIPTOR = "com.android.server.IAndyClawHeartbeat"
        private const val TRANSACTION_SET = 2 // IBinder.FIRST_CALL_TRANSACTION + 2
        private const val TRANSACTION_GET = 3 // IBinder.FIRST_CALL_TRANSACTION + 3

        private const val HEARTBEAT_SYSTEM_PROMPT = """Write a lockscreen executive summary as bullet points (• prefix). Summarize ONLY: device state (battery, connectivity), pending notifications, and upcoming tasks/reminders. Do NOT include the current time or date. Max 4-5 bullets, each one short sentence. Output ONLY the bullet points, nothing else."""

        private const val LOCKSCREEN_SYSTEM_PROMPT = """Update the lockscreen executive summary after the user issued a voice command. The FIRST bullet (• prefix) must be a one-sentence summary of what the user asked and what was done. Keep remaining bullets about: device state, notifications, tasks. Do NOT include the current time or date. Remove outdated bullets. Max 4-5 bullets total. Output ONLY the bullet points, nothing else."""

        private const val NOTIFICATION_SYSTEM_PROMPT = """Update the lockscreen executive summary to incorporate a new notification. Add or update a bullet (• prefix) for the new notification. Keep remaining bullets about: device state, notifications, tasks. Do NOT include the current time or date. Remove outdated bullets. Max 4-5 bullets total. Output ONLY the bullet points, nothing else."""
    }

    /**
     * Generate and store an executive summary after a heartbeat or notification.
     * No-op if the feature is disabled.
     */
    suspend fun generateAndStore(agentOutput: String) {
        if (!app.securePrefs.executiveSummaryEnabled.value) return

        try {
            val currentSummary = readSummaryFromService()
            val prompt = buildString {
                if (currentSummary.isNotBlank()) {
                    appendLine("Current executive summary: $currentSummary")
                    appendLine()
                }
                appendLine("Latest heartbeat output:")
                append(agentOutput.take(2000))
            }
            val newSummary = callLlm(HEARTBEAT_SYSTEM_PROMPT, prompt)
            if (newSummary.isNotBlank()) {
                writeSummaryToService(newSummary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate executive summary", e)
        }
    }

    /**
     * Generate and store an executive summary after a lockscreen voice prompt.
     * No-op if the feature is disabled.
     */
    suspend fun generateAndStoreForLockscreen(userPrompt: String, agentOutput: String) {
        if (!app.securePrefs.executiveSummaryEnabled.value) return

        try {
            val currentSummary = readSummaryFromService()
            val prompt = buildString {
                if (currentSummary.isNotBlank()) {
                    appendLine("Current executive summary: $currentSummary")
                    appendLine()
                }
                appendLine("User's voice command: $userPrompt")
                appendLine()
                appendLine("Execution result:")
                append(agentOutput.take(2000))
            }
            val newSummary = callLlm(LOCKSCREEN_SYSTEM_PROMPT, prompt)
            if (newSummary.isNotBlank()) {
                writeSummaryToService(newSummary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate lockscreen executive summary", e)
        }
    }

    /**
     * Generate and store an executive summary after an incoming notification.
     * The prompt already contains the current summary + notification details (built by the OS).
     */
    suspend fun generateAndStoreForNotification(prompt: String) {
        if (!app.securePrefs.executiveSummaryEnabled.value) return

        try {
            val newSummary = callLlm(NOTIFICATION_SYSTEM_PROMPT, prompt)
            if (newSummary.isNotBlank()) {
                writeSummaryToService(newSummary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate notification executive summary", e)
        }
    }

    private suspend fun callLlm(systemPrompt: String, userMessage: String): String {
        val client = app.getHeartbeatLlmClient()
        val useSameModel = app.securePrefs.heartbeatUseSameModel.value
        val modelId = if (useSameModel) {
            app.securePrefs.selectedModel.value
        } else {
            app.securePrefs.heartbeatModel.value
        }
        val model = AnthropicModels.fromModelId(modelId) ?: AnthropicModels.MINIMAX_M25

        val request = MessagesRequest(
            model = model.modelId,
            messages = listOf(
                Message.user(userMessage),
            ),
            system = systemPrompt,
            maxTokens = 300,
        )

        val response = client.sendMessage(request)
        return response.content
            .filterIsInstance<ContentBlock.TextBlock>()
            .joinToString("") { it.text }
            .trim()
    }

    private fun getServiceBinder(): IBinder? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            getService.invoke(null, "andyclawheartbeat") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get andyclawheartbeat service", e)
            null
        }
    }

    fun readSummaryFromService(): String {
        val binder = getServiceBinder() ?: return ""
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(OS_BINDER_DESCRIPTOR)
            binder.transact(
                IBinder.FIRST_CALL_TRANSACTION + TRANSACTION_GET,
                data, reply, 0
            )
            reply.readException()
            reply.readString() ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read executive summary from service", e)
            ""
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun writeSummaryToService(summary: String) {
        val binder = getServiceBinder() ?: return
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(OS_BINDER_DESCRIPTOR)
            data.writeString(summary)
            binder.transact(
                IBinder.FIRST_CALL_TRANSACTION + TRANSACTION_SET,
                data, null, IBinder.FLAG_ONEWAY
            )
            Log.d(TAG, "Executive summary written (${summary.length} chars)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write executive summary to service", e)
        } finally {
            data.recycle()
        }
    }
}
