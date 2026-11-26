package app.mittsu.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric has no AndroidKeyStore, so a reversible fake cipher stands in for it here. */
class XorCipher : SecretCipher {
    var broken = false
    override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun decrypt(blob: ByteArray): ByteArray {
        if (broken) error("key lost")
        return blob.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TokenStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(EncryptedTokenStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val token = "00000000-1111-2222-3333-444444444444"

    @Test fun savesEncryptedAndReadsBack() {
        val cipher = XorCipher()
        EncryptedTokenStore(prefs, cipher).save(token)
        val raw = prefs.all.values.joinToString()
        assertFalse("token must not be stored in plain text", raw.contains(token))
        assertEquals(token, EncryptedTokenStore(prefs, cipher).get())
    }

    @Test fun clearRemovesToken() {
        val store = EncryptedTokenStore(prefs, XorCipher())
        store.save(token)
        store.clear()
        assertNull(store.get())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun undecryptableTokenIsDiscarded() {
        val cipher = XorCipher()
        EncryptedTokenStore(prefs, cipher).save(token)
        cipher.broken = true
        assertNull(EncryptedTokenStore(prefs, cipher).get())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun tokenFormatChecks() {
        assertTrue(TokenFormat.looksValid(token))
        assertEquals(token, TokenFormat.normalise("  Bearer $token \n"))
        assertFalse(TokenFormat.looksValid("my-password"))
        assertFalse(TokenFormat.looksValid(""))
    }
}
