package com.hfspaces.explorer.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts API tokens before they reach the database. */
interface TokenCipher {
    fun encrypt(plain: String): String

    /**
     * Returns the plaintext, or null if [stored] can't be decrypted (for example after a restore
     * onto a new device, where the Keystore key no longer exists). Legacy plaintext is returned as-is.
     */
    fun decrypt(stored: String): String?

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    companion object {
        const val PREFIX = "enc1:"
    }
}

/** AES-256-GCM with a non-exportable key held in the Android Keystore. */
class KeystoreTokenCipher(private val alias: String = "hf_api_tokens") : TokenCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return TokenCipher.PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    override fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return stored
        return runCatching {
            val payload = Base64.getDecoder().decode(stored.removePrefix(TokenCipher.PREFIX))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
            String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
