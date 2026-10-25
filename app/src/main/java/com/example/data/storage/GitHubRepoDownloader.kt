package com.example.data.storage

import android.content.Context
import com.example.data.model.FileNode
import com.example.data.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class GitHubRepoDownloader(private val context: Context) {

    data class RepoCoordinates(
        val owner: String,
        val repo: String,
        val branch: String = "main"
    )

    data class DownloadResult(
        val success: Boolean,
        val project: Project? = null,
        val fileTree: List<FileNode> = emptyList(),
        val errorMessage: String? = null
    )

    /**
     * Parses various GitHub URL formats:
     * - https://github.com/owner/repo
     * - https://github.com/owner/repo.git
     * - https://github.com/owner/repo/tree/dev
     * - owner/repo
     */
    fun parseRepoUrl(input: String): RepoCoordinates? {
        val trimmed = input.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')

        val parts = trimmed.split('/')
        if (parts.size < 2) return null

        val owner = parts[0]
        val repo = parts[1]
        val branch = if (parts.size >= 4 && parts[2] == "tree") {
            parts.drop(3).joinToString("/")
        } else {
            "main"
        }

        return RepoCoordinates(owner = owner, repo = repo, branch = branch)
    }

    suspend fun downloadRepo(
        urlInput: String,
        customBranch: String? = null,
        onProgress: (String) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        val coords = parseRepoUrl(urlInput)
            ?: return@withContext DownloadResult(
                success = false,
                errorMessage = "Invalid GitHub repository format. Use: https://github.com/owner/repo"
            )

        val branch = customBranch?.takeIf { it.isNotBlank() } ?: coords.branch
        val owner = coords.owner
        val repo = coords.repo

        val projectsDir = File(context.filesDir, "projects")
        val timestamp = System.currentTimeMillis()
        val targetDir = File(projectsDir, "github_${owner}_${repo}_$timestamp")

        onProgress("Connecting to GitHub for $owner/$repo ($branch)...")

        // Try branch specified, then fallback to "master" if "main" returns 404
        val branchesToTry = if (branch == "main") listOf("main", "master") else listOf(branch)
        var stream: InputStream? = null
        var lastHttpCode = 0
        var successfulBranch = branch

        for (tryBranch in branchesToTry) {
            val downloadUrl = "https://github.com/$owner/$repo/archive/refs/heads/$tryBranch.zip"
            try {
                val connection = followRedirects(URL(downloadUrl))
                lastHttpCode = connection.responseCode
                if (lastHttpCode == 200) {
                    stream = connection.inputStream
                    successfulBranch = tryBranch
                    break
                }
            } catch (e: Exception) {
                // Try next branch or fail
            }
        }

        if (stream == null) {
            val msg = when (lastHttpCode) {
                404 -> "Repository not found or private ($owner/$repo). Please check spelling."
                403 -> "GitHub API rate limit exceeded. Please wait a moment."
                else -> "Failed to download repository ($owner/$repo, HTTP $lastHttpCode)."
            }
            return@withContext DownloadResult(success = false, errorMessage = msg)
        }

        onProgress("Extracting repository files safely...")
        val extractor = SafeZipExtractor()
        val extraction = stream.use { inStream ->
            extractor.extract(inStream, targetDir) { count ->
                onProgress("Extracting: $count files unpacked...")
            }
        }

        if (!extraction.success) {
            return@withContext DownloadResult(
                success = false,
                errorMessage = "Extraction failed: ${extraction.errorMessage}"
            )
        }

        // GitHub zips contain a root folder: owner-repo-commit/
        // If there is only one subdirectory, treat that as the effective root
        val subFiles = targetDir.listFiles() ?: emptyArray()
        val effectiveRoot = if (subFiles.size == 1 && subFiles[0].isDirectory) {
            subFiles[0]
        } else {
            targetDir
        }

        val tree = extractor.buildFileTree(effectiveRoot)
        val project = Project(
            id = "github_${owner}_${repo}_$timestamp",
            name = "$owner/$repo ($successfulBranch)",
            rootPath = effectiveRoot.absolutePath,
            isBundledSample = false,
            totalFiles = extraction.totalFiles,
            includedFilesCount = extraction.totalFiles
        )

        DownloadResult(
            success = true,
            project = project,
            fileTree = tree
        )
    }

    private fun followRedirects(url: URL): HttpURLConnection {
        var currentUrl = url
        var conn: HttpURLConnection
        var redirectCount = 0

        while (true) {
            conn = currentUrl.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "CodeFlowStudio-Android")

            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            ) {
                redirectCount++
                if (redirectCount > 5) throw IllegalStateException("Too many redirects")
                val newUrlStr = conn.getHeaderField("Location") ?: break
                currentUrl = URL(newUrlStr)
            } else {
                break
            }
        }
        return conn
    }
}
