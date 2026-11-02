package com.lashox.huewidget

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The HA token, encrypted at rest with an AES-256-GCM key that never leaves the Android Keystore.
 * Stored as Base64(iv + ciphertext) under [Keys.TOKEN_ENC]. A legacy plaintext [Keys.TOKEN] is
 * migrated transparently on first read. The plaintext is cached in memory for this process.
 */
internal object TokenStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "ha_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private val lock = Any()
    @Volatile private var cached: String? = null

    fun read(p: SharedPreferences): String {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val value = load(p)
            cached = value
            return value
        }
    }

    fun write(p: SharedPreferences, e: SharedPreferences.Editor, token: String) {
        synchronized(lock) {
            // unchanged and already stored encrypted: skip a Keystore round-trip
            if (token == read(p) && !p.contains(Keys.TOKEN) && (token.isEmpty() || p.contains(Keys.TOKEN_ENC))) return
            e.remove(Keys.TOKEN)
            if (token.isEmpty()) {
                e.remove(Keys.TOKEN_ENC)
            } else {
                val enc = encrypt(token)
                if (enc != null) e.putString(Keys.TOKEN_ENC, enc)
                else e.remove(Keys.TOKEN_ENC) // never fall back to plaintext; usable for this session only
            }
            cached = token
        }
    }

    private fun load(p: SharedPreferences): String {
        val enc = p.getString(Keys.TOKEN_ENC, null)
        if (enc != null) {
            return decrypt(enc) ?: run {
                HaLog.w("stored token cannot be decrypted (key lost?), please re-enter it")
                ""
            }
        }
        val legacy = p.getString(Keys.TOKEN, null) ?: return ""
        if (legacy.isEmpty()) {
            p.edit().remove(Keys.TOKEN).apply()
            return ""
        }
        val migrated = encrypt(legacy)
        if (migrated == null) {
            // Keystore unusable: keep the legacy value rather than lose the user's config.
            HaLog.w("token migration failed, legacy value kept")
            return legacy
        }
        p.edit().putString(Keys.TOKEN_ENC, migrated).remove(Keys.TOKEN).apply()
        HaLog.d { "token migrated to encrypted storage" }
        return legacy
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key()) // Keystore generates a fresh random IV
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    } catch (e: Exception) {
        HaLog.w("token encryption failed", e)
        null
    }

    private fun decrypt(stored: String): String? = try {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        if (all.size <= IV_LEN) null else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, all, 0, IV_LEN))
            String(cipher.doFinal(all, IV_LEN, all.size - IV_LEN), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        HaLog.w("token decryption failed", e)
        null
    }
}
