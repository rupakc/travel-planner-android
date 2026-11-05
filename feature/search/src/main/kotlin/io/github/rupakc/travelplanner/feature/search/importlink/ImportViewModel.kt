package io.github.rupakc.travelplanner.feature.search.importlink

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.ImportRepository
import io.github.rupakc.travelplanner.core.domain.search.DraftPlace
import io.github.rupakc.travelplanner.core.domain.search.DraftStop
import io.github.rupakc.travelplanner.core.domain.search.ImportAction
import io.github.rupakc.travelplanner.core.domain.search.ImportDraft
import io.github.rupakc.travelplanner.core.domain.search.ImportErrorView
import io.github.rupakc.travelplanner.core.domain.search.ImportInput
import io.github.rupakc.travelplanner.core.domain.search.ImportLimits
import io.github.rupakc.travelplanner.core.domain.search.ImportPlanIssue
import io.github.rupakc.travelplanner.core.domain.search.ImportSearch
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.domain.search.applyPreferences
import io.github.rupakc.travelplanner.core.domain.search.buildImportSearch
import io.github.rupakc.travelplanner.core.domain.search.defaultImportDates
import io.github.rupakc.travelplanner.core.domain.search.detectImportInput
import io.github.rupakc.travelplanner.core.domain.search.importErrorView
import io.github.rupakc.travelplanner.core.domain.search.importNights
import io.github.rupakc.travelplanner.core.domain.search.importPlanIssue
import io.github.rupakc.travelplanner.core.domain.search.previewToDraft
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvePlaceRequest
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the extract call is reading (drives the first progress step and the timing hint). */
enum class ImportSourceKind { URL, TEXT, IMAGES }

/** The staged progress story while extracting (web: steps at 0 / 2.5 / 7 s, elapsed seconds). */
@Immutable
data class ImportBusy(val kind: ImportSourceKind, val step: Int = 0, val elapsedSeconds: Int = 0)

/** "Place it" state of one unresolved place. */
@Immutable
data class TownLookup(val town: String = "", val busy: Boolean = false, val problem: TownProblem? = null)

/** Why "Place it" failed: backend message, or the route already has 3 stops ([stopName] can't be added). */
sealed interface TownProblem {
    data class Message(val text: String?) : TownProblem

    data class TooManyStops(val stopName: String) : TownProblem
}

@Immutable
data class ImportUiState(
    val today: LocalDate,
    val text: String = "",
    val shots: List<PreparedShot> = emptyList(),
    val shotProblems: List<ShotProblem> = emptyList(),
    /** Set when more than 4 screenshots were picked: how many were kept. */
    val keptShots: Int? = null,
    val busy: ImportBusy? = null,
    val error: ImportErrorView? = null,
    val preview: ImportPreview? = null,
    val draft: ImportDraft? = null,
    /** The usual trip fields of the review (seeded from the known form). */
    val trip: SearchForm = SearchForm.initial(today),
    val towns: Map<String, TownLookup> = emptyMap(),
) {
    val input: ImportInput get() = detectImportInput(text)
    val tooLong: Boolean get() = shots.isEmpty() && text.length > ImportLimits.MAX_TEXT
    val sourceKind: ImportSourceKind?
        get() =
            when {
                shots.isNotEmpty() -> ImportSourceKind.IMAGES
                input is ImportInput.Url -> ImportSourceKind.URL
                input is ImportInput.Text -> ImportSourceKind.TEXT
                else -> null
            }
    val canSubmit: Boolean get() = sourceKind != null && !tooLong && busy == null

    /** The search the review would run (null before a preview). */
    val built: ImportSearch?
        get() {
            val p = preview ?: return null
            val d = draft ?: return null
            return buildImportSearch(trip, p, d)
        }
    val nights: Int get() = draft?.let(::importNights) ?: 0
    val planIssue: ImportPlanIssue?
        get() {
            val d = draft ?: return null
            val b = built ?: return null
            return importPlanIssue(trip, b, d, today)
        }

    /** Ticked places in included stops (web `selectedTotal`, for the "first 25" hint). */
    val selectedTotal: Int
        get() {
            val d = draft ?: return 0
            val names = d.includedStops.map { it.name }.toSet()
            return (d.places + d.unresolved).count { it.selected && it.place.destination in names }
        }
}

sealed interface ImportEvent {
    data class TextChanged(val text: String) : ImportEvent

    data class Example(val text: String) : ImportEvent

    data class AddShots(val uris: List<Uri>) : ImportEvent

    data class RemoveShot(val id: Long) : ImportEvent

    data object Submit : ImportEvent

    data object Cancel : ImportEvent

    data class RunAction(val action: ImportAction) : ImportEvent

    data object StartOver : ImportEvent

    data class TogglePlace(val id: String) : ImportEvent

    data class SetPlaceStop(val id: String, val stopName: String?) : ImportEvent

    data class SelectStopPlaces(val stopName: String, val selected: Boolean) : ImportEvent

    data class ToggleStop(val stopName: String) : ImportEvent

    data class ChangeNights(val stopName: String, val delta: Int) : ImportEvent

    data class UpdateTrip(val form: SearchForm) : ImportEvent

    data class TownChanged(val id: String, val town: String) : ImportEvent

    data class PlaceIt(val id: String) : ImportEvent

    data object Plan : ImportEvent
}

sealed interface ImportEffect {
    /** The error card's "Drop a screenshot": open the Photo Picker. */
    data object PickImages : ImportEffect

    /** The reviewed import is ready to run as a normal search. */
    data class PlanReady(val search: ImportSearch) : ImportEffect
}

/**
 * Web "From a link ✨": one smart box (link or text) or 1–4 screenshots → `POST /api/import/extract`
 * with a staged progress story and errors that offer the next step; then the review (tick places,
 * move them between stops, nights per stop, place unplaced ones by town) → a normal search with
 * `must_include` + `import_source`.
 */
@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val importRepository: ImportRepository,
        private val screenshotReader: ScreenshotReader,
        private val clock: AppClock,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ImportUiState(today = clock.today()))
        val state: StateFlow<ImportUiState> = _state.asStateFlow()
        private val effects = Channel<ImportEffect>(Channel.BUFFERED)
        val effect: Flow<ImportEffect> = effects.receiveAsFlow()

        private var extractJob: Job? = null
        private var failedKind: ImportSourceKind? = null
        private var seeded = false

        /** The user edited the trip fields: late preferences must not overwrite them. */
        private var tripTouched = false
        private var shotSeq = 0L

        /** Once: the trip fields start from the known form; [importUrl] (chat link, share intent) is read straight away. */
        fun seed(known: SearchForm, importUrl: String?) {
            if (seeded) return
            seeded = true
            _state.update { it.copy(trip = known.copy(mustInclude = null, importSource = null)) }
            val url = importUrl?.trim()?.takeIf { detectImportInput(it) is ImportInput.Url && it.startsWith("http", ignoreCase = true) }
            if (url != null) {
                _state.update { it.copy(text = url) }
                start(ImportRequest(url = url), ImportSourceKind.URL)
            }
        }

        /** Preferences that arrive after seeding pre-fill the trip fields while the user hasn't touched them. */
        fun applyPreferences(prefs: Preferences) {
            if (tripTouched) return
            _state.update { s -> s.copy(trip = applyPreferences(s.trip, prefs)) }
        }

        @Suppress("CyclomaticComplexMethod") // one branch per event
        fun onEvent(event: ImportEvent) {
            when (event) {
                is ImportEvent.TextChanged -> {
                    _state.update { it.copy(text = event.text, error = null) }
                }

                is ImportEvent.Example -> {
                    _state.update { it.copy(text = event.text, shots = emptyList(), error = null) }
                }

                is ImportEvent.AddShots -> {
                    addShots(event.uris)
                }

                is ImportEvent.RemoveShot -> {
                    _state.update { s -> s.copy(shots = s.shots.filterNot { it.id == event.id }) }
                }

                ImportEvent.Submit -> {
                    submit()
                }

                ImportEvent.Cancel -> {
                    cancel()
                }

                is ImportEvent.RunAction -> {
                    runAction(event.action)
                }

                ImportEvent.StartOver -> {
                    _state.update { it.copy(preview = null, draft = null, towns = emptyMap()) }
                }

                is ImportEvent.TogglePlace -> {
                    updatePlace(event.id) { it.copy(selected = !it.selected) }
                }

                is ImportEvent.SetPlaceStop -> {
                    updatePlace(event.id) { it.copy(place = it.place.copy(destination = event.stopName)) }
                }

                is ImportEvent.SelectStopPlaces -> {
                    updateDraft { d ->
                        d.copy(places = d.places.map { if (it.place.destination == event.stopName) it.copy(selected = event.selected) else it })
                    }
                }

                is ImportEvent.ToggleStop -> {
                    updateDraft { d ->
                        d.copy(
                            stops =
                                d.stops.map {
                                    if (it.name ==
                                        event.stopName
                                    ) {
                                        it.copy(included = !it.included)
                                    } else {
                                        it
                                    }
                                },
                        )
                    }
                }

                is ImportEvent.ChangeNights -> {
                    updateDraft { d ->
                        d.copy(
                            stops =
                                d.stops.map {
                                    if (it.name == event.stopName) it.copy(nights = (it.nights + event.delta).coerceIn(1, ImportLimits.MAX_STOP_NIGHTS)) else it
                                },
                        )
                    }
                }

                is ImportEvent.UpdateTrip -> {
                    tripTouched = true
                    _state.update { it.copy(trip = event.form) }
                }

                is ImportEvent.TownChanged -> {
                    _state.update { s -> s.copy(towns = s.towns + (event.id to TownLookup(town = event.town))) }
                }

                is ImportEvent.PlaceIt -> {
                    placeIt(event.id)
                }

                ImportEvent.Plan -> {
                    plan()
                }
            }
        }

        private fun addShots(uris: List<Uri>) {
            if (uris.isEmpty()) return
            viewModelScope.launch {
                val room = ImportLimits.MAX_IMAGES - _state.value.shots.size
                val problems = mutableListOf<ShotProblem>()
                val added = mutableListOf<PreparedShot>()
                uris.take(room.coerceAtLeast(0)).forEach { uri ->
                    screenshotReader.read(uri, ++shotSeq)
                        .onSuccess { added += it }
                        .onFailure { e -> problems += (e as? ShotProblemException)?.problem ?: ShotProblem.Unreadable(uri.lastPathSegment.orEmpty()) }
                }
                _state.update { s ->
                    s.copy(
                        shots = (s.shots + added).take(ImportLimits.MAX_IMAGES),
                        shotProblems = problems,
                        keptShots = room.coerceAtLeast(0).takeIf { uris.size > it },
                        error = if (added.isNotEmpty()) null else s.error,
                    )
                }
            }
        }

        private fun submit() {
            val s = _state.value
            if (!s.canSubmit) return
            when (s.sourceKind) {
                ImportSourceKind.IMAGES -> start(ImportRequest(images = s.shots.map { it.image }), ImportSourceKind.IMAGES)
                ImportSourceKind.URL -> start(ImportRequest(url = (s.input as ImportInput.Url).url), ImportSourceKind.URL)
                ImportSourceKind.TEXT -> start(ImportRequest(text = s.text.trim()), ImportSourceKind.TEXT)
                null -> Unit
            }
        }

        private fun start(request: ImportRequest, kind: ImportSourceKind) {
            extractJob?.cancel()
            _state.update { it.copy(error = null, busy = ImportBusy(kind)) }
            val ticker = viewModelScope.launch { tick() }
            extractJob =
                viewModelScope.launch {
                    val result = importRepository.extract(request)
                    ticker.cancel()
                    when (result) {
                        is AppResult.Success -> {
                            showPreview(result.value)
                        }

                        is AppResult.Failure -> {
                            failedKind = kind
                            _state.update { it.copy(busy = null, error = importErrorView(result.error)) }
                        }
                    }
                }
            extractJob?.invokeOnCompletion { ticker.cancel() }
        }

        /** The story: step 2 at 2.5 s, step 3 at 7 s; the elapsed seconds tick once a second. */
        private suspend fun tick() {
            var elapsedMs = 0L
            while (viewModelScope.isActive) {
                delay(TICK_MS)
                elapsedMs += TICK_MS
                val step = STEP_AT_MS.count { elapsedMs >= it } - 1
                _state.update { s -> s.copy(busy = s.busy?.copy(step = step, elapsedSeconds = (elapsedMs / MS_PER_SECOND).toInt())) }
            }
        }

        private fun showPreview(preview: ImportPreview) {
            val today = clock.today()
            _state.update { s ->
                val (departure, _) = defaultImportDates(today, preview.suggestedDays)
                s.copy(
                    today = today,
                    busy = null,
                    preview = preview,
                    draft = previewToDraft(preview),
                    trip = s.trip.copy(departureDate = departure),
                    towns = emptyMap(),
                )
            }
        }

        private fun cancel() {
            extractJob?.cancel()
            extractJob = null
            _state.update { it.copy(busy = null) }
        }

        private fun runAction(action: ImportAction) {
            _state.update { it.copy(error = null) }
            when (action) {
                // A failed link makes room for its text; pasted text is kept to trim.
                ImportAction.TEXT -> _state.update { s -> s.copy(text = if (failedKind == ImportSourceKind.URL) "" else s.text, shots = emptyList()) }

                ImportAction.IMAGES -> effects.trySend(ImportEffect.PickImages)

                ImportAction.RETRY -> submit()
            }
        }

        private fun updateDraft(transform: (ImportDraft) -> ImportDraft) = _state.update { s -> s.copy(draft = s.draft?.let(transform)) }

        private fun updatePlace(id: String, transform: (DraftPlace) -> DraftPlace) =
            updateDraft { d ->
                d.copy(
                    places = d.places.map { if (it.id == id) transform(it) else it },
                    unresolved = d.unresolved.map { if (it.id == id) transform(it) else it },
                )
            }

        /** Web `UnresolvedRow.placeIt` + `placeResolved`: resolve by the typed town; a new stop only while the route has room. */
        private fun placeIt(id: String) {
            val s = _state.value
            val draft = s.draft ?: return
            val place = draft.unresolved.firstOrNull { it.id == id }?.place ?: return
            val lookup = s.towns[id] ?: TownLookup()
            if (lookup.town.isBlank() || lookup.busy) return
            setTown(id) { it.copy(busy = true, problem = null) }
            viewModelScope.launch {
                val request =
                    ResolvePlaceRequest(
                        id = place.id,
                        name = place.name,
                        kind = place.kind,
                        city = lookup.town.trim(),
                        note = place.note?.ifBlank { null },
                        quote = place.quote?.ifBlank { null },
                    )
                when (val result = importRepository.resolvePlace(request)) {
                    is AppResult.Failure -> {
                        setTown(id) { it.copy(busy = false, problem = TownProblem.Message(importErrorView(result.error).message)) }
                    }

                    is AppResult.Success -> {
                        val (resolved, destination) = result.value
                        val current = _state.value.draft ?: return@launch
                        val known = current.stops.any { it.name == destination.name }
                        if (!known && current.stops.size >= MAX_ROUTE_STOPS) {
                            setTown(id) { it.copy(busy = false, problem = TownProblem.TooManyStops(destination.name)) }
                            return@launch
                        }
                        _state.update { st ->
                            st.copy(
                                draft =
                                    current.copy(
                                        stops = if (known) current.stops else current.stops + DraftStop(destination, included = true),
                                        unresolved = current.unresolved.filterNot { it.id == id },
                                        places = current.places + DraftPlace(resolved, selected = true),
                                    ),
                                towns = st.towns - id,
                            )
                        }
                    }
                }
            }
        }

        private fun setTown(id: String, transform: (TownLookup) -> TownLookup) =
            _state.update { s -> s.copy(towns = s.towns + (id to transform(s.towns[id] ?: TownLookup()))) }

        private fun plan() {
            val today = clock.today()
            if (_state.value.today != today) _state.update { it.copy(today = today) }
            val s = _state.value
            val built = s.built ?: return
            if (s.planIssue != null) return
            effects.trySend(ImportEffect.PlanReady(built))
        }

        private companion object {
            const val TICK_MS = 500L
            const val MS_PER_SECOND = 1000L
            val STEP_AT_MS = listOf(0L, 2500L, 7000L)
            const val MAX_ROUTE_STOPS = 3
        }
    }
