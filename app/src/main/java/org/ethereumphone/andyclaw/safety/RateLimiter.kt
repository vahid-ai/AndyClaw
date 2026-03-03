package org.ethereumphone.andyclaw.safety

import java.util.concurrent.ConcurrentHashMap

data class RateLimitConfig(
    val requestsPerMinute: Int = 60,
    val requestsPerHour: Int = 1000,
)

sealed class RateLimitResult {
    data class Allowed(val remainingMinute: Int, val remainingHour: Int) : RateLimitResult()
    data class Limited(val retryAfterMs: Long, val limitType: LimitType) : RateLimitResult()
}

enum class LimitType { PER_MINUTE, PER_HOUR }

class RateLimiter {

    private data class WindowKey(val userId: String, val toolName: String)

    private data class WindowState(
        var windowStart: Long,
        var count: Int,
    )

    private val minuteWindows = ConcurrentHashMap<WindowKey, WindowState>()
    private val hourWindows = ConcurrentHashMap<WindowKey, WindowState>()

    fun checkAndRecord(
        userId: String,
        toolName: String,
        config: RateLimitConfig = RateLimitConfig(),
    ): RateLimitResult {
        val key = WindowKey(userId, toolName)
        val now = System.currentTimeMillis()

        val minuteState = minuteWindows.getOrPut(key) { WindowState(now, 0) }
        synchronized(minuteState) {
            if (now - minuteState.windowStart >= MINUTE_MS) {
                minuteState.windowStart = now
                minuteState.count = 0
            }
            if (minuteState.count >= config.requestsPerMinute) {
                val retryAfter = MINUTE_MS - (now - minuteState.windowStart)
                return RateLimitResult.Limited(retryAfter, LimitType.PER_MINUTE)
            }
        }

        val hourState = hourWindows.getOrPut(key) { WindowState(now, 0) }
        synchronized(hourState) {
            if (now - hourState.windowStart >= HOUR_MS) {
                hourState.windowStart = now
                hourState.count = 0
            }
            if (hourState.count >= config.requestsPerHour) {
                val retryAfter = HOUR_MS - (now - hourState.windowStart)
                return RateLimitResult.Limited(retryAfter, LimitType.PER_HOUR)
            }
        }

        synchronized(minuteState) { minuteState.count++ }
        synchronized(hourState) { hourState.count++ }

        return RateLimitResult.Allowed(
            remainingMinute = config.requestsPerMinute - minuteState.count,
            remainingHour = config.requestsPerHour - hourState.count,
        )
    }

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 3_600_000L
    }
}
