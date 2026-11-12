package io.github.rupakc.travelplanner.feature.share.myshares

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.share.LegacyPlanShare
import io.github.rupakc.travelplanner.core.model.share.ShareRow
import io.github.rupakc.travelplanner.core.model.share.SharesList
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeShareCardStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeSharesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MySharesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val shares = FakeSharesRepository()
    private val plans = FakePlansRepository()
    private val cards = FakeShareCardStore()
    private val telemetry = FakeTelemetry()

    private val row = ShareRow(token = "tok1", kind = "plan", title = "Lisbon", url = "https://h.test/share/tok1", planId = 3)
    private val savings = ShareRow(token = "tok2", kind = "savings", title = "Price alerts", url = "https://h.test/share/tok2")
    private val legacy = LegacyPlanShare(planId = 9, name = "Old", url = "https://h.test/share/old")

    private fun vm() = MySharesViewModel(shares, plans, cards, telemetry)

    @Test
    fun `loads the shares and the legacy section`() {
        shares.listResult = AppResult.Success(SharesList(listOf(row, savings), listOf(legacy)))
        val s = vm().state.value
        assertEquals(MySharesPhase.Content, s.phase)
        assertEquals(listOf("tok1", "tok2"), s.shares.map { it.token })
        assertEquals(listOf(legacy), s.legacy)
    }

    @Test
    fun `an empty list is content with nothing`() {
        val s = vm().state.value
        assertEquals(MySharesPhase.Content, s.phase)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `a failure shows the error and retry reloads`() {
        shares.listResult = AppResult.Failure(AppError.RateLimited("Slow down", null, null))
        val v = vm()
        assertEquals(MySharesPhase.Error, v.state.value.phase)
        assertEquals("Slow down", v.state.value.error?.message)
        shares.listResult = AppResult.Success(SharesList(listOf(row)))
        v.onEvent(MySharesEvent.Retry)
        assertEquals(MySharesPhase.Content, v.state.value.phase)
    }

    @Test
    fun `a network failure is flagged offline`() {
        shares.listResult = AppResult.Failure(AppError.Network(null))
        assertTrue(vm().state.value.error is AppError.Network)
    }

    @Test
    fun `revoke asks first and then removes the row and reports the kind`() =
        runTest(timeout = 30.seconds) {
            shares.listResult = AppResult.Success(SharesList(listOf(row, savings)))
            val v = vm()
            v.onEvent(MySharesEvent.AskRevoke(RevokeTarget.Share(row)))
            assertEquals(RevokeTarget.Share(row), v.state.value.confirm)
            assertTrue(shares.revoked.isEmpty())
            v.onEvent(MySharesEvent.ConfirmRevoke)
            assertEquals(listOf("tok1"), shares.revoked)
            assertEquals(listOf("tok2"), v.state.value.shares.map { it.token })
            assertNull(v.state.value.confirm)
            assertEquals(MySharesMessage.Revoked, v.state.value.message)
            assertEquals(listOf("share_revoke" to mapOf<String, Any?>("kind" to "plan")), telemetry.events)
        }

    @Test
    fun `cancel keeps the share`() {
        shares.listResult = AppResult.Success(SharesList(listOf(row)))
        val v = vm()
        v.onEvent(MySharesEvent.AskRevoke(RevokeTarget.Share(row)))
        v.onEvent(MySharesEvent.Dismiss)
        v.onEvent(MySharesEvent.ConfirmRevoke)
        assertTrue(shares.revoked.isEmpty())
        assertEquals(1, v.state.value.shares.size)
    }

    @Test
    fun `a revoke failure keeps the row and shows the backend text`() {
        shares.listResult = AppResult.Success(SharesList(listOf(row)))
        shares.revokeResult = AppResult.Failure(AppError.RateLimited("Too many", null, null))
        val v = vm()
        v.onEvent(MySharesEvent.AskRevoke(RevokeTarget.Share(row)))
        v.onEvent(MySharesEvent.ConfirmRevoke)
        assertEquals(1, v.state.value.shares.size)
        assertTrue(v.state.value.message is MySharesMessage.Failed)
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun `a revoke of an already gone share counts as removed`() {
        shares.listResult = AppResult.Success(SharesList(listOf(row)))
        shares.revokeResult = AppResult.Failure(AppError.NotFound("x", "share_not_found"))
        val v = vm()
        v.onEvent(MySharesEvent.AskRevoke(RevokeTarget.Share(row)))
        v.onEvent(MySharesEvent.ConfirmRevoke)
        assertTrue(v.state.value.shares.isEmpty())
        assertEquals(MySharesMessage.Revoked, v.state.value.message)
    }

    @Test
    fun `a legacy share is revoked through the plan`() {
        shares.listResult = AppResult.Success(SharesList(emptyList(), listOf(legacy)))
        val v = vm()
        v.onEvent(MySharesEvent.AskRevoke(RevokeTarget.Legacy(legacy)))
        v.onEvent(MySharesEvent.ConfirmRevoke)
        assertTrue(v.state.value.legacy.isEmpty())
        assertTrue(shares.revoked.isEmpty())
        assertEquals("share_revoke", telemetry.events.single().first)
        assertEquals(mapOf<String, Any?>("kind" to "plan"), telemetry.events.single().second)
    }

    @Test
    fun `copy sends the link effect and reports it`() =
        runTest(timeout = 30.seconds) {
            shares.listResult = AppResult.Success(SharesList(listOf(row)))
            val v = vm()
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(MySharesEvent.Copy(row.url, row.shareKind.wire))
                assertEquals(MySharesEffect.CopyLink(row.url), awaitItem())
            }
            assertEquals(MySharesMessage.Copied, v.state.value.message)
            assertEquals(listOf("share_action" to mapOf<String, Any?>("kind" to "plan", "target" to "copy")), telemetry.events)
        }

    @Test
    fun `an unknown kind sends no telemetry`() {
        val odd = row.copy(kind = "quiz")
        shares.listResult = AppResult.Success(SharesList(listOf(odd)))
        val v = vm()
        v.onEvent(MySharesEvent.Copy(odd.url, null))
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun `share again attaches the story card when it downloads`() =
        runTest(timeout = 30.seconds) {
            shares.listResult = AppResult.Success(SharesList(listOf(row)))
            val file = File("card.png")
            cards.result = AppResult.Success(file)
            val v = vm()
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(MySharesEvent.ShareAgain(RevokeTarget.Share(row)))
                assertEquals(MySharesEffect.Send(row.url, "plan", file), awaitItem())
            }
            assertEquals(listOf("https://h.test/api/share/tok1/card.png?size=story"), cards.downloads)
            assertEquals(listOf("share_action" to mapOf<String, Any?>("kind" to "plan", "target" to "native")), telemetry.events)
            assertNull(v.state.value.sharingToken)
        }

    @Test
    fun `share again falls back to the link alone when the card fails`() =
        runTest(timeout = 30.seconds) {
            shares.listResult = AppResult.Success(SharesList(listOf(row)))
            val v = vm()
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(MySharesEvent.ShareAgain(RevokeTarget.Share(row)))
                assertEquals(MySharesEffect.Send(row.url, "plan", null), awaitItem())
            }
            assertEquals(MySharesMessage.CardUnavailable, v.state.value.message)
        }

    @Test
    fun `a legacy share goes out as a link only`() =
        runTest(timeout = 30.seconds) {
            shares.listResult = AppResult.Success(SharesList(emptyList(), listOf(legacy)))
            val v = vm()
            v.effects.test(timeout = 10.seconds) {
                v.onEvent(MySharesEvent.ShareAgain(RevokeTarget.Legacy(legacy)))
                assertEquals(MySharesEffect.Send(legacy.url, "plan", null), awaitItem())
            }
            assertTrue(cards.downloads.isEmpty())
        }

    @Test
    fun `a send failure is reported`() {
        val v = vm()
        v.onEvent(MySharesEvent.SendFailed)
        assertEquals(MySharesMessage.ShareFailed, v.state.value.message)
        v.onEvent(MySharesEvent.MessageShown)
        assertNull(v.state.value.message)
    }
}
