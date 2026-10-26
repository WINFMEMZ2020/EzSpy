package com.udm.ezspy.data

import android.os.SystemClock

/** Hard cap for one temporary capture session. */
const val SESSION_TIMEOUT_MS = 3L * 60L * 1000L

enum class SessionEndReason {
    RETURNED,
    STOPPED,
    TIMEOUT,
    TASK_REMOVED,
    ACTIVITY_FINISHED,
    SERVICE_STOPPED,
}

/**
 * The single current capture session. It is deliberately ephemeral: there is
 * no history, no persistence and no second session.
 */
data class SessionState(
    val active: Boolean = false,
    val startedAtElapsed: Long = 0L,
    val endedAtElapsed: Long = 0L,
    val unlimited: Boolean = false,
    val endReason: SessionEndReason? = null,
) {
    fun elapsedMs(now: Long = SystemClock.elapsedRealtime()): Long {
        if (startedAtElapsed <= 0L) return 0L
        val end = if (active) now else endedAtElapsed.takeIf { it > 0L } ?: now
        return (end - startedAtElapsed).coerceAtLeast(0L)
    }

    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!active || unlimited) return 0L
        return (SESSION_TIMEOUT_MS - elapsedMs(now)).coerceAtLeast(0L)
    }
}
