package com.gram.client

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SecureDatabaseKey(private val context: Context) {
    private val alias = "gram_tdlib_database_key_wrapper_v1"
    private val preferences = context.getSharedPreferences("secure_bootstrap", Context.MODE_PRIVATE)

    fun getOrCreate(): ByteArray {
        val stored = preferences.getString("database_key", null)
        if (stored != null) return decrypt(Base64.decode(stored, Base64.NO_WRAP))
        val raw = ByteArray(32).also(SecureRandom()::nextBytes)
        preferences.edit().putString("database_key", Base64.encodeToString(encrypt(raw), Base64.NO_WRAP)).apply()
        return raw
    }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private fun encrypt(value: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        return cipher.iv + cipher.doFinal(value)
    }

    private fun decrypt(value: ByteArray): ByteArray {
        val iv = value.copyOfRange(0, 12)
        val ciphertext = value.copyOfRange(12, value.size)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
            doFinal(ciphertext)
        }
    }
}
