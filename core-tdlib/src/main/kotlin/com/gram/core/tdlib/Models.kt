package com.gram.core.tdlib

import kotlinx.coroutines.flow.StateFlow

data class TelegramSessionConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDirectory: String,
    val filesDirectory: String,
    val databaseEncryptionKey: ByteArray,
    val systemLanguageCode: String = "en",
    val deviceModel: String,
    val systemVersion: String,
    val applicationVersion: String,
) {
    val hasApiCredentials: Boolean
        get() = apiId > 0 && apiHash.isNotBlank() && apiHash != "not-configured"
}

sealed interface AuthorizationState {
    data object Starting : AuthorizationState
    data object WaitingForPhoneNumber : AuthorizationState
    data class WaitingForCode(val phoneNumber: String) : AuthorizationState
    data class WaitingForPassword(val hint: String?) : AuthorizationState
    data object WaitingForEmailAddress : AuthorizationState
    data class WaitingForEmailCode(val addressPattern: String, val length: Int) : AuthorizationState
    data class WaitingForOtherDeviceConfirmation(val link: String) : AuthorizationState
    data object Ready : AuthorizationState
    data class Failed(val message: String) : AuthorizationState
    data object LoggingOut : AuthorizationState
    data object Closing : AuthorizationState
    data object Closed : AuthorizationState
}

data class ChatSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val unreadCount: Int = 0,
    val updatedAtEpochSeconds: Long = 0,
)

data class MediaRef(
    val fileId: Int,
    val remoteId: String? = null,
    val localPath: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val thumbnailPath: String? = null,
)

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Photo(val media: MediaRef, val caption: String = "") : MessageContent
    data class Video(val media: MediaRef, val caption: String = "") : MessageContent
    data class Document(val media: MediaRef, val fileName: String, val caption: String = "") : MessageContent
    data class Audio(val media: MediaRef, val title: String?, val performer: String?) : MessageContent
    data class Voice(val media: MediaRef) : MessageContent
    data class Sticker(val media: MediaRef, val emoji: String?) : MessageContent
    data class Animation(val media: MediaRef, val caption: String = "") : MessageContent
    data class Contact(val name: String, val phoneNumber: String) : MessageContent
    data class Location(val latitude: Double, val longitude: Double) : MessageContent
    data class Poll(val question: String, val options: List<String>) : MessageContent
    data class Service(val text: String) : MessageContent
    data class Unsupported(val tdlibType: String) : MessageContent
}

data class GramMessage(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val outgoing: Boolean,
    val dateEpochSeconds: Long,
    val content: MessageContent,
)

enum class TransferPhase { QUEUED, ACTIVE, PAUSED, COMPLETED, CANCELED, FAILED }

data class MediaTransferState(
    val fileId: Int,
    val displayName: String,
    val phase: TransferPhase,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long = 0,
    val localPath: String? = null,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (downloadedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
}

enum class ConcurrencyMode(val limit: Int?) { AUTO(null), ONE(1), TWO(2), FOUR(4), EIGHT(8) }

data class OutgoingTransfer(
    val localId: Long,
    val chatId: Long,
    val displayName: String,
    val fraction: Float,
    val phase: TransferPhase,
    val error: String? = null,
)

data class FileSnapshot(
    val fileId: Int,
    val size: Long,
    val expectedSize: Long,
    val downloadedOffset: Long,
    val downloadedPrefixSize: Long,
    val downloadedSize: Long,
    val localPath: String?,
    val downloadingActive: Boolean,
    val completed: Boolean,
)

interface TelegramSession {
    val authorizationState: StateFlow<AuthorizationState>
    val chats: StateFlow<List<ChatSummary>>
    val outgoingTransfers: StateFlow<List<OutgoingTransfer>>
    fun messages(chatId: Long): StateFlow<List<GramMessage>>
    suspend fun submitPhoneNumber(phoneNumber: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun submitEmailAddress(email: String)
    suspend fun submitEmailCode(code: String)
    suspend fun sendText(chatId: Long, text: String)
    suspend fun sendMedia(chatId: Long, uris: List<String>)
    suspend fun cancelUpload(localId: Long)
    suspend fun retryUpload(localId: Long)
    suspend fun logOut()
    suspend fun close()
}

interface TelegramFileRepository {
    fun file(fileId: Int): StateFlow<FileSnapshot?>
}

interface DownloadCoordinator {
    val transfers: StateFlow<List<MediaTransferState>>
    val concurrency: StateFlow<ConcurrencyMode>
    val storageState: StateFlow<StorageSafetyState>
    suspend fun start(fileId: Int, displayName: String, totalBytes: Long, priority: Int = Priority.EXPLICIT)
    /** Retargets the file's sole TDLib request, then restores any persistent full-download intent. */
    suspend fun requestRange(fileId: Int, offset: Long, length: Long, priority: Int = Priority.VIEWED_RANGE)
    suspend fun pause(fileId: Int)
    suspend fun resume(fileId: Int)
    suspend fun cancel(fileId: Int)
    suspend fun retry(fileId: Int)
    suspend fun setConcurrency(mode: ConcurrencyMode)
}

object Priority {
    const val VIEWED_RANGE = 32
    const val VIEWED_FULL = 31
    const val EXPLICIT = 16
    const val PREFETCH = 8
}

sealed interface StorageSafetyState {
    data object Safe : StorageSafetyState
    data class Warning(val freeBytes: Long) : StorageSafetyState
    data class HardPaused(val freeBytes: Long) : StorageSafetyState
}

interface GramBackend : TelegramSession, TelegramFileRepository, DownloadCoordinator
