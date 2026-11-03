package mk.bosa.breakfast.contribution

import android.content.Context
import android.security.keystore.KeyInfo
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mk.bosa.breakfast.settings.SharedPreferencesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/** The real Android Keystore, under its own alias and preferences file so the app's saved login is left alone. */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretCipherTest {
    private val alias = "off_login_test"
    private val cipher = KeystoreSecretCipher(alias)
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("off_login_test", Context.MODE_PRIVATE)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @After fun cleanUp() {
        keyStore().deleteEntry(alias)
        prefs.edit().clear().commit()
    }

    @Test fun aTextComesBackAndEachSealingDiffers() {
        val plain = "pässwörd Лозинка 🔑"
        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)
        assertEquals(plain, cipher.decrypt(first))
        assertEquals(plain, cipher.decrypt(second))
        assertNotEquals(first, second)
        assertFalse(first.contains("pässwörd"))
    }

    @Test fun theKeyIsAes256AndCannotBeExported() {
        cipher.encrypt("x")
        val key = keyStore().getKey(alias, null) as SecretKey
        assertEquals("AES", key.algorithm)
        assertNull(key.encoded)
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(256, info.keySize)
    }

    @Test fun changedTextDoesNotOpen() {
        val bytes = Base64.decode(cipher.encrypt("secret"), Base64.NO_WRAP)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(Base64.encodeToString(bytes, Base64.NO_WRAP)) }
    }

    /** As after restored data: the key is gone. Nothing opens, no key is made for it, and the next save makes one. */
    @Test fun aLostKeyFailsTheReadAndTheNextSaveWorks() {
        val sealed = cipher.encrypt("secret")
        keyStore().deleteEntry(alias)
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(sealed) }
        assertFalse(keyStore().containsAlias(alias))
        assertEquals("again", cipher.decrypt(cipher.encrypt("again")))
    }

    @Test fun aSavedLoginGoesThroughRealPreferencesAndALostKeyClearsIt() {
        val login = RememberedLogin(SharedPreferencesStore(prefs), cipher)
        login.store(login.seal("ana", "secret")!!)
        assertTrue(prefs.all.values.none { it.toString().contains("ana") || it.toString().contains("secret") })
        val found = login.recall() as RecalledLogin.Found
        assertEquals("ana", found.login.username)
        assertEquals("secret", found.login.password)
        keyStore().deleteEntry(alias)
        assertEquals(RecalledLogin.Unreadable, login.recall())
        assertTrue(prefs.all.isEmpty())
        assertEquals(RecalledLogin.Nothing, login.recall())
    }
}
