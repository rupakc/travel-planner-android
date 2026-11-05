package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** App-only results utilities (no web counterpart): section order, text filter, search key. */
class ResultsExtrasTest {
    @Test
    fun `section arrangement puts visible features first and never drops sections`() {
        val all = SectionArrangement.arrange(null)
        assertEquals(SectionId.entries, all.map { it.id })
        assertTrue(all.all { it.relevant })
        val arranged = SectionArrangement.arrange(setOf(FeatureId.VISA_CHECKER, FeatureId.PACKING_LIST))
        assertEquals(SectionId.entries.size, arranged.size)
        val firstHidden = arranged.indexOfFirst { !it.relevant }
        assertTrue(arranged.take(firstHidden).all { it.relevant })
        assertTrue(!arranged.first { it.id == SectionId.EVENTS }.relevant)
        assertTrue(arranged.first { it.id == SectionId.VISA }.relevant)
    }

    @Test
    fun `text filter narrows list sections and matches others as a whole`() {
        val raw = obj("""{"results":[{"name":"Café Lisboa","city":"Lisbon"},{"name":"Tower","city":"Belém"}]}""")
        val hotels = hotelsOf(raw)
        val (filtered, count) = ResultsTextFilter.filter(hotels, raw, "cafe")
        assertEquals(1, count)
        assertEquals(listOf("Café Lisboa"), (filtered as HotelsData).results.map { it.name })
        assertEquals(1, ResultsTextFilter.filter(hotels, raw, "BELEM").second)
        val visaRaw = obj("""{"requirement":{"visa_type":"visa-free","notes":"90 days"}}""")
        val visa = SectionDecoder.decode(SectionId.VISA, visaRaw)
        assertEquals(1, ResultsTextFilter.filter(visa, visaRaw, "visa-free").second)
        assertEquals(0, ResultsTextFilter.filter(visa, visaRaw, "tokyo").second)
        assertTrue(ResultsTextFilter.matches(JsonPrimitive("x"), " "))
    }

    @Test
    fun `search key ignores case, spacing and list order but not the trip`() {
        val a = search(destination = "Tokyo").copy(interests = listOf("food", "art"))
        val b = a.copy(destination = " tokyo ", interests = listOf("Art", "food", "food"))
        assertEquals(searchKey(a), searchKey(b))
        assertNotEquals(searchKey(a), searchKey(a.copy(departureDate = "2026-04-02")))
        assertEquals(24, searchKey(a)!!.length)
        assertNull(searchKey(null))
    }
}
