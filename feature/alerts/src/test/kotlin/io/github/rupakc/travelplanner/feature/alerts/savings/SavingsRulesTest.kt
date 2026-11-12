package io.github.rupakc.travelplanner.feature.alerts.savings

import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsRulesTest {
    private fun json(text: String): JsonObject = TravelJson.parseToJsonElement(text).jsonObject

    private fun note(kind: String, data: String) = AppNotification(id = 1, kind = kind, data = json(data))

    @Test
    fun `savings view keeps USD and other currencies apart and ignores non-positive amounts`() {
        val s =
            TravelJson.decodeFromString(
                Savings.serializer(),
                """{"total_usd":245.0,"by_currency":{"EUR":12.0,"GBP":0,"CHF":"7.5"},"drops_caught":4,"targets_hit":1,
                    "watches_active":6,"best":{"kind":"hotel","watch_id":3,"title":"Lumen","caught":120.0,"currency":"USD"},
                    "milestones":[50,200,1000],"next_milestone":1000,"progress_pct":24,"since":"2026-10-01T00:00:00Z"}""",
            )
        val v = s.view()
        assertEquals(245.0, v.totalUsd, 0.0)
        assertEquals(listOf("CHF" to 7.5, "EUR" to 12.0), v.otherCurrencies)
        assertEquals(4, v.drops)
        assertEquals(1, v.targets)
        assertEquals(24, v.progressPct)
        assertEquals(1000, v.nextMilestone)
        assertEquals("Lumen", v.best?.title)
        assertFalse(v.isEmpty)
    }

    @Test
    fun `an empty tally and a finished one`() {
        assertTrue(Savings().view().isEmpty)
        val done = Savings(totalUsd = 1200.0, dropsCaught = 9, nextMilestone = null, progressPct = null).view()
        assertEquals(100, done.progressPct)
        assertNull(done.nextMilestone)
        assertEquals(100, Savings(progressPct = 400).view().progressPct)
    }

    @Test
    fun `a drop row reads amount, percent, target hit and the server XP only while the game shows`() {
        val n =
            note(
                "fare_drop",
                """{"drop":{"from":500,"to":454,"amount":46,"pct":9.2,"currency":"EUR","target_hit":true,"observed":true},
                    "game":{"xp":25,"awards":[{"rule":"drop","points":25}]}}""",
            )
        val shown = n.dropView(gameVisible = true)!!
        assertEquals(46.0, shown.amount!!, 0.0)
        assertEquals(9, shown.percent)
        assertEquals("EUR", shown.currency)
        assertTrue(shown.targetHit)
        assertEquals(25, shown.xp)
        assertNull(n.dropView(gameVisible = false)!!.xp)
    }

    @Test
    fun `an estimate, a missing price and old rows give no drop moment`() {
        assertNull(note("fare_drop", """{"drop":{"to":400,"amount":10,"observed":false}}""").dropView(true))
        assertNull(note("fare_drop", """{"drop":{"amount":10}}""").dropView(true))
        assertNull(note("fare_drop", """{"watch_id":3}""").dropView(true))
        assertNull(note("fare_drop", """{"drop":"garbage"}""").dropView(true))
        // A target hit without a positive amount still shows, without the "caught" line.
        assertNull(note("fare_target", """{"drop":{"to":400,"amount":0,"target_hit":true}}""").dropView(true)!!.amount)
    }

    @Test
    fun `a digest keeps five rows, counts the rest and tolerates unknown keys`() {
        val watches = (1..7).joinToString(",") { """{"kind":"hotel","watch_id":$it,"title":"W$it","latest":100,"change_7d":-5,"lowest":90,"extra":1}""" }
        val v =
            note(
                "price_digest",
                """{"digest":{"week":"2026-W46","watches":[$watches,{"title":""}],"savings":{"total_usd":50,"drops_caught":1}}}""",
            ).digestView()!!
        assertEquals("2026-W46", v.week)
        assertEquals(5, v.rows.size)
        assertEquals(2, v.more)
        assertEquals(50.0, v.savings!!.totalUsd, 0.0)
        assertNull(note("price_digest", "{}").digestView())
    }

    @Test
    fun `telemetry kind is always one of the four tokens`() {
        assertEquals("hotel", note("hotel_drop", "{}").telemetryKind())
        assertEquals("flight", note("fare_drop", "{}").telemetryKind())
        assertEquals("trip", note("watch_expired", """{"watch_kind":"trip"}""").telemetryKind())
        assertEquals("flight", note("mystery", "{}").telemetryKind())
    }
}
