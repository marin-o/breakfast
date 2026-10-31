package com.iron.breakfast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPOutputStream

class CatalogueFileTest {
    @get:Rule val folder = TemporaryFolder()

    private val content = ByteArray(200_000) { (it % 251).toByte() }
    private val gzip = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(content) } }.toByteArray()
    private var opens = 0

    private fun open(bytes: ByteArray = gzip): InputStream {
        opens++
        return ByteArrayInputStream(bytes)
    }

    private fun directory() = File(folder.root, "usda")

    private fun assertFailsWithNothingLeft(open: () -> InputStream) {
        try {
            catalogueFile(directory(), "v1.sqlite", open)
            fail("Expected a failed unpack")
        } catch (_: IOException) {
        }
        assertEquals(emptyList<String>(), directory().list().orEmpty().toList())
    }

    @Test fun `it unpacks the whole file under its name`() {
        val file = catalogueFile(directory(), "v1.sqlite") { open() }
        assertEquals("v1.sqlite", file.name)
        assertTrue(content.contentEquals(file.readBytes()))
        assertEquals(listOf("v1.sqlite"), directory().list()!!.toList())
    }

    @Test fun `a second start doesn't unpack again`() {
        val first = catalogueFile(directory(), "v1.sqlite") { open() }
        val second = catalogueFile(directory(), "v1.sqlite") { open() }
        assertEquals(first, second)
        assertEquals(1, opens)
    }

    @Test fun `a cut off gzip leaves nothing behind`() {
        assertFailsWithNothingLeft { open(gzip.copyOf(gzip.size / 2)) }
    }

    @Test fun `a damaged gzip leaves nothing behind`() {
        assertFailsWithNothingLeft { open(byteArrayOf(1, 2, 3, 4)) }
    }

    @Test fun `a stream that fails while reading leaves nothing behind`() {
        assertFailsWithNothingLeft {
            object : InputStream() {
                private val real = ByteArrayInputStream(gzip)
                override fun read(): Int = if (real.available() < gzip.size / 2) throw IOException("gone") else real.read()
            }
        }
    }

    @Test fun `after a failure the next start unpacks it`() {
        assertFailsWithNothingLeft { open(gzip.copyOf(10)) }
        assertTrue(content.contentEquals(catalogueFile(directory(), "v1.sqlite") { open() }.readBytes()))
    }

    @Test fun `an interrupted unpack's file doesn't count and goes`() {
        directory().mkdirs()
        File(directory(), "v1.sqlite.tmp").writeText("half")
        val file = catalogueFile(directory(), "v1.sqlite") { open() }
        assertTrue(content.contentEquals(file.readBytes()))
        assertEquals(listOf("v1.sqlite"), directory().list()!!.toList())
    }

    @Test fun `a new version replaces the one before it`() {
        val before = catalogueFile(directory(), "v1.sqlite") { open() }
        catalogueFile(directory(), "v2.sqlite") { open() }
        assertFalse(before.exists())
        assertEquals(listOf("v2.sqlite"), directory().list()!!.toList())
    }

    @Test fun `a kill after the rename leaves the old version to the next start, which doesn't unpack`() {
        directory().mkdirs()
        File(directory(), "v1.sqlite").writeText("old")
        File(directory(), "v2.sqlite").writeText("new")
        val file = catalogueFile(directory(), "v2.sqlite") { open() }
        assertEquals("new", file.readText())
        assertEquals(0, opens)
        assertEquals(listOf("v2.sqlite"), directory().list()!!.toList())
    }

    @Test fun `a bad gzip header still closes the stream`() {
        var closed = false
        assertFailsWithNothingLeft {
            object : ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) {
                override fun close() {
                    closed = true
                }
            }
        }
        assertTrue(closed)
    }
}
