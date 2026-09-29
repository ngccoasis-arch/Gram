package com.gram.core.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Live backend backed only by the generated Java/JNI bindings from the repository's pinned
 * official TDLib commit. No Telegram protocol implementation or third-party TDLib wrapper is used.
 */
internal fun TdlibGramBackend(scope: CoroutineScope, config: TelegramSessionConfig): GramBackend =
    LiveTdlibGramBackend(scope, config)

private class LiveTdlibGramBackend(
    private val scope: CoroutineScope,
    private val config: TelegramSessionConfig,
) : GramBackend {
    private data class FileOrigin(val chatId: Long, val messageId: Long)
    private data class RangeRequest(val offset: Long, val length: Long)

    private val messageFlows = ConcurrentHashMap<Long, MutableStateFlow<List<GramMessage>>>()
    private val fileFlows = ConcurrentHashMap<Int, MutableStateFlow<FileSnapshot?>>()
    private val fileOrigins = ConcurrentHashMap<Int, FileOrigin>()
    private val downloadNames = ConcurrentHashMap<Int, String>()
    private val downloadSizes = ConcurrentHashMap<Int, Long>()
    private val fullDownloadPriorities = ConcurrentHashMap<Int, Int>()
    private val pendingRanges = ConcurrentHashMap<Int, RangeRequest>()
    private val samplers = ConcurrentHashMap<Int, SpeedSampler>()
    private val retryUploads = ConcurrentHashMap<Long, Pair<Long, String>>()
    private val nextUploadId = AtomicLong(1)

    private val _authorizationState = MutableStateFlow<AuthorizationState>(AuthorizationState.Starting)
    override val authorizationState = _authorizationState.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    override val chats = _chats.asStateFlow()

    private val _outgoingTransfers = MutableStateFlow<List<OutgoingTransfer>>(emptyList())
    override val outgoingTransfers = _outgoingTransfers.asStateFlow()

    private val _transfers = MutableStateFlow<List<MediaTransferState>>(emptyList())
    override val transfers = _transfers.asStateFlow()

    private val _concurrency = MutableStateFlow(ConcurrencyMode.TWO)
    override val concurrency = _concurrency.asStateFlow()

    private val _storageState = MutableStateFlow<StorageSafetyState>(StorageSafetyState.Safe)
    override val storageState = _storageState.asStateFlow()

    private lateinit var client: Client
    private var lastPhoneNumber = ""
    private var lastInteractiveAuthorizationState: AuthorizationState = AuthorizationState.Starting

    init {
        if (!config.hasApiCredentials) {
            _authorizationState.value = AuthorizationState.Failed(
                "Telegram API credentials are not configured. Build with TELEGRAM_API_ID and TELEGRAM_API_HASH.",
            )
        } else {
            try {
                Client.setLogMessageHandler(1) { _, _ -> /* Never forward potentially sensitive TDLib logs. */ }
                client = Client.create(
                    { update -> handleUpdate(update) },
                    { error -> fail("TDLib update failed", error) },
                    { error -> fail("TDLib request callback failed", error) },
                )
            } catch (error: Throwable) {
                fail("TDLib native library could not start", error)
            }
        }
    }

    override fun messages(chatId: Long): StateFlow<List<GramMessage>> {
        val flow = messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        if (::client.isInitialized && flow.value.isEmpty()) {
            send(TdApi.GetChatHistory(chatId, 0, 0, 50, false), "load chat history") { result ->
                if (result is TdApi.Messages) {
                    flow.value = result.messages.orEmpty().mapNotNull(::mapMessage).sortedBy { it.dateEpochSeconds }
                }
            }
        }
        return flow.asStateFlow()
    }

    override fun file(fileId: Int): StateFlow<FileSnapshot?> =
        fileFlows.getOrPut(fileId) { MutableStateFlow(null) }.asStateFlow()

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        val normalized = phoneNumber.trim()
        if (normalized.isBlank()) {
            _authorizationState.value = AuthorizationState.Failed("Phone number is required")
            return
        }
        lastPhoneNumber = normalized
        sendAuth(TdApi.SetAuthenticationPhoneNumber(normalized, null), "submit phone number")
    }

    override suspend fun submitCode(code: String) {
        if (code.isBlank()) return authInputError("Authentication code is required")
        sendAuth(TdApi.CheckAuthenticationCode(code.trim()), "submit authentication code")
    }

    override suspend fun submitPassword(password: String) {
        if (password.isBlank()) return authInputError("Two-step verification password is required")
        sendAuth(TdApi.CheckAuthenticationPassword(password), "submit two-step verification password")
    }

    override suspend fun submitEmailAddress(email: String) {
        if (email.isBlank()) return authInputError("Email address is required")
        sendAuth(TdApi.SetAuthenticationEmailAddress(email.trim()), "submit email address")
    }

    override suspend fun submitEmailCode(code: String) {
        if (code.isBlank()) return authInputError("Email code is required")
        sendAuth(
            TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code.trim())),
            "submit email code",
        )
    }

    override suspend fun sendText(chatId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val content = TdApi.InputMessageText(TdApi.FormattedText(trimmed, null), null, true)
        send(TdApi.SendMessage(chatId, null, null, null, null, content), "send message") { result ->
            if (result is TdApi.Message) addOrReplaceMessage(result)
        }
    }

    override suspend fun sendMedia(chatId: Long, uris: List<String>) {
        // Android content:// URIs must be copied into app-private storage before TDLib can read
        // them. That app-layer bridge is deliberately reported as unavailable, never simulated.
        uris.forEach { uri ->
            val id = nextUploadId.getAndIncrement()
            retryUploads[id] = chatId to uri
            updateUpload(
                OutgoingTransfer(
                    localId = id,
                    chatId = chatId,
                    displayName = uri.substringAfterLast('/').ifBlank { "media" },
                    fraction = 0f,
                    phase = TransferPhase.FAILED,
                    error = "Media upload is not connected in this foundation build.",
                ),
            )
        }
    }

    override suspend fun cancelUpload(localId: Long) {
        _outgoingTransfers.value.firstOrNull { it.localId == localId }?.let {
            updateUpload(it.copy(phase = TransferPhase.CANCELED))
        }
    }

    override suspend fun retryUpload(localId: Long) {
        val (chatId, uri) = retryUploads[localId] ?: return
        _outgoingTransfers.value = _outgoingTransfers.value.filterNot { it.localId == localId }
        retryUploads.remove(localId)
        sendMedia(chatId, listOf(uri))
    }

    override suspend fun start(fileId: Int, displayName: String, totalBytes: Long, priority: Int) {
        if (!canTransfer()) return
        downloadNames[fileId] = displayName
        downloadSizes[fileId] = totalBytes
        fullDownloadPriorities[fileId] = priority
        val old = _transfers.value.firstOrNull { it.fileId == fileId }
        updateTransfer(
            old?.copy(phase = TransferPhase.QUEUED, error = null)
                ?: MediaTransferState(fileId, displayName, TransferPhase.QUEUED, 0, totalBytes),
        )
        val origin = fileOrigins[fileId]
        if (origin != null) {
            send(TdApi.AddFileToDownloads(fileId, origin.chatId, origin.messageId, priority), "start persistent download")
        } else {
            send(TdApi.DownloadFile(fileId, priority, 0, 0, false), "start download")
        }
    }

    override suspend fun requestRange(fileId: Int, offset: Long, length: Long, priority: Int) {
        if (!canTransfer()) return
        pendingRanges[fileId] = RangeRequest(offset, length)
        send(TdApi.DownloadFile(fileId, priority, offset, length, false), "request file range")
    }

    override suspend fun pause(fileId: Int) {
        pendingRanges.remove(fileId)
        send(TdApi.ToggleDownloadIsPaused(fileId, true), "pause download")
        mutateTransfer(fileId) { it.copy(phase = TransferPhase.PAUSED, bytesPerSecond = 0) }
    }

    override suspend fun resume(fileId: Int) {
        val origin = fileOrigins[fileId]
        if (origin != null) {
            send(TdApi.ToggleDownloadIsPaused(fileId, false), "resume download")
        } else {
            send(TdApi.DownloadFile(fileId, Priority.EXPLICIT, 0, 0, false), "resume download")
        }
        mutateTransfer(fileId) { it.copy(phase = TransferPhase.QUEUED, error = null) }
    }

    override suspend fun cancel(fileId: Int) {
        pendingRanges.remove(fileId)
        fullDownloadPriorities.remove(fileId)
        send(TdApi.CancelDownloadFile(fileId, false), "cancel download")
        mutateTransfer(fileId) { it.copy(phase = TransferPhase.CANCELED, bytesPerSecond = 0) }
    }

    override suspend fun retry(fileId: Int) = resume(fileId)

    override suspend fun setConcurrency(mode: ConcurrencyMode) {
        // TDLib schedules its own network workers. This setting is retained for Gram's queue
        // coordinator, which will gate future explicit starts without changing TDLib internals.
        _concurrency.value = mode
    }

    override suspend fun logOut() {
        if (::client.isInitialized) send(TdApi.LogOut(), "log out")
    }

    override suspend fun close() {
        if (::client.isInitialized) send(TdApi.Close(), "close TDLib")
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewChat -> upsertChat(update.chat)
            is TdApi.UpdateChatTitle -> mutateChat(update.chatId) { it.copy(title = update.title) }
            is TdApi.UpdateChatLastMessage -> {
                val last = update.lastMessage
                mutateChat(update.chatId) {
                    it.copy(
                        preview = last?.let(::messagePreview).orEmpty(),
                        updatedAtEpochSeconds = last?.date?.toLong() ?: it.updatedAtEpochSeconds,
                    )
                }
            }
            is TdApi.UpdateNewMessage -> addOrReplaceMessage(update.message)
            is TdApi.UpdateDeleteMessages -> {
                val flow = messageFlows[update.chatId] ?: return
                val deleted = update.messageIds.toSet()
                flow.value = flow.value.filterNot { it.id in deleted }
            }
            is TdApi.UpdateFile -> updateFile(update.file)
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val request = TdApi.SetTdlibParameters().apply {
                    useTestDc = false
                    databaseDirectory = config.databaseDirectory
                    filesDirectory = config.filesDirectory
                    databaseEncryptionKey = config.databaseEncryptionKey
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = config.apiId
                    apiHash = config.apiHash
                    systemLanguageCode = config.systemLanguageCode
                    deviceModel = config.deviceModel
                    systemVersion = config.systemVersion
                    applicationVersion = config.applicationVersion
                }
                sendAuth(request, "configure TDLib")
            }
            is TdApi.AuthorizationStateWaitPhoneNumber ->
                showAuthorizationState(AuthorizationState.WaitingForPhoneNumber)
            is TdApi.AuthorizationStateWaitCode ->
                showAuthorizationState(AuthorizationState.WaitingForCode(lastPhoneNumber))
            is TdApi.AuthorizationStateWaitPassword ->
                showAuthorizationState(AuthorizationState.WaitingForPassword(state.passwordHint.takeIf(String::isNotBlank)))
            is TdApi.AuthorizationStateWaitEmailAddress ->
                showAuthorizationState(AuthorizationState.WaitingForEmailAddress)
            is TdApi.AuthorizationStateWaitEmailCode ->
                showAuthorizationState(AuthorizationState.WaitingForEmailCode(
                    state.codeInfo.emailAddressPattern,
                    state.codeInfo.length,
                ))
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                showAuthorizationState(AuthorizationState.WaitingForOtherDeviceConfirmation(state.link))
            is TdApi.AuthorizationStateWaitRegistration ->
                _authorizationState.value = AuthorizationState.Failed(
                    "This MVP supports existing Telegram accounts only; registration is not available.",
                )
            is TdApi.AuthorizationStateWaitPremiumPurchase ->
                _authorizationState.value = AuthorizationState.Failed(
                    "Telegram requires a Premium purchase to continue this authorization attempt.",
                )
            is TdApi.AuthorizationStateReady -> {
                _authorizationState.value = AuthorizationState.Ready
                loadChats()
            }
            is TdApi.AuthorizationStateLoggingOut -> _authorizationState.value = AuthorizationState.LoggingOut
            is TdApi.AuthorizationStateClosing -> _authorizationState.value = AuthorizationState.Closing
            is TdApi.AuthorizationStateClosed -> _authorizationState.value = AuthorizationState.Closed
        }
    }

    private fun loadChats() {
        send(TdApi.LoadChats(null, 100), "load chats")
        send(TdApi.GetChats(null, 100), "get chats") { result ->
            if (result is TdApi.Chats) {
                result.chatIds.forEach { chatId ->
                    send(TdApi.GetChat(chatId), "get chat") { chat ->
                        if (chat is TdApi.Chat) upsertChat(chat)
                    }
                }
            }
        }
    }

    private fun upsertChat(chat: TdApi.Chat) {
        val value = ChatSummary(
            id = chat.id,
            title = chat.title,
            preview = chat.lastMessage?.let(::messagePreview).orEmpty(),
            unreadCount = chat.unreadCount,
            updatedAtEpochSeconds = chat.lastMessage?.date?.toLong() ?: 0,
        )
        _chats.value = (_chats.value.filterNot { it.id == value.id } + value)
            .sortedByDescending { it.updatedAtEpochSeconds }
    }

    private fun mutateChat(chatId: Long, block: (ChatSummary) -> ChatSummary) {
        _chats.value.firstOrNull { it.id == chatId }?.let { old ->
            _chats.value = (_chats.value.filterNot { it.id == chatId } + block(old))
                .sortedByDescending { it.updatedAtEpochSeconds }
        }
    }

    private fun addOrReplaceMessage(message: TdApi.Message) {
        val mapped = mapMessage(message) ?: return
        val flow = messageFlows.getOrPut(message.chatId) { MutableStateFlow(emptyList()) }
        flow.value = (flow.value.filterNot { it.id == mapped.id } + mapped).sortedBy { it.dateEpochSeconds }
    }

    private fun mapMessage(message: TdApi.Message): GramMessage? {
        val content = mapContent(message, message.content)
        val sender = when (val senderId = message.senderId) {
            is TdApi.MessageSenderUser -> if (message.isOutgoing) "You" else "User ${senderId.userId}"
            is TdApi.MessageSenderChat -> "Chat ${senderId.chatId}"
            else -> if (message.isOutgoing) "You" else "Telegram"
        }
        return GramMessage(message.id, message.chatId, sender, message.isOutgoing, message.date.toLong(), content)
    }

    private fun mapContent(message: TdApi.Message, content: TdApi.MessageContent): MessageContent = when (content) {
        is TdApi.MessageText -> MessageContent.Text(content.text.text)
        is TdApi.MessagePhoto -> {
            val size = content.photo.sizes.maxByOrNull { it.width.toLong() * it.height }
            if (size == null) MessageContent.Unsupported("MessagePhoto")
            else MessageContent.Photo(media(message, size.photo, "image/*", size.width, size.height), content.caption.text)
        }
        is TdApi.MessageVideo -> MessageContent.Video(
            media(
                message,
                content.video.video,
                content.video.mimeType,
                content.video.width,
                content.video.height,
                content.video.duration.toLong() * 1000,
                content.video.thumbnail?.file?.local?.path,
            ),
            content.caption.text,
        )
        is TdApi.MessageDocument -> MessageContent.Document(
            media(message, content.document.document, content.document.mimeType),
            content.document.fileName,
            content.caption.text,
        )
        is TdApi.MessageAudio -> MessageContent.Audio(
            media(message, content.audio.audio, content.audio.mimeType, durationMs = content.audio.duration.toLong() * 1000),
            content.audio.title.ifBlank { null },
            content.audio.performer.ifBlank { null },
        )
        is TdApi.MessageVoiceNote -> MessageContent.Voice(
            media(message, content.voiceNote.voice, content.voiceNote.mimeType, durationMs = content.voiceNote.duration.toLong() * 1000),
        )
        is TdApi.MessageSticker -> MessageContent.Sticker(
            media(message, content.sticker.sticker, null, content.sticker.width, content.sticker.height),
            content.sticker.emoji.ifBlank { null },
        )
        is TdApi.MessageAnimation -> MessageContent.Animation(
            media(
                message,
                content.animation.animation,
                content.animation.mimeType,
                content.animation.width,
                content.animation.height,
                content.animation.duration.toLong() * 1000,
                content.animation.thumbnail?.file?.local?.path,
            ),
            content.caption.text,
        )
        is TdApi.MessageContact -> MessageContent.Contact(
            listOf(content.contact.firstName, content.contact.lastName).filter(String::isNotBlank).joinToString(" "),
            content.contact.phoneNumber,
        )
        is TdApi.MessageLocation -> MessageContent.Location(content.location.latitude, content.location.longitude)
        is TdApi.MessagePoll -> MessageContent.Poll(
            content.poll.question.text,
            content.poll.options.map { it.text.text },
        )
        else -> {
            val type = content.javaClass.simpleName
            if (type.startsWith("MessageChat") || type.startsWith("MessageForum") || type.startsWith("MessageVideoChat")) {
                MessageContent.Service(type.removePrefix("Message").replace(Regex("([a-z])([A-Z])"), "$1 $2"))
            } else {
                MessageContent.Unsupported(type)
            }
        }
    }

    private fun media(
        message: TdApi.Message,
        file: TdApi.File,
        mimeType: String?,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null,
        thumbnailPath: String? = null,
    ): MediaRef {
        fileOrigins[file.id] = FileOrigin(message.chatId, message.id)
        updateFile(file)
        return MediaRef(
            fileId = file.id,
            remoteId = file.remote.id.takeIf(String::isNotBlank),
            localPath = file.local.path.takeIf(String::isNotBlank),
            mimeType = mimeType,
            width = width,
            height = height,
            durationMs = durationMs,
            thumbnailPath = thumbnailPath?.takeIf(String::isNotBlank),
        )
    }

    private fun messagePreview(message: TdApi.Message): String = when (val content = message.content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption.text.ifBlank { "Photo" }
        is TdApi.MessageVideo -> content.caption.text.ifBlank { "Video" }
        is TdApi.MessageDocument -> content.document.fileName.ifBlank { "Document" }
        is TdApi.MessageAudio -> content.audio.title.ifBlank { "Audio" }
        is TdApi.MessageVoiceNote -> "Voice message"
        is TdApi.MessageSticker -> content.sticker.emoji.ifBlank { "Sticker" }
        is TdApi.MessageAnimation -> content.caption.text.ifBlank { "Animation" }
        else -> message.content.javaClass.simpleName.removePrefix("Message")
    }

    private fun updateFile(file: TdApi.File) {
        val total = maxOf(file.size, file.expectedSize, downloadSizes[file.id] ?: 0)
        val path = file.local.path.takeIf(String::isNotBlank)
        fileFlows.getOrPut(file.id) { MutableStateFlow(null) }.value = FileSnapshot(
            fileId = file.id,
            size = file.size,
            expectedSize = file.expectedSize,
            downloadedOffset = file.local.downloadOffset,
            downloadedPrefixSize = file.local.downloadedPrefixSize,
            downloadedSize = file.local.downloadedSize,
            localPath = path,
            downloadingActive = file.local.isDownloadingActive,
            completed = file.local.isDownloadingCompleted,
        )
        val existing = _transfers.value.firstOrNull { it.fileId == file.id }
        if (existing != null || file.local.isDownloadingActive || file.local.isDownloadingCompleted) {
            val speed = samplers.getOrPut(file.id) { SpeedSampler() }
                .sample(file.local.downloadedSize, System.currentTimeMillis())
            updateTransfer(
                MediaTransferState(
                    fileId = file.id,
                    displayName = downloadNames[file.id] ?: existing?.displayName ?: "Telegram file ${file.id}",
                    phase = when {
                        file.local.isDownloadingCompleted -> TransferPhase.COMPLETED
                        file.local.isDownloadingActive -> TransferPhase.ACTIVE
                        existing?.phase == TransferPhase.CANCELED -> TransferPhase.CANCELED
                        existing?.phase == TransferPhase.PAUSED -> TransferPhase.PAUSED
                        else -> TransferPhase.QUEUED
                    },
                    downloadedBytes = file.local.downloadedSize,
                    totalBytes = total,
                    bytesPerSecond = if (file.local.isDownloadingActive) speed else 0,
                    localPath = path,
                    error = existing?.error,
                ),
            )
        }
        val range = pendingRanges[file.id]
        if (range != null) {
            val availableStart = file.local.downloadOffset
            val availableEnd = availableStart + file.local.downloadedPrefixSize
            val requestedEnd = range.offset + range.length
            if (range.offset >= availableStart && requestedEnd <= availableEnd && pendingRanges.remove(file.id, range)) {
                restoreFullDownload(file.id)
            }
        }
    }

    private fun restoreFullDownload(fileId: Int) {
        val priority = fullDownloadPriorities[fileId] ?: return
        val origin = fileOrigins[fileId]
        if (origin != null) {
            send(TdApi.AddFileToDownloads(fileId, origin.chatId, origin.messageId, priority), "restore full download")
        } else {
            send(TdApi.DownloadFile(fileId, priority, 0, 0, false), "restore full download")
        }
    }

    private fun canTransfer(): Boolean {
        val freeBytes = File(config.filesDirectory).usableSpace
        _storageState.value = when {
            freeBytes < HARD_PAUSE_BYTES -> StorageSafetyState.HardPaused(freeBytes)
            freeBytes < WARNING_BYTES -> StorageSafetyState.Warning(freeBytes)
            else -> StorageSafetyState.Safe
        }
        return if (freeBytes < HARD_PAUSE_BYTES) {
            _transfers.value
                .filter { it.phase == TransferPhase.ACTIVE || it.phase == TransferPhase.QUEUED }
                .forEach { transfer ->
                    if (fileOrigins.containsKey(transfer.fileId)) {
                        send(TdApi.ToggleDownloadIsPaused(transfer.fileId, true), "storage safety pause")
                    } else {
                        send(TdApi.CancelDownloadFile(transfer.fileId, false), "storage safety pause")
                    }
                }
            _transfers.value = _transfers.value.map {
                if (it.phase == TransferPhase.ACTIVE || it.phase == TransferPhase.QUEUED) {
                    it.copy(phase = TransferPhase.PAUSED, bytesPerSecond = 0, error = "Paused: less than 1 GB free")
                } else it
            }
            false
        } else true
    }

    private fun updateTransfer(value: MediaTransferState) {
        _transfers.value = (_transfers.value.filterNot { it.fileId == value.fileId } + value)
            .sortedBy { it.fileId }
    }

    private fun mutateTransfer(fileId: Int, block: (MediaTransferState) -> MediaTransferState) {
        _transfers.value.firstOrNull { it.fileId == fileId }?.let { updateTransfer(block(it)) }
    }

    private fun updateUpload(value: OutgoingTransfer) {
        _outgoingTransfers.value = _outgoingTransfers.value.filterNot { it.localId == value.localId } + value
    }

    private fun sendAuth(request: TdApi.Function<*>, operation: String) {
        send(request, operation) { result ->
            if (result is TdApi.Error) authInputError(result.message)
        }
    }

    private fun send(
        request: TdApi.Function<*>,
        @Suppress("UNUSED_PARAMETER") operation: String,
        onResult: (TdApi.Object) -> Unit = {},
    ) {
        if (!::client.isInitialized) return
        client.send(request, { result ->
            onResult(result)
        })
    }

    private fun authInputError(message: String) {
        _authorizationState.value = AuthorizationState.Failed(message)
        scope.launch {
            delay(1_800)
            if (_authorizationState.value is AuthorizationState.Failed) {
                _authorizationState.value = lastInteractiveAuthorizationState
            }
        }
    }

    private fun showAuthorizationState(state: AuthorizationState) {
        lastInteractiveAuthorizationState = state
        _authorizationState.value = state
    }

    private fun fail(message: String, error: Throwable? = null) {
        val detail = error?.message?.takeIf(String::isNotBlank)
        _authorizationState.value = AuthorizationState.Failed(if (detail == null) message else "$message: $detail")
    }

    private companion object {
        const val WARNING_BYTES = 5L * 1024 * 1024 * 1024
        const val HARD_PAUSE_BYTES = 1L * 1024 * 1024 * 1024
    }
}
