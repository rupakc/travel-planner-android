package io.github.rupakc.travelplanner.core.model.alerts

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract examples of docs/Alerts-API.md and every golden fixture of the web repo parse into the DTOs. */
class AlertsV2DecodeTest {
    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, json: String): T = TravelJson.decodeFromString(s, json)

    private fun cases(name: String): List<JsonObject> {
        val text = checkNotNull(javaClass.getResource("/alerts/$name.json")) { "missing $name" }.readText()
        return (TravelJson.parseToJsonElement(text) as JsonArray).map { it.jsonObject }
    }

    @Test
    fun `price story contract example`() {
        val s =
            decode(
                PriceStory.serializer(),
                """{"kind":"flight","watch_id":7,"currency":"USD","mode":"below_target","target":380.0,
                "points":[{"at":"2026-11-01T00:00:00Z","price":455.0}],"baseline":455.0,
                "latest":{"at":"2026-11-10T00:00:00Z","price":412.0},"lowest":{"at":"2026-11-05T00:00:00Z","price":398.0},
                "typical":{"low":390.0,"high":520.0,"source":"google"},"verdict":"good",
                "advice":{"code":"near_target","params":{"gap":32.0}},"checked_at":"2026-11-10T00:00:00Z","checked_days_ago":0,
                "next_check_at":"2026-11-11T00:00:00Z","pending_first_check":false,"estimate":null,"caught":57.0,"live_prices":true,"future":1}""",
            )
        assertEquals(7L, s.watchId)
        assertEquals(412.0, s.latest?.price!!, 0.0)
        assertEquals(StoryVerdict.GOOD, s.verdictKind)
        assertEquals(AlertsAdviceCode.NEAR_TARGET, s.adviceCode)
        assertEquals(32.0, s.advice?.params?.gap!!, 0.0)
        assertEquals("google", s.typical?.source)
        assertEquals(57.0, s.caught!!, 0.0)
        assertEquals(true, s.livePrices)
        assertEquals(false, s.pendingFirstCheck)
        assertNull(s.estimate)
    }

    @Test
    fun `story with an estimate and unknown codes falls back to unknown`() {
        val s =
            decode(
                PriceStory.serializer(),
                """{"kind":"hotel","watch_id":2,"verdict":"stellar","advice":{"code":"brand_new","params":{"x":1}},
                "estimate":{"price":"310","source":"nightly x nights"},"points":[],"checked_days_ago":"4","pending_first_check":true}""",
            )
        assertEquals(StoryVerdict.UNKNOWN, s.verdictKind)
        assertEquals(AlertsAdviceCode.UNKNOWN, s.adviceCode)
        assertEquals(310.0, s.estimate?.price!!, 0.0)
        assertEquals(4, s.checkedDaysAgo)
        assertEquals(true, s.pendingFirstCheck)
        assertEquals(StoryVerdict.UNKNOWN, decode(PriceStory.serializer(), "{}").verdictKind)
    }

    @Test
    fun `savings contract example`() {
        val s =
            decode(
                Savings.serializer(),
                """{"total_usd":245.0,"by_currency":{"EUR":12.0},"drops_caught":4,"targets_hit":1,"watches_active":6,
                "best":{"kind":"hotel","watch_id":3,"title":"Memmo","caught":120.0,"currency":"USD"},
                "milestones":[50,200,1000],"next_milestone":1000,"progress_pct":24,"since":"2026-11-01T00:00:00Z"}""",
            )
        assertEquals(245.0, s.totalUsd!!, 0.0)
        assertEquals(12.0, s.byCurrency["EUR"]!!, 0.0)
        assertEquals(4, s.dropsCaught)
        assertEquals(3L, s.best?.watchId)
        assertEquals(listOf(50, 200, 1000), s.milestones)
        assertEquals(1000, s.nextMilestone)
        assertEquals(24, s.progressPct)
        assertNull(decode(Savings.serializer(), """{"next_milestone":null,"progress_pct":100,"best":null}""").nextMilestone)
    }

    @Test
    fun `alert prefs contract example and update body omits nulls`() {
        val p =
            decode(
                AlertPrefs.serializer(),
                """{"digest_weekly":false,"digest_push":false,"digest_day":1,"instant":"all",
                "quiet_hours":{"enabled":true,"start":"22:00","end":"07:00"},
                "daily_cap":{"flight":3,"hotel":3,"activity":3,"trip":3},"group_window_min":15,
                "timezone":"Asia/Kolkata","quiet_hours_active":true}""",
            )
        assertEquals(InstantMode.ALL, p.instantMode)
        assertEquals("22:00", p.quietHours?.start)
        assertEquals(3, p.dailyCap?.trip)
        assertEquals(15, p.groupWindowMin)
        assertTrue(p.quietHoursActive)
        assertNull(decode(AlertPrefs.serializer(), """{"timezone":null}""").timezone)
        assertEquals(InstantMode.UNKNOWN, decode(AlertPrefs.serializer(), """{"instant":"weird"}""").instantMode)
        val body = TravelJson.encodeToString(AlertPrefsUpdate.serializer(), AlertPrefsUpdate(digestWeekly = true, groupWindowMin = 0))
        assertEquals("""{"digest_weekly":true,"group_window_min":0}""", body)
    }

    @Test
    fun `quick watch request and responses`() {
        val spec = JsonObject(mapOf("name" to JsonPrimitive("Memmo")))
        val body = TravelJson.encodeToString(QuickWatchRequest.serializer(), QuickWatchRequest("hotel", "Memmo", null, spec, 4L, "results"))
        assertEquals("""{"kind":"hotel","title":"Memmo","spec":{"name":"Memmo"},"plan_id":4,"surface":"results"}""", body)
        val created =
            decode(
                QuickWatchResponse.serializer(),
                """{"kind":"hotel","watch":{"id":9,"kind":"hotel","title":"Memmo","last_price":null,"pending_first_check":true,"new":1},"existing":false}""",
            )
        assertEquals(9L, created.watchId)
        assertEquals(false, created.existing)
        assertEquals(true, created.priceWatch()?.pendingFirstCheck)
        assertNull(created.priceWatch()?.lastPrice)
        val flight = decode(QuickWatchResponse.serializer(), """{"kind":"flight","watch":{"id":3,"pending_first_check":true},"existing":true}""")
        assertEquals(true, flight.fareWatch()?.pendingFirstCheck)
        assertEquals(true, flight.existing)
    }

    @Test
    fun `suggestions and advice`() {
        val s =
            decode(
                SuggestionsResponse.serializer(),
                """{"suggestions":[{"kind":"flight","title":"NYC to LIS","subtitle":"Dec 1","spec":{"origin":"NYC"},"reason":"departure_soon","already_watching":false},
                {"kind":"x","title":"t","spec":{},"reason":"novel","already_watching":true}],"live_prices":true}""",
            )
        assertEquals(2, s.suggestions.size)
        assertEquals(SuggestionReason.DEPARTURE_SOON, s.suggestions[0].reasonKind)
        assertEquals(SuggestionReason.UNKNOWN, s.suggestions[1].reasonKind)
        assertTrue(s.suggestions[1].alreadyWatching)
        assertTrue(s.livePrices)
        val a = decode(AdviceTip.serializer(), """{"tip":"Wait a week.","source":"ai","generated_at":"2026-11-10T00:00:00Z","cached":true}""")
        assertEquals("Wait a week.", a.tip)
        assertTrue(a.cached)
    }

    @Test
    fun `notification drop game digest and group count`() {
        val n =
            decode(
                AppNotification.serializer(),
                """{"id":1,"kind":"flight_drop","data":{"watch_id":7,"drop":{"from":455.0,"to":412.0,"amount":43.0,"pct":9.5,"currency":"USD",
                "target_hit":true,"observed":true},"game":{"xp":120,"awards":[],"badges":[],"level_up":null},"group_count":"3"}}""",
            )
        val drop = n.dropData()!!
        assertEquals(43.0, drop.amount!!, 0.0)
        assertTrue(drop.targetHit)
        assertEquals(120, n.gameDelta()?.xp)
        assertEquals(3, n.groupCount)
        assertFalse(n.isPriceDigest)
        val noGame = decode(AppNotification.serializer(), """{"id":2,"data":{"drop":{"amount":1},"game":null}}""")
        assertNull(noGame.gameDelta())
        assertNull(noGame.groupCount)
        val old = decode(AppNotification.serializer(), """{"id":3,"kind":"fare_drop","data":{"drop":"junk"}}""")
        assertNull(old.dropData())
        val digest =
            decode(
                AppNotification.serializer(),
                """{"id":4,"kind":"price_digest","title":"t","body":"b","data":{"digest":{"week":"2026-W46","watches":[
                {"kind":"hotel","watch_id":3,"title":"Memmo","latest":120.0,"change_7d":-10.0,"lowest":110.0,"checked_at":"2026-11-10T00:00:00Z"}],
                "savings":{"total_usd":43.0,"next_milestone":50,"progress_pct":86}}}}""",
            )
        assertTrue(digest.isPriceDigest)
        val d = digest.digestData()!!
        assertEquals("2026-W46", d.week)
        assertEquals(-10.0, d.watches.single().change7d!!, 0.0)
        assertEquals(86, d.savings?.progressPct)
    }

    @Test
    fun `pending first check on existing watch dtos defaults to null`() {
        assertNull(decode(FareWatch.serializer(), """{"id":1}""").pendingFirstCheck)
        assertEquals(true, decode(FareWatch.serializer(), """{"id":1,"pending_first_check":1}""").pendingFirstCheck)
        assertNull(decode(PriceWatch.serializer(), """{"id":1}""").pendingFirstCheck)
    }

    @Test
    fun `config features read the alerts flags with safe defaults`() {
        val c = decode(ClientConfigDto.serializer(), """{"features":{"accounts":true,"alerts_api":2,"price_alerts_v2":true,"live_prices":false,"zzz":3}}""")
        assertEquals(2, c.features?.alertsApi)
        assertTrue(c.features?.priceAlertsV2 == true)
        assertFalse(c.features?.livePrices == true)
        val old = decode(ClientConfigDto.serializer(), """{"features":{"accounts":true}}""").features!!
        assertNull(old.alertsApi)
        assertFalse(old.priceAlertsV2 || old.livePrices)
    }

    @Test
    fun `error codes map and unknown falls back`() {
        val pairs =
            mapOf(
                "watch_not_found" to AlertsErrorCode.WATCH_NOT_FOUND,
                "kind_invalid" to AlertsErrorCode.KIND_INVALID,
                "advice_unsupported" to AlertsErrorCode.ADVICE_UNSUPPORTED,
                "advice_failed" to AlertsErrorCode.ADVICE_FAILED,
                "advice_no_data" to AlertsErrorCode.ADVICE_NO_DATA,
                "ai_limit" to AlertsErrorCode.AI_LIMIT,
                "ai_billing" to AlertsErrorCode.AI_BILLING,
                "live_prices_off" to AlertsErrorCode.LIVE_PRICES_OFF,
                "watch_limit" to AlertsErrorCode.WATCH_LIMIT,
                "spec_invalid" to AlertsErrorCode.SPEC_INVALID,
                "trip_not_found" to AlertsErrorCode.TRIP_NOT_FOUND,
                "alert_prefs_invalid" to AlertsErrorCode.ALERT_PREFS_INVALID,
            )
        pairs.forEach { (wire, code) -> assertEquals(code, AlertsErrorCode.from(wire)) }
        assertEquals(AlertsErrorCode.UNKNOWN, AlertsErrorCode.from("nope"))
        assertEquals(AlertsErrorCode.WATCH_LIMIT, AppError.Client(409, "x", "watch_limit").alertsErrorCode())
        assertEquals(AlertsErrorCode.AI_LIMIT, AppError.Server(503, "x", "ai_limit").alertsErrorCode())
        assertEquals(AlertsErrorCode.WATCH_NOT_FOUND, AppError.NotFound("x", "watch_not_found").alertsErrorCode())
        assertEquals(AlertsErrorCode.UNKNOWN, AppError.Network().alertsErrorCode())
    }

    @Test
    fun `story golden fixtures parse as inputs and expected shapes`() {
        val all = cases("story_cases")
        assertEquals(all.size, all.map { it["name"].toString() }.toSet().size)
        val seen = mutableSetOf<AlertsAdviceCode>()
        all.forEach { c ->
            val input = c["input"]!!.jsonObject
            assertNotNull(AlertKind.from((input["kind"] as JsonPrimitive).content))
            val exp = c["expected"]!!.jsonObject
            val advice = TravelJson.decodeFromJsonElement(StoryAdvice.serializer(), exp["advice"]!!)
            assertTrue(c.toString(), advice.adviceCode != AlertsAdviceCode.UNKNOWN)
            seen += advice.adviceCode
            val verdict = (exp["verdict"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            assertEquals(verdict == null, StoryVerdict.from(verdict) == StoryVerdict.UNKNOWN)
            exp["typical"]?.takeIf { it is JsonObject }?.let {
                val band = TravelJson.decodeFromJsonElement(TypicalBand.serializer(), it)
                assertNotNull(band.low)
                assertNotNull(band.high)
            }
            when (advice.adviceCode) {
                AlertsAdviceCode.CHECKS_PAUSED, AlertsAdviceCode.STEADY -> assertNotNull(advice.params.days)
                AlertsAdviceCode.FEW_CHECKS -> assertNotNull(advice.params.n)
                AlertsAdviceCode.BELOW_TARGET -> assertNotNull(advice.params.by)
                AlertsAdviceCode.NEAR_TARGET -> assertNotNull(advice.params.gap)
                else -> Unit
            }
            ((exp["latest"] as? JsonPrimitive)?.doubleOrNull)?.let { assertTrue(it > 0) }
        }
        assertEquals(AlertsAdviceCode.entries.toSet() - AlertsAdviceCode.UNKNOWN, seen)
    }

    @Test
    fun `savings golden fixtures parse into Savings`() {
        cases("savings_cases").forEach { c ->
            val exp = c["expected"]!!
            val s = TravelJson.decodeFromJsonElement(Savings.serializer(), exp)
            val o = exp.jsonObject
            assertEquals(c.toString(), (o["total_usd"] as JsonPrimitive).doubleOrNull!!, s.totalUsd!!, 0.0)
            assertEquals((o["drops_caught"] as JsonPrimitive).content.toInt(), s.dropsCaught)
            assertEquals((o["watches_active"] as JsonPrimitive).content.toInt(), s.watchesActive)
            assertEquals(o["by_currency"]!!.jsonObject.size, s.byCurrency.size)
            assertEquals(o["best"] is JsonObject, s.best != null)
            assertEquals(3, s.milestones.size)
            assertNotNull(s.progressPct)
            assertEquals((o["next_milestone"] as JsonPrimitive).content == "null", s.nextMilestone == null)
            assertTrue(c["input"]!!.jsonObject["rows"] is JsonArray)
        }
    }

    @Test
    fun `quiet hours golden fixtures parse into QuietHours`() {
        cases("quiet_hours_cases").forEach { c ->
            val input = c["input"]!!.jsonObject
            val q = TravelJson.decodeFromJsonElement(QuietHours.serializer(), input["quiet_hours"]!!)
            assertNotNull(q.start)
            assertNotNull(q.end)
            val deliver = (c["expected"]!!.jsonObject["deliver"] as JsonPrimitive).content
            assertTrue(deliver == "now" || deliver == "held")
        }
    }
}
