package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.testing.fake.FakePlanJourney
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanJourneyTest {
    private val fakes = FakePlanJourney()
    private val journey = fakes.journey

    private val sections =
        buildJsonObject {
            put(
                "forex",
                buildJsonObject {
                    put("rate", 0.9)
                    put("verification", buildJsonObject { put("level", "verified") })
                },
            )
            put("weather", buildJsonObject { put("error", "boom") })
            put("flights", buildJsonObject { put("x", 1) })
        }

    @Test
    fun `snapshot body has only allowed keys and no verification`() =
        runTest(timeout = 30.seconds) {
            journey.onPlanSaved(7, sections)
            val (id, body) = fakes.packs.snapshots.single()
            assertEquals(7L, id)
            assertEquals(setOf("forex"), body.keys)
            assertFalse(body.toString().contains("verification"))
        }

    @Test
    fun `skipped when the trips flag is off`() =
        runTest(timeout = 30.seconds) {
            fakes.flags.flags = JourneyFlags(trips = false)
            journey.onPlanSaved(7, sections)
            assertTrue(fakes.packs.snapshots.isEmpty())
            assertTrue(fakes.outbox.queued.value.isEmpty())
            assertNull(journey.completeness(PlanDraft(planId = 7)))
        }

    @Test
    fun `413 is swallowed and nothing queues`() =
        runTest(timeout = 30.seconds) {
            fakes.packs.snapshotResult = AppResult.Failure(AppError.Client(413, null))
            journey.onPlanSaved(7, sections)
            assertTrue(fakes.outbox.queued.value.isEmpty())
        }

    @Test
    fun `no sections means no call`() =
        runTest(timeout = 30.seconds) {
            journey.onPlanSaved(7, null)
            journey.onPlanSaved(7, JsonObject(emptyMap()))
            assertTrue(fakes.packs.snapshots.isEmpty())
        }

    @Test
    fun `completeness is computed when trips are on and the meter event carries no PII`() =
        runTest(timeout = 30.seconds) {
            assertNotNull(journey.completeness(PlanDraft()))
            journey.meterViewed("drawer", 40)
            assertEquals(listOf("completeness_view" to mapOf<String, Any?>("surface" to "drawer", "percent" to 40)), fakes.telemetry.events)
        }
}
