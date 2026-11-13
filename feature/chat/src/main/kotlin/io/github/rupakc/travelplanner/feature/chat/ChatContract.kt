package io.github.rupakc.travelplanner.feature.chat

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.feature.chat.model.ChatTurn

/** What the polite live region announces when an assistant reply finishes. */
@Immutable
sealed interface ReplyAnnouncement {
    /** Monotonic so the same text announced twice still changes the node. */
    val seq: Int

    data class Text(override val seq: Int, val text: String) : ReplyAnnouncement

    data class PlanReady(override val seq: Int, val destination: String?) : ReplyAnnouncement

    data class Failed(override val seq: Int) : ReplyAnnouncement
}

/**
 * Chat screen state. [turns] are the saved messages of the open thread (Room);
 * [streaming] is the assistant turn in flight, kept apart so token deltas only
 * recompose that one bubble.
 */
@Immutable
data class ChatUiState(
    val threads: List<ChatThread> = emptyList(),
    val threadQuery: String = "",
    val currentThreadId: Long? = null,
    val turns: List<ChatTurn> = emptyList(),
    val streaming: ChatTurn? = null,
    val loadingMessages: Boolean = false,
    val selections: PlanSelections = PlanSelections.EMPTY,
    val savingPlanTurnId: Long? = null,
    /** Turn whose itinerary waits for "replace your unsaved My Plan?" confirmation. */
    val confirmReplaceTurnId: Long? = null,
    val announcement: ReplyAnnouncement? = null,
    /** One-tap "Watch price" may show on flight, hotel and activity cards. */
    val watchAvailable: Boolean = false,
    /** The shared plan draft's trip: a card shows "Watch price" only when it belongs to this trip. */
    val watchTrip: TravelSearchRequest? = null,
) {
    val isStreaming: Boolean get() = streaming?.streaming == true

    val currentTitle: String? get() = threads.firstOrNull { it.id == currentThreadId }?.title

    val isEmptyConversation: Boolean get() = turns.isEmpty() && streaming == null && !loadingMessages

    val planCount: Int get() = selections.count
}

/** Everything the chat UI can ask for. */
sealed interface ChatUiEvent {
    /** Send [text] (composer, starter prompt or suggestion chip). */
    data class Send(val text: String) : ChatUiEvent

    data object Stop : ChatUiEvent

    /** Re-ask the question behind the failed assistant turn [turnId]. */
    data class Retry(val turnId: Long) : ChatUiEvent

    data class TogglePlanItem(val item: PlanItem) : ChatUiEvent

    /** One-tap price watch for a flight, hotel or activity card. */
    data class WatchItem(val item: PlanItem) : ChatUiEvent

    data class SaveItinerary(val turnId: Long) : ChatUiEvent

    data object ConfirmReplacePlan : ChatUiEvent

    data object DismissReplacePlan : ChatUiEvent

    data class OpenImport(val url: String) : ChatUiEvent

    data object OpenMyPlan : ChatUiEvent

    data object NewChat : ChatUiEvent

    data class SelectThread(val threadId: Long) : ChatUiEvent

    data class SearchThreads(val query: String) : ChatUiEvent

    data class RenameThread(val threadId: Long, val title: String) : ChatUiEvent

    /** Hides the thread at once; [CommitDelete] or [UndoDelete] follows from the snackbar. */
    data class DeleteThread(val threadId: Long) : ChatUiEvent

    data class UndoDelete(val threadId: Long) : ChatUiEvent

    data class CommitDelete(val threadId: Long) : ChatUiEvent

    /** The live region has read announcement [seq]; it is not announced again. */
    data class AnnouncementShown(val seq: Int) : ChatUiEvent
}

/** One-shot effects for the route (navigation, snackbars). */
sealed interface ChatEffect {
    data class OpenImport(val url: String) : ChatEffect

    data object OpenMyPlan : ChatEffect

    data class OpenPlan(val planId: Long) : ChatEffect

    data class ThreadDeleted(val threadId: Long) : ChatEffect

    data class SaveFailed(val error: AppError) : ChatEffect

    /** A one-tap watch ended as [outcome] (shown as a snackbar). */
    data class QuickWatched(val outcome: QuickWatchOutcome) : ChatEffect
}
