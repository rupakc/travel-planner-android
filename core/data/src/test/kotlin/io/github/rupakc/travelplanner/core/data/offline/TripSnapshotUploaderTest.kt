package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.testing.fake.FakeOfflinePackRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeOutbox
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSnapshotUploaderTest {
    private val packs = FakeOfflinePackRepository()
    private val outbox = FakeOutbox()
    private val uploader = TripSnapshotUploader(packs, outbox)

    private val sections =
        buildJsonObject {
            put(
                "forex",
                buildJsonObject {
                    put("rate", 0.9)
                    put("verification", buildJsonObject { put("level", "verified") })
                },
            )
            put("itinerary", buildJsonObject { put("x", 1) })
        }

    @Test
    fun `only allowed sections without verification are sent`() =
        runTest {
            uploader.put(5, sections)
            val body = packs.snapshots.single().second
            assertEquals(setOf("forex"), body.keys)
            assertFalse(body.toString().contains("verification"))
            assertTrue(outbox.queued.value.isEmpty())
        }

    @Test
    fun `413 and 422 are swallowed`() =
        runTest {
            for (status in listOf(413, 422)) {
                packs.snapshotResult = AppResult.Failure(AppError.Client(status, null))
                uploader.put(5, sections)
            }
            assertTrue(outbox.queued.value.isEmpty())
        }

    @Test
    fun `a network failure queues one upload per trip`() =
        runTest {
            packs.snapshotResult = AppResult.Failure(AppError.Network())
            uploader.put(5, sections)
            uploader.put(5, sections)
            val queued = outbox.queued.value.single()
            assertEquals("snapshot:5", queued.key)
            assertEquals("api/trips/5/snapshot", queued.path)
            assertEquals("PUT", queued.method)
        }

    @Test
    fun `nothing allowed means no call`() =
        runTest {
            uploader.put(5, buildJsonObject { put("itinerary", buildJsonObject {}) })
            assertTrue(packs.snapshots.isEmpty())
        }
}
