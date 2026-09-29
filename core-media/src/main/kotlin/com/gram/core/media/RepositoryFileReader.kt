package com.gram.core.media

import com.gram.core.tdlib.DownloadCoordinator
import com.gram.core.tdlib.Priority
import com.gram.core.tdlib.TelegramFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile

class RepositoryFileReader(
    private val files: TelegramFileRepository,
    private val downloads: DownloadCoordinator,
) : TdlibFileReader {
    override fun size(fileId: Int): Long? = files.file(fileId).value?.let { snapshot ->
        snapshot.size.takeIf { it > 0 } ?: snapshot.expectedSize.takeIf { it > 0 }
    }

    override suspend fun read(fileId: Int, position: Long, target: ByteArray, offset: Int, length: Int): Int {
        downloads.requestRange(fileId, position, length.toLong(), Priority.VIEWED_RANGE)
        while (true) {
            val snapshot = files.file(fileId).value
            val availableStart = snapshot?.downloadedOffset ?: 0
            val availableEnd = availableStart + (snapshot?.downloadedPrefixSize ?: 0)
            val path = snapshot?.localPath
            if (path != null && position >= availableStart && availableEnd > position) {
                val readable = minOf(length.toLong(), availableEnd - position).toInt()
                return withContext(Dispatchers.IO) {
                    RandomAccessFile(path, "r").use { input -> input.seek(position); input.read(target, offset, readable) }
                }
            }
            if (snapshot?.completed == true) return -1
            delay(40)
        }
    }
}
