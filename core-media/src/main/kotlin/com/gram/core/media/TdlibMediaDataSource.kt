@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.gram.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import java.io.EOFException

interface TdlibFileReader {
    /** Waits for the requested range, raising its TDLib priority as needed, then reads it. */
    suspend fun read(fileId: Int, position: Long, target: ByteArray, offset: Int, length: Int): Int
    fun size(fileId: Int): Long?
}

/** Media3 random-access source for gram-tdlib://file/{fileId} media URIs. */
class TdlibMediaDataSource(private val reader: TdlibFileReader) : BaseDataSource(false) {
    private var dataSpec: DataSpec? = null
    private var fileId = 0
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        fileId = dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IllegalArgumentException("Expected gram-tdlib://file/{fileId}")
        position = dataSpec.position
        val knownSize = reader.size(fileId)
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            knownSize != null -> (knownSize - position).coerceAtLeast(0)
            else -> C.LENGTH_UNSET.toLong()
        }
        this.dataSpec = dataSpec
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val requested = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val count = runBlocking { reader.read(fileId, position, buffer, offset, requested) }
        if (count < 0) return C.RESULT_END_OF_INPUT
        if (count == 0) throw EOFException("TDLib returned no data for file $fileId at $position")
        position += count
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        if (dataSpec != null) transferEnded()
        dataSpec = null
        remaining = C.LENGTH_UNSET.toLong()
    }

    class Factory(private val reader: TdlibFileReader) : DataSource.Factory {
        override fun createDataSource(): DataSource = TdlibMediaDataSource(reader)
    }
}
