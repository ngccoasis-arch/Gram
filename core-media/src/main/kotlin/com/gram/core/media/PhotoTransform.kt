package com.gram.core.media

data class PhotoTransform(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)

object PhotoTransformPolicy {
    const val MAX_SCALE = 3f

    fun doubleTap(current: PhotoTransform, touchX: Float, touchY: Float): PhotoTransform =
        if (current.scale > 1.05f) PhotoTransform()
        else PhotoTransform(MAX_SCALE, -touchX * (MAX_SCALE - 1), -touchY * (MAX_SCALE - 1))

    fun zoom(current: PhotoTransform, factor: Float, panX: Float, panY: Float): PhotoTransform {
        val scale = (current.scale * factor).coerceIn(1f, MAX_SCALE)
        return if (scale == 1f) PhotoTransform() else current.copy(
            scale = scale,
            offsetX = current.offsetX + panX,
            offsetY = current.offsetY + panY,
        )
    }
}
