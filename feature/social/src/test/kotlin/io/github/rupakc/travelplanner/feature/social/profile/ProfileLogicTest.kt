package io.github.rupakc.travelplanner.feature.social.profile

import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileVisibility
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLogicTest {
    @Test
    fun `handles are lower case and capped`() {
        assertEquals("ann_1", normalizeHandle("  Ann_1 "))
        assertEquals(20, normalizeHandle("a".repeat(30)).length)
    }

    @Test
    fun `handle syntax is 3 to 20 of a-z 0-9 underscore`() {
        assertTrue(isValidHandle("abc"))
        assertTrue(isValidHandle("a_b_9"))
        assertFalse(isValidHandle("ab"))
        assertFalse(isValidHandle("has space"))
        assertFalse(isValidHandle("Upper"))
        assertFalse(isValidHandle("dash-ed"))
        assertFalse(isValidHandle("a".repeat(21)))
    }

    @Test
    fun `country input is two upper case ASCII letters`() {
        assertEquals("PT", normalizeCountry("pt"))
        assertEquals("P", normalizeCountry("p1"))
        assertEquals("AB", normalizeCountry("abc"))
        assertTrue(isCountryComplete(""))
        assertTrue(isCountryComplete("PT"))
        assertFalse(isCountryComplete("P"))
    }

    @Test
    fun `a country name falls back to the code`() {
        assertEquals("Portugal", countryName("PT", Locale.ENGLISH))
        assertEquals("ZZ", countryName("ZZ", Locale.ENGLISH))
    }

    @Test
    fun `retry days round up`() {
        assertNull(retryDays(null))
        assertNull(retryDays(0))
        assertEquals(1, retryDays(60))
        assertEquals(30, retryDays(30L * 86_400))
        assertEquals(2, retryDays(86_401))
    }

    @Test
    fun `a new profile starts private with everything off`() {
        val form = ProfileForm.from(OwnProfile())
        assertEquals(ProfileVisibility.PRIVATE, form.visibility)
        assertFalse(form.showAvatar || form.showHomeCountry || form.showCountries || form.showLevel || form.showBadges || form.leaderboardOptIn)
    }

    @Test
    fun `an unknown visibility reads as private`() {
        assertEquals(ProfileVisibility.PRIVATE, ProfileForm.from(OwnProfile(handle = "a", visibility = "friends")).visibility)
    }

    @Test
    fun `the diff holds only changed fields`() {
        val original = OwnProfile(handle = "ann", visibility = "followers", bio = "hi", showLevel = true)
        val same = ProfileForm.from(original)
        assertTrue(same.diff(original).isEmpty())
        val changed = same.copy(bio = "hello", showLevel = false, visibility = ProfileVisibility.PUBLIC)
        val diff = changed.diff(original)
        assertEquals("hello", diff.bio)
        assertEquals(false, diff.showLevel)
        assertEquals("public", diff.visibility)
        assertNull(diff.handle)
        assertNull(diff.showAvatar)
    }

    @Test
    fun `the first save sends the handle`() {
        val diff = ProfileForm(handle = "ann").diff(OwnProfile())
        assertEquals("ann", diff.handle)
        assertNull(diff.visibility)
    }
}
