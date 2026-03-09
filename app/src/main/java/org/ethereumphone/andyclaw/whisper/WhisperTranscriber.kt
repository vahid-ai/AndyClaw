package org.ethereumphone.andyclaw.whisper

import android.content.Context
import android.util.Log
import com.llamatik.library.platform.WhisperBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transcribes audio files using whisper.cpp via Llamatik's [WhisperBridge].
 *
 * The English-only base model (ggml-base.en.bin, ~142 MB) is bundled in the
 * APK assets and copied to internal storage on first use.
 */
class WhisperTranscriber(private val context: Context) {

    companion object {
        private const val TAG = "WhisperTranscriber"
        private const val ASSET_NAME = "ggml-base.en.bin"
    }

    private val modelFile = File(context.filesDir, ASSET_NAME)
    private var initialized = false
    private val mutex = Mutex()

    /**
     * Ensures the model is copied from assets and loaded into WhisperBridge.
     * Safe to call multiple times — no-ops if already initialized.
     */
    private suspend fun ensureInitialized() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            // Copy from assets to internal storage if not already there
            if (!modelFile.exists() || modelFile.length() == 0L) {
                Log.i(TAG, "Copying Whisper model from assets to ${modelFile.absolutePath}")
                context.assets.open(ASSET_NAME).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Model copied (${modelFile.length()} bytes)")
            }

            Log.i(TAG, "Initializing Whisper model: ${modelFile.absolutePath}")
            val success = WhisperBridge.initModel(modelFile.absolutePath)
            if (!success) {
                throw RuntimeException("Failed to initialize Whisper model")
            }
            initialized = true
            Log.i(TAG, "Whisper model initialized")
        }
    }

    /**
     * Transcribes the audio file at [audioPath].
     *
     * The file must be a 16 kHz mono 16-bit PCM WAV (which is what
     * [org.ethosmobile.ethoslauncher.dgent.AudioRecorder] produces).
     *
     * @return The transcribed text, or throws on failure.
     */
    suspend fun transcribe(audioPath: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val audioFile = File(audioPath)
            require(audioFile.exists()) { "Audio file does not exist: $audioPath" }

            ensureInitialized()

            Log.i(TAG, "Transcribing: $audioPath (${audioFile.length()} bytes)")

            val result = WhisperBridge.transcribeWav(audioPath, "en").trim()

            if (result.startsWith("ERROR:")) {
                Log.e(TAG, "Whisper error: $result")
                throw RuntimeException("Whisper transcription failed: $result")
            }

            Log.i(TAG, "Transcription result: $result")
            result
        }
    }

    /** Releases native resources. Call when the service is destroyed. */
    suspend fun release(): Unit = mutex.withLock {
        if (initialized) {
            withContext(Dispatchers.IO) {
                WhisperBridge.release()
                initialized = false
                Log.i(TAG, "Whisper model released")
            }
        }
    }
}
