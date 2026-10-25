package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.GitHubRepoDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubRepoDownloaderTest {

    @Test
    fun testParseStandardHttpsUrl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = GitHubRepoDownloader(context)

        val coords = downloader.parseRepoUrl("https://github.com/facebook/react")
        assertNotNull(coords)
        assertEquals("facebook", coords!!.owner)
        assertEquals("react", coords.repo)
        assertEquals("main", coords.branch)
    }

    @Test
    fun testParseUrlWithBranch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = GitHubRepoDownloader(context)

        val coords = downloader.parseRepoUrl("https://github.com/torvalds/linux/tree/master")
        assertNotNull(coords)
        assertEquals("torvalds", coords!!.owner)
        assertEquals("linux", coords.repo)
        assertEquals("master", coords.branch)
    }

    @Test
    fun testParseShortHandOwnerRepo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = GitHubRepoDownloader(context)

        val coords = downloader.parseRepoUrl("square/retrofit")
        assertNotNull(coords)
        assertEquals("square", coords!!.owner)
        assertEquals("retrofit", coords.repo)
    }

    @Test
    fun testParseInvalidUrlReturnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = GitHubRepoDownloader(context)

        assertNull(downloader.parseRepoUrl("invalid-string"))
        assertNull(downloader.parseRepoUrl(""))
    }
}
