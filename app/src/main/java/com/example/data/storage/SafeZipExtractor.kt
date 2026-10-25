package com.example.data.storage

import com.example.data.model.FileNode
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SafeZipExtractor {

    data class ExtractionResult(
        val success: Boolean,
        val totalFiles: Int,
        val totalBytes: Long,
        val rootDir: File,
        val errorMessage: String? = null
    )

    /**
     * Safely unpacks a ZIP stream into [destinationDir] preventing Zip-Slip vulnerabilities.
     */
    fun extract(
        inputStream: InputStream,
        destinationDir: File,
        onProgress: (filesExtracted: Int) -> Unit = {}
    ): ExtractionResult {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val canonicalDest = destinationDir.canonicalFile
        var fileCount = 0
        var byteCount = 0L
        var lastProgressTime = 0L
        val maxFiles = 2000
        val maxTotalBytes = 100L * 1024L * 1024L // 100 MB limit safety

        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name

                    // Guard: Reject absolute paths and dangerous characters
                    if (entryName.startsWith("/") || entryName.startsWith("\\") || entryName.contains("..")) {
                        throw SecurityException("Path traversal attempt detected in ZIP entry: $entryName")
                    }

                    val outputFile = File(destinationDir, entryName)
                    val canonicalOutputFile = outputFile.canonicalFile

                    // Guard: Verify canonical output stays strictly inside canonicalDest
                    if (!canonicalOutputFile.path.startsWith(canonicalDest.path + File.separator) &&
                        canonicalOutputFile.path != canonicalDest.path
                    ) {
                        throw SecurityException("Zip-Slip detected! Entry escapes destination: $entryName")
                    }

                    if (entry.isDirectory) {
                        canonicalOutputFile.mkdirs()
                    } else {
                        val parent = canonicalOutputFile.parentFile
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs()
                        }

                        BufferedOutputStream(FileOutputStream(canonicalOutputFile)).use { out ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zipIn.read(buffer).also { len = it } != -1) {
                                out.write(buffer, 0, len)
                                byteCount += len
                                if (byteCount > maxTotalBytes) {
                                    throw IllegalStateException("ZIP uncompressed size exceeds maximum 100MB limit.")
                                }
                            }
                        }
                        fileCount++
                        if (fileCount > maxFiles) {
                            throw IllegalStateException("ZIP contains too many entries (max $maxFiles allowed).")
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 120 || fileCount == 1) {
                            onProgress(fileCount)
                            lastProgressTime = now
                        }
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // Always dispatch final count
            onProgress(fileCount)

            return ExtractionResult(
                success = true,
                totalFiles = fileCount,
                totalBytes = byteCount,
                rootDir = destinationDir
            )
        } catch (e: Exception) {
            return ExtractionResult(
                success = false,
                totalFiles = fileCount,
                totalBytes = byteCount,
                rootDir = destinationDir,
                errorMessage = e.localizedMessage ?: "Unknown ZIP extraction error"
            )
        }
    }

    /**
     * Recursively traverses an extracted directory to build a clean FileNode hierarchy.
     */
    fun buildFileTree(rootDir: File, baseDir: File = rootDir): List<FileNode> {
        val files = rootDir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: return emptyList()

        return files.map { file ->
            val relPath = file.relativeTo(baseDir).path.replace('\\', '/')
            val ext = if (file.isDirectory) "" else file.extension.lowercase()
            if (file.isDirectory) {
                FileNode(
                    id = relPath,
                    name = file.name,
                    relativePath = relPath,
                    isDirectory = true,
                    children = buildFileTree(file, baseDir),
                    isIncluded = true,
                    extension = ext,
                    sizeBytes = 0L
                )
            } else {
                FileNode(
                    id = relPath,
                    name = file.name,
                    relativePath = relPath,
                    isDirectory = false,
                    children = emptyList(),
                    isIncluded = true,
                    extension = ext,
                    sizeBytes = file.length()
                )
            }
        }
    }
}
