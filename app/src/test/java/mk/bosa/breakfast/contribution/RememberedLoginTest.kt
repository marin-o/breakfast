package mk.bosa.breakfast.contribution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mk.bosa.breakfast.QueueDispatcher
import mk.bosa.breakfast.app.Notices
import mk.bosa.breakfast.data.OffContributionDraft
import mk.bosa.breakfast.data.OffContributionResult
import mk.bosa.breakfast.data.OffPreflightResult
import mk.bosa.breakfast.domain.MeasureUnit
import mk.bosa.breakfast.domain.NutritionValues
import mk.bosa.breakfast.settings.FakeSettingsStore
import mk.bosa.breakfast.testFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Stands in for the Keystore: reversible, not plain, and it can be made to fail. */
internal class FakeSecretCipher : SecretCipher {
    var failing = false
    var whileEncrypting: () -> Unit = {}
    override fun encrypt(plain: String): String {
        whileEncrypting()
        if (failing) error("no key")
        return "sealed:" + plain.reversed()
    }
    override fun decrypt(sealed: String): String {
        if (failing) error("no key")
        return sealed.removePrefix("sealed:").reversed()
    }
}

class RememberedLoginTest {
    private class FakeOff : OffContributions {
        var result: OffContributionResult = OffContributionResult.Success
        var whileSending: () -> Unit = {}
        val sentPasswords = mutableListOf<String>()
        override fun preflight(draft: OffContributionDraft) = OffPreflightResult.Existing
        override fun contribute(draft: OffContributionDraft, username: String, password: String): OffContributionResult {
            sentPasswords += password
            whileSending()
            return result
        }
    }

    private val off = FakeOff()
    private val store = FakeSettingsStore()
    private val cipher = FakeSecretCipher()
    private val io = QueueDispatcher()
    private val notices = Notices()
    private fun newFlow() = ContributionFlow(off, RememberedLogin(store, cipher), notices, CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), io, showContribute = { true })
    /** A later session makes its own with [newFlow]; only [store] carries over. */
    private var flow = newFlow()

    private val kefir = testFood("f", "Kefir", category = "dairy", unit = MeasureUnit.GRAM, values = NutritionValues(60.0, 3.0, 4.0, 3.0), barcode = "4006381333931")

    private fun openSignIn() {
        flow.openForFood(kefir)
        io.runAll()
        flow.confirm()
        io.runAll()
        assertTrue(flow.authOpen)
    }

    private fun send(result: OffContributionResult = OffContributionResult.Success) {
        off.result = result
        flow.submit()
        io.runAll()
    }

    private fun type(username: String = "ana", password: String = "secret") {
        flow.onUsername(username)
        flow.onPassword(password)
    }

    /** A login remembered by an earlier session, and the sign-in open again in a new one. */
    private fun rememberedAndOpen() {
        openSignIn()
        type()
        flow.toggleRememberLogin()
        send()
        flow = newFlow()
        openSignIn()
    }

    @Test fun theBoxIsOffByDefaultAndWithoutItNothingIsSaved() {
        openSignIn()
        assertFalse(flow.rememberLogin)
        type()
        send()
        assertEquals("Contribution sent to Open Food Facts.", notices.message)
        assertTrue(store.values.isEmpty())
    }

    @Test fun aSuccessWithTheBoxTickedSavesTheLoginSealed() {
        openSignIn()
        type(username = " mila ")
        flow.toggleRememberLogin()
        assertTrue("nothing before the login worked", store.values.isEmpty())
        send()
        assertEquals(2, store.values.size)
        assertTrue(store.values.values.none { it.toString().contains("mila") || it.toString().contains("secret") })
        assertEquals("", flow.password)
    }

    @Test fun nextTimeBothFieldsPrefillAndTheBoxIsTicked() {
        rememberedAndOpen()
        assertEquals("ana", flow.username)
        assertEquals("secret", flow.password)
        assertTrue(flow.rememberLogin)
        send()
        assertEquals("secret", off.sentPasswords.last())
    }

    @Test fun anyFailureSavesNothing() {
        listOf(
            OffContributionResult.AuthFailed,
            OffContributionResult.Unavailable,
            OffContributionResult.Uncertain,
            OffContributionResult.Rejected("no"),
            OffContributionResult.Error("no"),
        ).forEach { result ->
            flow = newFlow()
            openSignIn()
            type()
            flow.toggleRememberLogin()
            send(result)
            assertTrue("$result", store.values.isEmpty())
        }
    }

    @Test fun theBoxStaysTickedThroughARetryAfterAFailure() {
        openSignIn()
        type()
        flow.toggleRememberLogin()
        send(OffContributionResult.Unavailable)
        flow.retry()
        io.runAll()
        assertTrue(flow.rememberLogin)
        flow.onPassword("secret")
        send()
        assertEquals(2, store.values.size)
    }

    /** A new password that works replaces the saved one. */
    @Test fun aSuccessWithAnotherPasswordReplacesTheSavedOne() {
        rememberedAndOpen()
        flow.onPassword("changed")
        send()
        flow = newFlow()
        openSignIn()
        assertEquals("changed", flow.password)
    }

    @Test fun uncheckingForgetsAtOnceAndEmptiesTheForm() {
        rememberedAndOpen()
        flow.toggleRememberLogin()
        assertTrue(store.values.isEmpty())
        assertEquals("", flow.username)
        assertEquals("", flow.password)
        assertFalse(flow.rememberLogin)
        flow.dismissAuth()
        flow = newFlow()
        openSignIn()
        assertEquals("", flow.username)
        assertFalse(flow.rememberLogin)
    }

    @Test fun uncheckingKeepsWhatWasTypedInstead() {
        rememberedAndOpen()
        type(username = "other", password = "typed")
        flow.toggleRememberLogin()
        assertTrue(store.values.isEmpty())
        assertEquals("other", flow.username)
        assertEquals("typed", flow.password)
    }

    /** Unticked while the sign-in submits: the login that then succeeds is not kept, also while it is being sealed. */
    @Test fun uncheckingDuringTheSubmitKeepsItForgotten() {
        openSignIn()
        type()
        flow.toggleRememberLogin()
        off.whileSending = { flow.toggleRememberLogin() }
        send()
        assertTrue(store.values.isEmpty())

        flow = newFlow()
        openSignIn()
        type()
        flow.toggleRememberLogin()
        off.whileSending = {}
        cipher.whileEncrypting = { if (flow.rememberLogin) flow.toggleRememberLogin() }
        send()
        assertTrue(store.values.isEmpty())
    }

    @Test fun leavingWhileItSubmitsSavesNothing() {
        openSignIn()
        type()
        flow.toggleRememberLogin()
        off.whileSending = { flow.dismissAuth() }
        send()
        assertTrue(store.values.isEmpty())
    }

    @Test fun aRejectedSavedPasswordIsForgottenAndTheUsernameStays() {
        rememberedAndOpen()
        send(OffContributionResult.AuthFailed)
        assertEquals(1, store.values.size)
        flow.retry()
        io.runAll()
        assertEquals("ana", flow.username)
        assertEquals("", flow.password)
        assertTrue(flow.rememberLogin)
        // In a new session as well, and the user types it again.
        flow.dismissAuth()
        flow = newFlow()
        openSignIn()
        assertEquals("ana", flow.username)
        assertEquals("", flow.password)
        assertTrue(flow.rememberLogin)
        flow.onPassword("new")
        send()
        flow = newFlow()
        openSignIn()
        assertEquals("new", flow.password)
    }

    /** Only the saved password being rejected counts: a mistyped one over it leaves it as it was. */
    @Test fun aRejectedTypedPasswordLeavesTheSavedOne() {
        rememberedAndOpen()
        flow.onPassword("typo")
        send(OffContributionResult.AuthFailed)
        assertEquals(2, store.values.size)
        flow.retry()
        io.runAll()
        assertEquals("secret", flow.password)
    }

    @Test fun whatCannotBeReadCountsAsNothingSaved() {
        rememberedAndOpen()
        flow.dismissAuth()
        cipher.failing = true
        flow = newFlow()
        flow.onUsername("left in memory")
        openSignIn()
        assertTrue(store.values.isEmpty())
        assertEquals("", flow.username)
        assertEquals("", flow.password)
        assertFalse(flow.rememberLogin)
        assertNull(flow.failure)
        assertEquals("Contribution sent to Open Food Facts.", notices.message)
    }

    @Test fun aPasswordWithoutItsUsernameIsUnreadable() {
        store.values["password"] = cipher.encrypt("secret")
        openSignIn()
        assertTrue(store.values.isEmpty())
        assertEquals("", flow.password)
        assertFalse(flow.rememberLogin)
    }

    @Test fun whatCannotBeSealedIsNotSavedAndIsNoFailure() {
        rememberedAndOpen()
        cipher.failing = true
        send()
        assertTrue("the old login goes as well", store.values.isEmpty())
        assertFalse(flow.rememberLogin)
        assertNull(flow.failure)
        assertEquals("Contribution sent to Open Food Facts.", notices.message)
    }

    @Test fun typingBeforeTheReadEndsWins() {
        rememberedAndOpen()
        flow.dismissAuth()
        flow = newFlow()
        flow.openForFood(kefir)
        io.runAll()
        flow.confirm()
        flow.onUsername("typed")
        io.runAll()
        assertEquals("typed", flow.username)
        assertEquals("", flow.password)
    }

    @Test fun leavingBeforeTheReadEndsFillsNothing() {
        rememberedAndOpen()
        flow.dismissAuth()
        flow = newFlow()
        flow.openForFood(kefir)
        io.runAll()
        flow.confirm()
        flow.dismissAuth()
        io.runAll()
        assertEquals("", flow.password)
    }

    @Test fun aReplacedDiaryLeavesTheLogin() {
        rememberedAndOpen()
        flow.dismissAuth()
        val before = store.values.toMap()
        flow.reset()
        assertEquals(before, store.values)
    }

    @Test fun forgetLoginClearsTheStoreTheFormAndTheBox() {
        rememberedAndOpen()
        flow.forgetLogin()
        assertTrue(store.values.isEmpty())
        assertEquals("", flow.username)
        assertEquals("", flow.password)
        assertFalse(flow.rememberLogin)
    }

    /** The file named by [OFF_LOGIN_PREFS] is out of cloud backup and device transfer, under both rule sets. */
    @Test fun backupAndDeviceTransferLeaveTheLoginOut() {
        val rule = """<exclude domain="sharedpref" path="$OFF_LOGIN_PREFS.xml" />"""
        val older = File("src/main/res/xml/backup_rules.xml").readText()
        assertTrue(older.substringAfter("<full-backup-content>").contains(rule))
        val newer = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(newer.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>").contains(rule))
        assertTrue(newer.substringAfter("<device-transfer>").substringBefore("</device-transfer>").contains(rule))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
    }
}
