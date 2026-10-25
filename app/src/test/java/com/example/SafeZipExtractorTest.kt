package com.example

import com.example.data.storage.SafeZipExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SafeZipExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSafeZipExtraction_success() {
        val zipBytes = createZipArchive(
            mapOf(
                "src/Main.kt" to "fun main() = println(\"Hello\")",
                "README.md" to "# My App"
            )
        )

        val destDir = tempFolder.newFolder("extracted")
        val extractor = SafeZipExtractor()
        val result = extractor.extract(ByteArrayInputStream(zipBytes), destDir)

        assertTrue("Extraction should succeed", result.success)
        assertEquals(2, result.totalFiles)
        assertTrue(File(destDir, "src/Main.kt").exists())
        assertEquals("fun main() = println(\"Hello\")", File(destDir, "src/Main.kt").readText())
        assertTrue(File(destDir, "README.md").exists())
    }

    @Test
    fun testZipSlipPathTraversal_isRejected() {
        // Create an entry attempting to escape using ../
        val zipBytes = createZipArchive(
            mapOf(
                "../../malicious.txt" to "danger"
            )
        )

        val destDir = tempFolder.newFolder("safe_sandbox")
        val extractor = SafeZipExtractor()
        val result = extractor.extract(ByteArrayInputStream(zipBytes), destDir)

        assertFalse("Extraction with path traversal entry must fail", result.success)
        assertTrue(
            "Error message should mention path traversal or zip-slip",
            result.errorMessage?.contains("Path traversal", ignoreCase = true) == true ||
            result.errorMessage?.contains("Zip-Slip", ignoreCase = true) == true
        )
        assertFalse(File(destDir.parentFile, "malicious.txt").exists())
    }

    @Test
    fun testAbsolutePathEntry_isRejected() {
        val zipBytes = createZipArchive(
            mapOf(
                "/etc/evil.conf" to "danger"
            )
        )

        val destDir = tempFolder.newFolder("safe_sandbox2")
        val extractor = SafeZipExtractor()
        val result = extractor.extract(ByteArrayInputStream(zipBytes), destDir)

        assertFalse("Absolute path entry must fail", result.success)
    }

    private fun createZipArchive(entries: Map<String, String>): ByteArray {
        val byteOut = ByteArrayOutputStream()
        ZipOutputStream(byteOut).use { zipOut ->
            for ((name, content) in entries) {
                zipOut.putNextEntry(ZipEntry(name))
                zipOut.write(content.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
        }
        return byteOut.toByteArray()
    }
}
