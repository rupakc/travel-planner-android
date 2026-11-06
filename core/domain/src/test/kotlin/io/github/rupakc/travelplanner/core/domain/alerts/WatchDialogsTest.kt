package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDialogsTest {
    private fun json(text: String) = TravelJson.parseToJsonElement(text) as JsonObject

    private val trip = WatchTrip("Berlin", "Lisbon", "2026-12-01", "2026-12-08", 2)

    @Test
    fun `fare dialog pre-fills the target and validates before sending`() =
        runTest {
            val sent = mutableListOf<CreateFareWatchRequest>()
            val c =
                FareWatchDialogController(FareWatchDialogState(trip, price = 500.0, typicalLow = 452.0)) {
                    sent += it
                    AppResult.Success(FareWatch(id = 1, baselinePrice = 500.0))
                }
            assertEquals("450", c.state.value.target)
            c.setMode(AlertMode.BELOW_TARGET)
            c.setTarget("abc", Locale.US)
            assertEquals(TargetError.NOT_A_PRICE, c.state.value.shownTargetError)
            assertNull(c.submit())
            assertTrue(sent.isEmpty())
            c.setTarget("420", Locale.US)
            c.setFlexible(true)
            val w = c.submit()
            assertNotNull(w)
            assertEquals(420.0, sent.single().targetPrice)
            assertEquals(3, sent.single().flexibleDays)
            assertEquals(false, sent.single().emailEnabled)
            assertEquals(w, c.state.value.done)
        }

    @Test
    fun `fare dialog shows the backend message on failure`() =
        runTest {
            val c = FareWatchDialogController(FareWatchDialogState(trip)) { AppResult.Failure(AppError.Client(409, "You already have 10 active watches")) }
            c.submit()
            assertEquals(DialogError("You already have 10 active watches"), c.state.value.error)
            assertFalse(c.state.value.saving)
        }

    private val noLive =
        AppError.Client(
            status = 422,
            message = "We couldn't find a live price for Lumen Hotel.",
            code = CODE_NO_LIVE_PRICE,
            extras =
                mapOf(
                    "message" to "We couldn't find a live price for Lumen Hotel.",
                    "suggestions" to
                        """[{"title":"Lumen Lisboa","subtitle":"4 nights","price":398,"currency":"EUR",""" +
                        """"spec":{"name":"Lumen Lisboa","property_token":"abc"}}]""",
                    "estimate" to """{"price":410,"source":"ai_estimate"}""",
                ),
        )

    @Test
    fun `no live price shows suggestions and picking one watches it instead`() =
        runTest {
            val sent = mutableListOf<CreatePriceWatchRequest>()
            var first = true
            val c =
                PriceWatchDialogController(
                    PriceWatchDialogState(kind = "hotel", spec = json("""{"name":"Lumen Hotel"}""")),
                    create = {
                        sent += it
                        if (first) {
                            first = false
                            AppResult.Failure(noLive)
                        } else {
                            AppResult.Success(PriceWatch(id = 5, kind = "hotel", title = "Lumen Lisboa"))
                        }
                    },
                    delete = { AppResult.Success(Unit) },
                )
            assertNull(c.submit())
            val noMatch = c.state.value.noMatch!!
            assertEquals("Lumen Lisboa", noMatch.suggestions.single().title)
            assertEquals("EUR", noMatch.suggestions.single().currency)
            assertEquals(EstimateInfo(410.0, "USD", null), noMatch.estimateInfo())
            // Submit is disabled while suggestions are offered
            assertNull(c.submit())
            val outcome = c.pickSuggestion(noMatch.suggestions.single())!!
            assertTrue(outcome.viaSuggestion)
            assertEquals("abc", sent.last().spec.string("property_token"))
            assertNull(c.state.value.noMatch)
            assertEquals(5L, c.state.value.done?.id)
        }

    @Test
    fun `currency mismatch asks for the target again in that currency and resends the same spec`() =
        runTest {
            val mismatch =
                AppError.Client(
                    422,
                    "This is priced in EUR (now €120). Enter your target in EUR.",
                    CODE_CURRENCY_MISMATCH,
                    extras = mapOf("currency" to "EUR", "price" to "120.0"),
                )
            val sent = mutableListOf<CreatePriceWatchRequest>()
            val c =
                PriceWatchDialogController(
                    PriceWatchDialogState(kind = "activity", spec = json("""{"name":"Sail"}""")),
                    create = {
                        sent += it
                        if (sent.size == 1) AppResult.Failure(mismatch) else AppResult.Success(PriceWatch(id = 9, kind = "activity", currency = "EUR"))
                    },
                    delete = { AppResult.Success(Unit) },
                )
            c.setMode(AlertMode.BELOW_TARGET)
            c.setTarget("100", Locale.US)
            c.submit()
            assertEquals("EUR", c.state.value.currency)
            assertEquals("", c.state.value.target)
            assertEquals(120.0, c.state.value.currencyAsk?.price)
            c.setTarget("90", Locale.US)
            val outcome = c.submit()!!
            assertFalse(outcome.viaSuggestion)
            assertEquals("EUR", sent.last().targetCurrency)
            assertEquals(90.0, sent.last().targetPrice)
            assertEquals("USD", sent.first().targetCurrency)
        }

    private val watchedHotel =
        PriceWatch(
            id = 3,
            kind = "hotel",
            title = "Lumen Hotel & Show",
            alertMode = "any_drop",
            subject = json("""{"requested_name":"Lumen Hotel","city":"Lisbon","check_in":"2026-12-10","check_out":"2026-12-14","adults":2,"rooms":1}"""),
            lastObservation = json("""{"observed":{"alternatives":[{"title":"Lumen Lisboa","price":398,"spec":{"name":"Lumen Lisboa"}}]}}"""),
        )

    @Test
    fun `not this one swaps the hotel, offered once`() =
        runTest {
            val deleted = mutableListOf<Long>()
            var call = 0
            val c =
                PriceWatchDialogController(
                    PriceWatchDialogState(kind = "hotel", spec = json("""{"name":"Lumen Hotel"}""")),
                    create = {
                        call++
                        AppResult.Success(if (call == 1) watchedHotel else PriceWatch(id = 4, kind = "hotel", title = "Lumen Lisboa"))
                    },
                    delete = {
                        deleted += it
                        AppResult.Success(Unit)
                    },
                )
            c.submit()
            assertEquals(1, c.state.value.alternatives.size)
            c.toggleAlternatives()
            val outcome = c.swapTo(0)!!
            assertEquals(4L, outcome.watch.id)
            assertEquals(listOf(3L), deleted)
            assertTrue(c.state.value.swap.swapped)
            assertTrue(c.state.value.alternatives.isEmpty())
        }

    @Test
    fun `swap at the watch limit deletes first and puts the first watch back when the new one fails`() =
        runTest {
            var call = 0
            val requests = mutableListOf<CreatePriceWatchRequest>()
            val c =
                PriceWatchDialogController(
                    PriceWatchDialogState(kind = "hotel", spec = json("""{"name":"Lumen Hotel"}""")),
                    create = {
                        call++
                        requests += it
                        when (call) {
                            1 -> AppResult.Success(watchedHotel)
                            2 -> AppResult.Failure(AppError.Client(409, "limit"))
                            3 -> AppResult.Failure(AppError.Server(502, null))
                            else -> AppResult.Success(watchedHotel.copy(id = 6))
                        }
                    },
                    delete = { AppResult.Success(Unit) },
                )
            c.submit()
            c.swapTo(0)
            assertEquals(SwapError.KEPT_FIRST, c.state.value.swap.error)
            assertEquals(6L, c.state.value.done?.id)
            assertEquals("Lumen Hotel", requests.last().spec.string("name"))
        }

    @Test
    fun `a target typed in Spanish is sent as the number the user meant`() =
        runTest {
            val sent = mutableListOf<CreateFareWatchRequest>()
            val c =
                FareWatchDialogController(FareWatchDialogState(trip, price = 500.0)) {
                    sent += it
                    AppResult.Success(FareWatch(id = 1, baselinePrice = 500.0))
                }
            c.setMode(AlertMode.BELOW_TARGET)
            c.setTarget("450,50", Locale.forLanguageTag("es-ES"))
            assertNotNull(c.submit())
            assertEquals(450.5, sent.single().targetPrice)
        }

    @Test
    fun `a request cancelled mid-flight leaves the sheet ready to try again`() =
        runTest {
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val c =
                PriceWatchDialogController(
                    PriceWatchDialogState("hotel", json("""{"name":"Memmo","city":"Lisbon"}""")),
                    create = {
                        started.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    },
                    delete = { AppResult.Success(Unit) },
                )
            val job = launch { c.submit() }
            started.await()
            assertTrue(c.state.value.saving)
            job.cancel()
            job.join()
            assertFalse(c.state.value.saving)
        }
}
