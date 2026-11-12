package io.github.rupakc.travelplanner.feature.trips.offline

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackViewTest {
    private fun pack(json: String) = TravelJson.decodeFromString(OfflinePack.serializer(), json)

    private val full =
        """
        {"version":1,"trip_id":12,"title":"Lisbon","checkins":["2027-04-01"],
         "emergency":{"source":"ai","as_of":"2027-03-01T00:00:00Z","cities":[{"city":"Lisbon",
           "emergency_numbers":{"general":"112","police":"+351 21 765 4242","note":"none","fire":null},
           "embassy":{"name":"US Embassy","phone":"+351 21 727 3300"},
           "hospitals":[{"name":"Hospital da Luz"},{"phone":"1"}]}]},
         "selections":{"itinerary":{"days":[{"day_number":1,"date":"2027-04-01","slots":[
            {"day_number":1,"time_of_day":"morning","activity":"Alfama walk","date":"2027-04-01"}]}]}}}
        """.trimIndent()

    @Test
    fun `emergency numbers keep only dialable values and a tel uri`() {
        val view = PackView.of(pack(full))
        val e = view.emergency!!
        assertTrue(e.aiChecked)
        val city = e.cities.single()
        assertEquals(listOf("general", "police"), city.numbers.map { it.kind })
        assertEquals("tel:+351217654242", city.numbers[1].telUri)
        assertEquals("US Embassy", city.embassy!!.name)
        assertEquals(listOf("Hospital da Luz"), city.hospitals.map { it.name })
        assertNull(city.hospitals.single().phone)
    }

    @Test
    fun `static source or a missing date is the reference list`() {
        val v = PackView.of(pack("""{"emergency":{"source":"ai","as_of":null,"cities":[{"city":"X","emergency_numbers":{"general":"112"}}]}}"""))
        assertFalse(v.emergency!!.aiChecked)
        assertNull(PackView.of(pack("""{"emergency":{"source":"static","cities":[]}}""")).emergency)
    }

    @Test
    fun `the itinerary comes from the pack selections with no network`() {
        val view = PackView.of(pack(full))
        assertEquals(1, view.days.size)
        assertEquals("Alfama walk", view.days.single().slots.single().slot.activity)
        assertTrue(view.kb >= 1)
        assertTrue(PackView.of(pack("""{"title":"x"}""")).days.isEmpty())
    }

    @Test
    fun `converter multiplies to local and divides to home and rounds to the currency`() {
        val us = Locale.US
        assertEquals("92", PackConverter.convert("100", 0.92, true, "EUR", us))
        assertEquals("108.7", PackConverter.convert("100", 0.92, false, "USD", us))
        assertEquals("15,000", PackConverter.convert("100", 150.0, true, "JPY", us))
        assertNull(PackConverter.convert("abc", 0.92, true, "EUR", us))
        assertNull(PackConverter.convert("5", null, true, "EUR", us))
        assertNull(PackConverter.convert("5", 0.0, true, "EUR", us))
        assertNull(PackConverter.convert("-5", 0.9, true, "EUR", us))
    }
}
