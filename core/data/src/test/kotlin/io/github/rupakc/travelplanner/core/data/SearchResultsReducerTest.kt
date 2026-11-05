package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.data.impl.SearchResultsReducer
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.model.stream.EventDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultsReducerTest {
    private fun event(json: String) = EventDecoder.decodeSearch(json)!!

    private val static = event("""{"type":"visa","data":{"requirement":{"visa_type":"visa-free"}},"source":"static"}""")
    private val ai = event("""{"type":"visa","data":{"requirement":{"visa_type":"e-visa"}},"source":"ai"}""")
    private val aiError = event("""{"type":"visa","data":{"error":"timed out"},"source":"ai"}""")

    private fun SearchResultsState.visa() = (sections[SectionId.VISA]?.data as VisaData).requirement?.visaType

    @Test
    fun `an AI error after static data keeps the static data`() {
        val s = listOf(static, aiError).fold(SearchResultsState(), SearchResultsReducer::reduce)
        assertEquals("visa-free", s.visa())
        assertEquals(DataSource.STATIC, s.sections[SectionId.VISA]?.source)
    }

    @Test
    fun `static data replayed on the retry does not replace AI data`() {
        val s = listOf(static, ai, static).fold(SearchResultsState(), SearchResultsReducer::reduce)
        assertEquals("e-visa", s.visa())
        assertEquals(SectionStatus.DONE, s.statuses[SectionId.VISA])
    }
}
