package com.uplink.app

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class SecretRead {
    class Plain(val value: String) : SecretRead()
    class Sealed(val value: String) : SecretRead()
    object Corrupt : SecretRead()
}

class SecretBox(private val key: SecretKey) {
    companion object {
        const val PREFIX = "nxs1:"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12

        fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

        fun hex(bytes: ByteArray): String {
            val out = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                out.append("0123456789abcdef"[(b.toInt() shr 4) and 15])
                out.append("0123456789abcdef"[b.toInt() and 15])
            }
            return out.toString()
        }

        fun unhex(text: String): ByteArray? {
            if (text.length % 2 != 0) return null
            val out = ByteArray(text.length / 2)
            for (i in out.indices) {
                val hi = Character.digit(text[i * 2], 16)
                val lo = Character.digit(text[i * 2 + 1], 16)
                if (hi < 0 || lo < 0) return null
                out[i] = ((hi shl 4) or lo).toByte()
            }
            return out
        }
    }

    fun seal(label: String, plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(label.toByteArray(Charsets.UTF_8))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + hex(cipher.iv + body)
    }

    fun open(label: String, stored: String): SecretRead {
        if (!isSealed(stored)) return SecretRead.Plain(stored)
        val raw = unhex(stored.removePrefix(PREFIX)) ?: return SecretRead.Corrupt
        if (raw.size < IV_BYTES + TAG_BITS / 8) return SecretRead.Corrupt
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            cipher.updateAAD(label.toByteArray(Charsets.UTF_8))
            SecretRead.Sealed(String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8))
        } catch (_: GeneralSecurityException) {
            SecretRead.Corrupt
        }
    }

    fun sealChecked(label: String, plain: String): String {
        val sealed = seal(label, plain)
        val back = open(label, sealed)
        if (back !is SecretRead.Sealed || back.value != plain) throw GeneralSecurityException("seal check failed")
        return sealed
    }
}
