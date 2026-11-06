package io.github.rupakc.travelplanner.core.ui.watch

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.PricePreview
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogController
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogState
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * E2E finding O4, end to end through the real controller: "Start watching" on an AI-named hotel gets the backend's
 * 422 `no_live_price`; the sheet must list the live suggestions and the estimate, and every tap sends one request.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PriceWatchSheetFlowTest {
    @get:Rule val compose = createComposeRule()

    private val sent = mutableListOf<CreatePriceWatchRequest>()
    private val gate = CompletableDeferred<Unit>()

    private val noLivePrice: AppResult<PriceWatch> =
        AppResult.Failure(
            AppError.Client(
                status = 422,
                message = "We couldn't find live prices for this hotel on Google Hotels.",
                code = "no_live_price",
                extras =
                    mapOf(
                        "message" to "We couldn't find live prices for this hotel on Google Hotels.",
                        "suggestions" to
                            """[{"title":"Tandem Palacio Alfama Suites","subtitle":"4.6/5 (837 reviews)","price":522.0,"currency":"USD",""" +
                            """"spec":{"name":"Tandem Palacio Alfama Suites","city":"Lisbon","check_in":"2026-12-01","check_out":"2026-12-04","adults":2}}]""",
                        "estimate" to """{"price":1350.0,"source":"ai_estimate","label":"AI estimate"}""",
                    ),
            ),
        )

    private fun controller(): PriceWatchDialogController {
        val spec = TravelJson.parseToJsonElement("""{"name":"Memmo Alfama Hotel","city":"Lisbon"}""") as JsonObject
        return PriceWatchDialogController(
            PriceWatchDialogState("hotel", spec, PricePreview(title = "Memmo Alfama Hotel", estimate = 1350.0)),
            create = {
                sent += it
                gate.await()
                noLivePrice
            },
            delete = { AppResult.Success(Unit) },
        )
    }

    @Test
    fun `no live price shows suggestions and estimate, one request per tap`() {
        val controller = controller()
        compose.setContent {
            val state by controller.state.collectAsState()
            val scope = rememberCoroutineScope()
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                PriceWatchSheetContent(state, null, { it.applyTo(controller, scope) }, {}, {})
            }
        }
        compose.onNodeWithText("Start watching").performScrollTo().performClick()
        compose.waitForIdle()
        // Tapped again while the first request is in flight: nothing more is sent.
        compose.onNodeWithText("Finding the live price…").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, sent.size)

        gate.complete(Unit)
        compose.waitUntil(WAIT_MS) { controller.state.value.noMatch != null }
        compose.onNodeWithText("We couldn't find live prices for this hotel on Google Hotels.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Our AI estimate was $1,350 AI estimate — an estimate, not a live price, so we can’t watch it.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Live prices we did find:").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tandem Palacio Alfama Suites").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Watch this instead").performScrollTo().assertIsDisplayed()
        assertEquals(1, sent.size)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
