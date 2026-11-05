package io.github.rupakc.travelplanner.feature.settings.features

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.types
import io.github.rupakc.travelplanner.core.domain.profile.visibleFeatures
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One catalogue feature: whether it is on, and whether that is your type's default. */
data class FeatureRow(val id: FeatureId, val on: Boolean, val defaultOn: Boolean, val iconName: String? = null)

/** How the Features screen groups the catalogue. */
enum class FeatureGroup(val members: List<FeatureId>) {
    PLAN(listOf(FeatureId.SURPRISE_ME, FeatureId.COMPARE, FeatureId.IMPORT_LINK, FeatureId.CHAT, FeatureId.NEAR_ME, FeatureId.DEAL_ALERTS)),
    RESULTS(listOf(FeatureId.BEST_VALUE, FeatureId.BUDGET_PLANNER, FeatureId.ITINERARY_EDITOR, FeatureId.TRIP_HEALTH_CHECK, FeatureId.PACKING_LIST)),
    TRIP_INFO(
        listOf(
            FeatureId.VISA_CHECKER,
            FeatureId.FOREX,
            FeatureId.SIM,
            FeatureId.EMERGENCY_CARD,
            FeatureId.WALKING_TOURS,
            FeatureId.EVENTS,
            FeatureId.ACCESSIBILITY_INFO,
        ),
    ),
    ;

    companion object {
        /** Grouped features in display order, then any feature no group lists (so a new one never disappears). */
        fun orderedFeatures(): List<FeatureId> {
            val grouped = entries.flatMap { it.members }
            return grouped + FeatureId.entries.filterNot { it in grouped }
        }

        /** The group a feature is shown in; unlisted features go to the last group. */
        fun of(id: FeatureId): FeatureGroup = entries.firstOrNull { id in it.members } ?: entries.last()
    }
}

@Immutable
data class FeaturesUiState(
    val loading: Boolean = true,
    val rows: List<FeatureRow> = emptyList(),
    /** False until the onboarding team's FeatureCatalogue is bound: defaults are then empty. */
    val hasOverrides: Boolean = false,
) {
    val onCount: Int get() = rows.count { it.on }
}

sealed interface FeaturesEvent {
    data class Toggle(val id: FeatureId, val on: Boolean) : FeaturesEvent

    data object ResetToDefaults : FeaturesEvent
}

/** Settings → Features: toggle every catalogue feature (device-local overrides, spec §6.3). */
@HiltViewModel
class FeaturesViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val catalogue: FeatureCatalogue,
    ) : ViewModel() {
        val state: StateFlow<FeaturesUiState> =
            combine(profileRepository.profile, profileRepository.featureOverrides) { profile, overrides ->
                val defaults = catalogue.defaultsFor(profile.types())
                // The shared rule: before onboarding (or a skipped quiz) every feature is on unless disabled.
                val visible = catalogue.visibleFeatures(profile, overrides)
                val icons = catalogue.all.associate { it.id to it.iconName }
                FeaturesUiState(
                    loading = false,
                    rows = FeatureGroup.orderedFeatures().map { FeatureRow(it, it in visible, it in defaults, icons[it]) },
                    hasOverrides = overrides.enabled.isNotEmpty() || overrides.disabled.isNotEmpty(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FeaturesUiState())

        fun onEvent(event: FeaturesEvent) {
            viewModelScope.launch {
                when (event) {
                    is FeaturesEvent.Toggle -> {
                        // Back to the type default → drop the override, so retaking the quiz applies new defaults.
                        // Defaults come from the profile itself (state may still be the initial empty value).
                        val profile = profileRepository.profile.first()
                        // No type yet: everything is on by default (shared visibility rule).
                        val types = profile.types()
                        val defaultOn = types.isEmpty() || event.id in catalogue.defaultsFor(types)
                        val override = if (defaultOn == event.on) null else event.on
                        profileRepository.setFeature(event.id.id, override)
                    }

                    FeaturesEvent.ResetToDefaults -> {
                        profileRepository.resetFeatures()
                    }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
