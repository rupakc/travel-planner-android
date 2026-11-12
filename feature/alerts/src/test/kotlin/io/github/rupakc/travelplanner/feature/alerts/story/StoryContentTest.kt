package io.github.rupakc.travelplanner.feature.alerts.story

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.alerts.AdviceParams
import io.github.rupakc.travelplanner.core.model.alerts.PricePoint
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.StoryAdvice
import io.github.rupakc.travelplanner.core.model.alerts.StoryEstimate
import io.github.rupakc.travelplanner.core.model.alerts.TypicalBand
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class StoryContentTest {
    @get:Rule val compose = createComposeRule()

    private val points =
        listOf(
            PricePoint("2026-11-01T10:00:00Z", 455.0),
            PricePoint("2026-11-02T10:00:00Z", 430.0),
            PricePoint("2026-11-03T10:00:00Z", 412.0),
        )

    private fun story(
        code: String = "near_target",
        params: AdviceParams = AdviceParams(gap = 32.0),
        verdict: String? = "good",
        daysAgo: Int? = 2,
        estimate: StoryEstimate? = null,
        pts: List<PricePoint> = points,
        caught: Double? = null,
    ) = PriceStory(
        kind = "flight",
        watchId = 7,
        currency = "USD",
        target = 380.0,
        points = pts,
        latest = pts.lastOrNull(),
        lowest = pts.minByOrNull { it.price ?: 0.0 },
        typical = TypicalBand(390.0, 520.0, "google"),
        verdict = verdict,
        advice = StoryAdvice(code, params),
        checkedDaysAgo = daysAgo,
        estimate = estimate,
        caught = caught,
    )

    private fun show(state: PriceStoryUiState, onTip: () -> Unit = {}, onRetry: () -> Unit = {}, dark: Boolean = false) =
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                PriceStoryContent(state, "Berlin to Lisbon", onRetry, onTip, {})
            }
        }

    @Test(timeout = 30_000)
    fun `shows verdict, advice and how old the price is`() {
        show(PriceStoryUiState(loading = false, story = story()))
        compose.onNodeWithText("Good price").assertIsDisplayed()
        compose.onNodeWithText("Just $32 above your target.").assertExists()
        compose.onNodeWithText("Checked 2 days ago").assertExists()
    }

    @Test(timeout = 30_000)
    fun `checks paused says live checks are paused and an unknown verdict shows no chip`() {
        show(PriceStoryUiState(loading = false, story = story("checks_paused", AdviceParams(days = 9), verdict = null, daysAgo = 9)))
        compose.onNodeWithText("Live price checks are paused. The last check was 9 days ago.").assertExists()
        compose.onNodeWithText("Good price").assertDoesNotExist()
    }

    @Test(timeout = 30_000)
    fun `an unknown advice code falls back to a generic line`() {
        show(PriceStoryUiState(loading = false, story = story("brand_new_code")))
        compose.onNodeWithText("See the price history below.").assertExists()
    }

    @Test(timeout = 30_000)
    fun `pending story has no chart and waits for the first check`() {
        show(
            PriceStoryUiState(
                loading = false,
                story = story("pending", AdviceParams(), verdict = null, daysAgo = null, pts = emptyList(), estimate = StoryEstimate(500.0, "serp")),
            ),
        )
        compose.onNodeWithText("Waiting for the first price check.").assertExists()
        compose.onNodeWithText("No price checks yet.").assertExists()
        compose.onNodeWithText("Estimate: $500. This is not an observed price.").assertExists()
    }

    @Test(timeout = 30_000)
    fun `one check shows a count instead of a chart`() {
        show(PriceStoryUiState(loading = false, story = story("few_checks", AdviceParams(n = 1), pts = points.take(1))))
        compose.onNodeWithText("Only 1 check so far, so there is not much to compare yet.").assertExists()
        compose.onNodeWithText("1 check so far. A chart appears after two checks.").assertExists()
    }

    @Test(timeout = 30_000)
    fun `the drop is worded as caught, never saved`() {
        show(PriceStoryUiState(loading = false, story = story(caught = 57.0)))
        compose.onNodeWithText("Caught a drop of $57 since you started watching.").assertExists()
    }

    @Test(timeout = 30_000)
    fun `the chart exposes TalkBack steps and announces the picked check`() {
        show(PriceStoryUiState(loading = false, story = story()), dark = true)
        val chart = SemanticsMatcher("price chart") { it.config.getOrNull(SemanticsProperties.StateDescription) != null }
        compose.onNode(chart).assertExists()
        val labels = { compose.onNode(chart).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label } }
        assertEquals(listOf("Previous check", "Next check", "First check", "Latest check"), labels())
        assertTrue(compose.onNode(chart).fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("$412"))
        val prev = compose.onNode(chart).fetchSemanticsNode().config[SemanticsActions.CustomActions][0]
        compose.runOnUiThread { prev.action() }
        compose.waitForIdle()
        assertTrue(compose.onNode(chart).fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("$430"))
    }

    @Test(timeout = 30_000)
    fun `the tip button shows for flights and its errors use the translated copy`() {
        var asked = 0
        show(PriceStoryUiState(loading = false, story = story()), onTip = { asked++ })
        compose.onNodeWithText("Get a timing tip").performScrollTo().performClick()
        assertEquals(1, asked)
    }

    @Test(timeout = 30_000)
    fun `tip limit, billing and generic failures`() {
        show(PriceStoryUiState(loading = false, story = story(), tip = TipState.Failed(StoryError(TipFailure.LIMIT, "ignored"))))
        compose.onNodeWithText("The AI limit is reached for this month.").performScrollTo().assertIsDisplayed()
    }

    @Test(timeout = 30_000)
    fun `a failed load offers retry`() {
        var retried = 0
        show(PriceStoryUiState(loading = false, error = StoryError(TipFailure.OTHER, null)), onRetry = { retried++ })
        compose.onNodeWithText("Could not load the price story.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }
}
