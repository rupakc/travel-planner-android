package io.github.rupakc.travelplanner.feature.alerts.v2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.alerts.QuickWatcher
import io.github.rupakc.travelplanner.core.data.repository.AlertsV2Repository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTelemetry
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.model.alerts.AlertSuggestion
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What one suggestion row shows after a tap on Watch. */
@Immutable
sealed interface SuggestionStatus {
    data object Idle : SuggestionStatus

    data object Adding : SuggestionStatus

    /** A new pending watch (first check queued). */
    data object Added : SuggestionStatus

    data object Existing : SuggestionStatus

    /** Refused or failed; [detail] is the backend text (limit / failure) or null. */
    data class Blocked(val reason: SuggestionBlock, val detail: String?) : SuggestionStatus
}

enum class SuggestionBlock { LIVE_PRICES_OFF, LIMIT, INVALID, FAILED }

/** One suggestion plus its row status. [key] is stable across the list (its index at load time). */
@Immutable
data class SuggestionRow(val key: Int, val suggestion: AlertSuggestion, val status: SuggestionStatus = SuggestionStatus.Idle) {
    /** Already watched on the server, or just added/found here. */
    val watching: Boolean get() = suggestion.alreadyWatching || status == SuggestionStatus.Added || status == SuggestionStatus.Existing
}

@Immutable
sealed interface SuggestionsLoad {
    data object Idle : SuggestionsLoad

    data object Loading : SuggestionsLoad

    data class Ready(val rows: List<SuggestionRow>) : SuggestionsLoad

    /** [message] is the backend detail or null (generic copy). */
    data class Failed(val message: String?) : SuggestionsLoad
}

@Immutable
data class AlertToolsUiState(
    /** The v2 routes exist: show "Alert settings". */
    val prefsAvailable: Boolean = false,
    /** One-tap and suggestions are available (live prices and v2). */
    val suggestionsAvailable: Boolean = false,
    val sheetOpen: Boolean = false,
    val load: SuggestionsLoad = SuggestionsLoad.Idle,
)

/** Alerts 2.0 tools row on the Watched prices page: the suggestions sheet (one-tap watch) and the entry to alert settings. */
@HiltViewModel
class AlertSuggestionsViewModel
    @Inject
    constructor(
        private val flags: JourneyFlagsRepository,
        private val alerts: AlertsV2Repository,
        private val quickWatcher: QuickWatcher,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(AlertToolsUiState())
        val state: StateFlow<AlertToolsUiState> = mutable.asStateFlow()

        init {
            viewModelScope.launch {
                val f = flags.flags().alerts
                mutable.update { it.copy(prefsAvailable = f.v2Available, suggestionsAvailable = f.oneTapAvailable) }
            }
        }

        fun open() {
            mutable.update { it.copy(sheetOpen = true) }
            load()
        }

        fun close() {
            mutable.update { it.copy(sheetOpen = false) }
        }

        fun load() {
            mutable.update { it.copy(load = SuggestionsLoad.Loading) }
            viewModelScope.launch {
                when (val result = alerts.suggestions(null)) {
                    is AppResult.Success -> {
                        // The server only offers one-tap while live prices are on; honour its flag as well as the config's.
                        val items = if (result.value.livePrices) result.value.suggestions else emptyList()
                        mutable.update { it.copy(load = SuggestionsLoad.Ready(items.mapIndexed { i, s -> SuggestionRow(i, s) })) }
                        if (items.isNotEmpty()) telemetry.emit(AlertTelemetry.SUGGEST, mapOf("action" to "shown", "count" to items.size))
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(load = SuggestionsLoad.Failed(result.error.message)) }
                    }
                }
            }
        }

        fun accept(key: Int) {
            val row = rows().firstOrNull { it.key == key } ?: return
            if (row.watching || row.status == SuggestionStatus.Adding) return
            val s = row.suggestion
            val kind = s.kind ?: return
            setStatus(key, SuggestionStatus.Adding)
            viewModelScope.launch {
                val outcome = quickWatcher.watch(kind, s.title.orEmpty(), s.subtitle, s.spec, planId = null, surface = "suggest")
                setStatus(key, outcome.toStatus())
                if (outcome.watching) telemetry.emit(AlertTelemetry.SUGGEST, mapOf("action" to "accepted", "count" to 1))
            }
        }

        fun dismiss(key: Int) {
            if (rows().none { it.key == key }) return
            mutable.update { st ->
                val ready = st.load as? SuggestionsLoad.Ready ?: return@update st
                st.copy(load = ready.copy(rows = ready.rows.filterNot { it.key == key }))
            }
            telemetry.emit(AlertTelemetry.SUGGEST, mapOf("action" to "dismissed", "count" to 1))
        }

        private fun rows(): List<SuggestionRow> = (mutable.value.load as? SuggestionsLoad.Ready)?.rows.orEmpty()

        private fun setStatus(key: Int, status: SuggestionStatus) {
            mutable.update { st ->
                val ready = st.load as? SuggestionsLoad.Ready ?: return@update st
                st.copy(load = ready.copy(rows = ready.rows.map { if (it.key == key) it.copy(status = status) else it }))
            }
        }

        private fun QuickWatchOutcome.toStatus(): SuggestionStatus =
            when (this) {
                is QuickWatchOutcome.Added -> SuggestionStatus.Added
                is QuickWatchOutcome.Existing -> SuggestionStatus.Existing
                QuickWatchOutcome.LivePricesOff -> SuggestionStatus.Blocked(SuggestionBlock.LIVE_PRICES_OFF, null)
                is QuickWatchOutcome.Limit -> SuggestionStatus.Blocked(SuggestionBlock.LIMIT, detail)
                is QuickWatchOutcome.Invalid -> SuggestionStatus.Blocked(SuggestionBlock.INVALID, detail)
                is QuickWatchOutcome.Failed -> SuggestionStatus.Blocked(SuggestionBlock.FAILED, detail)
            }
    }
