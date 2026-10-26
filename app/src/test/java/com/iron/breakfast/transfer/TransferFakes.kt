package com.iron.breakfast.transfer

import com.iron.breakfast.data.DiaryFile
import com.iron.breakfast.data.DiaryRows
import com.iron.breakfast.data.DiarySettings
import com.iron.breakfast.data.diaryFileOf
import com.iron.breakfast.data.encodeDiary
import com.iron.breakfast.ui.components.LongDraft

internal val EMPTY_ROWS = DiaryRows(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

/** A diary file's text that passes every import check. */
internal fun validDiaryText(): String = encodeDiary(diaryFileOf(EMPTY_ROWS, DiarySettings(4, LongDraft.entries.first()), 0L))

/** Picked documents by name, and the safety copy, in memory; [events] records every effect in order. */
internal class FakeFiles(private val events: MutableList<String>) : DiaryFiles<String> {
    val documents = mutableMapOf<String, String>()
    var safetyCopy: String? = null
    var writeFailure: Exception? = null
    var readBackDiffers = false
    var tooBig = false
    var safetyCopyFailure: Exception? = null

    override fun write(document: String, bytes: ByteArray) {
        writeFailure?.let { throw it }
        documents[document] = String(bytes, Charsets.UTF_8)
        events += "write $document"
    }
    override fun read(document: String): String? {
        if (tooBig) return null
        val text = documents[document] ?: error("no input stream")
        return if (readBackDiffers) text.dropLast(1) else text
    }
    override fun delete(document: String) { documents.remove(document); events += "delete $document" }
    override fun writeSafetyCopy(file: DiaryFile) {
        safetyCopyFailure?.let { throw it }
        safetyCopy = encodeDiary(file)
        events += "safety copy"
    }
    override fun deleteSafetyCopy(): Boolean { safetyCopy = null; events += "delete safety copy"; return true }
}

internal class FakeData(private val events: MutableList<String>) : DiaryData {
    var failure: Exception? = null
    override suspend fun exportRows(): DiaryRows = EMPTY_ROWS
    override suspend fun replaceDiary(rows: DiaryRows) { failure?.let { throw it }; events += "replace" }
    override suspend fun deleteAllData(alsoPreferences: Boolean) { failure?.let { throw it }; events += "delete all alsoPreferences=$alsoPreferences" }
}

internal class RecordingLog : TransferLog {
    val lines = mutableListOf<String>()
    override fun error(message: String, cause: Throwable?) { lines += "E $message" }
    override fun warn(message: String) { lines += "W $message" }
}
