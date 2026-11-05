package io.github.rupakc.travelplanner.core.domain.profile

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCatalogueTest {
    private val catalogue = DefaultFeatureCatalogue

    @Test
    fun `every feature id has exactly one descriptor with keys and icon`() {
        assertEquals(FeatureId.entries.toSet(), catalogue.all.map { it.id }.toSet())
        assertEquals(FeatureId.entries.size, catalogue.all.size)
        catalogue.all.forEach { d ->
            assertEquals("feature_${d.id.id}_title", d.titleKey)
            assertEquals("feature_${d.id.id}_description", d.descriptionKey)
            assertTrue(d.iconName.isNotBlank())
            assertTrue("${d.id} is on for nobody", d.defaultOnFor.isNotEmpty())
        }
    }

    @Test
    fun `every type gets a useful default set`() {
        TravellerType.entries.forEach { type ->
            val defaults = catalogue.defaultsFor(listOf(type))
            assertTrue("$type has ${defaults.size}", defaults.size >= 3)
            assertTrue(FeatureId.CHAT in defaults)
        }
    }

    @Test
    fun `defaults for several types are the union`() {
        val family = catalogue.defaultsFor(listOf(TravellerType.FAMILY))
        val accessible = catalogue.defaultsFor(listOf(TravellerType.ACCESSIBLE))
        assertEquals(family + accessible, catalogue.defaultsFor(listOf(TravellerType.FAMILY, TravellerType.ACCESSIBLE)))
        assertTrue(FeatureId.ACCESSIBILITY_INFO in accessible)
        assertFalse(FeatureId.ACCESSIBILITY_INFO in family)
        assertEquals(emptySet<FeatureId>(), catalogue.defaultsFor(emptyList()))
    }

    @Test
    fun `defaults keep catalogue order`() {
        val defaults = catalogue.defaultsFor(TravellerType.entries).toList()
        assertEquals(catalogue.all.map { it.id }, defaults)
    }

    @Test
    fun `visible is defaults union enabled minus disabled`() {
        val types = listOf(TravellerType.BUSINESS)
        val defaults = catalogue.defaultsFor(types)
        assertTrue(FeatureId.SIM in defaults)
        assertFalse(FeatureId.WALKING_TOURS in defaults)
        val overrides = FeatureOverrides(enabled = setOf("walking_tours"), disabled = setOf("sim"))
        assertEquals(defaults + FeatureId.WALKING_TOURS - FeatureId.SIM, catalogue.visible(types, overrides))
    }

    @Test
    fun `disabled wins over enabled and unknown or core ids are ignored`() {
        val types = listOf(TravellerType.LUXURY)
        val overrides = FeatureOverrides(enabled = setOf("sim", "search", "bogus"), disabled = setOf("sim", "settings"))
        assertEquals(catalogue.defaultsFor(types) - FeatureId.SIM, catalogue.visible(types, overrides))
        CoreSurface.entries.forEach { assertEquals(null, FeatureId.fromId(it.name.lowercase())) }
    }

    @Test
    fun `visible features for a stored profile`() {
        val profile = TravellerProfile(onboardingCompleted = true, primaryType = "FAMILY", secondaryTypes = listOf("ACCESSIBLE"))
        assertEquals(
            catalogue.defaultsFor(listOf(TravellerType.FAMILY, TravellerType.ACCESSIBLE)),
            catalogue.visibleFeatures(profile, FeatureOverrides()),
        )
    }

    @Test
    fun `before onboarding everything is visible except explicit disables`() {
        val all = FeatureId.entries.toSet()
        assertEquals(all, catalogue.visibleFeatures(TravellerProfile(), FeatureOverrides()))
        assertEquals(all - FeatureId.CHAT, catalogue.visibleFeatures(TravellerProfile(), FeatureOverrides(disabled = setOf("chat"))))
    }

    @Test
    fun `overrides for a picked set reproduce it exactly`() {
        val types = listOf(TravellerType.ADVENTURE)
        val wanted = setOf(FeatureId.CHAT, FeatureId.FOREX, FeatureId.NEAR_ME)
        val overrides = catalogue.overridesFor(types, wanted)
        assertEquals(wanted, catalogue.visible(types, overrides))
        assertTrue("forex" in overrides.enabled)
        assertTrue("surprise_me" in overrides.disabled)
        assertEquals(FeatureOverrides(), catalogue.overridesFor(types, catalogue.defaultsFor(types)))
    }
}
