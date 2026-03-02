package org.ethereumphone.andyclaw.led

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ethereumphone.terminalsdk.TerminalSDK

/**
 * Abstraction over the dGEN1 3×3 LED matrix via TerminalSDK.
 *
 * Provides both automatic lifecycle-driven patterns (spinner while processing,
 * context-aware completion) and a full public API for direct control by the
 * AI agent via [LedSkill].
 *
 * Gracefully no-ops on non-dGEN1 devices where the TerminalSDK LED subsystem
 * is unavailable. All animations run on a background coroutine scope with
 * thread-safe cancellation.
 *
 * @param context Application context used to initialize TerminalSDK.
 * @param maxRgbProvider Lambda returning the user's configured max RGB cap (0–255).
 */
class LedMatrixController(
    context: Context,
    private val maxRgbProvider: () -> Int = { 255 },
) {
    companion object {
        private const val TAG = "LedMatrixController"
        private const val OFF = "#000000"
        private const val SPINNER_FRAME_MS = 120L
        private const val COMPLETION_DISPLAY_MS = 3000L

        /** Named patterns available via the TerminalSDK LED driver. */
        val BUILTIN_PATTERNS = listOf(
            "chad", "plus", "minus", "success", "error", "warning",
            "info", "arrowup", "arrowdown", "swap", "sign",
        )
    }

    private val terminal: TerminalSDK? = try {
        val sdk = TerminalSDK(context)
        Log.i(TAG, "TerminalSDK created: isAvailable=${sdk.isAvailable}, isLedAvailable=${sdk.isLedAvailable}, isDisplayAvailable=${sdk.isDisplayAvailable}")
        if (sdk.isLedAvailable) {
            val led = sdk.led!!
            Log.i(TAG, "TerminalLED available=${led.isAvailable}, systemColor=${led.getSystemColor()}, patterns=${led.getAvailablePatterns()}")
            // Max out the driver-level brightness (0–8 scale) so our RGB values are the only dimming factor.
            led.setBrightness(8)
            Log.i(TAG, "Driver brightness set to 8 (max)")
            sdk
        } else {
            Log.w(TAG, "LED subsystem not available on this device")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "TerminalSDK init failed", e)
        null
    }

    private val led get() = terminal?.led

    val isAvailable: Boolean get() = led != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var animationJob: Job? = null
    private var completionJob: Job? = null

    // ═══════════════════════════════════════════════════════════════════════
    //  Agent lifecycle hooks (automatic — driven by ChatViewModel etc.)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Called when the agent starts processing a prompt.
     * Starts a spinner animation that runs until [onPromptComplete] or [onPromptError].
     */
    fun onPromptStart() {
        if (led == null) return
        Log.i(TAG, "onPromptStart — starting spinner animation, maxBrightness=${maxRgbProvider()}")
        cancelAll()
        animationJob = scope.launch {
            runSpinnerAnimation()
        }
    }

    /**
     * Called when the agent finishes processing.
     * Shows a context-aware completion pattern that auto-clears.
     */
    fun onPromptComplete(responseText: String) {
        if (led == null) return
        cancelAll()
        val intent = LedIntent.classifyResponse(responseText)
        Log.i(TAG, "onPromptComplete — intent=$intent, maxBrightness=${maxRgbProvider()}")
        completionJob = scope.launch {
            showCompletionPattern(intent)
            delay(COMPLETION_DISPLAY_MS)
            led?.clear()
        }
    }

    /**
     * Called when the agent encounters an error.
     * Shows an error blink pattern that auto-clears.
     */
    fun onPromptError() {
        if (led == null) return
        Log.i(TAG, "onPromptError — showing error blink, maxBrightness=${maxRgbProvider()}")
        cancelAll()
        completionJob = scope.launch {
            showErrorBlink()
            delay(COMPLETION_DISPLAY_MS)
            led?.clear()
        }
    }

    /**
     * Called when the user sends a new message.
     * Immediately clears any lingering completion pattern.
     */
    fun onUserMessage() {
        if (led == null) return
        Log.d(TAG, "onUserMessage — clearing LEDs")
        cancelAll()
        led?.clear()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Public API for direct AI agent control (via LedSkill tools)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Display a named built-in pattern from the TerminalSDK driver.
     * @param name One of [BUILTIN_PATTERNS] (e.g. "success", "chad", "error").
     * @param color Optional hex color override (e.g. "#FF00FF"). Uses system accent if null.
     * @return true if the pattern was displayed.
     */
    fun displayNamedPattern(name: String, color: String? = null): Boolean {
        val led = led ?: return false
        cancelAll()
        val capped = color?.let { capColor(it) }
        Log.i(TAG, "displayNamedPattern name=$name, inputColor=$color, cappedColor=$capped, maxBrightness=${maxRgbProvider()}")
        led.displayPattern(name, capped)
        Log.d(TAG, "displayNamedPattern: displayPattern() returned")
        return true
    }

    /**
     * Flash a named built-in status pattern briefly, then revert to the chad pattern.
     * @param name One of "success", "error", "warning", "info".
     * @param durationMs How long to show the pattern before reverting (default 1000).
     * @return true if the flash was started.
     */
    fun flashPattern(name: String, durationMs: Long = 1000L): Boolean {
        val led = led ?: return false
        cancelAll()
        Log.i(TAG, "flashPattern name=$name, durationMs=$durationMs, maxBrightness=${maxRgbProvider()}")
        when (name.lowercase()) {
            "success" -> led.flashSuccess(durationMs = durationMs)
            "error" -> led.flashError(durationMs = durationMs)
            "warning" -> led.flashWarning(durationMs = durationMs)
            "info" -> led.flashInfo(durationMs = durationMs)
            else -> return false
        }
        return true
    }

    /**
     * Set a custom static 3×3 pattern.
     * @param pattern 3×3 array of hex color strings. "#000000" = off.
     * @return true if the pattern was set.
     */
    fun setCustomPattern(pattern: Array<Array<String>>): Boolean {
        val led = led ?: return false
        cancelAll()
        val capped = Array(3) { r ->
            Array(3) { c -> capColor(pattern[r][c]) }
        }
        Log.i(TAG, "setCustomPattern maxBrightness=${maxRgbProvider()}, row0=${capped[0].joinToString()}, row1=${capped[1].joinToString()}, row2=${capped[2].joinToString()}")
        led.setCustomPattern(capped, 8)
        return true
    }

    /**
     * Set a single LED to a specific color.
     * @param row Row index 0–2.
     * @param col Column index 0–2.
     * @param color Hex color string (e.g. "#FF0000").
     * @return true if the LED was set.
     */
    fun setSingleLed(row: Int, col: Int, color: String): Boolean {
        val led = led ?: return false
        val capped = capColor(color)
        Log.i(TAG, "setSingleLed [$row,$col] input=$color, capped=$capped, maxBrightness=${maxRgbProvider()}")
        led.setColor(row, col, capped, 8)
        return true
    }

    /**
     * Set all 9 LEDs to the same color.
     * @param color Hex color string.
     * @return true if successful.
     */
    fun setAllLeds(color: String): Boolean {
        val led = led ?: return false
        cancelAll()
        val capped = capColor(color)
        Log.i(TAG, "setAllLeds input=$color, capped=$capped, maxBrightness=${maxRgbProvider()}")
        led.setAllColor(capped, 8)
        return true
    }

    /**
     * Run a custom animation: a sequence of 3×3 frames played in order.
     *
     * @param frames List of 3×3 color grids. Each frame is an array of 3 rows of 3 hex strings.
     * @param intervalMs Delay between frames in milliseconds.
     * @param loops Number of full cycles to play. 0 = infinite (until next command cancels).
     * @return true if the animation was started.
     */
    fun runCustomAnimation(
        frames: List<Array<Array<String>>>,
        intervalMs: Long = 150L,
        loops: Int = 1,
    ): Boolean {
        if (led == null || frames.isEmpty()) return false
        cancelAll()

        val cappedFrames = frames.map { frame ->
            Array(3) { r -> Array(3) { c -> capColor(frame[r][c]) } }
        }
        Log.i(TAG, "runCustomAnimation frames=${cappedFrames.size}, intervalMs=$intervalMs, loops=$loops, maxBrightness=${maxRgbProvider()}")
        for ((i, frame) in cappedFrames.withIndex()) {
            Log.d(TAG, "  frame[$i]: ${frame.joinToString(" | ") { it.joinToString() }}")
        }

        animationJob = scope.launch {
            var cycle = 0
            while (loops == 0 || cycle < loops) {
                for (frame in cappedFrames) {
                    led?.setCustomPattern(frame, 8)
                    delay(intervalMs)
                }
                cycle++
            }
        }
        return true
    }

    /**
     * Turn off all LEDs and cancel any running animation.
     * @return true if LEDs are available.
     */
    fun clear(): Boolean {
        val led = led ?: return false
        Log.d(TAG, "clear — turning off all LEDs")
        cancelAll()
        led.clear()
        return true
    }

    /**
     * Return the list of available built-in pattern names.
     */
    fun getAvailablePatterns(): List<String> = BUILTIN_PATTERNS

    /**
     * Release all resources. Call from Application/Activity onDestroy.
     */
    fun destroy() {
        cancelAll()
        terminal?.destroy()
        scope.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Internal animations (used by lifecycle hooks)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Spinner animation cycling through the perimeter of the 3×3 grid.
     *
     * Positions trace the outer ring:
     *   0,0 → 0,1 → 0,2 → 1,2 → 2,2 → 2,1 → 2,0 → 1,0
     *
     * Uses full-brightness colors — LEDs get very dim below ~100 so the
     * head is pure white and the trail stays in the upper range.
     */
    private suspend fun runSpinnerAnimation() {
        val positions = listOf(
            0 to 0, 0 to 1, 0 to 2,
            1 to 2,
            2 to 2, 2 to 1, 2 to 0,
            1 to 0,
        )
        val color = capColor("#FFFFFF")
        val trailColor = capColor("#8080FF")
        var index = 0

        while (true) {
            val pattern = Array(3) { Array(3) { OFF } }
            val curr = positions[index % positions.size]
            val prev = positions[(index - 1 + positions.size) % positions.size]
            pattern[curr.first][curr.second] = color
            pattern[prev.first][prev.second] = trailColor
            led?.setCustomPattern(pattern, 8)
            delay(SPINNER_FRAME_MS)
            index++
        }
    }

    private fun showCompletionPattern(intent: LedIntent) {
        val led = led ?: return
        when (intent) {
            LedIntent.GREETING -> showSmilePattern()
            LedIntent.SUCCESS -> led.flashSuccess()
            LedIntent.ERROR -> led.flashError()
            LedIntent.PROCESSING -> led.displayInfo()
            LedIntent.IDLE -> led.setAllColor(capColor("#00FF00"), 8)
        }
    }

    /**
     * Smile: eyes at top corners, mouth across bottom row.
     * Uses pure full-brightness yellow (#FFFF00).
     */
    private fun showSmilePattern() {
        val c = capColor("#FFFF00")
        led?.setCustomPattern(arrayOf(
            arrayOf(c,   OFF, c),
            arrayOf(OFF, OFF, OFF),
            arrayOf(c,   c,   c),
        ), 8)
    }

    /**
     * Error blink: corner LEDs flash pure red 3 times.
     */
    private suspend fun showErrorBlink() {
        val red = capColor("#FF0000")
        val cornerPattern = arrayOf(
            arrayOf(red, OFF, red),
            arrayOf(OFF, OFF, OFF),
            arrayOf(red, OFF, red),
        )
        repeat(3) {
            led?.setCustomPattern(cornerPattern, 8)
            delay(250)
            led?.clear()
            delay(200)
        }
        led?.setCustomPattern(cornerPattern, 8)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Color utilities
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Apply the user's max RGB cap to a hex color string.
     * Each channel is scaled by `maxRgb / 255`.
     */
    internal fun capColor(hex: String): String {
        val maxRgb = maxRgbProvider().coerceIn(0, 255)
        if (maxRgb >= 255) {
            Log.v(TAG, "capColor $hex → $hex (no cap, maxBrightness=255)")
            return hex
        }

        val clean = hex.removePrefix("#").removePrefix("0x")
        val offset = if (clean.length == 8) 2 else 0
        val r = clean.substring(offset, offset + 2).toInt(16)
        val g = clean.substring(offset + 2, offset + 4).toInt(16)
        val b = clean.substring(offset + 4, offset + 6).toInt(16)

        val scale = maxRgb / 255.0
        val cr = (r * scale).toInt().coerceIn(0, 255)
        val cg = (g * scale).toInt().coerceIn(0, 255)
        val cb = (b * scale).toInt().coerceIn(0, 255)

        val result = "#%02X%02X%02X".format(cr, cg, cb)
        Log.d(TAG, "capColor $hex → $result (rgb $r,$g,$b → $cr,$cg,$cb, maxBrightness=$maxRgb)")
        return result
    }

    private fun cancelAll() {
        animationJob?.cancel()
        animationJob = null
        completionJob?.cancel()
        completionJob = null
    }
}
