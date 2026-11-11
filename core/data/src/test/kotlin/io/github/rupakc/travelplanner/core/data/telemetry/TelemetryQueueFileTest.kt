package io.github.rupakc.travelplanner.core.data.telemetry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class TelemetryQueueFileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val queueFile get() = File(context.noBackupFilesDir, "telemetry/queue.json")
    private lateinit var queue: TelemetryQueueFile

    @Before fun setUp() {
        File(context.noBackupFilesDir, "telemetry").deleteRecursively()
        queue = TelemetryQueueFile(context, TestAppDispatchers(Dispatchers.IO))
    }

    @Test
    fun `append then claim round-trips`() =
        runBlocking {
            queue.append(listOf(event("ui_tap", props = mapOf("n" to 2L, "t" to "x", "b" to true, "d" to 1.5)), event("screen_view", sid = "s2")))
            val got = queue.claim(NOW_MS)
            assertEquals(listOf("ui_tap", "screen_view"), got.map { it.event })
            assertEquals(mapOf<String, Any>("n" to 2L, "t" to "x", "b" to true, "d" to 1.5), got[0].props)
            assertEquals("s2", got[1].sessionId)
            assertEquals("/search", got[0].screen)
        }

    @Test
    fun `claim removes the file so a second claim is empty`() =
        runBlocking {
            queue.append(listOf(event()))
            assertTrue(queueFile.exists())
            assertEquals(1, queue.claim(NOW_MS).size)
            assertFalse(queueFile.exists())
            assertTrue(queue.claim(NOW_MS).isEmpty())
        }

    @Test
    fun `append during a claimed drain is not overwritten`() =
        runBlocking {
            val a = event("ui_tap", ts = NOW_MS - 2000)
            queue.append(listOf(a))
            val claimed = queue.claim(NOW_MS)
            queue.append(listOf(event("ui_toggle", ts = NOW_MS - 1000))) // B arrives during the drain
            queue.append(claimed) // the unsent rest returns
            assertEquals(listOf("ui_tap", "ui_toggle"), queue.claim(NOW_MS).map { it.event })
        }

    @Test
    fun `events older than 7 days are dropped`() =
        runBlocking {
            queue.append(listOf(event("ui_tap", ts = NOW_MS - 8L * 24 * 3600 * 1000), event("ui_toggle", ts = NOW_MS - 3600 * 1000)))
            assertEquals(listOf("ui_toggle"), queue.claim(NOW_MS).map { it.event })
        }

    @Test
    fun `keeps only the newest 500`() =
        runBlocking {
            queue.append((1..510).map { event(ts = NOW_MS - 1000 + it, props = mapOf("i" to it.toLong())) })
            val got = queue.claim(NOW_MS)
            assertEquals(500, got.size)
            assertEquals(11L, got.first().props["i"])
        }

    @Test
    fun `corrupt file yields empty and is deleted`() =
        runBlocking {
            queueFile.parentFile!!.mkdirs()
            queueFile.writeText("{not json")
            assertTrue(queue.claim(NOW_MS).isEmpty())
            assertFalse(queueFile.exists())
        }
}
