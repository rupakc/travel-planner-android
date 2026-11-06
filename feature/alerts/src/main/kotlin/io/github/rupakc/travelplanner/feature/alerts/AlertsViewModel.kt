package io.github.rupakc.travelplanner.feature.alerts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.getOrNull
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.LiveCheckRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.data.search.SearchLauncher
import io.github.rupakc.travelplanner.core.domain.alerts.AlertMode
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTab
import io.github.rupakc.travelplanner.core.domain.alerts.AnyWatch
import io.github.rupakc.travelplanner.core.domain.alerts.CheckOutcome
import io.github.rupakc.travelplanner.core.domain.alerts.KIND_FLIGHT
import io.github.rupakc.travelplanner.core.domain.alerts.WatchStatuses
import io.github.rupakc.travelplanner.core.domain.alerts.checkOutcome
import io.github.rupakc.travelplanner.core.domain.alerts.countByTab
import io.github.rupakc.travelplanner.core.domain.alerts.inTab
import io.github.rupakc.travelplanner.core.domain.alerts.isPast
import io.github.rupakc.travelplanner.core.domain.alerts.mergeWatches
import io.github.rupakc.travelplanner.core.domain.alerts.replanRequest
import io.github.rupakc.travelplanner.core.domain.alerts.routeLabel
import io.github.rupakc.travelplanner.core.domain.alerts.tripReplanRequest
import io.github.rupakc.travelplanner.core.domain.live.LiveError
import io.github.rupakc.travelplanner.core.domain.live.toLiveError
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.LiveCheckResult
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.UpdateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.UpdatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An action running on one card (web `busy`). */
enum class CardAction { CHECK, SAVE, STATUS, DELETE }

/** The latest outcome on one card, read out politely. */
@Immutable
sealed interface CardMessage {
    data class Checked(val outcome: CheckOutcome, val currency: String?, val fare: Boolean) : CardMessage

    /** A backend `detail` (shown as-is) or null → the generic copy for [action]. */
    data class Failed(val action: CardAction, val detail: String?) : CardMessage

    data class Paused(val fare: Boolean) : CardMessage

    data object Resumed : CardMessage

    data object Saved : CardMessage
}

/**
 * "Search the web now" for one watch ([key]). Held in ViewModel memory only: never written to Room or
 * DataStore and never merged into the watch, its price history or its alerts.
 */
@Immutable
data class LiveCheckUi(val key: String, val loading: Boolean = true, val result: LiveCheckResult? = null, val error: LiveError? = null)

/** Alert settings typed in a card's edit panel. */
data class WatchEdit(val mode: AlertMode, val target: Double?, val flexible: Boolean?, val email: Boolean, val targetChanged: Boolean)

@Immutable
data class AlertsUiState(
    val fares: List<FareWatch>? = null,
    val prices: List<PriceWatch>? = null,
    val faresError: String? = null,
    val faresFailed: Boolean = false,
    val pricesFailed: Boolean = false,
    val tab: AlertTab = AlertTab.ALL,
    val query: String = "",
    val busy: Map<String, CardAction> = emptyMap(),
    val messages: Map<String, CardMessage> = emptyMap(),
    /** Card key of a deep-linked watch (highlighted and scrolled to once). */
    val focusKey: String? = null,
    /** `/health`: live prices (SerpAPI) are set up on this server. Null = not known yet. */
    val livePrices: Boolean? = null,
    val emailAvailable: Boolean = false,
    val userEmail: String? = null,
    /** A removed watch the snackbar can still bring back. */
    val undo: AnyWatch? = null,
    val now: Instant = Instant.EPOCH,
    /** Airport code → city for fare routes saved with codes ("LIS" → "Lisbon"); codes not found stay codes. */
    val placeNames: Map<String, String> = emptyMap(),
    /** The open "Search the web now" panel, if any. */
    val liveCheck: LiveCheckUi? = null,
) {
    val loading: Boolean get() = fares == null && prices == null && !faresFailed
    val loaded: Boolean get() = fares != null && prices != null

    /** Both lists loaded, nothing in them and nothing waiting on Undo. */
    val nothingWatched: Boolean get() = loaded && !faresFailed && undo == null && fares.isNullOrEmpty() && prices.isNullOrEmpty()

    val watches: List<AnyWatch> = mergeWatches(fares.orEmpty(), prices.orEmpty())
    val counts: Map<AlertTab, Int> = countByTab(watches)

    /** In the tab and matching the search box (route, title, subtitle, city). */
    val shown: List<AnyWatch> = watches.filter { inTab(it, tab) && it.matches(query, placeNames) }
    val active: List<AnyWatch> get() = shown.filterNot(::isPast)
    val past: List<AnyWatch> get() = shown.filter(::isPast)
}

internal fun AnyWatch.matches(query: String, names: Map<String, String> = emptyMap()): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    val text =
        when (this) {
            is AnyWatch.Fare -> listOfNotNull(routeLabel(watch, names), watch.originLabel, watch.destinationLabel, watch.originCode, watch.destinationCode)
            is AnyWatch.Price -> listOfNotNull(watch.title, watch.subtitle)
        }
    return text.any { it.lowercase(Locale.ROOT).contains(q) }
}

sealed interface AlertsEvent {
    data class SelectTab(val tab: AlertTab) : AlertsEvent

    data class Query(val text: String) : AlertsEvent

    data object Retry : AlertsEvent

    data object RetryPrices : AlertsEvent

    data class Check(val key: String) : AlertsEvent

    data class SetStatus(val key: String, val active: Boolean) : AlertsEvent

    data class Save(val key: String, val edit: WatchEdit) : AlertsEvent

    data class Delete(val key: String) : AlertsEvent

    data object Undo : AlertsEvent

    /** The snackbar closed without Undo: delete for real. */
    data object UndoExpired : AlertsEvent

    data class Replan(val key: String, val cheapest: Boolean = false) : AlertsEvent

    data object FocusHandled : AlertsEvent

    /** "Search the web now" on a card. */
    data class LiveCheck(val key: String) : AlertsEvent

    data object RetryLiveCheck : AlertsEvent

    /** The panel closed: the answer is dropped. */
    data object DismissLiveCheck : AlertsEvent
}

/** One-off effects for the route. */
sealed interface AlertsEffect {
    /** A re-plan search was started. */
    data object Replanned : AlertsEffect
}

/** Watched prices (web AlertsPage): fare and price watches together, with tabs, search and card actions. */
@HiltViewModel
class AlertsViewModel
    @Suppress("LongParameterList") // Hilt-injected collaborators, one per concern (as ResultsViewModel)
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val fares: FareWatchesRepository,
        private val prices: PriceWatchesRepository,
        private val health: HealthRepository,
        private val auth: AuthRepository,
        private val preferences: PreferencesRepository,
        private val searchLauncher: SearchLauncher,
        private val notifications: NotificationsRepository,
        private val clock: AppClock,
        @param:ApplicationScope private val appScope: CoroutineScope,
        private val placeNames: PlaceNameLookup,
        private val liveChecks: LiveCheckRepository,
    ) : ViewModel() {
        /** Deep-link arguments (`AlertsRoute` property names). */
        private val link = AlertsRoute(kind = savedState.get<String>(ARG_KIND), watchId = savedState.get<Long>(ARG_WATCH))
        private val mutable = MutableStateFlow(AlertsUiState(now = clock.now()))
        val state: StateFlow<AlertsUiState> = mutable.asStateFlow()
        private val effects = Channel<AlertsEffect>(Channel.BUFFERED)
        val effect: Flow<AlertsEffect> = effects.receiveAsFlow()
        private var liveJob: Job? = null

        init {
            load()
            viewModelScope.launch {
                health.capabilities.collect { h ->
                    mutable.update { it.copy(livePrices = h?.serpapiConfigured, emailAvailable = h?.emailAlertsAvailable == true) }
                }
            }
            viewModelScope.launch { if (health.capabilities.value == null) health.refresh() }
            viewModelScope.launch { auth.session.collect { s -> mutable.update { it.copy(userEmail = s?.user?.email) } } }
        }

        fun onEvent(event: AlertsEvent) {
            when (event) {
                is AlertsEvent.SelectTab -> mutable.update { it.copy(tab = event.tab) }
                is AlertsEvent.Query -> mutable.update { it.copy(query = event.text) }
                AlertsEvent.Retry -> load()
                AlertsEvent.RetryPrices -> viewModelScope.launch { loadPrices() }
                is AlertsEvent.Check -> check(event.key)
                is AlertsEvent.SetStatus -> setStatus(event.key, event.active)
                is AlertsEvent.Save -> save(event.key, event.edit)
                is AlertsEvent.Delete -> delete(event.key)
                AlertsEvent.Undo -> undo()
                AlertsEvent.UndoExpired -> commitDelete()
                is AlertsEvent.Replan -> replan(event.key, event.cheapest)
                AlertsEvent.FocusHandled -> mutable.update { it.copy(focusKey = null) }
                is AlertsEvent.LiveCheck -> liveCheck(event.key)
                AlertsEvent.RetryLiveCheck -> mutable.value.liveCheck?.let { liveCheck(it.key) }
                AlertsEvent.DismissLiveCheck -> dismissLiveCheck()
            }
        }

        private fun load() {
            mutable.update { it.copy(faresFailed = false, faresError = null, now = clock.now()) }
            viewModelScope.launch {
                val f = async { fares.list() }
                val p = async { loadPrices() }
                when (val result = f.await()) {
                    is AppResult.Success -> {
                        mutable.update { it.copy(fares = result.value) }
                        nameFarePlaces(result.value)
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(fares = it.fares ?: emptyList(), faresFailed = true, faresError = result.error.message) }
                    }
                }
                p.await()
                focusDeepLink()
            }
        }

        /** Fare routes saved with airport codes ("JFK → LIS") show the cities (shared lookup). */
        private fun nameFarePlaces(list: List<FareWatch>) {
            val places = list.flatMap { listOf(it.originLabel, it.destinationLabel) }
            viewModelScope.launch {
                val names = placeNames.resolve(places)
                if (names.isNotEmpty()) mutable.update { it.copy(placeNames = it.placeNames + names) }
            }
        }

        private suspend fun loadPrices() {
            mutable.update { it.copy(pricesFailed = false) }
            when (val result = prices.list()) {
                is AppResult.Success -> mutable.update { it.copy(prices = result.value) }
                is AppResult.Failure -> mutable.update { it.copy(prices = it.prices ?: emptyList(), pricesFailed = true) }
            }
        }

        /** Deep link `?kind=&watch=`: a price watch has a kind, a fare watch has none. Shows all if the tab hides it. */
        private fun focusDeepLink() {
            val id = link.watchId ?: return
            val s = mutable.value
            val target =
                s.watches.firstOrNull { w ->
                    w.id == id && if (link.kind == null || link.kind == KIND_FLIGHT) w is AnyWatch.Fare else w.kind == link.kind
                } ?: return
            mutable.update { it.copy(focusKey = target.key, tab = if (inTab(target, it.tab)) it.tab else AlertTab.ALL, query = "") }
        }

        private fun find(key: String): AnyWatch? = mutable.value.watches.firstOrNull { it.key == key }

        private fun replace(watch: AnyWatch) =
            mutable.update { s ->
                when (watch) {
                    is AnyWatch.Fare -> s.copy(fares = s.fares?.map { if (it.id == watch.id) watch.watch else it })
                    is AnyWatch.Price -> s.copy(prices = s.prices?.map { if (it.id == watch.id) watch.watch else it })
                }
            }

        private fun say(key: String, message: CardMessage) = mutable.update { it.copy(messages = it.messages + (key to message)) }

        private inline fun withBusy(key: String, action: CardAction, crossinline block: suspend () -> Unit) {
            if (mutable.value.busy.containsKey(key)) return
            mutable.update { it.copy(busy = it.busy + (key to action)) }
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    mutable.update { it.copy(busy = it.busy - key, now = clock.now()) }
                }
            }
        }

        private fun check(key: String) {
            val w = find(key) ?: return
            withBusy(key, CardAction.CHECK) {
                val result =
                    when (w) {
                        is AnyWatch.Fare -> fares.check(w.id).toAny()
                        is AnyWatch.Price -> prices.check(w.id).toAny()
                    }
                when (result) {
                    is AppResult.Success -> {
                        val updated = result.value
                        // Shown inline on the card already: never post it again as a system notification.
                        val alert = (updated as? AnyWatch.Fare)?.watch?.notification ?: (updated as? AnyWatch.Price)?.watch?.notification
                        alert?.let { notifications.markSeen(listOf(it.id)) }
                        replace(updated)
                        val outcome =
                            when (updated) {
                                is AnyWatch.Fare -> checkOutcome(updated.watch)
                                is AnyWatch.Price -> checkOutcome(updated.watch)
                            }
                        say(key, CardMessage.Checked(outcome, (updated as? AnyWatch.Price)?.watch?.currency, updated is AnyWatch.Fare))
                    }

                    is AppResult.Failure -> {
                        say(key, CardMessage.Failed(CardAction.CHECK, result.error.message))
                    }
                }
            }
        }

        private fun setStatus(key: String, active: Boolean) {
            val w = find(key) ?: return
            val status = if (active) WatchStatuses.ACTIVE else WatchStatuses.PAUSED
            withBusy(key, CardAction.STATUS) {
                val result =
                    when (w) {
                        is AnyWatch.Fare -> fares.update(w.id, UpdateFareWatchRequest(status = status)).toAny()
                        is AnyWatch.Price -> prices.update(w.id, UpdatePriceWatchRequest(status = status)).toAny()
                    }
                when (result) {
                    is AppResult.Success -> {
                        replace(result.value)
                        say(key, if (active) CardMessage.Resumed else CardMessage.Paused(w is AnyWatch.Fare))
                    }

                    is AppResult.Failure -> {
                        say(key, CardMessage.Failed(CardAction.STATUS, result.error.message))
                    }
                }
            }
        }

        private fun save(key: String, edit: WatchEdit) {
            val w = find(key) ?: return
            val below = edit.mode == AlertMode.BELOW_TARGET
            mutable.update { it.copy(messages = it.messages - key) }
            withBusy(key, CardAction.SAVE) {
                val result =
                    when (w) {
                        is AnyWatch.Fare -> {
                            fares.update(
                                w.id,
                                UpdateFareWatchRequest(
                                    alertMode = edit.mode.wire,
                                    targetPrice = edit.target.takeIf { below },
                                    flexibleDays = edit.flexible?.let { if (it) FLEXIBLE_DAYS else 0 },
                                    emailEnabled = edit.email,
                                ),
                            ).toAny()
                        }

                        is AnyWatch.Price -> {
                            prices.update(
                                w.id,
                                UpdatePriceWatchRequest(
                                    alertMode = edit.mode.wire,
                                    // The exact target: saving other settings never moves it (web parity).
                                    targetPrice = edit.target.takeIf { below && (edit.targetChanged || w.watch.targetPrice == null) },
                                    emailEnabled = edit.email,
                                ),
                            ).toAny()
                        }
                    }
                when (result) {
                    is AppResult.Success -> {
                        replace(result.value)
                        say(key, CardMessage.Saved)
                    }

                    is AppResult.Failure -> {
                        say(key, CardMessage.Failed(CardAction.SAVE, result.error.message))
                    }
                }
            }
        }

        /**
         * Hides the watch at once and deletes it when the Undo snackbar closes without Undo
         * ([AlertsEvent.UndoExpired]), when another watch is deleted, or when the screen goes ([onCleared]).
         * No timer of our own: the snackbar stays up for the user's accessibility timeout (TalkBack can make it minutes).
         */
        private fun delete(key: String) {
            commitDelete()
            val w = find(key) ?: return
            mutable.update { s ->
                when (w) {
                    is AnyWatch.Fare -> s.copy(fares = s.fares?.filterNot { it.id == w.id }, undo = w)
                    is AnyWatch.Price -> s.copy(prices = s.prices?.filterNot { it.id == w.id }, undo = w)
                }
            }
        }

        private fun undo() {
            val w = mutable.value.undo ?: return
            mutable.update { s ->
                when (w) {
                    is AnyWatch.Fare -> s.copy(fares = s.fares.orEmpty() + w.watch, undo = null)
                    is AnyWatch.Price -> s.copy(prices = s.prices.orEmpty() + w.watch, undo = null)
                }
            }
        }

        private fun commitDelete() {
            val w = mutable.value.undo ?: return
            mutable.update { it.copy(undo = null) }
            // Outlives the screen: a delete the user didn't undo must reach the server.
            appScope.launch {
                val result =
                    when (w) {
                        is AnyWatch.Fare -> fares.delete(w.id)
                        is AnyWatch.Price -> prices.delete(w.id)
                    }
                if (result is AppResult.Failure) {
                    restoreAfterFailedDelete(w, result.error.message)
                }
            }
        }

        private fun restoreAfterFailedDelete(w: AnyWatch, detail: String?) {
            mutable.update { s ->
                when (w) {
                    is AnyWatch.Fare -> s.copy(fares = s.fares.orEmpty() + w.watch)
                    is AnyWatch.Price -> s.copy(prices = s.prices.orEmpty() + w.watch)
                }
            }
            say(w.key, CardMessage.Failed(CardAction.DELETE, detail))
        }

        private fun replan(key: String, cheapest: Boolean) {
            val w = find(key) ?: return
            viewModelScope.launch {
                val prefs = preferences.get().getOrNull()
                val request =
                    when (w) {
                        is AnyWatch.Fare -> replanRequest(w.watch, prefs, cheapest)
                        is AnyWatch.Price -> tripReplanRequest(w.watch, prefs)
                    } ?: return@launch
                // A re-plan is a new search: it starts a new plan for that trip (web runSearch).
                searchLauncher.startNewSearch(request)
                effects.send(AlertsEffect.Replanned)
            }
        }

        /** One live search at a time; its answer goes only into [AlertsUiState.liveCheck]. */
        private fun liveCheck(key: String) {
            val w = find(key) ?: return
            liveJob?.cancel()
            mutable.update { it.copy(liveCheck = LiveCheckUi(key)) }
            liveJob =
                viewModelScope.launch {
                    val ui =
                        when (val result = liveChecks.check(w.kind, w.id)) {
                            is AppResult.Success -> LiveCheckUi(key, loading = false, result = result.value)
                            is AppResult.Failure -> LiveCheckUi(key, loading = false, error = result.error.toLiveError())
                        }
                    mutable.update { s -> if (s.liveCheck?.key == key) s.copy(liveCheck = ui) else s }
                }
        }

        private fun dismissLiveCheck() {
            liveJob?.cancel()
            liveJob = null
            mutable.update { it.copy(liveCheck = null) }
        }

        override fun onCleared() {
            commitDelete()
            super.onCleared()
        }

        companion object {
            /** SavedStateHandle keys of [AlertsRoute]. */
            const val ARG_KIND = "kind"
            const val ARG_WATCH = "watchId"
            private const val FLEXIBLE_DAYS = 3
        }
    }

@JvmName("fareToAny")
private fun AppResult<FareWatch>.toAny(): AppResult<AnyWatch> =
    when (this) {
        is AppResult.Success -> AppResult.Success(AnyWatch.Fare(value))
        is AppResult.Failure -> this
    }

@JvmName("priceToAny")
private fun AppResult<PriceWatch>.toAny(): AppResult<AnyWatch> =
    when (this) {
        is AppResult.Success -> AppResult.Success(AnyWatch.Price(value))
        is AppResult.Failure -> this
    }
