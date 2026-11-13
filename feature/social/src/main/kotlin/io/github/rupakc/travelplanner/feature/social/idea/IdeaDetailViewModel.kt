package io.github.rupakc.travelplanner.feature.social.idea

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
import io.github.rupakc.travelplanner.core.model.social.Comment
import io.github.rupakc.travelplanner.core.model.social.ContentStatus
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.core.model.social.ReportTarget
import io.github.rupakc.travelplanner.feature.social.feed.COMMENT_MAX
import io.github.rupakc.travelplanner.feature.social.feed.IdeaChange
import io.github.rupakc.travelplanner.feature.social.feed.IdeaChanges
import io.github.rupakc.travelplanner.feature.social.feed.isGone
import io.github.rupakc.travelplanner.feature.social.feed.withLiked
import io.github.rupakc.travelplanner.feature.social.feed.withSaved
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class IdeaPhase { Loading, Error, Gone, Content }

/** What a report dialog is about. */
internal sealed interface ReportSubject {
    data object OfIdea : ReportSubject

    data class OfComment(val id: Long) : ReportSubject
}

internal sealed interface IdeaMessage {
    data object Reported : IdeaMessage

    data object Blocked : IdeaMessage

    data object Deleted : IdeaMessage

    /** The comment was accepted but waits for review (`held`); only its author sees it. */
    data object CommentHeld : IdeaMessage

    data class Failed(val error: AppError) : IdeaMessage
}

@Immutable
internal data class IdeaDetailUiState(
    val ideaId: Long = 0,
    val phase: IdeaPhase = IdeaPhase.Loading,
    val error: AppError? = null,
    val idea: Idea? = null,
    val comments: List<Comment> = emptyList(),
    val commentsLoaded: Boolean = false,
    val commentsError: AppError? = null,
    val commentsNext: String? = null,
    val loadingMoreComments: Boolean = false,
    val draft: String = "",
    val posting: Boolean = false,
    /** Idea like/save in flight. */
    val working: Boolean = false,
    /** Comment id with a delete or helpful call in flight. */
    val busyComment: Long? = null,
    val confirmDelete: Boolean = false,
    val confirmBlock: Boolean = false,
    val reporting: ReportSubject? = null,
    val reportSending: Boolean = false,
    val reportError: AppError? = null,
    val message: IdeaMessage? = null,
) {
    val canPost: Boolean get() = draft.isNotBlank() && !posting
}

internal sealed interface IdeaDetailEvent {
    data object Retry : IdeaDetailEvent

    data object ToggleLike : IdeaDetailEvent

    data object ToggleSave : IdeaDetailEvent

    data class DraftChanged(val text: String) : IdeaDetailEvent

    data object PostComment : IdeaDetailEvent

    data object LoadMoreComments : IdeaDetailEvent

    data object RetryComments : IdeaDetailEvent

    data class DeleteComment(val id: Long) : IdeaDetailEvent

    data class ToggleHelpful(val id: Long) : IdeaDetailEvent

    data object AskDelete : IdeaDetailEvent

    data object ConfirmDelete : IdeaDetailEvent

    data object DismissDelete : IdeaDetailEvent

    data object AskBlock : IdeaDetailEvent

    data object ConfirmBlock : IdeaDetailEvent

    data object DismissBlock : IdeaDetailEvent

    data class AskReport(val subject: ReportSubject) : IdeaDetailEvent

    data object DismissReport : IdeaDetailEvent

    data class SendReport(val reason: ReportReason) : IdeaDetailEvent

    data object MessageShown : IdeaDetailEvent
}

internal sealed interface IdeaDetailEffect {
    /** The idea was deleted or its author blocked: leave the screen. */
    data object Leave : IdeaDetailEffect

    /** The idea was saved as a remix: open the new plan. */
    data class OpenPlan(val planId: Long) : IdeaDetailEffect
}

/**
 * One idea and its comments (oldest first, cursor paged). The author can delete it, mark comments helpful and delete any comment;
 * a comment's author can delete theirs. Report (idea or comment) and block go through the confirmed flows. An idea that is
 * hidden, removed or of a blocked author answers 404 and shows one calm "not available" state. Telemetry: `social_idea_open`,
 * `social_idea_like`, `social_idea_save`, `social_comment`, `social_report`, `social_block`.
 */
@HiltViewModel
internal class IdeaDetailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val social: SocialRepository,
        private val telemetry: Telemetry,
        private val changes: IdeaChanges,
    ) : ViewModel() {
        private val ideaId: Long = savedState.get<Long>(ARG_IDEA_ID) ?: 0L
        private val _state = MutableStateFlow(IdeaDetailUiState(ideaId = ideaId))
        val state: StateFlow<IdeaDetailUiState> = _state.asStateFlow()
        private val _effects = Channel<IdeaDetailEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: IdeaDetailEvent) {
            when (event) {
                IdeaDetailEvent.Retry -> load()
                IdeaDetailEvent.ToggleLike -> toggleLike()
                IdeaDetailEvent.ToggleSave -> toggleSave()
                is IdeaDetailEvent.DraftChanged -> _state.update { it.copy(draft = event.text.take(COMMENT_MAX)) }
                IdeaDetailEvent.PostComment -> postComment()
                IdeaDetailEvent.LoadMoreComments -> loadMoreComments()
                IdeaDetailEvent.RetryComments -> loadComments()
                is IdeaDetailEvent.DeleteComment -> deleteComment(event.id)
                is IdeaDetailEvent.ToggleHelpful -> toggleHelpful(event.id)
                IdeaDetailEvent.AskDelete -> _state.update { it.copy(confirmDelete = true) }
                IdeaDetailEvent.DismissDelete -> _state.update { it.copy(confirmDelete = false) }
                IdeaDetailEvent.ConfirmDelete -> deleteIdea()
                IdeaDetailEvent.AskBlock -> _state.update { it.copy(confirmBlock = true) }
                IdeaDetailEvent.DismissBlock -> _state.update { it.copy(confirmBlock = false) }
                IdeaDetailEvent.ConfirmBlock -> block()
                is IdeaDetailEvent.AskReport -> _state.update { it.copy(reporting = event.subject, reportError = null) }
                IdeaDetailEvent.DismissReport -> _state.update { it.copy(reporting = null, reportError = null) }
                is IdeaDetailEvent.SendReport -> report(event.reason)
                IdeaDetailEvent.MessageShown -> _state.update { it.copy(message = null) }
            }
        }

        private fun load() {
            _state.update { it.copy(phase = IdeaPhase.Loading, error = null) }
            viewModelScope.launch {
                when (val result = social.idea(ideaId)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.SOCIAL_IDEA_OPEN, emptyMap())
                        _state.update { it.copy(phase = IdeaPhase.Content, idea = result.value) }
                        loadComments()
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(phase = if (result.error.isGone()) IdeaPhase.Gone else IdeaPhase.Error, error = result.error) }
                    }
                }
            }
        }

        private fun loadComments() {
            _state.update { it.copy(commentsError = null) }
            viewModelScope.launch {
                when (val page = social.comments(ideaId, null)) {
                    is AppResult.Success -> {
                        _state.update { it.copy(comments = page.value.comments, commentsNext = page.value.next, commentsLoaded = true) }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(commentsError = page.error, commentsLoaded = true) }
                    }
                }
            }
        }

        private fun loadMoreComments() {
            val cursor = _state.value.commentsNext ?: return
            if (_state.value.loadingMoreComments) return
            _state.update { it.copy(loadingMoreComments = true) }
            viewModelScope.launch {
                when (val page = social.comments(ideaId, cursor)) {
                    is AppResult.Success -> {
                        _state.update { st ->
                            val known = st.comments.mapTo(HashSet()) { it.id }
                            st.copy(
                                loadingMoreComments = false,
                                comments = st.comments + page.value.comments.filter { known.add(it.id) },
                                commentsNext = page.value.next,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(loadingMoreComments = false, message = IdeaMessage.Failed(page.error)) }
                    }
                }
            }
        }

        private fun setIdea(change: (Idea) -> Idea) {
            _state.update { st -> st.copy(idea = st.idea?.let(change)) }
            _state.value.idea?.let { changes.publish(IdeaChange.Updated(it)) }
        }

        private fun toggleLike() {
            val idea = _state.value.idea ?: return
            if (_state.value.working) return
            val target = !idea.liked
            _state.update { it.copy(working = true) }
            setIdea { it.withLiked(target) }
            viewModelScope.launch {
                when (val result = social.setLiked(ideaId, target)) {
                    is AppResult.Success -> {
                        if (target) telemetry.emit(TelemetryEvents.SOCIAL_IDEA_LIKE, emptyMap())
                        _state.update { it.copy(working = false) }
                    }

                    is AppResult.Failure -> {
                        setIdea { it.withLiked(!target) }
                        _state.update { it.copy(working = false, message = IdeaMessage.Failed(result.error)) }
                    }
                }
            }
        }

        private fun toggleSave() {
            val idea = _state.value.idea ?: return
            if (_state.value.working) return
            _state.update { it.copy(working = true) }
            viewModelScope.launch {
                if (!idea.saved) {
                    when (val result = social.saveIdea(ideaId)) {
                        is AppResult.Success -> {
                            setIdea { it.withSaved(true) }
                            _state.update { it.copy(working = false) }
                            telemetry.emit(TelemetryEvents.SOCIAL_IDEA_SAVE, emptyMap())
                            _effects.send(IdeaDetailEffect.OpenPlan(result.value))
                        }

                        is AppResult.Failure -> {
                            _state.update { it.copy(working = false, message = IdeaMessage.Failed(result.error)) }
                        }
                    }
                } else {
                    when (val result = social.unsaveIdea(ideaId)) {
                        is AppResult.Success -> {
                            setIdea { it.withSaved(false) }
                            _state.update { it.copy(working = false) }
                        }

                        is AppResult.Failure -> {
                            _state.update { it.copy(working = false, message = IdeaMessage.Failed(result.error)) }
                        }
                    }
                }
            }
        }

        private fun postComment() {
            val text = _state.value.draft.trim()
            if (text.isEmpty() || _state.value.posting) return
            _state.update { it.copy(posting = true) }
            viewModelScope.launch {
                when (val result = social.comment(ideaId, text)) {
                    is AppResult.Success -> {
                        val comment = result.value
                        telemetry.emit(TelemetryEvents.SOCIAL_COMMENT, emptyMap())
                        _state.update { st ->
                            st.copy(
                                posting = false,
                                draft = "",
                                comments = st.comments + comment,
                                idea = st.idea?.copy(comments = (st.idea.comments ?: 0) + if (comment.contentStatus == ContentStatus.HELD) 0 else 1),
                                message = if (comment.contentStatus == ContentStatus.HELD) IdeaMessage.CommentHeld else st.message,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(posting = false, message = IdeaMessage.Failed(result.error)) }
                    }
                }
            }
        }

        private fun deleteComment(id: Long) {
            if (_state.value.busyComment != null) return
            _state.update { it.copy(busyComment = id) }
            viewModelScope.launch {
                when (val result = social.deleteComment(id)) {
                    is AppResult.Success -> {
                        _state.update { st ->
                            st.copy(
                                busyComment = null,
                                comments = st.comments.filterNot { it.id == id },
                                idea = st.idea?.copy(comments = ((st.idea.comments ?: 1) - 1).coerceAtLeast(0)),
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        val gone = result.error.isGone()
                        _state.update { st ->
                            st.copy(
                                busyComment = null,
                                comments = if (gone) st.comments.filterNot { it.id == id } else st.comments,
                                message = IdeaMessage.Failed(result.error),
                            )
                        }
                    }
                }
            }
        }

        private fun toggleHelpful(id: Long) {
            val comment = _state.value.comments.firstOrNull { it.id == id } ?: return
            if (_state.value.busyComment != null || _state.value.idea?.mine != true) return
            _state.update { it.copy(busyComment = id) }
            viewModelScope.launch {
                when (val result = social.setHelpful(id, !comment.helpful)) {
                    is AppResult.Success -> {
                        _state.update { st ->
                            st.copy(busyComment = null, comments = st.comments.map { if (it.id == id) it.copy(helpful = !comment.helpful) else it })
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(busyComment = null, message = IdeaMessage.Failed(result.error)) }
                    }
                }
            }
        }

        private fun deleteIdea() {
            if (_state.value.working) return
            _state.update { it.copy(confirmDelete = false, working = true) }
            viewModelScope.launch {
                when (val result = social.deleteIdea(ideaId)) {
                    is AppResult.Success -> {
                        changes.publish(IdeaChange.Deleted(ideaId))
                        _state.update { it.copy(working = false, message = IdeaMessage.Deleted) }
                        _effects.send(IdeaDetailEffect.Leave)
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(working = false, message = IdeaMessage.Failed(result.error)) }
                    }
                }
            }
        }

        private fun block() {
            val handle = _state.value.idea?.author?.handle ?: return
            if (_state.value.working) return
            _state.update { it.copy(confirmBlock = false, working = true) }
            viewModelScope.launch {
                when (val result = social.block(handle)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.SOCIAL_BLOCK, emptyMap())
                        changes.publish(IdeaChange.AuthorBlocked(handle))
                        _state.update { it.copy(working = false, message = IdeaMessage.Blocked) }
                        _effects.send(IdeaDetailEffect.Leave)
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(working = false, message = IdeaMessage.Failed(result.error)) }
                    }
                }
            }
        }

        private fun report(reason: ReportReason) {
            val subject = _state.value.reporting ?: return
            if (_state.value.reportSending) return
            _state.update { it.copy(reportSending = true, reportError = null) }
            val (target, id) =
                when (subject) {
                    ReportSubject.OfIdea -> ReportTarget.IDEA to ideaId.toString()
                    is ReportSubject.OfComment -> ReportTarget.COMMENT to subject.id.toString()
                }
            viewModelScope.launch {
                when (val result = social.report(target, id, reason)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.SOCIAL_REPORT, mapOf("target" to target.wire, "reason" to reason.wire))
                        _state.update { it.copy(reportSending = false, reporting = null, message = IdeaMessage.Reported) }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(reportSending = false, reportError = result.error) }
                    }
                }
            }
        }

        companion object {
            /** SavedStateHandle key of `IdeaRoute.ideaId`. */
            const val ARG_IDEA_ID = "ideaId"
        }
    }
