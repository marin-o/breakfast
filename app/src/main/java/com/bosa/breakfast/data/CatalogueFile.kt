package com.bosa.breakfast.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * The file [fileName] in [directory], unpacked from the gzip [openGzip] gives when it isn't there yet. It is written to
 * a temporary file and renamed, so the name only ever means a whole file: a failed or interrupted unpack leaves nothing
 * under it and the next start tries again. Once a version is in place, every other file in [directory] goes (the
 * version before it, what an interrupted unpack left), also when an earlier start was killed before it got that far.
 *
 * Blocks; the caller keeps it off the main thread and to one at a time.
 *
 * @throws IOException when it can't be unpacked (no room, a damaged gzip), with nothing left behind
 */
internal fun catalogueFile(directory: File, fileName: String, openGzip: () -> InputStream): File {
    val target = File(directory, fileName)
    if (!target.exists()) unpack(target, openGzip)
    directory.listFiles()?.filter { it.name != fileName }?.forEach { it.delete() }
    return target
}

private fun unpack(target: File, openGzip: () -> InputStream) {
    val directory = target.parentFile!!
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Can't create $directory")
    val temporary = File(directory, "${target.name}.tmp")
    try {
        // The outer use closes the stream when the gzip header is bad and GZIPInputStream never gets to own it.
        openGzip().use { source ->
            GZIPInputStream(source).use { gzip ->
                FileOutputStream(temporary).use { out ->
                    gzip.copyTo(out)
                    out.fd.sync()
                }
            }
        }
        if (!temporary.renameTo(target)) throw IOException("Can't rename $temporary")
    } finally {
        temporary.delete()
    }
}
