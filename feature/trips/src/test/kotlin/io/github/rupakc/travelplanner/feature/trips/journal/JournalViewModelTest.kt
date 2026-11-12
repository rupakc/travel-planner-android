package io.github.rupakc.travelplanner.feature.trips.journal

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.trips.Entry
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceResponse
import io.github.rupakc.travelplanner.core.model.trips.TripPlace
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeJournalRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeOutbox
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JournalViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeJournalRepository()
    private val outbox = FakeOutbox()
    private val telemetry = FakeTelemetry()
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2026-11-12T23:30:00Z")

            override fun zone() = ZoneOffset.ofHours(9)
        }

    private val plans =
        FakePlansRepository(
            listOf(
                Plan(
                    id = 12,
                    name = "Tokyo",
                    selections =
                        PlanSelections(
                            itinerarySlots = listOf(ItinerarySlot(timeOfDay = "morning", activity = "Senso-ji", dayNumber = 1, date = "2026-11-13")),
                        ),
                ),
            ),
        )

    private fun vm(): JournalViewModel =
        JournalViewModel(SavedStateHandle(mapOf("tripId" to 12L)), repo, plans, outbox, telemetry, clock).also {
            var n = 0
            it.newClientId = { "cid-${++n}" }
        }

    private fun saved(id: Long, body: String) = Entry(id, "srv-$id", "2026-11-13", body, createdAt = "2026-11-13T01:00:00Z")

    @Test
    fun `loads entries and the itinerary places`() =
        runTest(timeout = 30.seconds) {
            repo.entriesResult = AppResult.Success(listOf(saved(1, "hello")))
            val s = vm().state.value
            assertFalse(s.loading)
            assertEquals(listOf("hello"), s.rows.map { it.entry.body })
            assertEquals(listOf("activity:senso-ji"), s.places.map { it.key })
        }

    @Test
    fun `an entry is sent with the local day and a client id, and telemetry has no text`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.createEntryResult = AppResult.Success(saved(5, "Great day"))
            vm.onEvent(JournalEvent.DraftChanged("  Great day  "))
            vm.onEvent(JournalEvent.MoodChanged("great"))
            vm.onEvent(JournalEvent.Save)
            val sent = repo.created.single()
            assertEquals("Great day", sent.body)
            assertEquals("2026-11-13", sent.day)
            assertEquals("great", sent.mood)
            assertEquals("cid-1", sent.clientId)
            assertEquals("", vm.state.value.draft)
            assertEquals(listOf("journal_action" to mapOf<String, Any?>("action" to "add", "queued" to false)), telemetry.events)
        }

    @Test
    fun `an empty or oversize body is never sent`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.onEvent(JournalEvent.DraftChanged("   "))
            vm.onEvent(JournalEvent.Save)
            vm.onEvent(JournalEvent.DraftChanged("x".repeat(4001)))
            vm.onEvent(JournalEvent.Save)
            assertTrue(repo.created.isEmpty())
            assertTrue(outbox.queued.value.isEmpty())
        }

    @Test
    fun `offline the entry is queued with its client id and shown as waiting after the synced ones`() =
        runTest(timeout = 30.seconds) {
            repo.entriesResult = AppResult.Success(listOf(saved(1, "synced")))
            val vm = vm()
            repo.createEntryResult = AppResult.Failure(AppError.Network())
            vm.onEvent(JournalEvent.DraftChanged("offline note"))
            vm.onEvent(JournalEvent.Save)
            val queued = outbox.queued.value.single()
            assertEquals("POST", queued.method)
            assertEquals("api/trips/12/journal", queued.path)
            assertTrue(queued.body!!.contains("\"client_id\":\"cid-1\""))
            assertEquals(listOf("synced", "offline note"), vm.state.value.rows.map { it.entry.body })
            assertEquals(listOf(false, true), vm.state.value.rows.map { it.pending })
            assertEquals(true, telemetry.events.single().second["queued"])
        }

    @Test
    fun `a limit error shows the limit message and keeps the draft`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.createEntryResult = AppResult.Failure(AppError.Client(409, "x", code = "journal_limit"))
            vm.onEvent(JournalEvent.DraftChanged("one more"))
            vm.onEvent(JournalEvent.Save)
            assertEquals(JournalRules.Problem.JournalLimit, vm.state.value.failure?.problem)
            assertEquals("one more", vm.state.value.draft)
            assertFalse(vm.state.value.saving)
        }

    @Test
    fun `visiting a place sends the shared key, the local day and no pending marker`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.putPlaceResult = AppResult.Success(PutPlaceResponse(TripPlace("activity:senso-ji", "Senso-ji", "activity", visited = true)))
            vm.onEvent(JournalEvent.SetVisited("activity:senso-ji", true))
            val (key, req) = repo.placePuts.single()
            assertEquals("activity:senso-ji", key)
            assertEquals("Senso-ji", req.name)
            assertEquals(true, req.visited)
            assertEquals("2026-11-13", req.day)
            assertTrue(vm.state.value.places.single().visited)
            assertEquals(listOf("place_action" to mapOf<String, Any?>("action" to "visited", "kind" to "activity")), telemetry.events)
        }

    @Test
    fun `unvisit reads as not visited even though the server keeps visited_at`() =
        runTest(timeout = 30.seconds) {
            repo.placesResult =
                AppResult.Success(listOf(TripPlace("activity:senso-ji", "Senso-ji", "activity", visited = true, visitedAt = "2026-11-13T01:00:00Z")))
            val vm = vm()
            repo.putPlaceResult =
                AppResult.Success(PutPlaceResponse(TripPlace("activity:senso-ji", "Senso-ji", "activity", visited = false, visitedAt = "2026-11-13T01:00:00Z")))
            vm.onEvent(JournalEvent.SetVisited("activity:senso-ji", false))
            assertFalse(vm.state.value.places.single().visited)
            assertEquals("unvisited", telemetry.events.single().second["action"])
        }

    @Test
    fun `rating sends stars and tapping the same star clears with an explicit null`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.putPlaceResult = AppResult.Success(PutPlaceResponse(TripPlace("activity:senso-ji", "Senso-ji", "activity", stars = 4)))
            vm.onEvent(JournalEvent.Rate("activity:senso-ji", 4))
            assertEquals(4, repo.placePuts.last().second.stars)
            assertEquals(mapOf<String, Any?>("action" to "rate", "kind" to "activity", "stars" to 4), telemetry.events.last().second)
            repo.putPlaceResult = AppResult.Success(PutPlaceResponse(TripPlace("activity:senso-ji", "Senso-ji", "activity", stars = null)))
            vm.onEvent(JournalEvent.Rate("activity:senso-ji", 4))
            val clear = repo.placePuts.last().second
            assertNull(clear.stars)
            assertTrue(clear.clearStars)
            assertTrue(clear.toJsonObject().toString().contains("\"stars\":null"))
            assertEquals("unrate", telemetry.events.last().second["action"])
        }

    @Test
    fun `an offline place change is queued as the full state under one key per place`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.putPlaceResult = AppResult.Failure(AppError.Network())
            vm.onEvent(JournalEvent.SetVisited("activity:senso-ji", true))
            vm.onEvent(JournalEvent.Rate("activity:senso-ji", 5))
            val queued = outbox.queued.value.single()
            assertEquals("PUT", queued.method)
            assertEquals("api/trips/12/places/activity:senso-ji", queued.path)
            assertTrue(queued.body!!.contains("\"visited\":true"))
            assertTrue(queued.body!!.contains("\"stars\":5"))
            assertTrue(vm.state.value.places.single().pending)
            assertEquals(5, vm.state.value.places.single().stars)
        }

    @Test
    fun `a places limit failure rolls the place back`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.putPlaceResult = AppResult.Failure(AppError.Client(409, "x", code = "places_limit"))
            vm.onEvent(JournalEvent.SetVisited("activity:senso-ji", true))
            assertFalse(vm.state.value.places.single().visited)
            assertEquals(JournalRules.Problem.PlacesLimit, vm.state.value.failure?.problem)
            assertTrue(telemetry.events.isEmpty())
        }

    @Test
    fun `a note longer than 500 is cut`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.putPlaceResult = AppResult.Success(PutPlaceResponse(TripPlace("activity:senso-ji", "Senso-ji", "activity")))
            vm.onEvent(JournalEvent.SaveNote("activity:senso-ji", "n".repeat(600)))
            assertEquals(500, repo.placePuts.single().second.note?.length)
            assertTrue(telemetry.events.isEmpty())
        }
}
