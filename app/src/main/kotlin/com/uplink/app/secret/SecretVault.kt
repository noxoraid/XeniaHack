package com.uplink.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object SecretVault {
    private const val ALIAS = "nx_secret_v1"
    private const val PROVIDER = "AndroidKeyStore"

    private var box: SecretBox? = null
    private var failed = false
    private var reported = false

    @Synchronized
    private fun boxOrNull(): SecretBox? {
        box?.let { return it }
        if (failed) return null
        return try {
            val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
            val existing = store.getKey(ALIAS, null) as? SecretKey
            val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
            SecretBox(key).also { box = it }
        } catch (e: Exception) {
            failed = true
            if (!reported) {
                reported = true
                ErrorLog.report(ErrorText("Secret storage unavailable, secrets stay unencrypted"), e)
            }
            null
        }
    }

    val available: Boolean get() = boxOrNull() != null

    fun seal(label: String, plain: String): String {
        if (plain.isEmpty()) return plain
        val b = boxOrNull() ?: return plain
        return try {
            b.sealChecked(label, plain)
        } catch (e: Exception) {
            ErrorLog.report(ErrorText("Could not encrypt a secret, it stays unencrypted"), e)
            plain
        }
    }

    fun read(label: String, stored: String): SecretRead {
        if (stored.isEmpty()) return SecretRead.Plain("")
        if (!SecretBox.isSealed(stored)) return SecretRead.Plain(stored)
        val b = boxOrNull() ?: return SecretRead.Corrupt
        return b.open(label, stored)
    }

    fun value(label: String, stored: String): String = when (val r = read(label, stored)) {
        is SecretRead.Plain -> r.value
        is SecretRead.Sealed -> r.value
        SecretRead.Corrupt -> ""
    }

    fun needsMigration(stored: String): Boolean = stored.isNotEmpty() && !SecretBox.isSealed(stored)
}
