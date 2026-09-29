package com.gram.core.tdlib

import kotlin.math.roundToLong

class SpeedSampler(
    private val smoothing: Double = 0.35,
) {
    init { require(smoothing in 0.0..1.0) }

    private var lastBytes: Long? = null
    private var lastTimeMs: Long? = null
    private var smoothed = 0.0

    fun sample(totalBytes: Long, nowMs: Long): Long {
        val priorBytes = lastBytes
        val priorTime = lastTimeMs
        lastBytes = totalBytes
        lastTimeMs = nowMs
        if (priorBytes == null || priorTime == null || nowMs <= priorTime || totalBytes < priorBytes) {
            smoothed = 0.0
            return 0
        }
        val instantaneous = (totalBytes - priorBytes) * 1000.0 / (nowMs - priorTime)
        smoothed = if (smoothed == 0.0) instantaneous else smoothing * instantaneous + (1 - smoothing) * smoothed
        return smoothed.roundToLong().coerceAtLeast(0)
    }

    fun reset() {
        lastBytes = null
        lastTimeMs = null
        smoothed = 0.0
    }
}

object StorageSafetyPolicy {
    const val WARNING_BYTES = 5L * 1024 * 1024 * 1024
    const val HARD_PAUSE_BYTES = 1L * 1024 * 1024 * 1024

    fun evaluate(freeBytes: Long): StorageSafetyState = when {
        freeBytes < HARD_PAUSE_BYTES -> StorageSafetyState.HardPaused(freeBytes)
        freeBytes < WARNING_BYTES -> StorageSafetyState.Warning(freeBytes)
        else -> StorageSafetyState.Safe
    }
}
