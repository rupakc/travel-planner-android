package io.github.rupakc.travelplanner.feature.search

import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureDescriptor
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.feature.search.di.visibleFeatures
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureVisibilityTest {
    /** Business travellers get Compare by default; nobody gets Surprise me or From a link. */
    private val catalogue =
        object : FeatureCatalogue {
            override val all = FeatureId.entries.map { FeatureDescriptor(it, it.id, "icon", emptySet()) }

            override fun defaultsFor(types: Collection<TravellerType>): Set<FeatureId> =
                if (TravellerType.BUSINESS in types) setOf(FeatureId.COMPARE) else emptySet()
        }

    @Test
    fun `before onboarding every feature is visible, minus explicit disables`() {
        val visible = visibleFeatures(catalogue, TravellerProfile(), FeatureOverrides(disabled = setOf("compare")))
        assertEquals(FeatureId.entries.toSet() - FeatureId.COMPARE, visible)
        val modes = visibleModes(visibleFeatures(catalogue, TravellerProfile(), FeatureOverrides()), SearchMode.KNOWN)
        assertEquals(SearchMode.entries, modes)
    }

    @Test
    fun `a typed profile is personalised even before onboarding is marked complete (A1 rule)`() {
        val profile = TravellerProfile(onboardingCompleted = false, primaryType = "BUSINESS")
        assertEquals(setOf(FeatureId.COMPARE), visibleFeatures(catalogue, profile, FeatureOverrides()))
    }

    @Test
    fun `after onboarding the type defaults and overrides apply`() {
        val profile = TravellerProfile(onboardingCompleted = true, primaryType = "BUSINESS")
        assertEquals(setOf(FeatureId.COMPARE, FeatureId.IMPORT_LINK), visibleFeatures(catalogue, profile, FeatureOverrides(enabled = setOf("import_link"))))
        assertEquals(
            listOf(SearchMode.KNOWN, SearchMode.COMPARE),
            visibleModes(visibleFeatures(catalogue, profile, FeatureOverrides()), SearchMode.KNOWN),
        )
    }
}
