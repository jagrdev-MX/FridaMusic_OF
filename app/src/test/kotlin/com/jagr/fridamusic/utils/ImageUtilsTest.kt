package com.jagr.fridamusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun playerSizeDoesNotPromoteHqThumbnailToOptionalMaxRes() {
        val original = "https://i.ytimg.com/vi/videoId/hqdefault.jpg"

        assertEquals(original, original.resize(width = 1200, height = 1200))
    }

    @Test
    fun existingMaxResThumbnailIsPreservedForPlayerSize() {
        val original = "https://i.ytimg.com/vi/videoId/maxresdefault.jpg"

        assertEquals(original, original.resize(width = 1200, height = 1200))
    }
}
