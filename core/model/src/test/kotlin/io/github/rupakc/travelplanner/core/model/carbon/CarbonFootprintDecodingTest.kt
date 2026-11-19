package io.github.rupakc.travelplanner.core.model.carbon

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.json.decodeRawBacked
import io.github.rupakc.travelplanner.core.model.section.Flight
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `/carbon/trip` answer decodes as documented, odd entries are dropped, and the TIM figure is read from the flight. */
class CarbonFootprintDecodingTest {
    private val documented =
        """{"total_kg":245.1,"per_person_kg":122.5,"travellers":2,"nights":4,"diet":"mixed","cabin":"economy",
        "categories":[{"key":"flights","kg":159.4,"share":0.6504,"included":true},"junk",{"key":"stays","kg":0,"share":0,"included":false}],
        "lines":[{"category":"flights","kind":"flight_factor","label":"Flight LHR–CDG (348 km)","kg":159.4,"method":"factor",
          "source":"desnz_2026","params":{"route":"LHR–CDG","km":348,"cabin":"economy"},
          "crawler_source":{"name":"DESNZ 2027","url":"https://www.gov.uk/x","as_of":"2027-06-10","licence":"OGL v3.0","source_id":"od-desnz","year":2027}},
          {"category":"food","kind":"meals","label":"Meals","kg":57.6,"method":"estimate","crawler_source":"nonsense"}],
        "comparisons":{"car_km":1477,"annual_budget_kg":2000,"budget_share":0.0613},
        "lower_carbon":[{"route":"LHR–CDG","from":"LHR","to":"CDG","rail_km":417,"legs":1,"flight_kg":159.4,"rail_kg":9.5,"saving_kg":149.9,"factor":"rail_international"}],
        "sources":[{"id":"desnz_2026","name":"UK DESNZ","url":"https://www.gov.uk","licence":"OGL v3.0","attribution":"Contains public sector information"}],
        "assumptions":[{"code":"rooms_default","text":"1 room(s): one per two travellers.","params":{"rooms":1}}],
        "attribution":["Contains public sector information licensed under the Open Government Licence v3.0"],
        "factors_version":"2026.1","crawler_sources":[{"name":"DESNZ 2027","as_of":"2027-06-10"},7]}"""

    @Test
    fun `the documented answer decodes and odd entries are dropped`() {
        val f = TravelJson.decodeFromString(CarbonFootprint.serializer(), documented)
        assertEquals(245.1, f.totalKg!!, 0.0)
        assertEquals(2, f.travellers)
        assertEquals(listOf("flights", "stays"), f.categories.map { it.key })
        assertEquals(2, f.lines.size)
        assertEquals("DESNZ 2027", f.lines[0].crawlerSource?.name)
        assertEquals(2027, f.lines[0].crawlerSource?.year)
        assertNull(f.lines[1].crawlerSource)
        assertEquals(149.9, f.lowerCarbon.single().savingKg!!, 0.0)
        assertEquals(1477.0, f.comparisons?.carKm!!, 0.0)
        assertEquals("rooms_default", f.assumptions.single().code)
        assertEquals(1, f.crawlerSources.size)
        assertEquals("2026.1", f.factorsVersion)
    }

    @Test
    fun `an older backend without crawler fields decodes to empty lists`() {
        val f = TravelJson.decodeFromString(CarbonFootprint.serializer(), """{"total_kg":10,"categories":"oops"}""")
        assertTrue(f.crawlerSources.isEmpty())
        assertTrue(f.categories.isEmpty())
        assertNull(f.lines.firstOrNull())
    }

    @Test
    fun `a flight's TIM emissions come from its raw JSON`() {
        val json =
            Json.parseToJsonElement(
                """{"price_usd":180,"emissions":{"kg_co2e_per_pax":134,"cabin":"economy",
                "by_class":{"economy":134,"premiumEconomy":134,"business":201,"first":201},"attribution_url":"https://travelimpactmodel.org"}}""",
            )
        val e = decodeRawBacked(Flight.serializer(), json)!!.timEmissions()!!
        assertEquals(134.0, e.kgPerPassenger!!, 0.0)
        assertEquals("https://travelimpactmodel.org", e.attributionUrl)
        assertNull(decodeRawBacked(Flight.serializer(), Json.parseToJsonElement("""{"price_usd":1}"""))!!.timEmissions())
        assertNull(decodeRawBacked(Flight.serializer(), Json.parseToJsonElement("""{"emissions":"n/a"}"""))!!.timEmissions())
        assertNull(decodeRawBacked(Flight.serializer(), Json.parseToJsonElement("""{"emissions":{"cabin":"economy"}}"""))!!.timEmissions())
    }
}
