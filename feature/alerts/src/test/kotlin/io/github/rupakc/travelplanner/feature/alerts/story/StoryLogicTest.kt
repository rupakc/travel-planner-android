package io.github.rupakc.travelplanner.feature.alerts.story

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.alerts.PricePoint
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.TypicalBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryLogicTest {
    private fun story(vararg prices: Double, typical: TypicalBand? = null, target: Double? = null, verdict: String? = null) =
        PriceStory(
            points = prices.mapIndexed { i, p -> PricePoint(at = "2026-11-0${i + 1}T10:00:00Z", price = p) },
            typical = typical,
            target = target,
            verdict = verdict,
            lowest = prices.minOrNull()?.let { PricePoint(price = it) },
        )

    @Test
    fun `fewer than two observed points has no chart`() {
        assertNull(chartModel(story()))
        assertNull(chartModel(story(400.0)))
    }

    @Test
    fun `the range covers points, band, target and lowest`() {
        val m = chartModel(story(400.0, 420.0, typical = TypicalBand(low = 390.0, high = 520.0), target = 380.0))!!
        assertEquals(380.0, m.min, 0.0)
        assertEquals(520.0, m.max, 0.0)
        assertEquals(0f, m.fraction(380.0), 0f)
        assertEquals(1f, m.fraction(520.0), 0f)
        assertEquals(1f, m.xFraction(1), 0f)
        assertEquals(0f, m.xFraction(0), 0f)
    }

    @Test
    fun `a flat series sits in the middle`() {
        val m = chartModel(story(400.0, 400.0))!!
        assertEquals(0.5f, m.fraction(400.0), 0f)
    }

    @Test
    fun `the band needs a low to be drawn`() {
        val m = chartModel(story(400.0, 410.0, typical = TypicalBand(low = null, high = 500.0)))!!
        assertNull(m.typicalHigh)
    }

    @Test
    fun `touch maps to the nearest point and stepping stays inside`() {
        assertEquals(0, indexAt(-5f, 100f, 5))
        assertEquals(2, indexAt(50f, 100f, 5))
        assertEquals(4, indexAt(500f, 100f, 5))
        assertEquals(0, stepIndex(0, -1, 3))
        assertEquals(2, stepIndex(2, 1, 3))
        assertEquals(1, stepIndex(0, 1, 3))
    }

    @Test
    fun `telemetry verdict is the server verdict or none`() {
        assertEquals("good", telemetryVerdict(story(1.0, verdict = "good")))
        assertEquals("none", telemetryVerdict(story(1.0, verdict = null)))
        assertEquals("none", telemetryVerdict(story(1.0, verdict = "wow")))
    }

    @Test
    fun `server codes map to tip failures`() {
        assertEquals(TipFailure.LIMIT, tipFailure(AppError.Server(503, "x", code = "ai_limit")))
        assertEquals(TipFailure.BILLING, tipFailure(AppError.Server(503, "x", code = "ai_billing")))
        assertEquals(TipFailure.FAILED, tipFailure(AppError.Server(502, "x", code = "advice_failed")))
        assertEquals(TipFailure.NO_DATA, tipFailure(AppError.Client(422, "x", code = "advice_no_data")))
        assertEquals(TipFailure.UNSUPPORTED, tipFailure(AppError.Client(422, "x", code = "advice_unsupported")))
        assertEquals(TipFailure.OTHER, tipFailure(AppError.Network()))
    }
}
