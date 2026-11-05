package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.data.profile.ProfileFeatureVisibility
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Architecture review I3: one visibility rule for every screen. */
class FeatureVisibilityTest {
    @Test
    fun `a user who skipped the quiz sees every feature except explicit disables`() =
        runTest {
            val profile = FakeProfileRepository(TravellerProfile(onboardingCompleted = true))
            profile.setFeature(FeatureId.EVENTS.id, false)
            val visible = ProfileFeatureVisibility(profile, DefaultFeatureCatalogue).visible().first()
            assertEquals(FeatureId.entries.toSet() - FeatureId.EVENTS, visible)
        }

    @Test
    fun `a typed user gets the type's defaults plus enabled minus disabled`() =
        runTest {
            val profile = FakeProfileRepository(TravellerProfile(onboardingCompleted = true, primaryType = "BUSINESS"))
            val visibility = ProfileFeatureVisibility(profile, DefaultFeatureCatalogue)
            val defaults = DefaultFeatureCatalogue.defaultsFor(listOf(io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.BUSINESS))
            assertEquals(defaults, visibility.visible().first())
            profile.setFeature(FeatureId.NEAR_ME.id, true)
            assertTrue(FeatureId.NEAR_ME in visibility.visible().first())
        }
}
