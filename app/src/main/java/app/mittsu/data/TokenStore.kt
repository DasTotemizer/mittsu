package app.mittsu.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Holds the WaniKani personal access token. Implementations must never log it. */
interface TokenStore {
    fun get(): String?
    fun save(token: String)
    fun clear()
}

/** Encrypts and decrypts small secrets. */
interface SecretCipher {
    /** Returns iv + ciphertext. */
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/** AES-256-GCM with a non-exportable key that lives in the Android Keystore. */
class KeystoreSecretCipher(private val alias: String = "mittsu_wanikani_token") : SecretCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * Stores only the encrypted token in a private preferences file that is excluded from
 * backups (see res/xml/backup_rules.xml). If decryption fails (e.g. the Keystore key was
 * wiped), the token is discarded and the user is asked to connect again.
 */
class EncryptedTokenStore(
    private val prefs: SharedPreferences,
    private val cipher: SecretCipher,
) : TokenStore {
    @Volatile private var cached: String? = null

    override fun get(): String? {
        cached?.let { return it }
        val stored = prefs.getString(KEY, null) ?: return null
        return try {
            String(cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8).also { cached = it }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun save(token: String) {
        val blob = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
        cached = token
    }

    override fun clear() {
        prefs.edit().remove(KEY).commit()
        cached = null
    }

    companion object {
        const val PREFS_NAME = "mittsu_secure"
        private const val KEY = "wanikani_token"

        fun create(context: Context) = EncryptedTokenStore(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            KeystoreSecretCipher(),
        )
    }
}

/** Basic shape check before any network call. WaniKani v2 tokens are UUIDs. */
object TokenFormat {
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    fun normalise(input: String): String = input.trim().removePrefix("Bearer ").trim()
    fun looksValid(token: String): Boolean = uuid.matches(token)
}
