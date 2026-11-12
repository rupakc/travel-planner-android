package io.github.rupakc.travelplanner.feature.social.connections

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.social.BlockList
import io.github.rupakc.travelplanner.core.model.social.FollowRequest
import io.github.rupakc.travelplanner.core.model.social.FollowRequests
import io.github.rupakc.travelplanner.core.model.social.HandlePage
import io.github.rupakc.travelplanner.core.model.social.HandleRef
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeSocialRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectionsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val telemetry = FakeTelemetry()

    private fun vm(tab: String? = null) =
        ConnectionsViewModel(SavedStateHandle(if (tab == null) emptyMap() else mapOf(ConnectionsViewModel.ARG_TAB to tab)), social, telemetry)

    private fun page(vararg handles: String, next: String? = null) = AppResult.Success(HandlePage(handles.map(::HandleRef), next))

    @Test
    fun `it opens on the requested tab and loads only that tab`() {
        social.followRequestsResult = AppResult.Success(FollowRequests(listOf(FollowRequest("bo"))))
        val v = vm()
        assertEquals(ConnectionTab.Requests, v.state.value.tab)
        assertEquals(listOf("bo"), v.state.value.current.handles)
        assertEquals(listOf("followRequests"), social.calls)
    }

    @Test
    fun `an unknown tab argument falls back to requests`() {
        assertEquals(ConnectionTab.Requests, vm("nope").state.value.tab)
        assertEquals(ConnectionTab.Blocked, vm("blocked").state.value.tab)
    }

    @Test
    fun `a tab loads once when first opened`() {
        social.handlePageResult = page("cy")
        val v = vm()
        v.onEvent(ConnectionsEvent.SelectTab(ConnectionTab.Followers))
        v.onEvent(ConnectionsEvent.SelectTab(ConnectionTab.Requests))
        v.onEvent(ConnectionsEvent.SelectTab(ConnectionTab.Followers))
        assertEquals(1, social.calls.count { it.startsWith("followers") })
        assertEquals(listOf("cy"), v.state.value.current.handles)
    }

    @Test
    fun `a failed tab shows its error and retry reloads`() {
        social.followRequestsResult = AppResult.Failure(AppError.Network(null))
        val v = vm()
        assertTrue(v.state.value.current.error is AppError.Network)
        social.followRequestsResult = AppResult.Success(FollowRequests(listOf(FollowRequest("bo"))))
        v.onEvent(ConnectionsEvent.Retry)
        assertNull(v.state.value.current.error)
        assertEquals(listOf("bo"), v.state.value.current.handles)
    }

    @Test
    fun `followers page with a cursor`() {
        social.handlePageResult = page("a", "b", next = "c1")
        val v = vm("followers")
        assertEquals("c1", v.state.value.current.next)
        social.handlePageResult = page("b", "c")
        v.onEvent(ConnectionsEvent.LoadMore)
        assertEquals(listOf("a", "b", "c"), v.state.value.current.handles)
        assertNull(v.state.value.current.next)
        assertTrue(social.calls.contains("followers(c1)"))
    }

    @Test
    fun `accept and decline remove the row`() {
        social.followRequestsResult = AppResult.Success(FollowRequests(listOf(FollowRequest("bo"), FollowRequest("cy"))))
        val v = vm()
        v.onEvent(ConnectionsEvent.Accept("bo"))
        v.onEvent(ConnectionsEvent.Decline("cy"))
        assertTrue(v.state.value.current.handles.isEmpty())
        assertTrue(social.calls.containsAll(listOf("acceptRequest(bo)", "declineRequest(cy)")))
    }

    @Test
    fun `unfollow removes the row and counts a none follow`() {
        social.handlePageResult = page("a", "b")
        val v = vm("following")
        v.onEvent(ConnectionsEvent.Unfollow("a"))
        assertEquals(listOf("b"), v.state.value.current.handles)
        assertEquals(listOf("social_follow" to mapOf<String, Any?>("state" to "none")), telemetry.events)
    }

    @Test
    fun `unblock removes the row`() {
        social.blocksResult = AppResult.Success(BlockList(listOf(HandleRef("x"))))
        val v = vm("blocked")
        v.onEvent(ConnectionsEvent.Unblock("x"))
        assertTrue(v.state.value.current.handles.isEmpty())
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun `a failed action keeps the row and reports the backend text`() {
        social.followRequestsResult = AppResult.Success(FollowRequests(listOf(FollowRequest("bo"))))
        social.unitResult = AppResult.Failure(AppError.NotFound("Request is gone", "request_not_found"))
        val v = vm()
        v.onEvent(ConnectionsEvent.Accept("bo"))
        assertEquals(listOf("bo"), v.state.value.current.handles)
        assertEquals("Request is gone", (v.state.value.message as ConnectionsMessage.Failed).error.message)
        assertNull(v.state.value.busyHandle)
    }
}
