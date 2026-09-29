package com.gram.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGesturesTest {
    @Test fun `five second seeks clamp to media boundaries`() {
        assertEquals(0L, PlayerGestureMath.exactSeek(2_000, 60_000, Direction.BACKWARD))
        assertEquals(15_000L, PlayerGestureMath.exactSeek(10_000, 60_000, Direction.FORWARD))
        assertEquals(60_000L, PlayerGestureMath.exactSeek(58_000, 60_000, Direction.FORWARD))
    }

    @Test fun `hold drag maps from one to eight times`() {
        assertEquals(1f, PlayerGestureMath.holdRate(0f, 1_000f))
        assertEquals(4.5f, PlayerGestureMath.holdRate(250f, 1_000f))
        assertEquals(8f, PlayerGestureMath.holdRate(900f, 1_000f))
    }
}
