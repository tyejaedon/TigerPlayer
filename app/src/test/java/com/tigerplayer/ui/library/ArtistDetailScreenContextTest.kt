package com.tigerplayer.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying that [ArtistDetailScreen] and Compose UI screens use
 * standard Jetpack Compose [androidx.compose.ui.platform.LocalContext] rather than
 * Glance App Widget [androidx.glance.LocalContext].
 */
class ArtistDetailScreenContextTest {

    private fun getSourceDirectory(): File {
        return listOf(
            File("app/src/main/java"),
            File("src/main/java")
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Source directory not found from working directory: ${System.getProperty("user.dir")}")
    }

    @Test
    fun `ArtistDetailScreen does not import androidx glance LocalContext`() {
        val srcDir = getSourceDirectory()
        val artistDetailFile = File(srcDir, "com/tigerplayer/ui/library/ArtistDetailScreen.kt")
        assertTrue("ArtistDetailScreen.kt must exist", artistDetailFile.exists())

        val content = artistDetailFile.readText()
        assertFalse(
            "ArtistDetailScreen.kt must not import androidx.glance.LocalContext (issue #379)",
            content.contains("import androidx.glance.LocalContext")
        )
        assertTrue(
            "ArtistDetailScreen.kt must import androidx.compose.ui.platform.LocalContext",
            content.contains("import androidx.compose.ui.platform.LocalContext")
        )
    }

    @Test
    fun `no Compose UI screens mistakenly import androidx glance LocalContext`() {
        val srcDir = getSourceDirectory()
        val uiDir = File(srcDir, "com/tigerplayer/ui")
        assertTrue("com/tigerplayer/ui directory must exist", uiDir.exists() && uiDir.isDirectory)

        val offendingFiles = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                text.contains("import androidx.glance.LocalContext")
            }
            .map { it.relativeTo(srcDir).path }
            .toList()

        assertTrue(
            "Found Compose UI files importing androidx.glance.LocalContext: $offendingFiles",
            offendingFiles.isEmpty()
        )
    }
}
