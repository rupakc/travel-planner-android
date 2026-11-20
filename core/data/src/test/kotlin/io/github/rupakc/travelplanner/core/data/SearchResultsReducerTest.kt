package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.data.impl.SearchResultsReducer
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.model.stream.EventDecoder
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---- Place facts (optional `facts` section, web docs/Place-Facts.md) ----

    private val facts = Fixtures.searchEvents(Fixtures.SEARCH_FACTS).filterIsInstance<SearchEvent.Section>().filter { it.id == SectionId.PLACE_FACTS }
    private val crawlerFacts = facts.first { it.source == DataSource.CRAWLER }
    private val aiFacts = facts.first { it.source == DataSource.AI }

    private fun SearchResultsState.factCount() =
        (sections[SectionId.PLACE_FACTS]?.data as? PlaceFactsData)?.places?.sumOf { p -> p.groups.values.sumOf { it.size } }

    @Test
    fun `place facts have no status until they arrive and never count as progress`() {
        val start = SearchResultsState()
        assertNull(start.statuses[SectionId.PLACE_FACTS])
        assertFalse(SectionId.PLACE_FACTS in start.statuses)
        val s = SearchResultsReducer.reduce(start, crawlerFacts)
        assertEquals(SectionStatus.ENHANCING, s.statuses[SectionId.PLACE_FACTS])
        assertEquals(0, s.readyCount)
        assertEquals(DataSource.CRAWLER, s.sections[SectionId.PLACE_FACTS]?.source)
    }

    @Test
    fun `crawler facts enhance, then the AI pass finishes them`() {
        val s = listOf(crawlerFacts, aiFacts).fold(SearchResultsState(), SearchResultsReducer::reduce)
        assertEquals(SectionStatus.DONE, s.statuses[SectionId.PLACE_FACTS])
        assertEquals(DataSource.AI, s.sections[SectionId.PLACE_FACTS]?.source)
        assertEquals(4, s.factCount())
    }

    @Test
    fun `crawler facts replayed on the retry never replace AI facts, an AI error never replaces facts`() {
        val error = event("""{"type":"facts","data":{"error":"no_facts","error_code":"no_facts"},"source":"ai"}""")
        val s = listOf(crawlerFacts, aiFacts, crawlerFacts, error).fold(SearchResultsState(), SearchResultsReducer::reduce)
        assertEquals(DataSource.AI, s.sections[SectionId.PLACE_FACTS]?.source)
        assertEquals(4, s.factCount())
        assertEquals(SectionStatus.DONE, s.statuses[SectionId.PLACE_FACTS])
    }

    @Test
    fun `crawler-only facts are done at the stream end, a stream without facts has no facts status`() {
        val crawlerOnly = SearchResultsReducer.reduce(SearchResultsReducer.reduce(SearchResultsState(), crawlerFacts), SearchEvent.Done)
        assertEquals(SectionStatus.DONE, crawlerOnly.statuses[SectionId.PLACE_FACTS])
        assertEquals(StreamPhase.DONE, crawlerOnly.phase)
        val none = SearchResultsReducer.reduce(SearchResultsState(), SearchEvent.Done)
        assertFalse(SectionId.PLACE_FACTS in none.statuses)
        assertEquals(SectionId.CORE.size, none.readyCount)
    }

    @Test
    fun `the facts fixture folds with the agent status ignored`() {
        val s = Fixtures.searchEvents(Fixtures.SEARCH_FACTS).fold(SearchResultsState(), SearchResultsReducer::reduce)
        assertEquals(SectionStatus.DONE, s.statuses[SectionId.PLACE_FACTS])
        assertEquals(setOf(SectionId.TIPS, SectionId.PLACE_FACTS), s.sections.keys)
    }
}
