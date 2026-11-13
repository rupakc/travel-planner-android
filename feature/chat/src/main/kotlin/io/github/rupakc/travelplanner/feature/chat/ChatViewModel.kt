package io.github.rupakc.travelplanner.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.alerts.PlanWatchOffers
import io.github.rupakc.travelplanner.core.data.alerts.QuickWatcher
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.offline.PlanJourney
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.ChatRepository
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchSpecs
import io.github.rupakc.travelplanner.core.domain.chat.applyPlanAction
import io.github.rupakc.travelplanner.core.domain.chat.buildApiHistory
import io.github.rupakc.travelplanner.core.domain.chat.chatItinerarySlots
import io.github.rupakc.travelplanner.core.domain.chat.chatItinerarySnapshot
import io.github.rupakc.travelplanner.core.domain.chat.chatTripSearchRequest
import io.github.rupakc.travelplanner.core.domain.chat.parseFollowUps
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.searchKey
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.feature.chat.model.ChatStreamReducer
import io.github.rupakc.travelplanner.feature.chat.model.ChatTurn
import io.github.rupakc.travelplanner.feature.chat.model.StreamState
import io.github.rupakc.travelplanner.feature.chat.model.TurnError
import io.github.rupakc.travelplanner.feature.chat.model.encodeExtras
import io.github.rupakc.travelplanner.feature.chat.model.toTurn
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Chat (web ChatPage): threads in Room, one streamed assistant turn at a time.
 *
 * Each turn resends the whole history ([buildApiHistory]), the shared My Plan
 * selections, the latest sections the thread received and the thread's stored
 * `session_context` (the server is stateless). `plan_action` events edit
 * [CurrentPlanStore] exactly like the web's `applyPlanAction`.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val chat: ChatRepository,
        private val planStore: CurrentPlanStore,
        private val journey: PlanJourney,
        private val dispatchers: AppDispatchers,
        @param:ApplicationScope private val appScope: CoroutineScope,
        private val quickWatcher: QuickWatcher,
        private val clock: AppClock,
        private val offers: PlanWatchOffers,
    ) : ViewModel() {
        private val currentThread = MutableStateFlow<Long?>(null)
        private val query = MutableStateFlow("")
        private val hiddenThreads = MutableStateFlow<Set<Long>>(emptySet())
        private val streamingTurn = MutableStateFlow<ChatTurn?>(null)
        private val local = MutableStateFlow(LocalState())
        private val effects = Channel<ChatEffect>(Channel.BUFFERED)

        /** Navigation and snackbar effects, consumed once by the route. */
        val effectFlow: Flow<ChatEffect> = effects.receiveAsFlow()

        private var streamJob: Job? = null
        private var sessionContext: JsonObject = EmptyJsonObject
        private var opened = false
        private var announcementSeq = 0

        private val messages: Flow<MessagesLoad> =
            currentThread.flatMapLatest { id ->
                if (id == null) {
                    flowOf(MessagesLoad(null, emptyList(), loading = false))
                } else {
                    chat.messages(id)
                        .map { list -> MessagesLoad(id, list.map { it.toTurn() }, loading = false) }
                        .flowOn(dispatchers.default)
                        .onStart { emit(MessagesLoad(id, emptyList(), loading = true)) }
                }
            }

        /** Threads whose messages contain the query (Room LIKE search), empty for a blank query. */
        private val contentMatches: Flow<Set<Long>> =
            query.debounce(SEARCH_DEBOUNCE_MILLIS).flatMapLatest { q -> if (q.isBlank()) flowOf(emptySet()) else chat.threadIdsMatching(q.trim()) }
                // Titles filter at once; content matches join after the debounce.
                .onStart { emit(emptySet()) }

        private val threadList =
            combine(chat.threads, query, hiddenThreads, contentMatches) { threads, q, hidden, matches ->
                threads.filter { it.id !in hidden && (q.isBlank() || it.title.contains(q.trim(), ignoreCase = true) || it.id in matches) }
            }

        val uiState: StateFlow<ChatUiState> =
            combine(threadList, messages, streamingTurn, planStore.draft, local) { threads, load, streaming, draft, l ->
                // A finished turn is dropped once Room shows it (no flicker between the two).
                val visibleStreaming = streaming?.takeUnless { s -> s.id != ChatTurn.PENDING_ID && load.turns.any { it.id == s.id } }
                ChatUiState(
                    threads = threads,
                    threadQuery = l.query,
                    currentThreadId = load.threadId,
                    turns = load.turns,
                    streaming = visibleStreaming,
                    loadingMessages = load.loading,
                    selections = draft.selections,
                    savingPlanTurnId = l.savingPlanTurnId,
                    confirmReplaceTurnId = l.confirmReplaceTurnId,
                    announcement = l.announcement,
                    watchAvailable = l.watchAvailable,
                    watchTrip = draft.searchRequest,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatUiState())

        /**
         * Opens [threadId] (from the route), or the most recent thread when null. Only the first call counts.
         * The id must be one of the signed-in user's threads ([ChatRepository.threads] is per username):
         * any other id (another user's, or a forged route) falls back to the user's latest thread.
         */
        fun open(threadId: Long?) {
            if (opened) return
            opened = true
            viewModelScope.launch {
                val own = chat.threads.first()
                val id = threadId?.takeIf { wanted -> own.any { it.id == wanted } } ?: own.firstOrNull()?.id
                if (currentThread.value == null && id != null) switchTo(id)
            }
        }

        init {
            // One-tap "Watch price" on cards shows only with live prices and Alerts 2.0 (the Results gate).
            viewModelScope.launch { local.update { it.copy(watchAvailable = runCatching { quickWatcher.available() }.getOrDefault(false)) } }
        }

        fun onEvent(event: ChatUiEvent) {
            when (event) {
                is ChatUiEvent.WatchItem -> watchItem(event.item)
                is ChatUiEvent.Send -> send(event.text)
                ChatUiEvent.Stop -> stop()
                is ChatUiEvent.Retry -> retry(event.turnId)
                is ChatUiEvent.TogglePlanItem -> planStore.toggle(event.item)
                is ChatUiEvent.SaveItinerary -> requestSaveItinerary(event.turnId)
                ChatUiEvent.ConfirmReplacePlan -> local.value.confirmReplaceTurnId?.let { saveItinerary(it) }
                ChatUiEvent.DismissReplacePlan -> local.update { it.copy(confirmReplaceTurnId = null) }
                is ChatUiEvent.OpenImport -> effects.trySend(ChatEffect.OpenImport(event.url))
                ChatUiEvent.OpenMyPlan -> effects.trySend(ChatEffect.OpenMyPlan)
                ChatUiEvent.NewChat -> newChat()
                is ChatUiEvent.SelectThread -> if (!isStreaming()) viewModelScope.launch { switchTo(event.threadId) }
                is ChatUiEvent.SearchThreads -> setQuery(event.query)
                is ChatUiEvent.RenameThread -> rename(event.threadId, event.title)
                is ChatUiEvent.DeleteThread -> delete(event.threadId)
                is ChatUiEvent.UndoDelete -> undoDelete(event.threadId)
                is ChatUiEvent.CommitDelete -> commitDelete(event.threadId)
                is ChatUiEvent.AnnouncementShown -> local.update { if (it.announcement?.seq == event.seq) it.copy(announcement = null) else it }
            }
        }

        /** One-tap watch for a flight, hotel or activity card, built from the plan draft's search (surface `chat`). */
        private fun watchItem(item: PlanItem) {
            viewModelScope.launch {
                val trip = planStore.draft.value.searchRequest
                val draft = if (QuickWatchSpecs.matchesTrip(item, trip)) QuickWatchSpecs.forItem(item, trip, clock.today()) else null
                val outcome =
                    if (draft == null) {
                        QuickWatchOutcome.Invalid(null)
                    } else {
                        quickWatcher.watch(draft.kind, draft.title, draft.subtitle, draft.spec, planStore.draft.value.planId, "chat")
                    }
                effects.trySend(ChatEffect.QuickWatched(outcome))
            }
        }

        private fun isStreaming() = streamingTurn.value?.streaming == true

        private suspend fun switchTo(threadId: Long?, keepStreaming: Boolean = false) {
            if (!keepStreaming) streamingTurn.value = null
            sessionContext = threadId?.let { chat.sessionContext(it) } ?: EmptyJsonObject
            currentThread.value = threadId
        }

        private fun newChat() {
            if (isStreaming()) return
            viewModelScope.launch { switchTo(null) }
        }

        private fun setQuery(value: String) {
            query.value = value
            local.update { it.copy(query = value) }
        }

        private fun rename(threadId: Long, title: String) {
            val clean = title.trim().take(MAX_TITLE)
            if (clean.isEmpty()) return
            viewModelScope.launch { chat.renameThread(threadId, clean) }
        }

        private fun delete(threadId: Long) {
            if (isStreaming() && threadId == currentThread.value) return
            hiddenThreads.update { it + threadId }
            if (threadId == currentThread.value) viewModelScope.launch { switchTo(null) }
            // Committed by the route when the undo snackbar is dismissed (or cancelled), or in onCleared.
            effects.trySend(ChatEffect.ThreadDeleted(threadId))
        }

        private fun undoDelete(threadId: Long) {
            hiddenThreads.update { it - threadId }
        }

        /** Deletes on the application scope so the delete survives this screen. */
        private fun commitDelete(threadId: Long) {
            if (threadId !in hiddenThreads.value) return
            appScope.launch {
                chat.deleteThread(threadId)
                hiddenThreads.update { it - threadId }
            }
        }

        override fun onCleared() {
            // Deletes still inside their undo window are committed: the user never undid them.
            val pending = hiddenThreads.value
            if (pending.isNotEmpty()) appScope.launch { pending.forEach { chat.deleteThread(it) } }
            super.onCleared()
        }

        // --- streaming ------------------------------------------------------

        private fun send(text: String) {
            val question = text.trim()
            if (question.isEmpty() || isStreaming()) return
            streamingTurn.value = ChatStreamReducer.start().turn
            streamJob =
                viewModelScope.launch {
                    val existing = currentThread.value
                    val threadId = existing ?: chat.createThread(question.take(MAX_TITLE)).also { switchTo(it, keepStreaming = true) }
                    val earlier = if (existing == null) emptyList() else loadTurns(threadId)
                    val userId = chat.appendMessage(threadId, ChatMessage.ROLE_USER, question)
                    stream(threadId, earlier + ChatTurn(id = userId, role = ChatMessage.ROLE_USER, content = question))
                }.also(::clearPlaceholderOnEnd)
        }

        /** A Stop before the first event leaves no reply to save: drop the placeholder bubble. */
        private fun clearPlaceholderOnEnd(job: Job) {
            job.invokeOnCompletion { streamingTurn.update { turn -> turn?.takeUnless { it.streaming } } }
        }

        /**
         * Web parity: re-asks the question behind the failed reply [turnId] as a new message at
         * the end of the thread, with the full history. The failed reply stays, marked failed.
         */
        private fun retry(turnId: Long) {
            if (isStreaming()) return
            val threadId = currentThread.value ?: return
            viewModelScope.launch {
                val turns = loadTurns(threadId)
                val index = turns.indexOfFirst { it.id == turnId }
                if (index < 0) return@launch
                turns.subList(0, index).lastOrNull { it.isUser }?.let { send(it.content) }
            }
        }

        private suspend fun loadTurns(threadId: Long): List<ChatTurn> =
            withContext(dispatchers.default) { chat.messages(threadId).first().map { it.toTurn() } }

        /** Streams one assistant turn for [history] (ending with the user's question) and saves it. */
        private suspend fun stream(threadId: Long, history: List<ChatTurn>) {
            val request =
                ChatRequest(
                    messages = buildApiHistory(history.map { it.toHistory() }),
                    selections = planStore.draft.value.selections,
                    searchResults = latestSections(history),
                    sessionContext = sessionContext,
                )
            var state = ChatStreamReducer.start()
            try {
                chat.send(request).collect { event ->
                    applySideEffects(threadId, event)
                    state = ChatStreamReducer.reduce(state, event)
                    streamingTurn.value = state.turn
                }
                state = ChatStreamReducer.finish(state)
            } catch (e: CancellationException) {
                state = ChatStreamReducer.stop(state)
                withContext(NonCancellable) { persist(threadId, state) }
                throw e
            } catch (e: AppException) {
                state = ChatStreamReducer.fail(state, e.error)
            }
            persist(threadId, state)
        }

        private suspend fun persist(threadId: Long, state: StreamState) {
            val turn = state.turn.copy(streaming = false)
            streamingTurn.value = turn
            val id = chat.appendMessage(threadId, ChatMessage.ROLE_ASSISTANT, turn.content, encodeExtras(turn))
            streamingTurn.value = turn.copy(id = id)
            announce(turn)
        }

        private suspend fun applySideEffects(threadId: Long, event: ChatEvent) {
            when (event) {
                is ChatEvent.PlanAction -> {
                    planStore.setSelections(applyPlanAction(planStore.draft.value.selections, event))
                }

                is ChatEvent.SessionContextUpdate -> {
                    sessionContext = event.context
                    chat.saveSessionContext(threadId, event.context)
                }

                else -> {
                    Unit
                }
            }
        }

        private fun stop() {
            streamJob?.cancel()
            streamJob = null
        }

        private fun announce(turn: ChatTurn) {
            val seq = ++announcementSeq
            val announcement =
                when {
                    turn.error != null && turn.content.isBlank() -> ReplyAnnouncement.Failed(seq)
                    turn.itinerary != null -> ReplyAnnouncement.PlanReady(seq, turn.tripMap?.cities?.mapNotNull { it.city }?.joinToString(", "))
                    turn.content.isNotBlank() -> ReplyAnnouncement.Text(seq, parseFollowUps(turn.content).body.take(MAX_ANNOUNCE))
                    else -> null
                }
            local.update { it.copy(announcement = announcement ?: it.announcement) }
        }

        // --- itinerary → My Plan ---------------------------------------------

        private fun requestSaveItinerary(turnId: Long) {
            val draft = planStore.draft.value
            if (draft.dirty && !draft.selections.isEmpty) {
                local.update { it.copy(confirmReplaceTurnId = turnId) }
            } else {
                saveItinerary(turnId)
            }
        }

        private fun saveItinerary(turnId: Long) {
            val threadId = currentThread.value ?: return
            local.update { it.copy(confirmReplaceTurnId = null, savingPlanTurnId = turnId) }
            viewModelScope.launch {
                val turn = loadTurns(threadId).firstOrNull { it.id == turnId }
                val itinerary = turn?.itinerary
                if (turn == null || itinerary == null) {
                    local.update { it.copy(savingPlanTurnId = null) }
                    return@launch
                }
                val request = chatTripSearchRequest(turn.sessionContext ?: sessionContext, turn.tripMap?.route)
                val cities = turn.tripMap?.cities?.mapNotNull { it.city }.orEmpty()
                val name = cities.joinToString(" → ").ifBlank { PlanDraft.DEFAULT_NAME }
                // One step (new draft generation), so an in-flight save of the previous draft can't take this one over.
                planStore.replace(
                    PlanDraft(
                        name = name,
                        searchRequest = request,
                        searchKey = request?.searchKey(),
                        selections =
                            PlanSelections.EMPTY.copy(
                                itinerarySlots = chatItinerarySlots(itinerary),
                                itinerarySnapshot = chatItinerarySnapshot(itinerary),
                            ),
                        dirty = true,
                    ),
                )
                when (val result = planStore.save(name)) {
                    is AppResult.Success -> {
                        // Same snapshot as the results screen (emergency card, forex, visa, packing, weather), behind features.trips.
                        journey.onPlanSaved(result.value.id, turn.sections.mapValues { it.value.raw }.let(::JsonObject))
                        offers.markSaved(result.value.id)
                        effects.trySend(ChatEffect.OpenPlan(result.value.id))
                    }

                    is AppResult.Failure -> {
                        effects.trySend(ChatEffect.SaveFailed(result.error))
                    }
                }
                local.update { it.copy(savingPlanTurnId = null) }
            }
        }

        private data class LocalState(
            val query: String = "",
            val savingPlanTurnId: Long? = null,
            val confirmReplaceTurnId: Long? = null,
            val announcement: ReplyAnnouncement? = null,
            val watchAvailable: Boolean = false,
        )

        private data class MessagesLoad(val threadId: Long?, val turns: List<ChatTurn>, val loading: Boolean)

        companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
            private const val MAX_TITLE = 60
            private const val MAX_ANNOUNCE = 280
            private const val SEARCH_DEBOUNCE_MILLIS = 250L

            /** Web `getLatestSearchResults`: the sections of the latest turn that has any. */
            internal fun latestSections(turns: List<ChatTurn>): JsonObject =
                turns.lastOrNull { it.sections.isNotEmpty() }
                    ?.let { turn -> JsonObject(turn.sectionOrder.mapNotNull { name -> turn.sections[name]?.let { name to it.raw } }.toMap()) }
                    ?: EmptyJsonObject
        }
    }

/** True when the turn failed and can be retried. */
internal val ChatTurn.canRetry: Boolean get() = error != null && !isUser

/** Errors other than a stream error with text replace the whole bubble body. */
internal val ChatTurn.showsErrorCopy: Boolean get() = error != null && (error != TurnError.STREAM || content.isBlank())
