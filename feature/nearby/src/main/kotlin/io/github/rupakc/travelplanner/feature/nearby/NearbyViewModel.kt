package io.github.rupakc.travelplanner.feature.nearby

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.live.LiveErrorCodes
import io.github.rupakc.travelplanner.core.domain.live.LiveFailure
import io.github.rupakc.travelplanner.core.domain.live.safeWebUri
import io.github.rupakc.travelplanner.core.domain.live.toLiveError
import io.github.rupakc.travelplanner.core.model.grounding.GroundingSource
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyRequest
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the Near me screen is. */
sealed interface NearbyPhase {
    /** Explain why, then ask (coarse; precise optional). */
    data object NeedsPermission : NearbyPhase

    data object Denied : NearbyPhase

    data object Locating : NearbyPhase

    /** Asking the server (a grounded answer takes several seconds). */
    data object Loading : NearbyPhase

    data object Loaded : NearbyPhase

    data object LocationOff : NearbyPhase

    data object NoFix : NearbyPhase

    /** 404 `no_place`: [message] is the backend's own words. */
    data class NoPlace(val message: String?) : NearbyPhase

    /** Any other failure; [message] is the backend `detail` (429, 503, 502) or null → copy for [failure]. */
    data class Failed(val message: String?, val failure: LiveFailure = LiveFailure.OTHER) : NearbyPhase
}

/**
 * Near me state. The answer lives only here (ViewModel memory): it is never written to Room, DataStore
 * or a repository cache (Google Maps grounding terms), so leaving the screen forgets it.
 */
@Immutable
data class NearbyUiState(
    val phase: NearbyPhase = NearbyPhase.Locating,
    val place: NearbyPlace? = null,
    val sections: List<NearbySectionUi> = emptyList(),
    /** "Google Maps", shown as sent. */
    val attribution: String? = null,
    val sources: List<GroundingSource> = emptyList(),
) {
    val isEmpty: Boolean get() = sections.all { it.cards.isEmpty() }
}

sealed interface NearbyEvent {
    /** The system permission dialog answered. */
    data class PermissionResult(val granted: Boolean) : NearbyEvent

    data object Refresh : NearbyEvent
}

/**
 * Near me v2: current location → `POST /api/nearby` with `sections: true` → places to see, eat,
 * shop and stay (Google Maps grounding). Nothing is cached; Refresh asks again.
 */
@HiltViewModel
class NearbyViewModel
    @Inject
    constructor(
        private val location: LocationClient,
        private val nearby: NearbyRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(NearbyUiState(phase = if (location.hasPermission()) NearbyPhase.Locating else NearbyPhase.NeedsPermission))
        val state: StateFlow<NearbyUiState> = mutable.asStateFlow()
        private var job: Job? = null

        init {
            if (location.hasPermission()) load()
        }

        fun onEvent(event: NearbyEvent) {
            when (event) {
                is NearbyEvent.PermissionResult -> if (event.granted) load() else mutable.update { it.copy(phase = NearbyPhase.Denied) }
                NearbyEvent.Refresh -> if (location.hasPermission()) load() else mutable.update { it.copy(phase = NearbyPhase.NeedsPermission) }
            }
        }

        private fun load() {
            job?.cancel()
            job =
                viewModelScope.launch {
                    mutable.update { it.copy(phase = NearbyPhase.Locating) }
                    val fix =
                        when (val f = location.current()) {
                            is LocationFix.Found -> f
                            LocationFix.NoPermission -> return@launch mutable.update { it.copy(phase = NearbyPhase.NeedsPermission) }
                            LocationFix.Off -> return@launch mutable.update { it.copy(phase = NearbyPhase.LocationOff) }
                            LocationFix.Unavailable -> return@launch mutable.update { it.copy(phase = NearbyPhase.NoFix) }
                        }
                    // Remembered as a ~5 km cell to gate the background invitation, only while it is turned on.
                    if (settings.settings.first().nearbyNotifications) nearby.rememberLocation(fix.lat, fix.lng)
                    mutable.update { it.copy(phase = NearbyPhase.Loading) }
                    when (val result = nearby.nearby(fix.lat, fix.lng, NearbyRequest.DEFAULT_LIMIT, Locale.getDefault().language.ifBlank { null })) {
                        is AppResult.Success -> {
                            val r = result.value
                            mutable.update {
                                NearbyUiState(
                                    phase = NearbyPhase.Loaded,
                                    place = r.place,
                                    sections = sectionsOf(r),
                                    attribution = r.attribution?.takeIf(String::isNotBlank),
                                    sources = r.sources.filter { s -> safeWebUri(s.uri) != null },
                                )
                            }
                        }

                        is AppResult.Failure -> {
                            val e = result.error.toLiveError()
                            val phase =
                                if (e.failure == LiveFailure.NOT_FOUND && e.code == LiveErrorCodes.NO_PLACE) {
                                    NearbyPhase.NoPlace(e.message)
                                } else {
                                    NearbyPhase.Failed(e.message, e.failure)
                                }
                            // A failed refresh drops the old answer too: nothing stale is shown as current.
                            mutable.update { NearbyUiState(phase = phase) }
                        }
                    }
                }
        }
    }
