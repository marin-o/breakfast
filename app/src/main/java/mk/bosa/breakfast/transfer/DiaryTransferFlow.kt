package mk.bosa.breakfast.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mk.bosa.breakfast.app.DiaryChange
import mk.bosa.breakfast.app.DiarySession
import mk.bosa.breakfast.app.Notices
import mk.bosa.breakfast.data.DiaryFile
import mk.bosa.breakfast.data.DiaryImport
import mk.bosa.breakfast.data.DiaryRows
import mk.bosa.breakfast.data.DiarySettings
import mk.bosa.breakfast.data.decodeDiary
import mk.bosa.breakfast.data.diaryFileOf
import mk.bosa.breakfast.data.readDiaryText
import mk.bosa.breakfast.data.writeDiaryChecked
import mk.bosa.breakfast.settings.SettingsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The whole diary in storage: what export reads and what import and Delete all replace. `BreakfastEngine` in the app. */
internal interface DiaryData {
    suspend fun exportRows(): DiaryRows
    /** Every table becomes [rows], in one transaction. */
    suspend fun replaceDiary(rows: DiaryRows)
    /** Entries and foods (and with [alsoPreferences] the learned picks) go in one transaction; the catalogue is seeded again. */
    suspend fun deleteAllData(alsoPreferences: Boolean)
}

/**
 * The files a transfer touches: the picked documents [D] (a content `Uri` in the app) and the safety copy. All calls
 * block; the owner runs them on its IO dispatcher.
 */
internal interface DiaryFiles<D> {
    /** Writes [bytes] as the whole content of [document]; throws when it cannot. */
    fun write(document: D, bytes: ByteArray)
    /** The text of [document], or null when it is too big to be a diary; throws when it cannot be read. */
    fun read(document: D): String?
    /** Removes [document] (a failed export, so no partial file looks like a backup); throws when it cannot. */
    fun delete(document: D)
    /** Writes [file] as the safety copy, replacing the previous one only once complete; throws when that fails. */
    fun writeSafetyCopy(file: DiaryFile)
    /** Removes the safety copy; true when none is left. */
    fun deleteSafetyCopy(): Boolean
}

/** Where transfers report what went wrong: logcat in the app. Users see only the notice. */
internal interface TransferLog {
    fun error(message: String, cause: Throwable? = null)
    fun warn(message: String)
}

/**
 * Export diary, Import diary (read and check, then Replace diary) and Delete all data, with the safety copy and the
 * document picker request.
 *
 * - The picker: Settings asks for one ([requestPicker]); the root composable, which keeps the launchers registered so
 *   a result after a recreation still lands, launches it ([pickerLaunched]) and hands the result to [export] or
 *   [import].
 * - Export and reading an import file hold the diary still ([DiarySession.whenIdle]); Replace diary and Delete all
 *   are diary writes that publish [DiaryChange.DiaryReplaced], on which the coordinator resets the session.
 * - Before a replace the current diary goes to the safety copy (no copy, no replace); Delete all always writes it;
 *   import with "Safety copy" Don't keep writes none and removes the old one.
 *
 * File work runs on [io]; [log] gets the causes.
 */
internal class DiaryTransfer<D>(
    private val session: DiarySession,
    private val data: DiaryData,
    private val files: DiaryFiles<D>,
    private val settings: SettingsState,
    private val notices: Notices,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val log: TransferLog,
    /** A diary file was written and checked (the export reminder goes). */
    private val onExported: () -> Unit,
    /** Delete all data went through (the remembered Open Food Facts login goes). */
    private val onDeletedAll: () -> Unit,
) {
    /** The picker Settings asked for, until the root launched it. */
    var pickerRequest by mutableStateOf<DiaryPicker?>(null); private set
    /** A diary file that passed every check, waiting for Replace diary. */
    var pendingImport by mutableStateOf<DiaryImport.Valid?>(null); private set
    /** The Delete all data dialog, and its "Also settings and learned picks" chip (off each time it opens). */
    var deleteAllOpen by mutableStateOf(false); private set
    var deleteAllAlsoSettings by mutableStateOf(false); private set

    fun requestPicker(picker: DiaryPicker) { if (session.canWrite) pickerRequest = picker }
    fun pickerLaunched() { pickerRequest = null }

    /**
     * Writes every stored row and the settings to [document], the new file the save picker just created. It says
     * "Diary exported." only after the copy was read back and imports; on any failure the file is deleted.
     */
    fun export(document: D) {
        // A picker result can arrive before storage is open or while a write runs (after recreation): it waits.
        session.whenIdle(
            onSuccess = { written: Boolean ->
                if (written) { settings.lastExportMs = clock(); onExported() }
                notices.show(if (written) DIARY_EXPORTED else DIARY_EXPORT_FAILED)
            },
        ) {
            withContext(NonCancellable) {
                val problem = try {
                    val file = currentFile()
                    withContext(io) { writeDiaryChecked(file, write = { files.write(document, it) }, readBack = { files.read(document) }) }
                } catch (e: Exception) {
                    "write failed: $e"
                }
                if (problem != null) {
                    log.error("Diary export failed: $problem")
                    withContext(io) { runCatching { files.delete(document) } }
                }
                problem == null
            }
        }
    }

    /** Reads and checks the whole file [document]; only a file that passes everything asks to replace. */
    fun import(document: D) {
        // Holds the gate while the file is read, so no write changes the diary under the check.
        session.whenIdle(
            onSuccess = { result: DiaryImport ->
                when (result) {
                    is DiaryImport.Valid -> pendingImport = result
                    is DiaryImport.Invalid -> { log.warn("Diary import refused: ${result.reason}"); notices.show(DIARY_IMPORT_REFUSED) }
                }
            },
        ) {
            try {
                withContext(io) {
                    val text = files.read(document)
                    if (text == null) DiaryImport.Invalid("file too big") else decodeDiary(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiaryImport.Invalid("unreadable: $e")
            }
        }
    }

    fun cancelImport() { pendingImport = null }

    /**
     * Replace diary: the current diary is first saved to the safety copy (no copy, no replace; with "Safety copy"
     * Don't keep, none is written and an old one removed), then every table becomes the file's rows in one
     * transaction. The phone's settings stay as they are.
     */
    fun confirmImport() {
        val pending = pendingImport ?: return
        if (!session.canWrite) return
        pendingImport = null
        session.write(
            change = { replaced: Boolean -> DiaryChange.DiaryReplaced.takeIf { replaced } },
            onSuccess = { replaced -> if (!replaced) notices.show(DIARY_IMPORT_REFUSED) },
        ) {
            withContext(NonCancellable) {
                try {
                    when (safetyCopyStep(settings.keepSafetyCopy, deletingAll = false)) {
                        SafetyCopyStep.WRITE -> { val current = currentFile(); withContext(io) { files.writeSafetyCopy(current) } }
                        // "Safety copy" Don't keep: no copy, and an old one is removed.
                        SafetyCopyStep.DELETE -> withContext(io) { files.deleteSafetyCopy() }
                    }
                    data.replaceDiary(pending.rows)
                    true
                } catch (e: Exception) {
                    log.error("Diary import failed; the stored diary is unchanged", e)
                    false
                }
            }
        }
    }

    fun openDeleteAll() { if (session.canWrite) { deleteAllAlsoSettings = false; deleteAllOpen = true } }
    fun toggleDeleteAllAlsoSettings() { deleteAllAlsoSettings = !deleteAllAlsoSettings }
    fun cancelDeleteAll() { deleteAllOpen = false; deleteAllAlsoSettings = false }

    /**
     * Delete all in the dialog: the diary is first saved to the safety copy, whatever "Safety copy" says (no copy, no
     * delete); then entries and foods go in one transaction and the catalogue is seeded again. With the chip on, the
     * learned preferences go in the same transaction and every setting returns to its default.
     */
    fun confirmDeleteAll() {
        if (!deleteAllAllowed(deleteAllOpen, session.canWrite)) return
        val alsoSettings = deleteAllAlsoSettings
        cancelDeleteAll()
        session.write(
            change = { deleted: Boolean -> DiaryChange.DiaryReplaced.takeIf { deleted } },
            onSuccess = { deleted ->
                if (!deleted) notices.show(DIARY_DELETE_FAILED)
                else {
                    onDeletedAll()
                    if (alsoSettings) settings.resetToDefaults()
                }
            },
        ) {
            withContext(NonCancellable) {
                try {
                    if (safetyCopyStep(settings.keepSafetyCopy, deletingAll = true) == SafetyCopyStep.WRITE) {
                        val current = currentFile()
                        withContext(io) { files.writeSafetyCopy(current) }
                    }
                    data.deleteAllData(alsoPreferences = alsoSettings)
                    true
                } catch (e: Exception) {
                    log.error("Delete all failed; the stored diary is unchanged", e)
                    false
                }
            }
        }
    }

    /** "Safety copy" Don't keep also removes the copy kept so far. */
    fun removeSafetyCopy() {
        scope.launch(io) { if (!files.deleteSafetyCopy()) log.warn("The safety copy could not be deleted") }
    }

    /** The diary as stored now, with the settings a file carries. */
    private suspend fun currentFile(): DiaryFile =
        diaryFileOf(data.exportRows(), DiarySettings(settings.dayStartHour, settings.longDraft), clock())
}

/** [DiaryFiles] over the system document picker's content URIs and the app's `no_backup` folder. */
internal class ContentDiaryFiles(private val resolver: ContentResolver, private val safetyDir: File) : DiaryFiles<Uri> {
    override fun write(document: Uri, bytes: ByteArray) {
        val out = runCatching { resolver.openOutputStream(document, "wt") }.getOrNull() ?: resolver.openOutputStream(document)
        (out ?: error("no output stream")).use { it.write(bytes) }
    }
    override fun read(document: Uri): String? = (resolver.openInputStream(document) ?: error("no input stream")).use { readDiaryText(it) }
    override fun delete(document: Uri) { DocumentsContract.deleteDocument(resolver, document) }
    override fun writeSafetyCopy(file: DiaryFile) { mk.bosa.breakfast.data.writeSafetyCopy(safetyDir, file) }
    override fun deleteSafetyCopy(): Boolean = mk.bosa.breakfast.data.deleteSafetyCopy(safetyDir)
}
