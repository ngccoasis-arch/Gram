package com.gram.core.tdlib

import kotlinx.coroutines.CoroutineScope

/** Entry point compiled only when the pinned TDLib Maven artifact is present. */
object SessionFactory {
    const val isDemo = false
    fun create(scope: CoroutineScope, config: TelegramSessionConfig): GramBackend = TdlibGramBackend(scope, config)
}
