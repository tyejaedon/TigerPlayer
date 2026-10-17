package com.tigerplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTestTagsTest {

    @Test
    fun `all test tags are unique and non-empty`() {
        val tags = listOf(
            KeyboardTestTags.LIBRARY_SEARCH_FIELD,
            KeyboardTestTags.PLAYLIST_CREATE_NAME_FIELD,
            KeyboardTestTags.HOME_SEARCH_FIELD,
            KeyboardTestTags.NAVIDROME_SERVER_URL,
            KeyboardTestTags.NAVIDROME_USERNAME,
            KeyboardTestTags.NAVIDROME_PASSWORD,
            KeyboardTestTags.NAVIDROME_LOGIN_CARD,
            KeyboardTestTags.YOUTUBE_SEARCH_FIELD,
            KeyboardTestTags.CLOUD_SEARCH_FIELD,
            KeyboardTestTags.STATS_SEARCH_FIELD
        )

        tags.forEach { tag ->
            assertTrue("Tag must not be blank", tag.isNotBlank())
        }

        val uniqueTags = tags.toSet()
        assertEquals("All test tags must be unique", tags.size, uniqueTags.size)
    }
}
