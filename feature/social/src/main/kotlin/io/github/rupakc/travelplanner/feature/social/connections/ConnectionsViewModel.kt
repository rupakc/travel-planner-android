package io.github.rupakc.travelplanner.feature.social.connections

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The four lists of the connections screen; [wire] is the route argument. */
internal enum class ConnectionTab(val wire: String) {
    Requests("requests"),
    Followers("followers"),
    Following("following"),
    Blocked("blocked"),
    ;

    companion object {
        fun from(wire: String?): ConnectionTab = entries.firstOrNull { it.wire == wire } ?: Requests
    }
}

/** One tab's list. Followers and following are cursor paged ([next] non-null = more); the others come in one call. */
@Immutable
internal data class TabState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val handles: List<String> = emptyList(),
    val next: String? = null,
)

internal sealed interface ConnectionsMessage {
    data class Failed(val error: AppError) : ConnectionsMessage
}

@Immutable
internal data class ConnectionsUiState(
    val tab: ConnectionTab = ConnectionTab.Requests,
    val tabs: Map<ConnectionTab, TabState> = emptyMap(),
    /** Handle with a request, unfollow or unblock in flight. */
    val busyHandle: String? = null,
    val message: ConnectionsMessage? = null,
) {
    val current: TabState get() = tabs[tab] ?: TabState()
}

internal sealed interface ConnectionsEvent {
    data class SelectTab(val tab: ConnectionTab) : ConnectionsEvent

    data object Retry : ConnectionsEvent

    data object LoadMore : ConnectionsEvent

    data class Accept(val handle: String) : ConnectionsEvent

    data class Decline(val handle: String) : ConnectionsEvent

    data class Unfollow(val handle: String) : ConnectionsEvent

    data class Unblock(val handle: String) : ConnectionsEvent

    data object MessageShown : ConnectionsEvent
}

/**
 * Follow requests (accept or decline), followers, following (unfollow) and blocked users (unblock). Each tab loads when first
 * opened. Rows are handles only. Telemetry: `social_follow` with `state: none` for an unfollow.
 */
@HiltViewModel
internal class ConnectionsViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val social: SocialRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(ConnectionsUiState(tab = ConnectionTab.from(savedState.get<String>(ARG_TAB))))
        val state: StateFlow<ConnectionsUiState> = _state.asStateFlow()

        init {
            loadFirst(_state.value.tab)
        }

        fun onEvent(event: ConnectionsEvent) {
            when (event) {
                is ConnectionsEvent.SelectTab -> {
                    _state.update { it.copy(tab = event.tab) }
                    if (_state.value.tabs[event.tab]?.loaded != true) loadFirst(event.tab)
                }

                ConnectionsEvent.Retry -> {
                    loadFirst(_state.value.tab)
                }

                ConnectionsEvent.LoadMore -> {
                    loadMore()
                }

                is ConnectionsEvent.Accept -> {
                    act(event.handle, ConnectionTab.Requests, social::acceptRequest)
                }

                is ConnectionsEvent.Decline -> {
                    act(event.handle, ConnectionTab.Requests, social::declineRequest)
                }

                is ConnectionsEvent.Unfollow -> {
                    act(event.handle, ConnectionTab.Following, social::unfollow) {
                        telemetry.emit(TelemetryEvents.SOCIAL_FOLLOW, mapOf("state" to "none"))
                    }
                }

                is ConnectionsEvent.Unblock -> {
                    act(event.handle, ConnectionTab.Blocked, social::unblock)
                }

                ConnectionsEvent.MessageShown -> {
                    _state.update { it.copy(message = null) }
                }
            }
        }

        private fun setTab(tab: ConnectionTab, change: (TabState) -> TabState) {
            _state.update { it.copy(tabs = it.tabs + (tab to change(it.tabs[tab] ?: TabState()))) }
        }

        private fun loadFirst(tab: ConnectionTab) {
            setTab(tab) { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val page = fetch(tab, null)) {
                    is AppResult.Success -> setTab(tab) { TabState(loaded = true, handles = page.value.first, next = page.value.second) }
                    is AppResult.Failure -> setTab(tab) { it.copy(loading = false, error = page.error) }
                }
            }
        }

        private fun loadMore() {
            val tab = _state.value.tab
            val cursor = _state.value.current.next ?: return
            if (_state.value.current.loadingMore) return
            setTab(tab) { it.copy(loadingMore = true) }
            viewModelScope.launch {
                when (val page = fetch(tab, cursor)) {
                    is AppResult.Success -> {
                        setTab(tab) { it.copy(loadingMore = false, handles = (it.handles + page.value.first).distinct(), next = page.value.second) }
                    }

                    is AppResult.Failure -> {
                        setTab(tab) { it.copy(loadingMore = false) }
                        _state.update { it.copy(message = ConnectionsMessage.Failed(page.error)) }
                    }
                }
            }
        }

        /** Handles and the next cursor of one page. */
        private suspend fun fetch(tab: ConnectionTab, cursor: String?): AppResult<Pair<List<String>, String?>> =
            when (tab) {
                ConnectionTab.Requests -> social.followRequests().map { r -> r.requests.map { it.handle } to null }
                ConnectionTab.Followers -> social.followers(cursor).map { p -> p.users.map { it.handle } to p.next }
                ConnectionTab.Following -> social.following(cursor).map { p -> p.users.map { it.handle } to p.next }
                ConnectionTab.Blocked -> social.blocks().map { b -> b.users.map { it.handle } to null }
            }

        private fun act(handle: String, tab: ConnectionTab, call: suspend (String) -> AppResult<Unit>, onDone: () -> Unit = {}) {
            if (_state.value.busyHandle != null) return
            _state.update { it.copy(busyHandle = handle) }
            viewModelScope.launch {
                when (val result = call(handle)) {
                    is AppResult.Success -> {
                        onDone()
                        setTab(tab) { s -> s.copy(handles = s.handles.filterNot { it == handle }) }
                        _state.update { it.copy(busyHandle = null) }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(busyHandle = null, message = ConnectionsMessage.Failed(result.error)) }
                    }
                }
            }
        }

        companion object {
            /** SavedStateHandle key of `ConnectionsRoute.tab`. */
            const val ARG_TAB = "tab"
        }
    }
