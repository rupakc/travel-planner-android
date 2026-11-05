package io.github.rupakc.travelplanner.core.model

import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LenientSerializersTest {
    @Test
    fun `numbers may arrive as strings, words or nothing`() {
        val a =
            TravelJson.decodeFromString(
                Activity.serializer(),
                """{"name":"x","price_usd":"$1,200.50","rating":"varies","review_count":12.0}""",
            )
        assertEquals(1200.5, a.priceUsd!!, 0.0)
        assertNull(a.rating)
        assertEquals(12, a.reviewCount)
        val b = TravelJson.decodeFromString(Activity.serializer(), """{"name":"x","price_usd":null,"accessibility_features":"step-free"}""")
        assertNull(b.priceUsd)
        assertEquals(listOf("step-free"), b.accessibilityFeatures)
    }

    @Test
    fun `sqlite 0 or 1 booleans decode`() {
        val w = TravelJson.decodeFromString(FareWatch.serializer(), """{"id":3,"email_enabled":1,"status":"active","unknown":true}""")
        assertEquals(true, w.emailEnabled)
    }
}
