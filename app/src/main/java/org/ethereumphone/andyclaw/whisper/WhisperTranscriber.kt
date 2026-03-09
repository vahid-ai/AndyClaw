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
 *
 * Because the WhisperBridge API does not expose whisper.cpp's `initial_prompt`
 * parameter, domain-specific vocabulary (crypto/web3) is corrected via
 * post-processing regex replacements in [applyVocabCorrections].
 */
class WhisperTranscriber(private val context: Context) {

    companion object {
        private const val TAG = "WhisperTranscriber"
        private const val ASSET_NAME = "ggml-base.en.bin"

        /**
         * Case-insensitive word-boundary replacements for crypto/web3 terms
         * that Whisper's base model commonly misrecognises.
         *
         * Order matters — more specific patterns (e.g. "cronjobs") must come
         * before shorter/broader ones (e.g. "cron") to avoid double-replacing.
         */
        private val VOCAB_CORRECTIONS: List<Pair<Regex, String>> = listOf(
            // ethOS devices — must come before broader Ethereum patterns
            "\\bd[- ]?gen\\s?(?:1|one)\\b"  to "dGEN1",
            "\\bdeje[mn]\\s?(?:1|one)\\b"   to "dGEN1",
            "\\bd[- ]?gen\\b"               to "dGEN",

            // Ethereum ecosystem
            "\\bethe?r[ei]?um\\b"      to "Ethereum",
            "\\beth\\s?os\\b"          to "ethOS",
            "\\bg\\s?wei\\b"           to "gwei",
            "\\bde\\s?fi\\b"           to "DeFi",
            "\\bdefy\\b"               to "DeFi",
            "\\bnfts\\b"               to "NFTs",
            "\\bn\\s?f\\s?t\\b"        to "NFT",

            // Tokens & stablecoins
            "\\bu\\s?s\\s?d\\s?[cs]\\b" to "USDC",
            "\\bu\\s?s\\s?d\\s?t\\b"    to "USDT",
            "\\bw\\s?e\\s?t\\s?h\\b"    to "WETH",
            "\\bw\\s?b\\s?t\\s?c\\b"    to "WBTC",
            "\\bd\\s?a\\s?i\\b"          to "DAI",

            // Protocols & standards
            "\\buni\\s?swap\\b"        to "Uniswap",
            "\\baave\\b"               to "Aave",
            "\\be\\s?n\\s?s\\b"        to "ENS",
            "\\berc\\s?[-\\s]?20\\b"   to "ERC-20",
            "\\berc\\s?[-\\s]?721\\b"  to "ERC-721",
            "\\berc\\s?[-\\s]?1155\\b" to "ERC-1155",

            // Layer 2 / scaling
            "\\bl\\s?2\\b"             to "L2",
            "\\brollups\\b"            to "rollups",
            "\\brollup\\b"             to "rollup",
            "\\barbitrum\\b"           to "Arbitrum",
            "\\boptimism\\b"           to "Optimism",
            "\\bpolygon\\b"            to "Polygon",

            // General crypto
            "\\bblockchain\\b"         to "blockchain",
            "\\bsmart\\s?contract\\b"  to "smart contract",
            "\\bseed\\s?phrase\\b"     to "seed phrase",
            "\\bmnemonic\\b"           to "mnemonic",
            "\\bgas\\s?fee\\b"         to "gas fee",
            "\\bstaking\\b"            to "staking",

            // Scheduling — "cron" is often misheard as "chrome" or "kron"
            "\\bcronjobs\\b"           to "cronjobs",
            "\\bcronjob\\b"            to "cronjob",
            "\\bcron\\s?jobs\\b"       to "cronjobs",
            "\\bcron\\s?job\\b"        to "cronjob",
            "\\bchrome\\s?jobs\\b"     to "cronjobs",
            "\\bchrome\\s?job\\b"      to "cronjob",
            "\\bkron\\s?jobs\\b"       to "cronjobs",
            "\\bkron\\s?job\\b"        to "cronjob",
            "\\bkron\\b"              to "cron",
            "\\bcron\\b"              to "cron",
        ).map { (pattern, replacement) ->
            Regex(pattern, RegexOption.IGNORE_CASE) to replacement
        }
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

            val raw = WhisperBridge.transcribeWav(audioPath, "en").trim()

            if (raw.startsWith("ERROR:")) {
                Log.e(TAG, "Whisper error: $raw")
                throw RuntimeException("Whisper transcription failed: $raw")
            }

            val result = applyVocabCorrections(raw)

            if (result != raw) {
                Log.i(TAG, "Vocab corrected: \"$raw\" -> \"$result\"")
            }
            Log.i(TAG, "Transcription result: $result")
            result
        }
    }

    /**
     * Applies domain-specific vocabulary corrections to raw Whisper output.
     * Fixes common misrecognitions of crypto/web3 and scheduling terms.
     */
    private fun applyVocabCorrections(text: String): String {
        var corrected = text
        for ((pattern, replacement) in VOCAB_CORRECTIONS) {
            corrected = pattern.replace(corrected, replacement)
        }
        return corrected
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
