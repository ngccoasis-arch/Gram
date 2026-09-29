package com.gram.core.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPoliciesTest {
    @Test fun `speed sampler smooths byte deltas`() {
        val sampler = SpeedSampler(smoothing = 0.5)
        assertEquals(0L, sampler.sample(1_000, 1_000))
        assertEquals(1_000L, sampler.sample(2_000, 2_000))
        assertEquals(1_500L, sampler.sample(4_000, 3_000))
    }

    @Test fun `storage limits are strict below thresholds`() {
        assertTrue(StorageSafetyPolicy.evaluate(StorageSafetyPolicy.WARNING_BYTES) is StorageSafetyState.Safe)
        assertTrue(StorageSafetyPolicy.evaluate(StorageSafetyPolicy.WARNING_BYTES - 1) is StorageSafetyState.Warning)
        assertTrue(StorageSafetyPolicy.evaluate(StorageSafetyPolicy.HARD_PAUSE_BYTES - 1) is StorageSafetyState.HardPaused)
    }
}
