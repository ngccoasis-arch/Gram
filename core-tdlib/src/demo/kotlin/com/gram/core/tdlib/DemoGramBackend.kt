package com.gram.core.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class DemoGramBackend(private val scope: CoroutineScope) : GramBackend {
    private val nextMessageId = AtomicLong(10_000)
    private val nextUploadId = AtomicLong(1)
    private val messageFlows = ConcurrentHashMap<Long, MutableStateFlow<List<GramMessage>>>()
    private val fileFlows = ConcurrentHashMap<Int, MutableStateFlow<FileSnapshot?>>()
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val uploadUris = ConcurrentHashMap<Long, Pair<Long, String>>()

    private val _authorizationState = MutableStateFlow<AuthorizationState>(AuthorizationState.Ready)
    override val authorizationState = _authorizationState.asStateFlow()

    private val _chats = MutableStateFlow(demoChats())
    override val chats = _chats.asStateFlow()

    private val _outgoingTransfers = MutableStateFlow<List<OutgoingTransfer>>(emptyList())
    override val outgoingTransfers = _outgoingTransfers.asStateFlow()

    private val _transfers = MutableStateFlow<List<MediaTransferState>>(emptyList())
    override val transfers = _transfers.asStateFlow()

    private val _concurrency = MutableStateFlow(ConcurrencyMode.TWO)
    override val concurrency = _concurrency.asStateFlow()

    private val _storageState = MutableStateFlow<StorageSafetyState>(StorageSafetyState.Safe)
    override val storageState = _storageState.asStateFlow()

    init { demoMessages().forEach { (id, messages) -> messageFlows[id] = MutableStateFlow(messages) } }

    override fun messages(chatId: Long): StateFlow<List<GramMessage>> =
        messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }.asStateFlow()

    override fun file(fileId: Int): StateFlow<FileSnapshot?> =
        fileFlows.getOrPut(fileId) { MutableStateFlow(null) }.asStateFlow()

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        _authorizationState.value = AuthorizationState.WaitingForCode(phoneNumber)
    }

    override suspend fun submitCode(code: String) {
        _authorizationState.value = if (code.isNotBlank()) AuthorizationState.Ready else AuthorizationState.Failed("Code is required")
    }

    override suspend fun submitPassword(password: String) {
        _authorizationState.value = if (password.isNotBlank()) AuthorizationState.Ready else AuthorizationState.Failed("Password is required")
    }

    override suspend fun submitEmailAddress(email: String) {
        _authorizationState.value = if (email.contains('@')) AuthorizationState.WaitingForEmailCode("${email.take(1)}***@${email.substringAfter('@')}", 6) else AuthorizationState.Failed("Valid email is required")
    }

    override suspend fun submitEmailCode(code: String) {
        _authorizationState.value = if (code.isNotBlank()) AuthorizationState.Ready else AuthorizationState.Failed("Email code is required")
    }

    override suspend fun sendText(chatId: Long, text: String) {
        if (text.isBlank()) return
        append(chatId, MessageContent.Text(text.trim()))
    }

    override suspend fun sendMedia(chatId: Long, uris: List<String>) {
        uris.forEach { uri ->
            val id = nextUploadId.getAndIncrement()
            uploadUris[id] = chatId to uri
            updateUpload(OutgoingTransfer(id, chatId, uri.substringAfterLast('/'), 0f, TransferPhase.QUEUED))
            scope.launch {
                for (step in 1..20) {
                    delay(90)
                    val current = _outgoingTransfers.value.firstOrNull { it.localId == id } ?: return@launch
                    if (current.phase == TransferPhase.CANCELED) return@launch
                    updateUpload(current.copy(fraction = step / 20f, phase = TransferPhase.ACTIVE))
                }
                val pair = uploadUris[id] ?: return@launch
                updateUpload(_outgoingTransfers.value.first { it.localId == id }.copy(fraction = 1f, phase = TransferPhase.COMPLETED))
                val media = MediaRef(fileId = -id.toInt(), localPath = uri, mimeType = "image/*", width = 1920, height = 1080)
                append(pair.first, MessageContent.Photo(media, "Sent at original quality"))
            }
        }
    }

    override suspend fun cancelUpload(localId: Long) {
        _outgoingTransfers.value.firstOrNull { it.localId == localId }?.let { updateUpload(it.copy(phase = TransferPhase.CANCELED)) }
    }

    override suspend fun retryUpload(localId: Long) {
        val pair = uploadUris[localId] ?: return
        _outgoingTransfers.value = _outgoingTransfers.value.filterNot { it.localId == localId }
        sendMedia(pair.first, listOf(pair.second))
    }

    override suspend fun start(fileId: Int, displayName: String, totalBytes: Long, priority: Int) {
        val existing = _transfers.value.firstOrNull { it.fileId == fileId }
        val initial = existing?.copy(phase = TransferPhase.QUEUED, error = null)
            ?: MediaTransferState(fileId, displayName, TransferPhase.QUEUED, 0, totalBytes)
        updateTransfer(initial)
        launchDownload(fileId)
    }

    override suspend fun requestRange(fileId: Int, offset: Long, length: Long, priority: Int) {
        val current = _transfers.value.firstOrNull { it.fileId == fileId } ?: return
        if (current.phase != TransferPhase.COMPLETED) launchDownload(fileId)
    }

    override suspend fun pause(fileId: Int) {
        jobs.remove(fileId)?.cancel()
        mutateTransfer(fileId) { it.copy(phase = TransferPhase.PAUSED, bytesPerSecond = 0) }
    }

    override suspend fun resume(fileId: Int) {
        mutateTransfer(fileId) { it.copy(phase = TransferPhase.QUEUED, error = null) }
        launchDownload(fileId)
    }

    override suspend fun cancel(fileId: Int) {
        jobs.remove(fileId)?.cancel()
        mutateTransfer(fileId) { it.copy(phase = TransferPhase.CANCELED, bytesPerSecond = 0) }
    }

    override suspend fun retry(fileId: Int) = resume(fileId)
    override suspend fun setConcurrency(mode: ConcurrencyMode) { _concurrency.value = mode }

    override suspend fun logOut() { _authorizationState.value = AuthorizationState.WaitingForPhoneNumber }
    override suspend fun close() { jobs.values.forEach(Job::cancel); _authorizationState.value = AuthorizationState.Closed }

    private fun launchDownload(fileId: Int) {
        if (jobs[fileId]?.isActive == true) return
        val job = scope.launch {
            val sampler = SpeedSampler()
            while (true) {
                delay(120)
                val current = _transfers.value.firstOrNull { it.fileId == fileId } ?: return@launch
                val increment = (current.totalBytes / 55).coerceAtLeast(64 * 1024)
                val bytes = (current.downloadedBytes + increment).coerceAtMost(current.totalBytes)
                val speed = sampler.sample(bytes, System.currentTimeMillis())
                val completed = bytes >= current.totalBytes
                updateTransfer(current.copy(
                    phase = if (completed) TransferPhase.COMPLETED else TransferPhase.ACTIVE,
                    downloadedBytes = bytes,
                    bytesPerSecond = if (completed) 0 else speed,
                    localPath = if (completed) "/demo/${current.displayName}" else null,
                ))
                fileFlows.getOrPut(fileId) { MutableStateFlow(null) }.value = FileSnapshot(
                    fileId, current.totalBytes, current.totalBytes, 0, bytes, bytes,
                    if (completed) "/demo/${current.displayName}" else null, !completed, completed,
                )
                if (completed) return@launch
            }
        }
        jobs[fileId] = job
    }

    private fun append(chatId: Long, content: MessageContent) {
        val flow = messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + GramMessage(nextMessageId.getAndIncrement(), chatId, "You", true, System.currentTimeMillis() / 1000, content)
    }

    private fun updateTransfer(value: MediaTransferState) {
        _transfers.value = (_transfers.value.filterNot { it.fileId == value.fileId } + value).sortedBy { it.fileId }
    }

    private fun mutateTransfer(fileId: Int, block: (MediaTransferState) -> MediaTransferState) {
        _transfers.value.firstOrNull { it.fileId == fileId }?.let { updateTransfer(block(it)) }
    }

    private fun updateUpload(value: OutgoingTransfer) {
        _outgoingTransfers.value = _outgoingTransfers.value.filterNot { it.localId == value.localId } + value
    }
}

private fun demoChats() = listOf(
    ChatSummary(1, "Saved Messages", "Media playground", updatedAtEpochSeconds = 1_790_000_000),
    ChatSummary(2, "Gram Test Group", "Try the gallery and transfers", 3, 1_789_999_000),
    ChatSummary(3, "Video Archive", "4K sample collection", updatedAtEpochSeconds = 1_789_990_000),
)

private fun demoMessages(): Map<Long, List<GramMessage>> {
    fun message(id: Long, chat: Long, content: MessageContent, sender: String = "Gram Demo") =
        GramMessage(id, chat, sender, false, 1_789_900_000 + id, content)
    return mapOf(
        1L to listOf(
            message(1, 1, MessageContent.Service("Offline demo mode — install the pinned TDLib AAR for Telegram login.")),
            message(2, 1, MessageContent.Text("Gram keeps media viewing and transfer state first-class.")),
            message(3, 1, MessageContent.Photo(MediaRef(101, mimeType = "image/jpeg", width = 4000, height = 3000), "Mountain light")),
            message(4, 1, MessageContent.Video(MediaRef(102, mimeType = "video/mp4", width = 3840, height = 2160, durationMs = 125_000), "4K test clip")),
            message(5, 1, MessageContent.Document(MediaRef(103, mimeType = "application/pdf"), "benchmark.pdf")),
        ),
        2L to listOf(
            message(20, 2, MessageContent.Photo(MediaRef(201, mimeType = "image/jpeg", width = 3000, height = 4000), "Portrait sample"), "Alex"),
            message(21, 2, MessageContent.Poll("Which download concurrency?", listOf("Auto", "2", "4", "8")), "Alex"),
            message(22, 2, MessageContent.Unsupported("messageStory"), "Taylor"),
        ),
        3L to listOf(
            message(30, 3, MessageContent.Video(MediaRef(301, mimeType = "video/mp4", width = 1920, height = 1080, durationMs = 600_000), "Long seek test")),
        ),
    )
}
