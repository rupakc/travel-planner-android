package io.github.rupakc.travelplanner.core.model.notifications

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsDecodeTest {
    private val json = TravelJson

    @Test
    fun `contract example decodes`() {
        val s =
            json.decodeFromString(
                NotificationSettings.serializer(),
                """{"categories":{"price_drops":{"enabled":true},"trip_reminders":{"enabled":true},"morning_brief":{"enabled":false},
                "weather":{"enabled":true},"nearby":{"enabled":true},"social":{"enabled":true},"quiz":{"enabled":true},"future":{"enabled":false}},
                "quiet_hours":{"enabled":true,"start":"22:00","end":"07:00"},"daily_cap":3,"timezone":"Asia/Kolkata",
                "quiet_hours_active":true,"push":{"fcm":false,"apns":false},"devices":2}""",
            )
        assertTrue(s.isEnabled(NotificationCategory.PRICE_DROPS))
        assertFalse(s.isEnabled(NotificationCategory.MORNING_BRIEF))
        assertEquals("22:00", s.quietHours?.start)
        assertEquals(3, s.dailyCap)
        assertEquals("Asia/Kolkata", s.timezone)
        assertTrue(s.quietHoursActive)
        assertFalse(s.push.fcm)
        assertEquals(2, s.devices)
        assertNull(NotificationCategory.from("future"))
    }

    @Test
    fun `absent categories count as on and a null timezone stays null`() {
        val s = json.decodeFromString(NotificationSettings.serializer(), """{"timezone":null,"devices":"4"}""")
        assertTrue(NotificationCategory.entries.all(s::isEnabled))
        assertNull(s.timezone)
        assertEquals(4, s.devices)
    }

    @Test
    fun `update bodies omit nulls`() {
        assertEquals(
            """{"categories":{"nearby":{"enabled":false}}}""",
            json.encodeToString(NotificationSettingsUpdate.serializer(), NotificationSettingsUpdate.category(NotificationCategory.NEARBY, false)),
        )
        assertEquals(
            """{"quiet_hours":{"enabled":true,"start":"21:30","end":"07:00"},"daily_cap":5}""",
            json.encodeToString(
                NotificationSettingsUpdate.serializer(),
                NotificationSettingsUpdate(quietHours = QuietHours(true, "21:30", "07:00"), dailyCap = 5),
            ),
        )
    }

    @Test
    fun `catalog decodes and error code maps`() {
        val c = json.decodeFromString(NotificationCatalog.serializer(), """{"locale":"es","templates":{"nearby_arrival":"Estás cerca de {place}"}}""")
        assertEquals("es", c.locale)
        assertEquals("Estás cerca de {place}", c.templates["nearby_arrival"])
        assertEquals(
            NotificationsErrorCode.NOTIFICATION_SETTINGS_INVALID,
            AppError.Client(422, "bad", code = "notification_settings_invalid").notificationsErrorCode(),
        )
        assertEquals(NotificationsErrorCode.UNKNOWN, AppError.Client(422, "bad", code = "other").notificationsErrorCode())
        assertEquals(NotificationsErrorCode.UNKNOWN, AppError.Unknown().notificationsErrorCode())
    }
}
