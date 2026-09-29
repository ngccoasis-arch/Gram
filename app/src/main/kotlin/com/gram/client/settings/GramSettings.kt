package com.gram.client.settings

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.gram.core.tdlib.ConcurrencyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gramDataStore by preferencesDataStore("gram_settings")

class GramSettings(private val context: Context) {
    private val concurrencyKey = stringPreferencesKey("download_concurrency")

    val concurrency: Flow<ConcurrencyMode> = context.gramDataStore.data.map { values ->
        values[concurrencyKey]?.let { runCatching { ConcurrencyMode.valueOf(it) }.getOrNull() } ?: ConcurrencyMode.TWO
    }

    suspend fun setConcurrency(value: ConcurrencyMode) {
        context.gramDataStore.edit { it[concurrencyKey] = value.name }
    }
}
