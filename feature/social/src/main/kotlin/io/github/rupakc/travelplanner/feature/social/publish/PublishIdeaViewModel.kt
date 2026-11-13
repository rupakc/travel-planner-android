package io.github.rupakc.travelplanner.feature.social.publish

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.social.ContentStatus
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import io.github.rupakc.travelplanner.feature.social.feed.CAPTION_MAX
import io.github.rupakc.travelplanner.feature.social.feed.PublishProblem
import io.github.rupakc.travelplanner.feature.social.feed.TITLE_MAX
import io.github.rupakc.travelplanner.feature.social.feed.toPublishProblem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The result of a publish: [held] ideas wait for review and are visible only to their author. */
@Immutable
internal data class Published(val ideaId: Long, val held: Boolean)

@Immutable
internal data class PublishIdeaUiState(
    val kind: IdeaKind = IdeaKind.PLAN,
    val title: String = "",
    val caption: String = "",
    val publishing: Boolean = false,
    /** A guiding refusal (profile, email, limit, content, ...); null for none. */
    val problem: PublishProblem? = null,
    /** Any other failure: backend text, or a generic string when it has none. */
    val error: AppError? = null,
    val published: Published? = null,
) {
    val canPublish: Boolean get() = title.isNotBlank() && !publishing && published == null
}

internal sealed interface PublishIdeaEvent {
    data class TitleChanged(val text: String) : PublishIdeaEvent

    data class CaptionChanged(val text: String) : PublishIdeaEvent

    data object Publish : PublishIdeaEvent
}

/**
 * Publishes (or updates, the server is idempotent per plan and kind) the idea of the caller's plan. Plain text only, so there is
 * nothing to preview as markup. Refusals are mapped to guiding states (`profile_required`, `email_unverified`, `limit_reached`,
 * `content_rejected`, ...); a `held` answer is a notice, not an error. Telemetry: `social_idea_publish` (kind).
 */
@HiltViewModel
internal class PublishIdeaViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val social: SocialRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val planId: Long = savedState.get<Long>(ARG_PLAN_ID) ?: 0L
        private val _state =
            MutableStateFlow(
                PublishIdeaUiState(
                    kind =
                        IdeaKind.from(savedState.get<String>(ARG_KIND)).let {
                            if (it ==
                                IdeaKind.UNKNOWN
                            ) {
                                IdeaKind.PLAN
                            } else {
                                it
                            }
                        },
                ),
            )
        val state: StateFlow<PublishIdeaUiState> = _state.asStateFlow()

        fun onEvent(event: PublishIdeaEvent) {
            when (event) {
                is PublishIdeaEvent.TitleChanged -> _state.update { it.copy(title = event.text.take(TITLE_MAX), problem = null, error = null) }
                is PublishIdeaEvent.CaptionChanged -> _state.update { it.copy(caption = event.text.take(CAPTION_MAX), problem = null, error = null) }
                PublishIdeaEvent.Publish -> publish()
            }
        }

        private fun publish() {
            val current = _state.value
            if (!current.canPublish) return
            _state.update { it.copy(publishing = true, problem = null, error = null) }
            viewModelScope.launch {
                when (val result = social.publishIdea(planId, current.kind, current.title.trim(), current.caption.trim())) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.SOCIAL_IDEA_PUBLISH, mapOf("kind" to current.kind.wire))
                        _state.update { it.copy(publishing = false, published = result.value.toPublished()) }
                    }

                    is AppResult.Failure -> {
                        val problem = result.error.toPublishProblem()
                        _state.update { it.copy(publishing = false, problem = problem, error = if (problem == null) result.error else null) }
                    }
                }
            }
        }

        private fun Idea.toPublished() = Published(id, held = contentStatus == ContentStatus.HELD)

        companion object {
            /** SavedStateHandle keys of `PublishIdeaRoute`. */
            const val ARG_PLAN_ID = "planId"
            const val ARG_KIND = "kind"
        }
    }
