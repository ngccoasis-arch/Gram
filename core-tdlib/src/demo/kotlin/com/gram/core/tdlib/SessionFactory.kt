package com.gram.core.tdlib

import kotlinx.coroutines.CoroutineScope

object SessionFactory {
    const val isDemo = true
    fun create(scope: CoroutineScope, config: TelegramSessionConfig): GramBackend = DemoGramBackend(scope)
}
