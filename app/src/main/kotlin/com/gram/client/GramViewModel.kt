package com.gram.client

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gram.client.settings.GramSettings
import com.gram.client.transfer.TransferJobService
import com.gram.core.tdlib.ConcurrencyMode
import kotlinx.coroutines.launch

class GramViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = GramSettings(application)
    val backend = (application as GramApplication).backend
    val authorization = backend.authorizationState
    val chats = backend.chats
    val transfers = backend.transfers
    val concurrency = backend.concurrency
    val uploads = backend.outgoingTransfers

    init {
        viewModelScope.launch { settings.concurrency.collect { backend.setConcurrency(it) } }
    }

    fun sendText(chatId: Long, text: String) = viewModelScope.launch { backend.sendText(chatId, text) }
    fun submitPhone(value: String) = viewModelScope.launch { backend.submitPhoneNumber(value) }
    fun submitCode(value: String) = viewModelScope.launch { backend.submitCode(value) }
    fun submitPassword(value: String) = viewModelScope.launch { backend.submitPassword(value) }
    fun submitEmail(value: String) = viewModelScope.launch { backend.submitEmailAddress(value) }
    fun submitEmailCode(value: String) = viewModelScope.launch { backend.submitEmailCode(value) }
    fun sendMedia(chatId: Long, uris: List<String>) = viewModelScope.launch { backend.sendMedia(chatId, uris) }
    fun cancelUpload(localId: Long) = viewModelScope.launch { backend.cancelUpload(localId) }
    fun retryUpload(localId: Long) = viewModelScope.launch { backend.retryUpload(localId) }
    fun startDownload(fileId: Int, name: String, bytes: Long) = viewModelScope.launch {
        backend.start(fileId, name, bytes)
        TransferJobService.schedule(getApplication())
    }
    fun pause(fileId: Int) = viewModelScope.launch { backend.pause(fileId) }
    fun resume(fileId: Int) = viewModelScope.launch { backend.resume(fileId) }
    fun cancel(fileId: Int) = viewModelScope.launch { backend.cancel(fileId) }
    fun setConcurrency(mode: ConcurrencyMode) = viewModelScope.launch {
        settings.setConcurrency(mode)
        backend.setConcurrency(mode)
    }
}
