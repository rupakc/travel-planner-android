@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length") // fixtures ported verbatim from the web tests

package io.github.rupakc.travelplanner.core.domain.results

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Every case of `verification.test.js` and `orderSectionByCity.test.js` (names: "describe - it"). */
class SectionJsonTest {
    @Test
    fun `clearPendingVerification - clears pending on sections still pending, copying not mutating`() {
        val pendingV = obj("level" to "unverified", "summary" to "Checking which links can be found online…", "pending" to true)
        val results =
            mapOf(
                "hotels" to obj("results" to listOf(mapOf("name" to "A")), "verification" to pendingV),
                "flights" to obj("results" to emptyList<Any>(), "verification" to mapOf("level" to "verified", "pending" to false)),
                "visa" to obj("requirement" to emptyMap<String, Any>()),
            )
        val out = clearPendingVerification(results)
        assertNotSame(results, out)
        assertNotSame(results["hotels"], out["hotels"])
        val v = out.getValue("hotels")["verification"]!!.jsonObject
        assertEquals("false", v["pending"]!!.jsonPrimitive.content)
        assertFalse(v["summary"]!!.jsonPrimitive.content.contains("Checking"))
        assertSame(results.getValue("hotels")["results"], out.getValue("hotels")["results"])
        assertEquals("true", results.getValue("hotels")["verification"]!!.jsonObject["pending"]!!.jsonPrimitive.content)
        assertSame(results["flights"], out["flights"])
        assertSame(results["visa"], out["visa"])
    }

    @Test
    fun `clearPendingVerification - returns the same object when nothing is pending`() {
        val results = mapOf("flights" to obj("verification" to mapOf("pending" to false)))
        assertSame(results, clearPendingVerification(results))
        val empty = emptyMap<String, JsonObject>()
        assertSame(empty, clearPendingVerification(empty))
    }

    @Test
    fun `stripLinkEvidence - drops link_verified and link_checked from saved items without mutating`() {
        val act = obj("name" to "Tour", "link_verified" to true, "link_checked" to true, "booking_url" to "https://x")
        val hotel = obj("name" to "H", "link_verified" to false)
        val plain = obj("name" to "Plain")
        val selections =
            obj(
                "hotel" to hotel,
                "activities" to listOf(act, plain),
                "places_to_see" to emptyList<Any>(),
                "itinerary_notes" to mapOf("a" to "note"),
                "flight" to null,
            )
        val out = stripLinkEvidence(selections)
        assertEquals(obj("name" to "Tour", "booking_url" to "https://x"), out["activities"]!!.jsonArray[0])
        assertSame(plain, out["activities"]!!.jsonArray[1])
        assertEquals(obj("name" to "H"), out["hotel"])
        assertSame(selections["itinerary_notes"], out["itinerary_notes"])
        assertEquals(selections["flight"], out["flight"])
        assertEquals("true", act["link_verified"]!!.jsonPrimitive.content)
        assertEquals("false", hotel["link_verified"]!!.jsonPrimitive.content)
        // Typed selections strip the same keys.
        assertFalse(stripLinkEvidence(selectionsOf(selections)).activities[0].raw.containsKey("link_verified"))
    }

    private val destinations = listOf("Amsterdam", "New York", "San Francisco")

    private fun names(o: JsonObject, field: String) = (o[field] as JsonArray).map { (it as JsonObject)["name"]?.jsonPrimitive?.content }

    @Test
    fun `orderSectionByCity - does not throw and sorts city-tagged lists into journey order`() {
        val data =
            obj(
                "results" to
                    listOf(
                        mapOf("name" to "c", "city" to "San Francisco"),
                        mapOf("name" to "a", "city" to "Amsterdam"),
                        mapOf(
                            "name" to "b",
                            "city" to "New York, USA",
                        ),
                    ),
            )
        assertEquals(listOf("a", "b", "c"), names(orderSectionByCity(data, destinations), "results"))
    }

    @Test
    fun `orderSectionByCity - is a stable sort within the same city and puts unknown cities last`() {
        val data =
            obj(
                "hotels" to
                    listOf(
                        mapOf("name" to "x", "city" to "Atlantis"),
                        mapOf("name" to "h1", "city" to "Amsterdam"),
                        mapOf("name" to "h2", "city" to "Amsterdam"),
                    ),
            )
        assertEquals(listOf("h1", "h2", "x"), names(orderSectionByCity(data, destinations), "hotels"))
    }

    @Test
    fun `orderSectionByCity - returns input untouched for single-city trips and non-object data`() {
        val data = obj("results" to listOf(mapOf("city" to "Rome")))
        assertSame(data, orderSectionByCity(data, listOf("Rome")))
        assertSame(data, orderSectionByCity(data, null))
        // Null / array section data cannot reach this typed function.
    }

    @Test
    fun `orderSectionByCity - handles array-valued city fields and missing cities without crashing`() {
        val data =
            obj(
                "places" to
                    listOf(
                        mapOf("name" to "p2", "city" to listOf("New York", "NYC")),
                        mapOf("name" to "p3"),
                        mapOf("name" to "p1", "city" to "amsterdam"),
                    ),
            )
        assertEquals(listOf("p1", "p2", "p3"), names(orderSectionByCity(data, destinations), "places"))
    }
}
