package io.github.rupakc.travelplanner.feature.settings

import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.feature.settings.preferences.PreferencesRules
import io.github.rupakc.travelplanner.feature.settings.preferences.TravellerField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same rules as the web TravelerPanel, TagInput, AlertEmailField and PreferencesPage save. */
class PreferencesRulesTest {
    private val one = Preferences(adults = 1)

    @Test
    fun `counts never drop the group below one traveller or below zero`() {
        assertEquals(one, PreferencesRules.change(one, TravellerField.ADULTS, -1))
        val withChild = PreferencesRules.change(one, TravellerField.CHILDREN, 1)
        assertEquals(1, withChild.children)
        assertEquals(0, PreferencesRules.change(withChild, TravellerField.ADULTS, -1).adults)
        assertEquals(withChild, PreferencesRules.change(withChild, TravellerField.SENIORS, -1))
    }

    @Test
    fun `counts cap at the backend maximum of 20`() {
        val many = Preferences(adults = 20)
        assertEquals(many, PreferencesRules.change(many, TravellerField.ADULTS, 1))
    }

    @Test
    fun `minus is disabled at zero and for the last adult`() {
        assertFalse(PreferencesRules.canDecrease(one, TravellerField.ADULTS))
        assertFalse(PreferencesRules.canDecrease(one, TravellerField.INFANTS))
        assertTrue(PreferencesRules.canDecrease(Preferences(adults = 2), TravellerField.ADULTS))
        assertTrue(PreferencesRules.canDecrease(Preferences(adults = 1, infants = 1), TravellerField.ADULTS))
    }

    @Test
    fun `tags are trimmed and deduplicated`() {
        assertEquals(listOf("UK"), PreferencesRules.addTag(emptyList(), "  UK "))
        assertEquals(listOf("UK"), PreferencesRules.addTag(listOf("UK"), "UK"))
        assertEquals(listOf("UK"), PreferencesRules.addTag(listOf("UK"), "   "))
        assertEquals(listOf("UK", "US", "JP"), PreferencesRules.addTags(listOf("UK"), "US, JP,"))
    }

    @Test
    fun `toggle adds or removes`() {
        assertEquals(listOf("food"), PreferencesRules.toggle(emptyList(), "food"))
        assertEquals(emptyList<String>(), PreferencesRules.toggle(listOf("food"), "food"))
    }

    @Test
    fun `save sets num_travelers to the group total, at least one`() {
        assertEquals(4, PreferencesRules.forSave(Preferences(adults = 2, children = 1, infants = 1, numTravelers = 1)).numTravelers)
        assertEquals(1, PreferencesRules.forSave(Preferences(adults = 0, numTravelers = 3)).numTravelers)
    }

    @Test
    fun `email check mirrors the server`() {
        assertTrue(PreferencesRules.isEmail("name@example.com"))
        assertTrue(PreferencesRules.isEmail(" a@b.co.uk "))
        assertFalse(PreferencesRules.isEmail("name@example"))
        assertFalse(PreferencesRules.isEmail("na me@example.com"))
        assertFalse(PreferencesRules.isEmail("name@.com"))
    }
}
