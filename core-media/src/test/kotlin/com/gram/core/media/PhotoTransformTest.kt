package com.gram.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoTransformTest {
    @Test fun `double tap toggles fit and three times around touch point`() {
        val zoomed = PhotoTransformPolicy.doubleTap(PhotoTransform(), 20f, -10f)
        assertEquals(3f, zoomed.scale)
        assertEquals(-40f, zoomed.offsetX)
        assertEquals(20f, zoomed.offsetY)
        assertEquals(PhotoTransform(), PhotoTransformPolicy.doubleTap(zoomed, 0f, 0f))
    }

    @Test fun `pinch scale is capped`() {
        assertEquals(3f, PhotoTransformPolicy.zoom(PhotoTransform(scale = 2f), 2f, 0f, 0f).scale)
        assertEquals(1f, PhotoTransformPolicy.zoom(PhotoTransform(scale = 2f), .1f, 0f, 0f).scale)
    }
}
