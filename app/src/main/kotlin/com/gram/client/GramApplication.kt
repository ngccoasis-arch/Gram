package com.gram.client

import android.app.Application
import android.os.Build
import com.gram.core.tdlib.GramBackend
import com.gram.core.tdlib.SessionFactory
import com.gram.core.tdlib.TelegramSessionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GramApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var backend: GramBackend
        private set

    override fun onCreate() {
        super.onCreate()
        val databaseDirectory = noBackupFilesDir.resolve("tdlib/database").apply { mkdirs() }
        val filesDirectory = filesDir.resolve("telegram-media").apply { mkdirs() }
        backend = SessionFactory.create(
            applicationScope,
            TelegramSessionConfig(
                apiId = BuildConfig.TELEGRAM_API_ID,
                apiHash = BuildConfig.TELEGRAM_API_HASH,
                databaseDirectory = databaseDirectory.absolutePath,
                filesDirectory = filesDirectory.absolutePath,
                databaseEncryptionKey = SecureDatabaseKey(this).getOrCreate(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                systemVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                applicationVersion = BuildConfig.VERSION_NAME,
            ),
        )
    }
}
