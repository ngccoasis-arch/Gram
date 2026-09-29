package com.gram.core.tdlib

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSessionConfigTest {
    @Test fun `credentials require positive id and non-placeholder hash`() {
        assertFalse(config(0, "not-configured").hasApiCredentials)
        assertFalse(config(123, "not-configured").hasApiCredentials)
        assertFalse(config(123, "").hasApiCredentials)
        assertTrue(config(123, "development-hash").hasApiCredentials)
    }

    private fun config(apiId: Int, apiHash: String) = TelegramSessionConfig(
        apiId = apiId,
        apiHash = apiHash,
        databaseDirectory = "database",
        filesDirectory = "files",
        databaseEncryptionKey = ByteArray(32),
        deviceModel = "test",
        systemVersion = "test",
        applicationVersion = "test",
    )
}
