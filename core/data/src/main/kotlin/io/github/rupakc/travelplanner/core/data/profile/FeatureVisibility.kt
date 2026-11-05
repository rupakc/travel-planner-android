package io.github.rupakc.travelplanner.core.data.profile

import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.visibleFeatures
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The one feature-visibility rule (spec §6.3, A1's `FeatureCatalogue.visibleFeatures`): with a traveller type,
 * the type's defaults ∪ enabled − disabled; before onboarding (or after skipping the quiz) every feature minus
 * the user's explicit disables. Home, Global search, Settings, the Search mode chips and the Results section
 * order all read this (architecture review I3), so a skipped-quiz user sees the same thing everywhere.
 */
fun interface FeatureVisibility {
    fun visible(): Flow<Set<FeatureId>>
}

class ProfileFeatureVisibility
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val catalogue: FeatureCatalogue,
    ) : FeatureVisibility {
        override fun visible(): Flow<Set<FeatureId>> =
            combine(profileRepository.profile, profileRepository.featureOverrides) { profile, overrides ->
                catalogue.visibleFeatures(profile, overrides)
            }.distinctUntilChanged()
    }
