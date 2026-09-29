package com.gram.core.media

import kotlin.math.abs

sealed interface PlayerGestureState {
    data object Idle : PlayerGestureState
    data class FiveSecondSeek(val direction: Direction, val targetMs: Long) : PlayerGestureState
    data class ForwardRateHold(val requestedRate: Float, val achievedRate: Float, val positionMs: Long) : PlayerGestureState
    data class SimulatedRewindHold(val effectiveRate: Float, val positionMs: Long, val targetMs: Long) : PlayerGestureState
}

enum class Direction { BACKWARD, FORWARD }

object PlayerGestureMath {
    const val DOUBLE_TAP_SEEK_MS = 5_000L
    const val MIN_HOLD_RATE = 1f
    const val MAX_HOLD_RATE = 8f

    fun exactSeek(positionMs: Long, durationMs: Long, direction: Direction): Long {
        val delta = if (direction == Direction.FORWARD) DOUBLE_TAP_SEEK_MS else -DOUBLE_TAP_SEEK_MS
        return (positionMs + delta).coerceIn(0, durationMs.coerceAtLeast(0))
    }

    /** Horizontal displacement across half the viewport maps linearly from 1× to 8×. */
    fun holdRate(horizontalDeltaPx: Float, viewportWidthPx: Float): Float {
        if (viewportWidthPx <= 0f) return MIN_HOLD_RATE
        val fraction = (abs(horizontalDeltaPx) / (viewportWidthPx / 2f)).coerceIn(0f, 1f)
        return MIN_HOLD_RATE + fraction * (MAX_HOLD_RATE - MIN_HOLD_RATE)
    }

    fun rewindStepMs(rate: Float, tickMs: Long): Long =
        (rate.coerceIn(MIN_HOLD_RATE, MAX_HOLD_RATE) * tickMs).toLong()
}
