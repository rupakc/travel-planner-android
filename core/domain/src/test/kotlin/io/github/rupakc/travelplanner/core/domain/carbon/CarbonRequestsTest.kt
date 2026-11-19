package io.github.rupakc.travelplanner.core.domain.carbon

import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.carbon.CarbonCategory
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import io.github.rupakc.travelplanner.core.model.carbon.CarbonHint
import io.github.rupakc.travelplanner.core.model.carbon.CarbonSource
import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.config.FeaturesDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The port of the web's `utils/carbon.js` request builders and the card's presentation rules. */
class CarbonRequestsTest {
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val search =
        obj(
            """{"origin":"London","destination":"Paris","departure_date":"2026-05-01","return_date":"2026-05-05",
            "num_travelers":2,"adults":2,"children":0,"seniors":0,"infants":0,"interests":["food"],"nationality":"British",
            "budget_usd":3000,"destinations":null}""",
        )

    @Test
    fun `results body keeps the search fields, the cheapest flight trimmed and the first hotel`() {
        val flights =
            obj(
                """{"results":[
                {"price_usd":0,"outbound":{"origin":"LHR","destination":"CDG","airline":"Free"}},
                {"price_usd":180,"airline":"BA","booking_url":"https://x","outbound":{"origin":"LHR","destination":"CDG","stops":0,
                  "airline":"BA","emissions":{"kg_co2e_per_pax":54},"segments":[{"flight_number":"BA1","origin":"LHR","destination":"CDG","aircraft":"A320"}],
                  "layovers":[{"airport":"AMS","city":"Amsterdam"}]},"return":{"origin":"CDG","destination":"LHR","departure_time":"10:00"}},
                {"price_usd":120,"outbound":{"origin":"LGW","destination":"ORY"}}
                ]}""",
            )
        val hotels = obj("""{"results":[{"name":"Hôtel X","country_code":"FR","price_per_night_usd":200},{"name":"Other"}]}""")
        val body = CarbonRequests.fromResults(search, flights, hotels, null, CarbonDiet.VEGAN)
        assertEquals(
            obj(
                """{"search":{"origin":"London","destination":"Paris","departure_date":"2026-05-01","return_date":"2026-05-05",
                "num_travelers":2,"adults":2,"children":0,"seniors":0},
                "results":{"flights":{"results":[{"outbound":{"origin":"LGW","destination":"ORY"}}]},
                "hotels":{"results":[{"name":"Hôtel X","country_code":"FR"}]}},"diet":"vegan"}""",
            ),
            body,
        )
    }

    @Test
    fun `trimmed legs keep emissions, segments and layover airports only`() {
        val flight =
            obj(
                """{"price_usd":180,"outbound":{"origin":"LHR","destination":"CDG","stops":1,"airline":"BA",
                "emissions":{"kg_co2e_per_pax":54},"segments":[{"flight_number":"BA1","origin":"LHR","destination":"AMS","aircraft":"A320"}],
                "layovers":[{"airport":"AMS","city":"Amsterdam"}]}}""",
            )
        assertEquals(
            obj(
                """{"outbound":{"origin":"LHR","destination":"CDG","stops":1,"emissions":{"kg_co2e_per_pax":54},
                "layovers":[{"airport":"AMS"}],"segments":[{"flight_number":"BA1","origin":"LHR","destination":"AMS"}]}}""",
            ),
            CarbonRequests.trimFlight(flight),
        )
    }

    @Test
    fun `multi-city results send the cheapest flight of each leg`() {
        val flights =
            obj(
                """{"trip_type":"multi_city","results":[],"legs":[
                {"leg_index":0,"results":[{"price_usd":300,"leg_index":0,"origin":"LHR"},{"price_usd":100,"leg_index":0,"origin":"LGW"}]},
                {"leg_index":1,"results":[]}]}""",
            )
        val body = CarbonRequests.fromResults(search, flights, null, null, CarbonDiet.MIXED)
        assertEquals(
            obj("""{"legs":[{"results":[{"origin":"LGW","leg_index":0}]},{"results":[]}]}"""),
            body["results"]!!.jsonObject["flights"],
        )
        assertEquals(obj("""{"results":[]}"""), body["results"]!!.jsonObject["hotels"])
    }

    @Test
    fun `cheapest skips missing and zero prices and keeps the first among equals`() {
        val list = Json.parseToJsonElement("""[{"id":1},{"id":2,"price_usd":50},{"id":3,"price":"50"},{"id":4,"price_usd":"abc"}]""")
        assertEquals("2", CarbonRequests.cheapest(list)!!["id"].toString())
        assertEquals("1", CarbonRequests.cheapest(Json.parseToJsonElement("""[{"id":1},{"id":2,"price_usd":0}]"""))!!["id"].toString())
        assertNull(CarbonRequests.cheapest(null))
    }

    @Test
    fun `itinerary is trimmed to 60 days of 12 slots with activity and coordinates`() {
        val slots = (1..15).joinToString(",") { """{"activity":"A$it","lat":1.5,"lng":2.5,"time":"09:00","cost_usd":10}""" }
        val days = (1..70).joinToString(",") { """{"day":$it,"slots":[$slots]}""" }
        val trimmed = CarbonRequests.trimItinerary(obj("""{"days":[$days]}"""))!!
        val outDays = trimmed["days"] as kotlinx.serialization.json.JsonArray
        assertEquals(60, outDays.size)
        val first = outDays[0].jsonObject["slots"] as kotlinx.serialization.json.JsonArray
        assertEquals(12, first.size)
        assertEquals(obj("""{"activity":"A1","lat":1.5,"lng":2.5}"""), first[0])
        assertNull(CarbonRequests.trimItinerary(obj("""{"days":[]}""")))
        assertNull(CarbonRequests.trimItinerary(null))
    }

    @Test
    fun `selections body trims every picked item`() {
        val selections =
            obj(
                """{"flight":{"price_usd":99,"airline":"AF","outbound":{"origin":"LHR","destination":"CDG","stops":0}},"flights":[],
                "hotel":{"name":"Hôtel X","city":"Paris","country_code":"FR","rating":4.5},
                "getting_around":[{"type":"metro","scope":"intra_city","name":"Métro","price_usd":2}],
                "activities":[{"name":"Seine cruise","price_usd":15}],"tips":[{"tip":"x"}]}""",
            )
        val itinerary = obj("""{"days":[{"slots":[{"activity":"Louvre","lat":48.86,"lng":2.34,"notes":"n"}]}]}""")
        val body = CarbonRequests.fromSelections(search, selections, itinerary, CarbonDiet.PESCATARIAN)
        assertEquals(
            obj(
                """{"flight":{"outbound":{"origin":"LHR","destination":"CDG","stops":0}},"flights":[],
                "hotel":{"name":"Hôtel X","city":"Paris","country_code":"FR"},
                "getting_around":[{"type":"metro","scope":"intra_city"}],"activities":[{"name":"Seine cruise"}]}""",
            ),
            body["selections"],
        )
        assertEquals(obj("""{"days":[{"slots":[{"activity":"Louvre","lat":48.86,"lng":2.34}]}]}"""), body["itinerary"])
        assertEquals("\"pescatarian\"", body["diet"].toString())
        assertFalse("results" in body)
    }

    @Test
    fun `the plan card needs a flight or a hotel`() {
        assertFalse(CarbonRequests.selectionsHaveTravel(obj("""{"flight":null,"flights":[],"hotel":null,"activities":[{"name":"x"}]}""")))
        assertTrue(CarbonRequests.selectionsHaveTravel(obj("""{"flight":null,"flights":[{"price_usd":1}],"hotel":null}""")))
        assertTrue(CarbonRequests.selectionsHaveTravel(obj("""{"hotel":{"name":"H"}}""")))
        assertFalse(CarbonRequests.selectionsHaveTravel(null))
    }

    @Test
    fun `diet wire values round trip and unknown is mixed`() {
        CarbonDiet.entries.forEach { assertEquals(it, CarbonDiet.fromWire(it.wire)) }
        assertEquals(CarbonDiet.MIXED, CarbonDiet.fromWire("keto"))
        assertEquals(CarbonDiet.MIXED, CarbonDiet.fromWire(null))
    }

    @Test
    fun `percent labels follow the web`() {
        assertEquals("0%", CarbonView.pctLabel(0.0))
        assertEquals("0%", CarbonView.pctLabel(null))
        assertEquals("<1%", CarbonView.pctLabel(0.004))
        assertEquals("1%", CarbonView.pctLabel(0.005))
        assertEquals("65%", CarbonView.pctLabel(0.6504))
    }

    @Test
    fun `visible categories, best hint and live sources`() {
        val footprint =
            CarbonFootprint(
                categories =
                    listOf(
                        CarbonCategory("flights", 159.4, 0.65, true),
                        CarbonCategory("stays", 0.0, 0.0, false),
                        CarbonCategory("spaceflight", 10.0, 0.1, true),
                        CarbonCategory("food", 57.6, 0.24, true),
                    ),
                lowerCarbon =
                    listOf(
                        CarbonHint(route = "A", savingKg = 10.0),
                        CarbonHint(route = "B", savingKg = 149.9),
                        CarbonHint(route = "C", savingKg = -1.0),
                    ),
                crawlerSources = listOf(CarbonSource(name = "DESNZ 2027"), CarbonSource(url = "https://x")),
            )
        assertEquals(listOf("flights", "food"), CarbonView.visibleCategories(footprint).map { it.key })
        assertEquals("B", CarbonView.bestHint(footprint)?.route)
        assertNull(CarbonView.bestHint(CarbonFootprint(lowerCarbon = listOf(CarbonHint(route = "C", savingKg = 0.0)))))
        assertEquals(listOf("DESNZ 2027"), CarbonView.liveSources(footprint).map { it.name })
    }

    @Test
    fun `safe urls and days`() {
        assertEquals("https://x.org", CarbonView.safeUrl(" https://x.org "))
        assertEquals("HTTP://x.org", CarbonView.safeUrl("HTTP://x.org"))
        assertNull(CarbonView.safeUrl("javascript:alert(1)"))
        assertEquals("2026-11-19", CarbonView.day("2026-11-19T08:00:00Z"))
        assertNull(CarbonView.day("soon"))
    }

    @Test
    fun `the flag is on only from features carbon 1`() {
        assertFalse(JourneyFlags(ClientConfigDto(features = FeaturesDto())).carbon)
        assertFalse(JourneyFlags(ClientConfigDto(features = FeaturesDto(carbon = 0))).carbon)
        assertTrue(JourneyFlags(ClientConfigDto(features = FeaturesDto(carbon = 1))).carbon)
        assertFalse(JourneyFlags(null).carbon)
    }
}
