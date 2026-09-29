package com.gram.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream

object BoundedImageDecoder {
    private const val MAX_DECODED_PIXELS = 32_000_000L

    suspend fun decode(context: Context, source: String, viewportWidth: Int, viewportHeight: Int, zoom: Float = 3f): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { open(context, source)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val requestedWidth = (viewportWidth * zoom).toInt().coerceAtLeast(1)
        val requestedHeight = (viewportHeight * zoom).toInt().coerceAtLeast(1)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= requestedWidth && bounds.outHeight / (sample * 2) >= requestedHeight) sample *= 2
        while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MAX_DECODED_PIXELS) sample *= 2

        runCatching { open(context, source)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } }.getOrNull()
    }

    private fun open(context: Context, source: String): InputStream? =
        if (source.startsWith("content:")) context.contentResolver.openInputStream(Uri.parse(source)) else FileInputStream(source)
}
