package io.github.rupakc.travelplanner.feature.trips.journal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.JournalRepository
import io.github.rupakc.travelplanner.core.data.repository.Outbox
import io.github.rupakc.travelplanner.core.data.repository.OutboxRequest
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.itinerary.PlanItinerary
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEnums
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.trips.CreateEntryRequest
import io.github.rupakc.travelplanner.core.model.trips.Entry
import io.github.rupakc.travelplanner.core.model.trips.JournalMoods
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceRequest
import io.github.rupakc.travelplanner.core.model.trips.TripPlace
import io.github.rupakc.travelplanner.core.model.trips.UpdateEntryRequest
import io.github.rupakc.travelplanner.feature.trips.trip.TripViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** A refused write: [problem] picks our copy, [message] is the backend text for anything else. */
@Immutable
data class JournalFailure(val problem: JournalRules.Problem, val message: String? = null, val places: Boolean = false)

@Immutable
data class JournalUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val rows: List<JournalRules.Row> = emptyList(),
    val places: List<JournalRules.PlaceRow> = emptyList(),
    val draft: String = "",
    val mood: String? = null,
    /** The entry being edited, null when composing a new one. */
    val editingId: Long? = null,
    val saving: Boolean = false,
    val failure: JournalFailure? = null,
) {
    val canSave: Boolean get() = !saving && JournalRules.cleanBody(draft) != null
}

sealed interface JournalEvent {
    data object Retry : JournalEvent

    data class DraftChanged(val text: String) : JournalEvent

    data class MoodChanged(val mood: String?) : JournalEvent

    data object Save : JournalEvent

    data class StartEdit(val entryId: Long) : JournalEvent

    data object CancelEdit : JournalEvent

    data class Delete(val entryId: Long) : JournalEvent

    data class SetVisited(val key: String, val visited: Boolean) : JournalEvent

    /** Star [stars] tapped on a place (the same star again clears the rating). */
    data class Rate(val key: String, val stars: Int) : JournalEvent

    data class SaveNote(val key: String, val note: String) : JournalEvent

    data object DismissFailure : JournalEvent
}

/** Journal tab: entries (offline-capable through the outbox) plus visited/stars/note for the trip's places. */
@HiltViewModel
class JournalViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repo: JournalRepository,
        private val plans: PlansRepository,
        private val outbox: Outbox,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tripId: Long = savedStateHandle.get<Long>(TripViewModel.TRIP_ID_ARG) ?: -1L
        private val _state = MutableStateFlow(JournalUiState())
        val state: StateFlow<JournalUiState> = _state.asStateFlow()

        /** Overridable for tests. */
        internal var newClientId: () -> String = { UUID.randomUUID().toString() }

        private var serverEntries: List<Entry> = emptyList()
        private var localEntries: List<Entry> = emptyList()
        private var serverPlaces: List<TripPlace> = emptyList()
        private var days: List<ScheduledDay> = emptyList()
        private var pending: Set<String> = emptySet()
        private var overlay: Map<String, JournalRules.PlaceRow> = emptyMap()
        private var nextLocalId = -1L
        private var loaded = false
        private val json = Json { encodeDefaults = false }

        init {
            load()
            viewModelScope.launch {
                outbox.pendingKeys().collect { keys ->
                    val mine = keys.filter { it.startsWith("${prefix()}journal:") || it.startsWith("${prefix()}place:") }.toSet()
                    val drained = pending - mine
                    pending = mine
                    if (drained.isNotEmpty() && loaded) {
                        localEntries = localEntries.filterNot { e -> entryKey(e.clientId) in drained }
                        overlay = overlay.filterKeys { k -> placeKey(k) !in drained }
                        load()
                    } else {
                        publish()
                    }
                }
            }
        }

        private fun prefix() = "trip:$tripId:"

        private fun entryKey(clientId: String?) = "${prefix()}journal:$clientId"

        private fun placeKey(key: String) = "${prefix()}place:$key"

        fun onEvent(event: JournalEvent) {
            when (event) {
                JournalEvent.Retry -> load()
                is JournalEvent.DraftChanged -> _state.update { it.copy(draft = event.text.take(JournalRules.MAX_BODY + 1)) }
                is JournalEvent.MoodChanged -> _state.update { it.copy(mood = event.mood?.takeIf { m -> m in JournalMoods.ALL }) }
                JournalEvent.Save -> save()
                is JournalEvent.StartEdit -> startEdit(event.entryId)
                JournalEvent.CancelEdit -> _state.update { it.copy(editingId = null, draft = "", mood = null) }
                is JournalEvent.Delete -> delete(event.entryId)
                is JournalEvent.SetVisited -> placeChange(event.key) { row -> row.copy(visited = event.visited) }
                is JournalEvent.Rate -> placeChange(event.key) { row -> row.copy(stars = JournalRules.starsAfterTap(row.stars, event.stars)) }
                is JournalEvent.SaveNote -> placeChange(event.key) { row -> row.copy(note = event.note.trim().take(JournalRules.MAX_NOTE)) }
                JournalEvent.DismissFailure -> _state.update { it.copy(failure = null) }
            }
        }

        private fun load() {
            _state.update { it.copy(loading = !loaded, failed = false) }
            viewModelScope.launch {
                val (entries, places, plan) =
                    coroutineScope {
                        val e = async { repo.entries(tripId) }
                        val p = async { repo.places(tripId) }
                        val pl = async { plans.get(tripId) }
                        Triple(e.await(), p.await(), pl.await())
                    }
                val e = (entries as? AppResult.Success)?.value
                val p = (places as? AppResult.Success)?.value
                if (e == null && p == null) {
                    _state.update { it.copy(loading = false, failed = !loaded) }
                    return@launch
                }
                e?.let { serverEntries = it }
                p?.let { serverPlaces = it }
                (plan as? AppResult.Success)?.value?.let { days = PlanItinerary.days(it.selections) }
                // A place the server now knows and that is no longer queued needs no local override.
                overlay = overlay.filterKeys { placeKey(it) in pending }
                loaded = true
                publish()
            }
        }

        private fun publish() {
            val pendingClientIds = localEntries.mapNotNull { it.clientId }.filter { entryKey(it) in pending }.toSet()
            val syncedIds = serverEntries.mapNotNull { it.clientId }.toSet()
            val entries = serverEntries + localEntries.filter { it.clientId !in syncedIds }
            val rows = JournalRules.sortEntries(entries, pendingClientIds)
            val placePending = pending.filter { it.startsWith("${prefix()}place:") }.map { it.removePrefix("${prefix()}place:") }.toSet()
            val merged =
                JournalRules.placeRows(days, serverPlaces, placePending).map { row ->
                    overlay[row.key]?.copy(pending = row.key in placePending) ?: row
                }
            val extra = overlay.values.filter { o -> merged.none { it.key == o.key } }.map { it.copy(pending = it.key in placePending) }
            _state.update { it.copy(loading = false, failed = false, rows = rows, places = merged + extra) }
        }

        private fun startEdit(entryId: Long) {
            val entry = (serverEntries + localEntries).firstOrNull { it.id == entryId } ?: return
            if (entryId < 0) return // a queued entry is edited after it has synced
            _state.update { it.copy(editingId = entryId, draft = entry.body, mood = entry.mood, failure = null) }
        }

        private fun save() {
            val s = _state.value
            val body = JournalRules.cleanBody(s.draft)
            if (body == null || s.saving) return
            _state.update { it.copy(saving = true, failure = null) }
            viewModelScope.launch {
                val editing = s.editingId
                if (editing != null) edit(editing, body, s.mood) else add(body, s.mood)
            }
        }

        private suspend fun add(body: String, mood: String?) {
            val request = CreateEntryRequest(newClientId(), body, clock.today().toString(), mood)
            when (val result = repo.createEntry(tripId, request)) {
                is AppResult.Success -> {
                    serverEntries = serverEntries + result.value
                    entryDone("add", queued = false)
                }

                is AppResult.Failure -> {
                    if (result.error is AppError.Network && queue(request)) {
                        entryDone("add", queued = true)
                    } else {
                        failed(result.error, places = false)
                    }
                }
            }
        }

        /** Queues [request] and shows it locally as waiting; false when the outbox refused it. */
        private suspend fun queue(request: CreateEntryRequest): Boolean {
            val queued =
                outbox.enqueue(
                    OutboxRequest(
                        "POST",
                        "api/trips/$tripId/journal",
                        json.encodeToString(CreateEntryRequest.serializer(), request),
                        entryKey(request.clientId),
                    ),
                )
            if (queued !is AppResult.Success) return false
            val local = Entry(nextLocalId--, request.clientId, request.day, request.body, request.mood)
            localEntries = localEntries + local
            pending = pending + entryKey(request.clientId)
            return true
        }

        private suspend fun edit(entryId: Long, body: String, mood: String?) {
            when (val result = repo.updateEntry(tripId, entryId, UpdateEntryRequest(body = body, mood = mood))) {
                is AppResult.Success -> {
                    serverEntries = serverEntries.map { if (it.id == entryId) result.value else it }
                    entryDone("edit", queued = false)
                }

                is AppResult.Failure -> {
                    failed(result.error, places = false)
                }
            }
        }

        private fun entryDone(action: String, queued: Boolean) {
            telemetry.emit(TelemetryEvents.JOURNAL_ACTION, mapOf("action" to action, "queued" to queued))
            _state.update { it.copy(saving = false, draft = "", mood = null, editingId = null) }
            publish()
        }

        private fun delete(entryId: Long) {
            if (entryId < 0) return
            viewModelScope.launch {
                when (val result = repo.deleteEntry(tripId, entryId)) {
                    is AppResult.Success -> {
                        serverEntries = serverEntries.filterNot { it.id == entryId }
                        telemetry.emit(TelemetryEvents.JOURNAL_ACTION, mapOf("action" to "delete", "queued" to false))
                        _state.update { if (it.editingId == entryId) it.copy(editingId = null, draft = "", mood = null) else it }
                        publish()
                    }

                    is AppResult.Failure -> {
                        failed(result.error, places = false)
                    }
                }
            }
        }

        private fun failed(error: AppError, places: Boolean) {
            val code = (error as? AppError.Client)?.code
            val problem = JournalRules.problemOf(code)
            val message = if (problem == JournalRules.Problem.Other) error.userFacingDetail() else null
            _state.update { it.copy(saving = false, failure = JournalFailure(problem, message, places)) }
        }

        private fun placeChange(key: String, change: (JournalRules.PlaceRow) -> JournalRules.PlaceRow) {
            val before = _state.value.places.firstOrNull { it.key == key } ?: return
            val after = change(before)
            if (after == before || !JournalRules.validStars(after.stars)) return
            val request =
                PutPlaceRequest(
                    name = after.name.take(JournalRules.MAX_NAME),
                    kind = after.kind,
                    visited = after.visited,
                    stars = after.stars,
                    note = after.note,
                    day = if (after.visited && !before.visited) clock.today().toString() else after.day,
                    lat = after.lat,
                    lng = after.lng,
                    clearStars = after.stars == null && before.stars != null,
                )
            overlay = overlay + (key to after)
            publish()
            viewModelScope.launch {
                when (val result = repo.putPlace(tripId, key, request)) {
                    is AppResult.Success -> {
                        serverPlaces = serverPlaces.filterNot { it.placeKey == key } + result.value.place
                        overlay = overlay - key
                        emitPlace(before, after)
                        publish()
                    }

                    is AppResult.Failure -> {
                        val queued =
                            result.error is AppError.Network &&
                                outbox.enqueue(
                                    OutboxRequest("PUT", "api/trips/$tripId/places/$key", request.toJsonObject().toString(), placeKey(key)),
                                ) is AppResult.Success
                        if (queued) {
                            pending = pending + placeKey(key)
                            emitPlace(before, after)
                            publish()
                        } else {
                            overlay = overlay - key
                            failed(result.error, places = true)
                            publish()
                        }
                    }
                }
            }
        }

        private fun emitPlace(before: JournalRules.PlaceRow, after: JournalRules.PlaceRow) {
            val kind = after.kind.takeIf { it in TelemetryEnums.PLACE_KIND } ?: "other"
            val action =
                when {
                    after.visited != before.visited -> if (after.visited) "visited" else "unvisited"
                    after.stars != before.stars -> if (after.stars != null) "rate" else "unrate"
                    else -> return // a note edit has no event
                }
            val props = mutableMapOf<String, Any?>("action" to action, "kind" to kind)
            after.stars?.takeIf { action == "rate" }?.let { props["stars"] = it }
            telemetry.emit(TelemetryEvents.PLACE_ACTION, props)
        }
    }
