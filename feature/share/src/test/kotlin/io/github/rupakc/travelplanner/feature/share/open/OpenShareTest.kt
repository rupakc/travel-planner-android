package io.github.rupakc.travelplanner.feature.share.open

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.feature.share.ShareEvent
import io.github.rupakc.travelplanner.feature.share.ShareLinks
import io.github.rupakc.travelplanner.feature.share.ShareViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenShareTest {
    @get:Rule val main = MainDispatcherRule()

    private val telemetry = FakeTelemetry()
    private val repo = FakePlansRepository()

    private fun plan(json: String): SharedPlan = TravelJson.decodeFromString(SharedPlan.serializer(), json)

    private fun vm(token: String, web: String = "https://voyager.example") =
        ShareViewModel(
            SavedStateHandle(mapOf(ShareViewModel.TOKEN_ARG to token)),
            repo,
            FakeAuthRepository(null),
            TestAppDispatchers(main.testDispatcher),
            PlaceNameLookup(FakeSearchRepository()),
            FakeConsentRepository(),
            telemetry,
            AppBuildInfo("http://x", "local", "1", true, true, web),
        )

    @Test
    fun `a recap share shows the card, the sharer name only when given, and counts a view`() =
        runTest(timeout = TIMEOUT) {
            repo.sharedPlans["recap-token"] =
                plan(
                    """{"name":"Japan","kind":"recap","options":{"dates":"month","show_origin":false,"show_name":true},"owner_name":"Ana",
                    "card":{"headline":"My trip to Japan: recap","stats":[{"key":"places","count":4}],"destination":"Japan","period":"2027-04"},
                    "search_data":{},"selections":{}}""",
                )
            vm("recap-token").state.test {
                val s = expectMostRecentItem()
                assertEquals(ShareKind.RECAP, s.kind)
                assertNull(s.trip)
                assertEquals("My trip to Japan: recap", s.card!!.headline)
                assertEquals("Ana", s.ownerName)
                assertFalse(s.unknownKind)
            }
            assertEquals(listOf("share_view" to mapOf<String, Any?>("kind" to "recap")), telemetry.events)
        }

    @Test
    fun `a savings share shows the card without a name`() =
        runTest(timeout = TIMEOUT) {
            repo.sharedPlans["sav-token"] =
                plan(
                    """{"name":"Price alerts","kind":"savings",
                    "card":{"headline":"Caught drops worth ${'$'}412","stats":[{"key":"drops_caught","count":3}]}}""",
                )
            vm("sav-token").state.test {
                val s = expectMostRecentItem()
                assertEquals(ShareKind.SAVINGS, s.kind)
                assertEquals(3, s.card!!.stats.single().count)
                assertNull(s.ownerName)
            }
            assertEquals("savings", telemetry.events.single().second["kind"])
        }

    @Test
    fun `a plan share and a legacy token both load the trip and report plan`() =
        runTest(timeout = TIMEOUT) {
            repo.sharedPlans["plan-token"] = plan("""{"name":"Lisbon","kind":"plan","search_data":{"destination":"Lisbon"},"selections":{}}""")
            repo.sharedPlans["legacy-token"] = plan("""{"name":"Old","search_data":{"destination":"Rome"},"selections":{}}""")
            vm("plan-token").state.test {
                val s = expectMostRecentItem()
                assertEquals("Lisbon", s.trip!!.destination)
                // No dates unless the server sent them: the trip carries none.
                assertNull(s.trip!!.startDate)
                assertNull(s.card)
            }
            vm("legacy-token").state.test { assertEquals("Rome", expectMostRecentItem().trip!!.destination) }
            assertEquals(listOf("plan", "plan"), telemetry.events.map { it.second["kind"] })
        }

    @Test
    fun `an unknown kind is a plain link and is not reported`() =
        runTest(timeout = TIMEOUT) {
            repo.sharedPlans["new-token"] = plan("""{"name":"X","kind":"quiz","card":{"headline":"h"},"search_data":{},"selections":{}}""")
            vm("new-token").state.test {
                val s = expectMostRecentItem()
                assertEquals(ShareKind.UNKNOWN, s.kind)
                assertTrue(s.unknownKind)
                assertNull(s.card)
                assertNull(s.trip)
                assertEquals("https://voyager.example/share/new-token", s.plainLink)
            }
            assertTrue(telemetry.events.isEmpty())
        }

    @Test
    fun `a revoked or unknown token is the not-found state and sends nothing`() =
        runTest(timeout = TIMEOUT) {
            vm("gone-token").state.test {
                val s = expectMostRecentItem()
                assertTrue(s.error is AppError.NotFound)
                assertNull(s.trip)
                assertNull(s.card)
            }
            assertTrue(telemetry.events.isEmpty())
        }

    @Test
    fun `a missing link shows not found and retry loads it once it exists`() =
        runTest(timeout = TIMEOUT) {
            val model = vm("late-token")
            model.state.test {
                assertTrue(expectMostRecentItem().error is AppError.NotFound)
                repo.sharedPlans["late-token"] = plan("""{"name":"Lisbon","kind":"plan","search_data":{},"selections":{}}""")
                model.onEvent(ShareEvent.Retry)
                val s = expectMostRecentItem()
                assertNull(s.error)
                assertNotNull(s.trip)
            }
        }

    @Test
    fun `plain link needs an http(s) web base with a host and a valid token`() {
        assertEquals("https://h.example/share/abc", ShareLinks.plainLink("https://h.example/", "abc"))
        assertNull(ShareLinks.plainLink("", "abc"))
        assertNull(ShareLinks.plainLink("javascript:alert(1)", "abc"))
        assertNull(ShareLinks.plainLink("https://", "abc"))
        assertNull(ShareLinks.plainLink("https://h.example", "a/b"))
    }

    @Test
    fun `pasted input yields a token from a link or a bare code only`() {
        assertEquals("AbC_def-12345", ShareLinkInput.token("  https://any.example/share/AbC_def-12345 \n"))
        assertEquals("AbC_def-12345", ShareLinkInput.token("travelplanner://share/AbC_def-12345"))
        assertEquals("AbC_def-12345", ShareLinkInput.token("AbC_def-12345"))
        assertNull(ShareLinkInput.token("hello"))
        assertNull(ShareLinkInput.token(""))
        assertNull(ShareLinkInput.token(null))
        assertNull(ShareLinkInput.token("https://any.example/plans/AbC_def-12345"))
        assertNull(ShareLinkInput.token("javascript:alert(1)"))
        assertNull(ShareLinkInput.token("two words here"))
    }

    @Test
    fun `period shows month and year, a bad period shows nothing`() {
        assertNotNull(periodLabel("2027-04"))
        assertNull(periodLabel("2027-13"))
        assertNull(periodLabel(null))
        assertNull(periodLabel("2027-04-01"))
    }

    private companion object {
        val TIMEOUT = kotlin.time.Duration.parse("30s")
    }
}
