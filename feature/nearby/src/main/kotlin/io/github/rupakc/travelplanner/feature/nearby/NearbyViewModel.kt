package io.github.rupakc.travelplanner.feature.nearby

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.getOrNull
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyRequest
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.Activity
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

    /** Asking the server; a cache miss takes 10–40 s. */
    data object Loading : NearbyPhase

    data object Loaded : NearbyPhase

    data object LocationOff : NearbyPhase

    data object NoFix : NearbyPhase

    /** 404 `no_place`: [message] is the backend's own words. */
    data class NoPlace(val message: String?) : NearbyPhase

    /** Any other failure; [message] is the backend `detail` (429 included) or null. */
    data class Failed(val message: String?) : NearbyPhase
}

/** One suggestion with its "Why this?" interests and whether it's in My Plan. */
@Immutable
data class NearbyItem(val key: String, val activity: Activity, val why: List<String>, val inPlan: Boolean)

@Immutable
data class NearbyUiState(
    val phase: NearbyPhase = NearbyPhase.Locating,
    val place: NearbyPlace? = null,
    val items: List<NearbyItem> = emptyList(),
    /** Interests the suggestions were asked for (shown so "Why this?" has context). */
    val interests: List<String> = emptyList(),
)

sealed interface NearbyEvent {
    /** The system permission dialog answered. */
    data class PermissionResult(val granted: Boolean) : NearbyEvent

    data object Refresh : NearbyEvent

    data class TogglePlan(val key: String) : NearbyEvent
}

/**
 * Near me (spec §8): current location → `POST /api/nearby` (cached 1 h per ~5 km cell in the
 * repository). The backend folds in saved interests and the Taste Graph; when the server has no
 * interests the traveller profile's are sent. API order is kept: nothing is re-ranked here.
 */
@HiltViewModel
class NearbyViewModel
    @Inject
    constructor(
        private val location: LocationClient,
        private val nearby: NearbyRepository,
        private val preferences: PreferencesRepository,
        private val profile: ProfileRepository,
        private val plan: CurrentPlanStore,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(NearbyUiState(phase = if (location.hasPermission()) NearbyPhase.Locating else NearbyPhase.NeedsPermission))
        val state: StateFlow<NearbyUiState> = mutable.asStateFlow()
        private var job: Job? = null

        init {
            if (location.hasPermission()) load()
            viewModelScope.launch {
                plan.draft.collect {
                    mutable.update { s ->
                        s.copy(items = s.items.map { it.copy(inPlan = plan.isSelected(PlanItem.ActivityItem(it.activity))) })
                    }
                }
            }
        }

        fun onEvent(event: NearbyEvent) {
            when (event) {
                is NearbyEvent.PermissionResult -> if (event.granted) load() else mutable.update { it.copy(phase = NearbyPhase.Denied) }
                NearbyEvent.Refresh -> if (location.hasPermission()) load() else mutable.update { it.copy(phase = NearbyPhase.NeedsPermission) }
                is NearbyEvent.TogglePlan -> state.value.items.firstOrNull { it.key == event.key }?.let { plan.toggle(PlanItem.ActivityItem(it.activity)) }
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
                    // Remembered as a ~5 km cell for background suggestions, only while they are turned on.
                    if (settings.settings.first().nearbyNotifications) nearby.rememberLocation(fix.lat, fix.lng)
                    mutable.update { it.copy(phase = NearbyPhase.Loading) }
                    val serverInterests = preferences.get().getOrNull()?.interests.orEmpty()
                    val sent = if (serverInterests.isEmpty()) profileInterests(profile.profile.first()).ifEmpty { null } else null
                    val shown = serverInterests.ifEmpty { sent.orEmpty() }
                    when (val result = nearby.nearby(fix.lat, fix.lng, sent, NearbyRequest.DEFAULT_LIMIT)) {
                        is AppResult.Success -> {
                            val items =
                                result.value.items.mapIndexed { i, a ->
                                    NearbyItem("$i-${a.name}", a, whyThis(a, shown), plan.isSelected(PlanItem.ActivityItem(a)))
                                }
                            mutable.update { it.copy(phase = NearbyPhase.Loaded, place = result.value.place, items = items, interests = shown) }
                        }

                        is AppResult.Failure -> {
                            val e = result.error
                            val phase =
                                if (e is AppError.NotFound && e.code == CODE_NO_PLACE) NearbyPhase.NoPlace(e.message) else NearbyPhase.Failed(e.message)
                            mutable.update { it.copy(phase = phase) }
                        }
                    }
                }
        }

        private companion object {
            const val CODE_NO_PLACE = "no_place"
        }
    }
