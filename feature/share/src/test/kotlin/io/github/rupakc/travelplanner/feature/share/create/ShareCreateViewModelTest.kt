package io.github.rupakc.travelplanner.feature.share.create

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.share.ShareDates
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeShareCardStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeSharesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShareCreateViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val shares = FakeSharesRepository()
    private val cards = FakeShareCardStore()
    private val telemetry = FakeTelemetry()

    private fun vm(kind: String = "plan", planId: Long? = 4, sessionId: String? = null) =
        ShareCreateViewModel(
            SavedStateHandle(
                buildMap {
                    put("kind", kind)
                    if (planId != null) put("planId", planId)
                    if (sessionId != null) put("sessionId", sessionId)
                },
            ),
            shares,
            cards,
            telemetry,
        )

    @Test
    fun `a quiz share sends its session and a status share its trip`() =
        runTest(timeout = 30.seconds) {
            val quiz = vm(kind = "quiz", planId = null, sessionId = "sess-1")
            quiz.onEvent(ShareCreateEvent.Submit)
            val status = vm(kind = "status", planId = 7)
            status.onEvent(ShareCreateEvent.Submit)
            assertEquals(
                listOf(
                    FakeSharesRepository.CreateCall(ShareKind.QUIZ, null, ShareOptions.PRIVATE, "sess-1"),
                    FakeSharesRepository.CreateCall(ShareKind.STATUS, 7, ShareOptions.PRIVATE, null),
                ),
                shares.createCalls,
            )
            assertEquals(ShareKind.QUIZ, quiz.state.value.kind)
            assertTrue(telemetry.events.contains("share_create" to mapOf<String, Any?>("kind" to "quiz", "dates" to "hidden")))
        }

    @Test
    fun `opening reports the kind and starts private`() {
        val v = vm()
        assertEquals(ShareOptions.PRIVATE, v.state.value.options)
        assertNull(v.state.value.created)
        assertEquals(listOf("share_open" to mapOf<String, Any?>("kind" to "plan")), telemetry.events)
    }

    @Test
    fun `create sends the chosen options then update replaces them`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.onEvent(ShareCreateEvent.SetDates(ShareDates.MONTH))
            v.onEvent(ShareCreateEvent.SetShowOrigin(true))
            v.onEvent(ShareCreateEvent.Submit)
            assertEquals(ShareCreateMessage.Created, v.state.value.message)
            assertEquals(FakeSharesRepository.CreateCall(ShareKind.PLAN, 4, ShareOptions("month", true, false)), shares.createCalls.single())
            assertTrue(telemetry.events.contains("share_create" to mapOf<String, Any?>("kind" to "plan", "dates" to "month")))

            // The server echoes what it was sent; the options only differ after the next edit.
            shares.createResult =
                AppResult.Success(shares.createResult.let { (it as AppResult.Success).value.copy(options = ShareOptions("month", true, false)) })
            v.onEvent(ShareCreateEvent.Submit)
            v.onEvent(ShareCreateEvent.SetShowName(true))
            assertTrue(v.state.value.dirty)
            assertFalse(v.state.value.canShare)
            v.onEvent(ShareCreateEvent.Submit)
            assertEquals(ShareCreateMessage.Updated, v.state.value.message)
            assertEquals(3, shares.createCalls.size)
            assertTrue(shares.createCalls.last().options.showName)
        }

    @Test
    fun `savings sends no plan and offline and server text are shown`() =
        runTest(timeout = 30.seconds) {
            val v = vm("savings", null)
            shares.createResult = AppResult.Failure(AppError.Network(null))
            v.onEvent(ShareCreateEvent.Submit)
            assertEquals(ShareCreateMessage.Offline, v.state.value.message)
            assertNull(shares.createCalls.single().planId)
            shares.createResult = AppResult.Failure(AppError.RateLimited("Too many requests", 30))
            v.onEvent(ShareCreateEvent.Submit)
            assertEquals(AppError.RateLimited("Too many requests", 30), (v.state.value.message as ShareCreateMessage.Failed).error)
            assertFalse(v.state.value.busy)
        }

    @Test
    fun `share downloads the story card and emits the send effect`() =
        runTest(timeout = 30.seconds) {
            val file = File("card.png")
            cards.result = AppResult.Success(file)
            val v = vm()
            v.onEvent(ShareCreateEvent.Share) // nothing to share yet
            assertEquals(0, cards.downloads.size)
            v.onEvent(ShareCreateEvent.Submit)
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(ShareCreateEvent.Share)
                val send = awaitItem() as ShareCreateEffect.Send
                assertEquals("https://example.test/share/tok123", send.url)
                assertEquals(file, send.card)
                assertEquals(ShareKind.PLAN, send.kind)
            }
            assertEquals(listOf("https://example.test/api/share/tok123/card.png?size=story"), cards.downloads)
            assertNull(v.state.value.message)
            assertTrue(telemetry.events.contains("share_action" to mapOf<String, Any?>("kind" to "plan", "target" to "native")))
        }

    @Test
    fun `a card that cannot be fetched still shares the link`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.onEvent(ShareCreateEvent.Submit)
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(ShareCreateEvent.Share)
                assertNull((awaitItem() as ShareCreateEffect.Send).card)
            }
            assertEquals(ShareCreateMessage.CardUnavailable, v.state.value.message)
        }

    @Test
    fun `copy emits the link and reports`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.onEvent(ShareCreateEvent.Submit)
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(ShareCreateEvent.Copy)
                assertEquals("https://example.test/share/tok123", (awaitItem() as ShareCreateEffect.CopyLink).url)
            }
            assertEquals(ShareCreateMessage.Copied, v.state.value.message)
            assertTrue(telemetry.events.contains("share_action" to mapOf<String, Any?>("kind" to "plan", "target" to "copy")))
        }

    @Test
    fun `revoke asks first then removes the link`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.onEvent(ShareCreateEvent.AskRevoke)
            assertFalse(v.state.value.confirmRevoke) // no link yet
            v.onEvent(ShareCreateEvent.Submit)
            v.onEvent(ShareCreateEvent.AskRevoke)
            assertTrue(v.state.value.confirmRevoke)
            v.onEvent(ShareCreateEvent.CancelRevoke)
            assertTrue(shares.revoked.isEmpty())
            v.onEvent(ShareCreateEvent.AskRevoke)
            v.onEvent(ShareCreateEvent.ConfirmRevoke)
            assertEquals(listOf("tok123"), shares.revoked)
            assertNull(v.state.value.created)
            assertEquals(ShareCreateMessage.Revoked, v.state.value.message)
            assertTrue(telemetry.events.contains("share_revoke" to mapOf<String, Any?>("kind" to "plan")))
        }

    @Test
    fun `a failed revoke keeps the link`() =
        runTest(timeout = 30.seconds) {
            val v = vm()
            v.onEvent(ShareCreateEvent.Submit)
            shares.revokeResult = AppResult.Failure(AppError.Server(500, null))
            v.onEvent(ShareCreateEvent.AskRevoke)
            v.onEvent(ShareCreateEvent.ConfirmRevoke)
            assertNotNull(v.state.value.created)
            assertTrue((v.state.value.message as ShareCreateMessage.Failed).revoke)
        }
}
