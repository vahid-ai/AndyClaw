package org.ethereumphone.andyclaw.whisper

/**
 * JNI bridge to whisper.cpp with optimised inference parameters.
 *
 * Key speed improvements over the Llamatik WhisperBridge:
 * - Dynamic `audio_ctx` proportional to actual audio length (vs fixed 1500 / 30 s)
 * - Uses all available CPU cores (vs Llamatik's cap of ~4)
 * - Greedy decoding, no timestamps, no temperature fallback
 * - Native build with -O3 and ARM NEON
 */
object WhisperBridgeNative {

    init {
        System.loadLibrary("whisper_jni")
    }

    /** Load the GGML model from [filePath]. Returns true on success. */
    @JvmStatic
    external fun initModel(filePath: String): Boolean

    /**
     * Transcribe a 16 kHz mono 16-bit PCM WAV file.
     * Returns the transcribed text, or a string starting with "ERROR:" on failure.
     */
    @JvmStatic
    external fun transcribeWav(audioPath: String, language: String): String

    /** Release native resources. */
    @JvmStatic
    external fun release()
}
