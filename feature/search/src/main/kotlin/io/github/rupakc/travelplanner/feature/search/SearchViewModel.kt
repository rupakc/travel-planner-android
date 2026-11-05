package io.github.rupakc.travelplanner.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.getOrNull
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.profile.FeatureVisibility
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.RecentSearchesRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.data.search.SearchLauncher
import io.github.rupakc.travelplanner.core.domain.profile.quizPace
import io.github.rupakc.travelplanner.core.domain.search.ExtraStop
import io.github.rupakc.travelplanner.core.domain.search.MAX_EXTRA_STOPS
import io.github.rupakc.travelplanner.core.domain.search.RecentSearchSummary
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.TravellerCounts
import io.github.rupakc.travelplanner.core.domain.search.applyDiscoveredDestination
import io.github.rupakc.travelplanner.core.domain.search.applyPreferences
import io.github.rupakc.travelplanner.core.domain.search.budgetText
import io.github.rupakc.travelplanner.core.domain.search.buildSearchRequest
import io.github.rupakc.travelplanner.core.domain.search.describeRecentSearch
import io.github.rupakc.travelplanner.core.domain.search.preferencesChanged
import io.github.rupakc.travelplanner.core.domain.search.preferencesFromForm
import io.github.rupakc.travelplanner.core.domain.search.recentDatesPassed
import io.github.rupakc.travelplanner.core.domain.search.recentSearchToForm
import io.github.rupakc.travelplanner.core.domain.search.toRecentSearch
import io.github.rupakc.travelplanner.core.domain.search.validateSearchForm
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A recent search as its card shows it. */
@Immutable
data class RecentSearchCard(val entry: RecentSearch, val summary: RecentSearchSummary, val datesPassed: Boolean) {
    val id: String get() = entry.id
}

/** "Loaded {route}…" notice after a recent search fills the form (web teal notice). */
@Immutable
data class RecentNotice(val route: String, val datesShifted: Boolean)

/** Known-destination form, mode switcher and recent searches. */
@Immutable
data class SearchUiState(
    val today: LocalDate,
    val mode: SearchMode = SearchMode.KNOWN,
    val visibleModes: List<SearchMode> = SearchMode.entries,
    val form: SearchForm = SearchForm.initial(today),
    val stops: List<ExtraStop> = emptyList(),
    /** Shown after a submit attempt (the web relies on the browser's required/min messages). */
    val issues: List<SearchFormIssue> = emptyList(),
    val recent: List<RecentSearchCard> = emptyList(),
    val recentExpanded: Boolean = true,
    val notice: RecentNotice? = null,
    /** Saved preferences, once loaded (null = not loaded or failed: the form keeps its defaults). */
    val preferences: Preferences? = null,
    /** Set while asking "Start a new plan?" because the current draft has unsaved picks. */
    val confirmNewPlan: Boolean = false,
) {
    val canAddStop: Boolean get() = stops.size < MAX_EXTRA_STOPS
}

/** Everything the known-destination form and the mode switcher can do. */
sealed interface SearchFormEvent {
    data class SelectMode(val mode: SearchMode) : SearchFormEvent

    data class UpdateForm(val form: SearchForm) : SearchFormEvent

    data object AddStop : SearchFormEvent

    data class UpdateStop(val index: Int, val stop: ExtraStop) : SearchFormEvent

    data class RemoveStop(val index: Int) : SearchFormEvent

    data object Submit : SearchFormEvent

    /** The UI language (per-app locale): budgets are read in it ("1.500" is 1500 in Spanish). */
    data class SetLocale(val locale: Locale) : SearchFormEvent

    data class LoadRecent(val id: String) : SearchFormEvent

    data class RerunRecent(val id: String) : SearchFormEvent

    data class RemoveRecent(val id: String) : SearchFormEvent

    data class UndoRemoveRecent(val id: String) : SearchFormEvent

    /** The undo snackbar went away without Undo: delete on the server. */
    data class CommitRemoveRecent(val id: String) : SearchFormEvent

    data object ToggleRecent : SearchFormEvent

    data object DismissNotice : SearchFormEvent

    /** "Start new plan" in the unsaved-picks dialog. */
    data object ConfirmNewPlan : SearchFormEvent

    /** "Keep editing" in the unsaved-picks dialog. */
    data object CancelNewPlan : SearchFormEvent
}

/** One-shot effects for the route. */
sealed interface SearchEffect {
    /** A search was started on [SearchRepository]; go to Results. */
    data object SearchStarted : SearchEffect

    /** A recent search was removed: offer Undo. */
    data class RecentRemoved(val id: String, val route: String) : SearchEffect
}

/**
 * Search tab state (web SearchPage): the known-destination form with preference pre-fill and
 * two-way binding, the mode switcher (chips follow the visible features), recent searches,
 * and the one place every mode starts a search ([startSearch], web `startSearch`).
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val searchRepository: SearchRepository,
        private val searchLauncher: SearchLauncher,
        private val currentPlan: CurrentPlanStore,
        private val preferencesRepository: PreferencesRepository,
        private val recentSearchesRepository: RecentSearchesRepository,
        private val profileRepository: ProfileRepository,
        private val clock: AppClock,
        featureVisibility: FeatureVisibility,
        @ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val route: SearchRoute = runCatching { savedStateHandle.toRoute<SearchRoute>() }.getOrDefault(SearchRoute())

        /** The form as it was before process death (typed fields, stops, mode), if any. */
        private val restored: SavedSearchForm? = savedStateHandle.get<String>(SAVED_FORM_KEY)?.let(SavedSearchForm::decode)

        private val _state =
            MutableStateFlow(
                SearchUiState(today = clock.today(), mode = SearchMode.fromRoute(route.mode)).let { initial ->
                    restored?.let { initial.copy(form = it.form, stops = it.stops, mode = it.mode) } ?: initial
                },
            )
        val state: StateFlow<SearchUiState> = _state.asStateFlow()

        private val effects = Channel<SearchEffect>(Channel.BUFFERED)

        /** One-shot effects (navigation, undo snackbar). */
        val effect: Flow<SearchEffect> = effects.receiveAsFlow()

        /** Recent search ids hidden while their undo snackbar shows. */
        private val pendingRemoval = MutableStateFlow<Set<String>>(emptySet())

        /** Removals already sent to the server. */
        private val deleting: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** While a loaded recent search (or a restored form) owns the form, preferences must not overwrite it (web `loadedRecentRef`). */
        private var loadedRecent = restored != null

        /** The user picked a pace: the onboarding answer must not overwrite it. */
        private var paceTouched = restored != null

        /** Guards against a double tap starting the same search twice. */
        private var lastStartAt: Instant? = null
        private var locale: Locale = Locale.getDefault()

        init {
            combine(featureVisibility.visible(), _state.map { it.mode }.distinctUntilChanged()) { visible, mode -> visibleModes(visible, mode) }
                .onEach { modes -> _state.update { it.copy(visibleModes = modes) } }
                .launchIn(viewModelScope)
            combine(recentSearchesRepository.recent, pendingRemoval) { list, hidden -> list.filterNot { it.id in hidden } }
                .onEach { list -> _state.update { s -> s.copy(recent = list.map(::card)) } }
                .launchIn(viewModelScope)
            viewModelScope.launch { recentSearchesRepository.refresh() }
            _state
                .map { SavedSearchForm(it.form, it.stops, it.mode) }
                .distinctUntilChanged()
                .onEach { savedStateHandle[SAVED_FORM_KEY] = it.encode() }
                .launchIn(viewModelScope)
            viewModelScope.launch {
                val prefs = preferencesRepository.get().getOrNull()
                if (prefs != null) {
                    _state.update { s -> s.copy(preferences = prefs, form = if (loadedRecent) s.form else applyPreferences(s.form, prefs)) }
                }
                if (restored == null) {
                    route.recentSearchId?.let { id ->
                        recentSearchesRepository.recent.first().firstOrNull { it.id == id }?.let(::loadRecent)
                    }
                }
                // Spec §6.1 Q4: the onboarding pace answer seeds the form when nothing else set it.
                if (!loadedRecent && !paceTouched) {
                    quizPace(profileRepository.profile.first().answers)?.let { pace ->
                        if (!paceTouched) _state.update { it.copy(form = it.form.copy(pace = pace)) }
                    }
                }
            }
        }

        override fun onCleared() {
            // Leaving mid-snackbar must not lose the delete: commit what is still pending on the app scope.
            (pendingRemoval.value - deleting).forEach(::deleteOnServer)
        }

        fun onEvent(event: SearchFormEvent) {
            when (event) {
                is SearchFormEvent.SelectMode -> {
                    _state.update { it.copy(mode = event.mode) }
                }

                is SearchFormEvent.UpdateForm -> {
                    if (event.form.pace != _state.value.form.pace) paceTouched = true
                    _state.update { s -> s.copy(form = event.form, issues = s.revalidated(event.form, s.stops)) }
                }

                SearchFormEvent.AddStop -> {
                    updateStops { if (it.size < MAX_EXTRA_STOPS) it + ExtraStop() else it }
                }

                is SearchFormEvent.UpdateStop -> {
                    updateStops { it.mapIndexed { i, s -> if (i == event.index) event.stop else s } }
                }

                is SearchFormEvent.RemoveStop -> {
                    updateStops { it.filterIndexed { i, _ -> i != event.index } }
                }

                SearchFormEvent.Submit -> {
                    submitKnown()
                }

                is SearchFormEvent.SetLocale -> {
                    locale = event.locale
                }

                is SearchFormEvent.LoadRecent -> {
                    recentById(event.id)?.let(::loadRecent)
                }

                is SearchFormEvent.RerunRecent -> {
                    rerunRecent(event.id)
                }

                is SearchFormEvent.RemoveRecent -> {
                    removeRecent(event.id)
                }

                is SearchFormEvent.UndoRemoveRecent -> {
                    undoRemoval(event.id)
                }

                is SearchFormEvent.CommitRemoveRecent -> {
                    commitRemoval(event.id)
                }

                SearchFormEvent.ToggleRecent -> {
                    _state.update { it.copy(recentExpanded = !it.recentExpanded) }
                }

                SearchFormEvent.DismissNotice -> {
                    _state.update { it.copy(notice = null) }
                }

                SearchFormEvent.ConfirmNewPlan -> {
                    confirmNewPlan(confirmed = true)
                }

                SearchFormEvent.CancelNewPlan -> {
                    confirmNewPlan(confirmed = false)
                }
            }
        }

        /** Typeahead source for From/To/stops (`GET /api/airports/search`); failures show no suggestions (web parity). */
        suspend fun airports(query: String): List<Airport> = (searchRepository.airports(query) as? AppResult.Success)?.value.orEmpty()

        /** Typeahead source for nationality (`GET /api/nationalities/search`). */
        suspend fun nationalities(query: String): List<Nationality> =
            (searchRepository.nationalities(query) as? AppResult.Success)?.value.orEmpty()

        /** Surprise me "Plan this trip": fill the known form from the pick and switch to it (web: fills, doesn't submit). */
        fun applyDiscovered(discoverForm: SearchForm, pick: DiscoveredDestination) {
            _state.update { s ->
                s.copy(mode = SearchMode.KNOWN, form = applyDiscoveredDestination(s.form, discoverForm, pick), stops = emptyList(), issues = emptyList())
            }
        }

        /**
         * Compare "Plan this trip": only the fields Compare shows come from the compared request; the rest
         * (permits, visas, accessibility, serendipity) stay as in the known form (web `handlePlanCompared`).
         */
        fun planCompared(request: TravelSearchRequest) {
            val known = _state.value.form
            val form =
                known.copy(
                    origin = request.origin,
                    destination = request.destination,
                    departureDate = request.departureDate,
                    returnDate = request.returnDate.orEmpty(),
                    nationality = request.nationality,
                    interests = request.interests,
                    budgetUsd = request.budgetUsd?.let(::budgetText).orEmpty(),
                    travellers = TravellerCounts.of(request.adults, request.children, request.seniors, request.infants) ?: known.travellers,
                )
            _state.update { it.copy(form = form, stops = emptyList(), mode = SearchMode.KNOWN, issues = emptyList()) }
            startSearch(buildSearchRequest(form, emptyList(), locale), form)
        }

        /** "From a link" plan: the import's form and stops run as a normal search; preferences keep the known form's interests. */
        fun planImport(form: SearchForm, stops: List<ExtraStop>) {
            startSearch(buildSearchRequest(form, stops, locale), form, preferenceInterests = _state.value.form.interests)
        }

        private fun SearchUiState.revalidated(form: SearchForm, stops: List<ExtraStop>): List<SearchFormIssue> =
            if (issues.isEmpty()) emptyList() else validateSearchForm(form, stops, clock.today(), locale)

        private fun updateStops(transform: (List<ExtraStop>) -> List<ExtraStop>) =
            _state.update { s ->
                val stops = transform(s.stops)
                s.copy(stops = stops, issues = s.revalidated(s.form, stops))
            }

        /** Today, read at use (a tab left open past midnight must not validate against yesterday). */
        private fun today(): LocalDate = clock.today().also { t -> if (_state.value.today != t) _state.update { it.copy(today = t) } }

        private fun submitKnown() {
            val today = today()
            val s = _state.value
            val issues = validateSearchForm(s.form, s.stops, today, locale)
            _state.update { it.copy(issues = issues) }
            if (issues.isEmpty()) startSearch(buildSearchRequest(s.form, s.stops, locale), s.form)
        }

        /**
         * Web `startSearch`: write changed preference fields back (two-way binding), save the recent
         * search, start the app-wide stream and go to Results.
         */
        private fun startSearch(request: TravelSearchRequest, form: SearchForm, preferenceInterests: List<String> = form.interests) {
            val draft = currentPlan.draft.value
            if (draft.dirty && !draft.selections.isEmpty) {
                // A new search starts a new plan (web runSearch); ask before dropping unsaved picks.
                pendingStart = PendingStart(request, form, preferenceInterests)
                _state.update { it.copy(confirmNewPlan = true) }
                return
            }
            launch(request, form, preferenceInterests)
        }

        private fun launch(request: TravelSearchRequest, form: SearchForm, preferenceInterests: List<String>) {
            val now = clock.now()
            val last = lastStartAt
            if (last != null && Duration.between(last, now) < DOUBLE_SUBMIT_WINDOW) return
            lastStartAt = now
            val prefs = _state.value.preferences
            if (prefs != null && preferencesChanged(prefs, form, locale, preferenceInterests)) {
                val updated = preferencesFromForm(prefs, form, locale, preferenceInterests)
                _state.update { it.copy(preferences = updated) }
                viewModelScope.launch { preferencesRepository.update { preferencesFromForm(it, form, locale, preferenceInterests) } }
            }
            viewModelScope.launch { recentSearchesRepository.add(toRecentSearch(request)) }
            loadedRecent = false
            _state.update { it.copy(notice = null, issues = emptyList()) }
            searchLauncher.startNewSearch(request)
            effects.trySend(SearchEffect.SearchStarted)
        }

        private class PendingStart(val request: TravelSearchRequest, val form: SearchForm, val interests: List<String>)

        private var pendingStart: PendingStart? = null

        private fun confirmNewPlan(confirmed: Boolean) {
            val pending = pendingStart
            pendingStart = null
            _state.update { it.copy(confirmNewPlan = false) }
            if (confirmed && pending != null) launch(pending.request, pending.form, pending.interests)
        }

        private fun recentById(id: String): RecentSearch? = _state.value.recent.firstOrNull { it.id == id }?.entry

        private fun loadRecent(entry: RecentSearch) {
            val restored = recentSearchToForm(entry.search, today(), _state.value.form)
            loadedRecent = true
            _state.update {
                it.copy(
                    mode = SearchMode.KNOWN,
                    form = restored.form,
                    stops = restored.stops,
                    issues = emptyList(),
                    notice = RecentNotice(describeRecentSearch(entry.search).route, restored.datesShifted),
                )
            }
        }

        private fun rerunRecent(id: String) {
            val entry = recentById(id) ?: return
            val today = today()
            if (recentDatesPassed(entry.search, today)) return
            val restored = recentSearchToForm(entry.search, today, _state.value.form)
            _state.update { it.copy(form = restored.form, stops = restored.stops, mode = SearchMode.KNOWN) }
            startSearch(buildSearchRequest(restored.form, restored.stops, locale), restored.form)
        }

        private fun removeRecent(id: String) {
            val card = _state.value.recent.firstOrNull { it.id == id } ?: return
            pendingRemoval.update { it + id }
            effects.trySend(SearchEffect.RecentRemoved(id, card.summary.route))
        }

        private fun undoRemoval(id: String) {
            pendingRemoval.update { it - id }
        }

        /** The undo snackbar closed without Undo (dismissed, timed out, or the screen left): delete now. */
        private fun commitRemoval(id: String) {
            if (id !in pendingRemoval.value) return
            deleteOnServer(id)
        }

        /** Runs on the application scope so leaving the screen can't cancel it. */
        private fun deleteOnServer(id: String) {
            if (!deleting.add(id)) return
            appScope.launch {
                val result = recentSearchesRepository.delete(id)
                pendingRemoval.update { it - id }
                deleting -= id
                // A failed delete brings the card back from the cache (web: refetch on failure).
                if (result is AppResult.Failure) recentSearchesRepository.refresh()
            }
        }

        private fun card(entry: RecentSearch): RecentSearchCard =
            RecentSearchCard(entry, describeRecentSearch(entry.search, Locale.getDefault()), recentDatesPassed(entry.search, clock.today()))

        private companion object {
            const val SAVED_FORM_KEY = "search_form"
            val DOUBLE_SUBMIT_WINDOW: Duration = Duration.ofSeconds(1)
        }
    }
