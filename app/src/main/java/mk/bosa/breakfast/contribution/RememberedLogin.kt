package mk.bosa.breakfast.contribution

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import mk.bosa.breakfast.settings.SettingsStore
import mk.bosa.breakfast.settings.SharedPreferencesStore
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals text for storage; [KeystoreSecretCipher] in the app, a fake in tests. Both calls block and throw when they cannot. */
internal interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(sealed: String): String
}

/** A remembered Open Food Facts account. Its password is null after Open Food Facts rejected the saved one. */
internal class SavedLogin(val username: String, val password: String?)

/** What [RememberedLogin.recall] found. */
internal sealed interface RecalledLogin {
    data object Nothing : RecalledLogin
    /** There was data that could not be read; it is gone now. */
    data object Unreadable : RecalledLogin
    class Found(val login: SavedLogin) : RecalledLogin
}

/** A login as it is written: only sealed text. */
internal class SealedLogin(val username: String, val password: String)

/**
 * The Open Food Facts login the user chose to remember, sealed by [cipher] in its own preferences ([OFF_LOGIN_PREFS]),
 * which backup, device transfer and the diary export leave out. Failures never reach the user: what cannot be read or
 * written counts as nothing saved. Nothing here logs. [recall] and [seal] run the cipher, so they run off the main thread.
 */
internal class RememberedLogin(private val prefs: SettingsStore, private val cipher: SecretCipher) {
    fun recall(): RecalledLogin {
        val username = prefs.getString(USERNAME_KEY, null)
        val password = prefs.getString(PASSWORD_KEY, null)
        if (username == null && password == null) return RecalledLogin.Nothing
        return try {
            RecalledLogin.Found(SavedLogin(cipher.decrypt(requireNotNull(username)), password?.let(cipher::decrypt)))
        } catch (_: Exception) {
            forget()
            RecalledLogin.Unreadable
        }
    }

    /** Null when the cipher cannot seal it. */
    fun seal(username: String, password: String): SealedLogin? = try {
        SealedLogin(cipher.encrypt(username), cipher.encrypt(password))
    } catch (_: Exception) {
        null
    }

    fun store(login: SealedLogin) = prefs.commit {
        putString(USERNAME_KEY, login.username)
        putString(PASSWORD_KEY, login.password)
    }

    /** Open Food Facts rejected the saved password: the username stays. */
    fun forgetPassword() {
        val username = prefs.getString(USERNAME_KEY, null)
        prefs.commit {
            clear()
            if (username != null) putString(USERNAME_KEY, username)
        }
    }

    fun forget() = prefs.commit { clear() }

    private companion object {
        const val USERNAME_KEY = "username"
        const val PASSWORD_KEY = "password"
    }
}

/** The preferences file of [RememberedLogin]; `res/xml/backup_rules.xml` and `data_extraction_rules.xml` exclude it by this name. */
internal const val OFF_LOGIN_PREFS = "off_login"

internal fun rememberedLogin(context: Context) = RememberedLogin(
    SharedPreferencesStore(context.getSharedPreferences(OFF_LOGIN_PREFS, Context.MODE_PRIVATE)),
    KeystoreSecretCipher(),
)

/**
 * AES-256-GCM under a key that stays inside the Android Keystore and cannot be exported. Each text gets its own IV,
 * written before the ciphertext, all in Base64. A key that no longer works is replaced on the next [encrypt]; [decrypt]
 * never makes one, as a new key could not read what is stored.
 */
internal class KeystoreSecretCipher(private val alias: String = "off_login") : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = try {
            encryptingCipher(existingKey() ?: newKey())
        } catch (_: Exception) {
            keyStore().deleteEntry(alias)
            encryptingCipher(newKey())
        }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    override fun decrypt(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val key = existingKey() ?: throw GeneralSecurityException("No key")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun encryptingCipher(key: SecretKey) = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey() = keyStore().getKey(alias, null) as SecretKey?

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
