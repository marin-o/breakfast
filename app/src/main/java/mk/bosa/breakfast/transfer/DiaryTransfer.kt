package mk.bosa.breakfast.transfer

/** The system document picker Settings asks for: save a new diary file, or open one to import. */
enum class DiaryPicker { EXPORT, IMPORT }

/** Any file may be offered: a diary arrives as JSON, as a plain binary from a file manager, or as text from a messenger or email. Import checks it in full anyway. */
internal val DIARY_FILE_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")
internal const val DIARY_EXPORTED = "Diary exported."
internal const val DIARY_EXPORT_FAILED = "Could not export the diary."
internal const val DIARY_IMPORT_REFUSED = "That file can't be imported."
internal const val DIARY_DELETE_FAILED = "Could not delete the diary."

/** What happens to the safety copy before the diary is replaced or deleted. */
internal enum class SafetyCopyStep { WRITE, DELETE }

/**
 * Import with "Safety copy" Keep writes it, with Don't keep removes any old one and writes none. Delete all data always
 * writes it first, whatever the setting.
 */
internal fun safetyCopyStep(keep: Boolean, deletingAll: Boolean): SafetyCopyStep =
    if (keep || deletingAll) SafetyCopyStep.WRITE else SafetyCopyStep.DELETE

/** Delete all data runs only from its confirm dialog, once storage is open and no other write runs. */
internal fun deleteAllAllowed(dialogOpen: Boolean, canWrite: Boolean): Boolean = dialogOpen && canWrite
