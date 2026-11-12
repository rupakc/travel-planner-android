package io.github.rupakc.travelplanner.feature.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarEncoderTest {
    @Test fun `an image over 2 MB is downscaled until it fits`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val out =
            AvatarEncoder.encode { edge, quality ->
                calls += edge to quality
                // 3 MB at the first sizes, 1 MB once the edge is 512 or less.
                ByteArray(if (edge > 512) 3 * 1024 * 1024 else 1024 * 1024)
            }
        assertTrue(out!!.size <= AvatarEncoder.MAX_BYTES)
        assertEquals(512, calls.last().first)
        assertTrue(calls.first().first > calls.last().first)
    }

    @Test fun `a small image is encoded once`() {
        var n = 0
        val out = AvatarEncoder.encode { _, _ -> ByteArray(10).also { n++ } }
        assertEquals(10, out!!.size)
        assertEquals(1, n)
    }

    @Test fun `unreadable image gives null`() {
        assertNull(AvatarEncoder.encode { _, _ -> null })
    }

    @Test fun `never fitting gives null`() {
        assertNull(AvatarEncoder.encode(maxBytes = 5) { _, _ -> ByteArray(10) })
    }
}
