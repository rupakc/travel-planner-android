package io.github.rupakc.travelplanner.core.data.telemetry

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.datastore.StoredSession
import io.github.rupakc.travelplanner.core.datastore.TelemetryPrefsStore
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePrefs : TelemetryPrefsStore {
    val state = MutableStateFlow<ConsentChoice?>(null)

    override suspend fun anonId(): String = "00000000-0000-4000-8000-000000000000"

    override val consent: Flow<ConsentChoice?> = state

    override suspend fun setConsent(choice: ConsentChoice) {
        state.value = choice
    }

    override suspend fun session(): StoredSession? = null

    override suspend fun setSession(session: StoredSession) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentRepositoryImplTest {
    private val prefs = FakePrefs()
    private var zone = "America/New_York"
    private val zoneChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    private fun TestScope.repo() = ConsentRepositoryImpl(prefs, { zone }, zoneChanges, backgroundScope)

    @Test fun `EU zone with no choice - allowed false and prompt FirstRun`() =
        runTest(UnconfinedTestDispatcher()) {
            zone = "Europe/Paris"
            val r = repo()
            assertFalse(r.allowedNow())
            assertEquals(ConsentPromptState.FirstRun, r.prompt.value)
        }

    @Test fun `non-EU with no choice - allowed true and prompt Hidden`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            assertTrue(r.allowedNow())
            assertEquals(ConsentPromptState.Hidden, r.prompt.value)
        }

    @Test fun `choose GRANTED - allowed true, prompt Hidden, choices emits GRANTED`() =
        runTest(UnconfinedTestDispatcher()) {
            zone = "Europe/Paris"
            val r = repo()
            r.choices.test {
                r.choose(ConsentChoice.GRANTED)
                assertEquals(ConsentChoice.GRANTED, awaitItem())
            }
            assertTrue(r.allowedNow())
            assertEquals(ConsentPromptState.Hidden, r.prompt.value)
        }

    @Test fun `denied stays denied in any zone`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.choose(ConsentChoice.DENIED)
            assertFalse(r.allowedNow())
            zone = "Asia/Tokyo"
            assertFalse(r.allowedNow())
            zone = "Europe/Paris"
            assertFalse(r.allowedNow())
        }

    @Test fun `allowed follows the zone at call time`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            assertTrue(r.allowedNow())
            zone = "Europe/Paris"
            assertFalse(r.allowedNow())
            r.allowed.test { assertFalse(awaitItem()) }
        }

    @Test fun `choices does not replay`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.choose(ConsentChoice.GRANTED)
            r.choices.test { expectNoEvents() }
        }

    @Test fun `openChoices shows Reopened current and close hides it`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.choose(ConsentChoice.DENIED)
            r.openChoices()
            assertEquals(ConsentPromptState.Reopened(false), r.prompt.value)
            r.closeChoices()
            assertEquals(ConsentPromptState.Hidden, r.prompt.value)
            r.openChoices()
            r.choose(ConsentChoice.GRANTED)
            assertEquals(ConsentPromptState.Hidden, r.prompt.value)
        }

    @Test fun `prompt appears after the zone switches into the EU`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            assertEquals(ConsentPromptState.Hidden, r.prompt.value)
            zone = "Europe/Paris"
            zoneChanges.emit(Unit)
            assertEquals(ConsentPromptState.FirstRun, r.prompt.value)
            zone = "Asia/Tokyo"
            zoneChanges.emit(Unit)
            assertEquals(ConsentPromptState.Hidden, r.prompt.value)
        }

    @Test fun `allowed flow re-evaluates on a zone change`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.allowed.test {
                assertTrue(awaitItem())
                zone = "Europe/Paris"
                zoneChanges.emit(Unit)
                assertFalse(awaitItem())
            }
        }

    @Test fun `openChoices shows Reopened true when granted`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.choose(ConsentChoice.GRANTED)
            r.openChoices()
            assertEquals(ConsentPromptState.Reopened(true), r.prompt.value)
        }
}
