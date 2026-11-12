package io.github.rupakc.travelplanner.core.domain.notifications

import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.config.FeaturesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsDomainTest {
    private fun flags(f: FeaturesDto?) = NotificationsFlags(ClientConfigDto(features = f))

    @Test
    fun `missing config or features is everything off`() {
        assertEquals(NotificationsFlags.OFF, NotificationsFlags(null))
        assertEquals(NotificationsFlags.OFF, flags(null))
        assertFalse(flags(FeaturesDto()).apiAvailable)
        assertFalse(flags(FeaturesDto(notificationsApi = 0)).apiAvailable)
    }

    @Test
    fun `api needs notifications_api 1 and v2 and apns are independent`() {
        val f = flags(FeaturesDto(notificationsApi = 1, notificationsV2 = true, apns = true))
        assertTrue(f.apiAvailable && f.v2Active && f.apns)
        assertTrue(flags(FeaturesDto(notificationsApi = 2)).apiAvailable)
        assertFalse(flags(FeaturesDto(notificationsV2 = true)).apiAvailable)
        assertFalse(flags(FeaturesDto(notificationsApi = 1)).v2Active)
    }

    @Test
    fun `journey flags carry the notification gates`() {
        assertTrue(JourneyFlags(ClientConfigDto(features = FeaturesDto(notificationsApi = 1))).notifications.apiAvailable)
        assertEquals(NotificationsFlags.OFF, JourneyFlags.OFF.notifications)
    }

    @Test
    fun `catalog locale is a supported base language or English`() {
        assertEquals("es", NotificationText.catalogLocale("es-MX"))
        assertEquals("zh", NotificationText.catalogLocale("zh_Hans_CN"))
        assertEquals("bn", NotificationText.catalogLocale("BN"))
        assertEquals("en", NotificationText.catalogLocale("pt-BR"))
        assertEquals("en", NotificationText.catalogLocale(null))
        assertEquals("en", NotificationText.catalogLocale(""))
    }

    @Test
    fun `render fills placeholders and blanks missing ones`() {
        val text =
            NotificationText.render(
                NotificationText.BUNDLED_EN.getValue(NotificationText.NEARBY_ARRIVAL),
                mapOf(
                    "place" to "Alfama",
                    "item" to "Cafe",
                    "minutes" to 4,
                ),
            )
        assertEquals("You're near Alfama: Cafe is 4 min away", text)
        assertEquals("Tip: ", NotificationText.render("Tip: {tip}", emptyMap()))
        assertEquals("a {B} c", NotificationText.render("a {B} c", mapOf("B" to 1)))
    }

    @Test
    fun `bundled english has every contract key`() {
        listOf("trip_t30", "trip_t7", "trip_t1", "trip_morning", "nearby_arrival").forEach { assertTrue(it, it in NotificationText.BUNDLED_EN) }
    }

    @Test
    fun `cap is clamped and times parse strictly`() {
        assertEquals(1, NotificationSettingsRules.clampCap(0))
        assertEquals(10, NotificationSettingsRules.clampCap(99))
        assertEquals(4, NotificationSettingsRules.clampCap(4))
        assertEquals("07:05", NotificationSettingsRules.formatTime(7, 5))
        assertEquals(22 to 0, NotificationSettingsRules.parseTime("22:00"))
        assertNull(NotificationSettingsRules.parseTime("24:00"))
        assertNull(NotificationSettingsRules.parseTime("7:00"))
        assertNull(NotificationSettingsRules.parseTime(null))
    }
}
