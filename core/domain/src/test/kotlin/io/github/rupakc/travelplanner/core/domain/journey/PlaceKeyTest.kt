package io.github.rupakc.travelplanner.core.domain.journey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectors from the shared fixture `place_key_cases.json` (Trips-API, Place key). */
class PlaceKeyTest {
    @Test
    fun `every golden vector passes`() {
        val root =
            Json.parseToJsonElement(
                checkNotNull(javaClass.getResourceAsStream("/journey/place_key_cases.json")).bufferedReader().readText(),
            ).jsonObject
        val cases = root["cases"]!!.jsonArray
        assertTrue(cases.size >= 20)
        for (el in cases) {
            val c = el.jsonObject
            val name = c["name"]!!.jsonPrimitive.content
            assertEquals(name, c["key"]!!.jsonPrimitive.content, PlaceKey.of(c["kind"]!!.jsonPrimitive.content, name))
        }
    }

    @Test
    fun `named examples`() {
        assertEquals("activity:pasteis-de-belem", PlaceKey.of("activity", "Pastéis de Belém!"))
        assertEquals("place:x-fa440c98", PlaceKey.of("place", "東京タワー"))
        assertEquals("other:x", PlaceKey.of("other", ""))
    }

    @Test
    fun `keys always match the server pattern`() {
        val re = Regex("^[a-z]+:[a-z0-9-]{1,60}$")
        listOf("Straße", "ताज महल", "x".repeat(200) + "東", "---", "Ørestad").forEach {
            assertTrue(it, re.matches(PlaceKey.of("place", it)))
        }
    }
}
