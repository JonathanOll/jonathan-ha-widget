package com.lashox.huewidget

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device/emulator only (real Android Keystore). Uses its own prefs file,
 * so the app's saved configuration is never touched.
 */
@RunWith(AndroidJUnit4::class)
class TokenStoreTest {
    private lateinit var p: SharedPreferences

    /** Forgets the in-memory plaintext, so the next read really decrypts / migrates. */
    private fun dropMemoryCache() {
        TokenStore::class.java.getDeclaredField("cached").apply { isAccessible = true }.set(null, null)
    }

    private fun write(token: String) {
        val e = p.edit()
        TokenStore.write(p, e, token)
        e.commit()
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        p = ctx.getSharedPreferences("token_store_test", Context.MODE_PRIVATE)
        p.edit().clear().commit()
        dropMemoryCache()
    }

    @After fun tearDown() {
        p.edit().clear().commit()
        dropMemoryCache()
    }

    @Test fun roundTrip_encryptedAtRest() {
        write("test-token")
        val enc = p.getString(Keys.TOKEN_ENC, null)
        assertNotNull(enc)
        assertFalse(enc!!.contains("test-token"))
        assertFalse(p.contains(Keys.TOKEN))
        dropMemoryCache()
        assertEquals("test-token", TokenStore.read(p))
    }

    @Test fun freshIvEachEncryption() {
        write("test-token")
        val first = p.getString(Keys.TOKEN_ENC, null)
        p.edit().clear().commit()
        dropMemoryCache()
        write("test-token")
        assertNotEquals(first, p.getString(Keys.TOKEN_ENC, null))
    }

    @Test fun legacyPlaintext_migratedToTokenEnc() {
        p.edit().putString(Keys.TOKEN, "legacy-test-token").commit()
        assertEquals("legacy-test-token", TokenStore.read(p))
        assertFalse(p.contains(Keys.TOKEN))
        assertTrue(p.contains(Keys.TOKEN_ENC))
        dropMemoryCache()
        assertEquals("legacy-test-token", TokenStore.read(p))
    }

    @Test fun emptyLegacy_removed() {
        p.edit().putString(Keys.TOKEN, "").commit()
        assertEquals("", TokenStore.read(p))
        assertFalse(p.contains(Keys.TOKEN))
        assertFalse(p.contains(Keys.TOKEN_ENC))
    }

    @Test fun writeEmpty_removesBoth() {
        write("test-token")
        write("")
        assertFalse(p.contains(Keys.TOKEN_ENC))
        assertFalse(p.contains(Keys.TOKEN))
        dropMemoryCache()
        assertEquals("", TokenStore.read(p))
    }

    @Test fun corruptedCiphertext_readsEmpty() {
        p.edit().putString(Keys.TOKEN_ENC, "AAAA").commit()
        assertEquals("", TokenStore.read(p))
        dropMemoryCache()
        p.edit().putString(Keys.TOKEN_ENC, "bm90IGEgcmVhbCBjaXBoZXJ0ZXh0IGF0IGFsbA==").commit()
        assertEquals("", TokenStore.read(p))
    }

    @Test fun overwrite_newTokenWins() {
        write("test-token")
        write("other-test-token")
        dropMemoryCache()
        assertEquals("other-test-token", TokenStore.read(p))
    }
}
