package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTargetTest {
    @Test
    fun `trip phase kinds open the trip through plan_id`() {
        listOf("trip_t30", "trip_t7", "trip_t1", "trip_morning").forEach { kind ->
            assertEquals(NotificationTarget.TripToday(12), resolveNotificationTarget(kind, "/trips/99/today", "12", null, null, "trip_reminders"))
        }
    }

    @Test
    fun `a trip kind falls back to the web path, then to the list`() {
        assertEquals(NotificationTarget.TripToday(7), resolveNotificationTarget("trip_t7", "/trips/7/today", "", null, null))
        assertEquals(NotificationTarget.TripToday(7), resolveNotificationTarget("trip_t7", "/trips/7", null, null, null))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("trip_t7", null, "", null, null))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("trip_t7", "/trips/abc/today", "abc", null, null))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("trip_t1", "https://evil.example/trips/3", "0", null, null))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("trip_t1", null, "99999999999999999999", null, null))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("trip_t1", "/trips/3/../../x", "-4", null, null))
    }

    @Test
    fun `price kinds keep opening the alerts screen even when they start with trip_`() {
        assertEquals(
            NotificationTarget.Alert(AlertLink("trip", 3)),
            resolveNotificationTarget("trip_drop", "/alerts?kind=trip&watch=3", "", 3, "trip", "price_drops"),
        )
        assertEquals(NotificationTarget.Alert(AlertLink(null, null)), resolveNotificationTarget("fare_drop", "/alerts", null, null, null))
    }

    @Test
    fun `an unknown kind of another category opens the list, an unknown kind without category stays on alerts`() {
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("quiz_streak", "", "", null, null, "quiz"))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("brand_new", null, null, null, null, "brand_new_category"))
        assertEquals(NotificationTarget.Alert(AlertLink(null, null)), resolveNotificationTarget("brand_new", null, null, null, null))
    }

    @Test
    fun `categories come from the payload, else the kind`() {
        assertEquals(NotificationCategory.MORNING_BRIEF, notificationCategory(null, "trip_morning"))
        assertEquals(NotificationCategory.TRIP_REMINDERS, notificationCategory("", "trip_t30"))
        assertEquals(NotificationCategory.PRICE_DROPS, notificationCategory(null, "fare_drop"))
        assertEquals(NotificationCategory.WEATHER, notificationCategory("weather", "x"))
        assertNull(notificationCategory("brand_new_category", "x"))
    }

    @Test
    fun `rows read category and plan_id from data, number or string`() {
        fun row(json: String) = AppNotification(id = 1, kind = "trip_t7", data = TravelJson.parseToJsonElement(json) as JsonObject)
        assertEquals(NotificationTarget.TripToday(5), row("""{"category":"trip_reminders","plan_id":5,"link":"/trips/5/today"}""").target())
        assertEquals(NotificationTarget.TripToday(5), row("""{"plan_id":"5"}""").target())
        assertEquals(NotificationTarget.Inbox, row("""{"plan_id":null}""").target())
        assertEquals(NotificationTarget.Inbox, row("{}").target())
        assertEquals("trip_reminders", row("""{"category":"trip_reminders"}""").category)
    }

    @Test
    fun `trip kinds are styled as trip rows and unknown kinds still get a style`() {
        assertEquals(AlertAction.OPEN_TRIP, alertKindFor(AppNotification(id = 1, kind = "trip_t30")).action)
        assertEquals(AlertIcon.CALENDAR, alertKindFor(AppNotification(id = 1, kind = "trip_morning")).icon)
        alertKindFor(AppNotification(id = 1, kind = "from_the_future"))
    }

    @Test
    fun `social kinds open the feed and quiz kinds their match`() {
        assertEquals(NotificationTarget.Feed, resolveNotificationTarget("social_idea_saved", "/feed", null, null, null))
        assertEquals(NotificationTarget.Feed, resolveNotificationTarget("social_new_follower", "/feed", null, null, null, "social"))
        assertEquals(NotificationTarget.H2h("m-1"), resolveNotificationTarget("quiz_h2h_invite", "/play/h2h/m-1", null, null, null))
        assertEquals(NotificationTarget.H2h("m1"), resolveNotificationTarget("quiz_h2h_result", "/play/h2h/m1", null, null, null, "quiz"))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("quiz_h2h_result", null, null, null, null))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("quiz_h2h_invite", "https://evil.test/x", null, null, null))
    }

    @Test
    fun `social kinds open the idea or the profile their link names, else the feed`() {
        val saved = "social_idea_saved"
        val follower = "social_new_follower"
        assertEquals(NotificationTarget.Idea(42), resolveNotificationTarget(saved, "/feed/42", null, null, null))
        assertEquals(NotificationTarget.Idea(42), resolveNotificationTarget(saved, "/feed/42/?x=1", null, null, null))
        assertEquals(NotificationTarget.Profile("ann_9"), resolveNotificationTarget(follower, "/u/Ann_9", null, null, null))
        for (bad in listOf(
            null,
            "",
            "/feed",
            "/feed/abc",
            "/feed/0",
            "/feed/42/../x",
            "https://evil.example/feed/42",
            "/u/a",
            "/u/ann smith",
            "/u/../x",
            "/u/" + "a".repeat(21),
        )) {
            assertEquals(bad, NotificationTarget.Feed, resolveNotificationTarget(saved, bad, null, null, null))
        }
    }
}
