package io.github.rupakc.travelplanner.feature.social.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.social.FeedScope
import io.github.rupakc.travelplanner.core.model.social.Idea
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One scope's list: cursor paged ([next] non-null = more). */
@Immutable
internal data class ScopeState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val ideas: List<Idea> = emptyList(),
    val next: String? = null,
)

internal sealed interface FeedMessage {
    /** An idea disappeared (hidden, removed or blocked) while the user acted on it. */
    data object Gone : FeedMessage

    data class Failed(val error: AppError) : FeedMessage
}

@Immutable
internal data class FeedUiState(
    val scope: FeedScope = FeedScope.FOR_YOU,
    val scopes: Map<FeedScope, ScopeState> = emptyMap(),
    /** Idea ids with a like or save call in flight. */
    val busy: Set<Long> = emptySet(),
    val message: FeedMessage? = null,
) {
    val current: ScopeState get() = scopes[scope] ?: ScopeState()
}

internal sealed interface FeedEvent {
    data class SelectScope(val scope: FeedScope) : FeedEvent

    /** Reload the current scope from the first page. */
    data object Refresh : FeedEvent

    data object LoadMore : FeedEvent

    data class ToggleLike(val id: Long) : FeedEvent

    data class ToggleSave(val id: Long) : FeedEvent

    /** The user opened an idea (counted; the screen navigates). */
    data object IdeaOpened : FeedEvent

    data object MessageShown : FeedEvent
}

internal sealed interface FeedEffect {
    /** The idea was saved as a remix: open the new plan. */
    data class OpenPlan(val planId: Long) : FeedEffect
}

/**
 * The ideas feed: `for_you`, `following` and `new`, each loaded the first time it is shown and cursor paged. Like and save are
 * optimistic and roll back on failure; an idea that turns out to be gone leaves the list. Telemetry: `social_feed_view` (scope),
 * `social_idea_open`, `social_idea_like`, `social_idea_save`.
 */
@HiltViewModel
internal class FeedViewModel
    @Inject
    constructor(
        private val social: SocialRepository,
        private val telemetry: Telemetry,
        private val changes: IdeaChanges,
        private val journeyFlags: JourneyFlagsRepository,
    ) : ViewModel() {
        private var off = false

        private val _state = MutableStateFlow(FeedUiState())
        val state: StateFlow<FeedUiState> = _state.asStateFlow()
        private val _effects = Channel<FeedEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        init {
            viewModelScope.launch { changes.changes.collect(::apply) }
            // `features.social` off (a stale deep link): no request, no count; the screen shows its empty state.
            viewModelScope.launch {
                if (journeyFlags.flags().social) {
                    telemetry.emit(TelemetryEvents.SOCIAL_FEED_VIEW, mapOf("scope" to FeedScope.FOR_YOU.wire))
                    loadFirst(FeedScope.FOR_YOU)
                } else {
                    off = true
                }
            }
        }

        fun onEvent(event: FeedEvent) {
            when (event) {
                is FeedEvent.SelectScope -> selectScope(event.scope)
                FeedEvent.Refresh -> loadFirst(_state.value.scope)
                FeedEvent.LoadMore -> loadMore()
                is FeedEvent.ToggleLike -> toggleLike(event.id)
                is FeedEvent.ToggleSave -> toggleSave(event.id)
                FeedEvent.IdeaOpened -> telemetry.emit(TelemetryEvents.SOCIAL_IDEA_OPEN, emptyMap())
                FeedEvent.MessageShown -> _state.update { it.copy(message = null) }
            }
        }

        private fun selectScope(scope: FeedScope) {
            if (off || scope == _state.value.scope) return
            _state.update { it.copy(scope = scope) }
            telemetry.emit(TelemetryEvents.SOCIAL_FEED_VIEW, mapOf("scope" to scope.wire))
            if (_state.value.scopes[scope]?.loaded != true) loadFirst(scope)
        }

        private fun setScope(scope: FeedScope, change: (ScopeState) -> ScopeState) {
            _state.update { it.copy(scopes = it.scopes + (scope to change(it.scopes[scope] ?: ScopeState()))) }
        }

        private fun loadFirst(scope: FeedScope) {
            if (off) return
            setScope(scope) { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val page = social.feed(scope)) {
                    is AppResult.Success -> {
                        setScope(scope) {
                            ScopeState(loaded = true, ideas = mergeIdeas(emptyList(), page.value.ideas), next = page.value.next)
                        }
                    }

                    is AppResult.Failure -> {
                        setScope(scope) { it.copy(loading = false, error = page.error) }
                    }
                }
            }
        }

        private fun loadMore() {
            val scope = _state.value.scope
            val current = _state.value.current
            val cursor = current.next ?: return
            if (off) return
            if (current.loadingMore || current.loading) return
            setScope(scope) { it.copy(loadingMore = true) }
            viewModelScope.launch {
                when (val page = social.feed(scope, cursor)) {
                    is AppResult.Success -> {
                        setScope(scope) { it.copy(loadingMore = false, ideas = mergeIdeas(it.ideas, page.value.ideas), next = page.value.next) }
                    }

                    is AppResult.Failure -> {
                        setScope(scope) { it.copy(loadingMore = false) }
                        _state.update { it.copy(message = FeedMessage.Failed(page.error)) }
                    }
                }
            }
        }

        private fun find(id: Long): Idea? = _state.value.scopes.values.firstNotNullOfOrNull { s -> s.ideas.firstOrNull { it.id == id } }

        private fun replace(id: Long, change: (Idea) -> Idea) {
            _state.update { st ->
                st.copy(scopes = st.scopes.mapValues { (_, s) -> s.copy(ideas = s.ideas.map { if (it.id == id) change(it) else it }) })
            }
        }

        private fun remove(id: Long) {
            _state.update { st -> st.copy(scopes = st.scopes.mapValues { (_, s) -> s.copy(ideas = s.ideas.filterNot { it.id == id }) }) }
        }

        private fun toggleLike(id: Long) {
            val idea = find(id) ?: return
            if (id in _state.value.busy) return
            val target = !idea.liked
            _state.update { it.copy(busy = it.busy + id) }
            replace(id) { it.withLiked(target) }
            viewModelScope.launch {
                when (val result = social.setLiked(id, target)) {
                    is AppResult.Success -> {
                        if (target) telemetry.emit(TelemetryEvents.SOCIAL_IDEA_LIKE, emptyMap())
                        _state.update { it.copy(busy = it.busy - id) }
                    }

                    is AppResult.Failure -> {
                        failed(id, result.error) { it.withLiked(!target) }
                    }
                }
            }
        }

        private fun toggleSave(id: Long) {
            val idea = find(id) ?: return
            if (id in _state.value.busy) return
            val target = !idea.saved
            _state.update { it.copy(busy = it.busy + id) }
            viewModelScope.launch {
                if (target) {
                    when (val result = social.saveIdea(id)) {
                        is AppResult.Success -> {
                            replace(id) { it.withSaved(true) }
                            _state.update { it.copy(busy = it.busy - id) }
                            telemetry.emit(TelemetryEvents.SOCIAL_IDEA_SAVE, emptyMap())
                            _effects.send(FeedEffect.OpenPlan(result.value))
                        }

                        is AppResult.Failure -> {
                            failed(id, result.error) { it }
                        }
                    }
                } else {
                    when (val result = social.unsaveIdea(id)) {
                        is AppResult.Success -> {
                            replace(id) { it.withSaved(false) }
                            _state.update { it.copy(busy = it.busy - id) }
                        }

                        is AppResult.Failure -> {
                            failed(id, result.error) { it }
                        }
                    }
                }
            }
        }

        private fun failed(id: Long, error: AppError, rollback: (Idea) -> Idea) {
            if (error.isGone()) {
                remove(id)
                _state.update { it.copy(busy = it.busy - id, message = FeedMessage.Gone) }
            } else {
                replace(id, rollback)
                _state.update { it.copy(busy = it.busy - id, message = FeedMessage.Failed(error)) }
            }
        }

        private fun apply(change: IdeaChange) {
            when (change) {
                is IdeaChange.Updated -> {
                    replace(change.idea.id) { change.idea }
                }

                is IdeaChange.Deleted -> {
                    remove(change.id)
                }

                is IdeaChange.AuthorBlocked -> {
                    _state.update { st ->
                        st.copy(
                            scopes =
                                st.scopes.mapValues { (_, s) ->
                                    s.copy(ideas = s.ideas.filterNot { it.author.handle.equals(change.handle, ignoreCase = true) })
                                },
                        )
                    }
                }
            }
        }
    }
