package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.data.profile.FeatureVisibility
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Feature visibility driven by the test; defaults to every feature (a user before onboarding). */
class FakeFeatureVisibility(initial: Set<FeatureId> = FeatureId.entries.toSet()) : FeatureVisibility {
    val state = MutableStateFlow(initial)

    override fun visible(): Flow<Set<FeatureId>> = state
}
