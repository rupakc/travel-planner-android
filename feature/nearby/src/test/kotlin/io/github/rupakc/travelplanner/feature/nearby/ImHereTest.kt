package io.github.rupakc.travelplanner.feature.nearby

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlaceItem
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.nearby.NearbySection
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceResponse
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripPlace
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeJournalRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNearbyRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.FakeTripsRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val fixedClock =
    object : AppClock {
        override fun now(): Instant = Instant.parse("2026-11-12T10:00:00Z")

        override fun zone() = ZoneOffset.UTC
    }

/** A Near me ViewModel with no trips: the pre-"I'm here" behaviour. */
internal fun nearbyVm(
    location: LocationClient,
    repo: FakeNearbyRepository,
    settings: SettingsRepository,
    trips: FakeTripsRepository = FakeTripsRepository(),
    journal: FakeJournalRepository = FakeJournalRepository(),
    telemetry: FakeTelemetry = FakeTelemetry(),
) = NearbyViewModel(location, repo, settings, trips, journal, telemetry, fixedClock)

class ImHereTest {
    @get:Rule val main = MainDispatcherRule()

    private class Loc : LocationClient {
        override fun hasPermission() = true

        override suspend fun current(): LocationFix = LocationFix.Found(38.72345, -9.14567)
    }

    private val plain = NearbyCard("see-0-Cafe", "Cafe", null, null, grounded = false)
    private val grounded = NearbyCard("see-1", "Castelo", null, "https://maps.google.com/?cid=1", grounded = true)
    private val road = Trip(1, "Lisbon", phase = Phase.ON_THE_ROAD)
    private val planning = Trip(2, "Rome", phase = Phase.PLANNING)

    @Test
    fun `name is trimmed and capped at 120`() {
        assertEquals("a".repeat(120), ImHere.name("  " + "a".repeat(300) + " "))
        assertEquals("Cafe", ImHere.name(" Cafe "))
    }

    @Test
    fun `hidden for grounded items and when there is no trip`() {
        val trips = listOf(HereTrip(1, "Lisbon"))
        assertTrue(ImHere.available(plain, trips))
        assertFalse(ImHere.available(grounded, trips))
        assertFalse(ImHere.available(plain, emptyList()))
    }

    @Test
    fun `v2 items from the server are all grounded`() {
        val response =
            NearbyResponse(
                place = NearbyPlace(name = "Lisbon"),
                sections =
                    listOf(
                        NearbySection("see", items = listOf(NearbyPlaceItem(name = "A", placeId = "p1"), NearbyPlaceItem(name = "B", mapsUri = "https://m/1"))),
                    ),
            )
        assertTrue(sectionsOf(response).flatMap { it.cards }.all { it.grounded })
    }

    @Test
    fun `auto trip is the single on-the-road trip, else the user picks`() {
        assertEquals(1L, ImHere.autoTrip(listOf(planning, road))?.id)
        assertNull(ImHere.autoTrip(listOf(planning)))
        assertNull(ImHere.autoTrip(listOf(road, road.copy(id = 3))))
        assertEquals(listOf(1L, 2L), ImHere.pickable(listOf(planning, road)).map { it.id })
    }

    @Test
    fun `request uses the rounded device location and today`() {
        val r = ImHere.request(plain, "2026-11-12", 38.72345, -9.14567)
        assertEquals("nearby", r.kind)
        assertEquals(true, r.visited)
        assertEquals(38.723, r.lat!!, 0.0)
        assertEquals(-9.146, r.lng!!, 0.0)
        assertEquals("2026-11-12", r.day)
    }

    private fun loaded(trips: List<Trip>, item: NearbyPlaceItem): Triple<NearbyViewModel, FakeJournalRepository, FakeTelemetry> {
        val journal = FakeJournalRepository()
        journal.putPlaceResult = AppResult.Success(PutPlaceResponse(TripPlace("nearby:cafe")))
        val tripsRepo = FakeTripsRepository().also { it.listResult = AppResult.Success(TripsResponse(trips)) }
        val telemetry = FakeTelemetry()
        val repo = FakeNearbyRepository(AppResult.Success(NearbyResponse(NearbyPlace(name = "Lisbon"), listOf(NearbySection("eat", items = listOf(item))))))
        return Triple(nearbyVm(Loc(), repo, FakeSettingsRepository(), tripsRepo, journal, telemetry), journal, telemetry)
    }

    @Test
    fun `an on-the-road trip records a visited nearby place and emits place_action without coordinates`() =
        runTest(timeout = 30.seconds) {
            // A grounded server item is hidden, so a place_id-less item stands in for a future v1/offline item.
            val (vm, journal, telemetry) = loaded(listOf(planning, road), NearbyPlaceItem(name = "  Cafe  "))
            advanceUntilIdle()
            val card = vm.state.value.sections.flatMap { it.cards }.single()
            vm.onEvent(NearbyEvent.ImHere(card.key))
            advanceUntilIdle()
            assertEquals("nearby:cafe", journal.placePuts.single().first)
            assertEquals(true, journal.placePuts.single().second.visited)
            assertTrue(card.key in vm.state.value.markedKeys)
            val (event, props) = telemetry.events.single()
            assertEquals(TelemetryEvents.PLACE_ACTION, event)
            assertEquals(mapOf("action" to "visited", "kind" to "nearby"), props)
        }

    @Test
    fun `without an on-the-road trip it asks which trip, a grounded item does nothing`() =
        runTest(timeout = 30.seconds) {
            val (vm, journal, _) = loaded(listOf(planning), NearbyPlaceItem(name = "Cafe"))
            advanceUntilIdle()
            val card = vm.state.value.sections.flatMap { it.cards }.single()
            vm.onEvent(NearbyEvent.ImHere(card.key))
            assertEquals(card.key, vm.state.value.pickingFor)
            vm.onEvent(NearbyEvent.PickTrip(2))
            advanceUntilIdle()
            assertTrue(journal.calls.single().startsWith("putPlace:2:"))

            val (vm2, journal2, _) = loaded(listOf(road), NearbyPlaceItem(name = "Castelo", placeId = "p1"))
            advanceUntilIdle()
            vm2.onEvent(NearbyEvent.ImHere(vm2.state.value.sections.flatMap { it.cards }.single().key))
            assertTrue(journal2.calls.isEmpty())
        }

    @Test
    fun `no trip means no action and a failed save is reported`() =
        runTest(timeout = 30.seconds) {
            val (vm, journal, _) = loaded(emptyList(), NearbyPlaceItem(name = "Cafe"))
            advanceUntilIdle()
            assertTrue(vm.state.value.hereTrips.isEmpty())
            vm.onEvent(NearbyEvent.ImHere(vm.state.value.sections.flatMap { it.cards }.single().key))
            assertTrue(journal.calls.isEmpty())

            val (vm2, journal2, telemetry2) = loaded(listOf(road), NearbyPlaceItem(name = "Cafe"))
            journal2.putPlaceResult = AppResult.Failure(AppError.Unknown("x"))
            advanceUntilIdle()
            vm2.onEvent(NearbyEvent.ImHere(vm2.state.value.sections.flatMap { it.cards }.single().key))
            advanceUntilIdle()
            assertTrue(vm2.state.value.hereFailed)
            assertTrue(telemetry2.events.isEmpty())
        }
}
