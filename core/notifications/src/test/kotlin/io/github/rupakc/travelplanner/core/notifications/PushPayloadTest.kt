package io.github.rupakc.travelplanner.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.messaging.RemoteMessage
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Notifications 2.0 push payloads (fake FCM messages, never FCM itself): channels, taps, grouping, tolerance. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class PushPayloadTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = RecordingNotifier()
    private val signedIn = FakeAuthRepository(Session("t", TestUser))
    private val repo = FakeNotificationsRepository()

    private fun handle(vararg pairs: Pair<String, String>, settings: AppSettings = AppSettings()): Boolean {
        val message = RemoteMessage.Builder("voyager@fcm").setData(mapOf(*pairs)).build()
        return runBlocking { PushAlertHandler(signedIn, repo, FakeSettingsRepository(settings), notifier).handle(pushPayload(message)) }
    }

    private fun trip(kind: String, category: String, planId: String = "12") =
        arrayOf(
            "kind" to kind,
            "notification_id" to "81",
            "category" to category,
            "plan_id" to planId,
            "link" to "/trips/$planId/today",
            "title" to "Lisbon in 7 days",
            "body" to "Check your visa.",
        )

    @Test
    fun `trip-phase pushes use the trip channel and open the Today tab through plan_id`() {
        listOf("trip_t30", "trip_t7", "trip_t1").forEach { kind ->
            notifier.posts.clear()
            assertTrue(handle(*trip(kind, "trip_reminders")))
            val post = notifier.posts.single()
            assertEquals(TravelChannel.TRIP_REMINDERS, post.channel)
            assertEquals("travelplanner://trip/12?tab=today&notif_cat=trip_reminders", post.deepLink)
            assertEquals("Lisbon in 7 days", post.title)
        }
    }

    @Test
    fun `the morning brief has its own channel`() {
        assertTrue(handle(*trip("trip_morning", "morning_brief")))
        assertEquals(TravelChannel.MORNING_BRIEF, notifier.posts.single().channel)
        assertEquals("travelplanner://trip/12?tab=today&notif_cat=morning_brief", notifier.posts.single().deepLink)
    }

    @Test
    fun `an empty or invalid plan_id opens the notification list, never a wrong trip`() {
        val noLink = trip("trip_t7", "trip_reminders", planId = "").filter { it.first != "link" }.toTypedArray()
        assertTrue(handle(*noLink))
        assertEquals("travelplanner://notifications?notif_cat=trip_reminders", notifier.posts.single().deepLink)
        notifier.posts.clear()
        assertTrue(handle(*trip("trip_t7", "trip_reminders", planId = "../../x").filter { it.first != "link" }.toTypedArray()))
        assertEquals("travelplanner://notifications?notif_cat=trip_reminders", notifier.posts.single().deepLink)
    }

    @Test
    fun `an unknown kind or category shows on the default channel and nothing special`() {
        assertTrue(handle("kind" to "from_the_future", "notification_id" to "90", "category" to "holograms", "title" to "Hello", "plan_id" to ""))
        val post = notifier.posts.single()
        assertEquals(TravelChannel.ACCOUNT, post.channel)
        assertEquals("travelplanner://notifications", post.deepLink)
        assertFalse(post.quiet)
    }

    @Test
    fun `a known category with an unknown kind uses that category's channel`() {
        assertTrue(handle("kind" to "weather_change", "notification_id" to "91", "category" to "weather", "title" to "Rain on day 2"))
        assertEquals(TravelChannel.WEATHER, notifier.posts.single().channel)
    }

    @Test
    fun `a grouped price push replaces the previous group and opens the alerts list`() {
        val group =
            arrayOf(
                "kind" to "hotel_drop",
                "notification_id" to "92",
                "category" to "price_drops",
                "plan_id" to "",
                "group_count" to "4",
                "link" to "/alerts",
                "title" to "4 prices dropped",
                "body" to "a; b; c and 1 more",
            )
        assertTrue(handle(*group))
        assertTrue(handle(*(group.filter { it.first != "notification_id" } + ("notification_id" to "93")).toTypedArray()))
        val (first, second) = notifier.posts
        assertEquals(first.id, second.id)
        assertEquals(NotificationIds.group(NotificationCategory.PRICE_DROPS), first.id)
        assertEquals(TravelChannel.PRICE_ALERTS, first.channel)
        assertEquals("travelplanner://alerts?notif_cat=price_drops", first.deepLink)
        // A single push keeps its own id.
        notifier.posts.clear()
        assertTrue(handle("kind" to "hotel_drop", "notification_id" to "94", "category" to "price_drops", "title" to "x", "watch_id" to "3"))
        assertEquals(NotificationIds.priceAlert(94), notifier.posts.single().id)
    }

    @Test
    fun `only price drops honour the local price-alert toggle`() {
        val off = AppSettings(priceAlertNotifications = false)
        assertFalse(handle("kind" to "fare_drop", "notification_id" to "95", "category" to "price_drops", "title" to "x", settings = off))
        assertFalse(handle("kind" to "fare_drop", "notification_id" to "96", "title" to "x", settings = off))
        assertTrue(handle(*trip("trip_t1", "trip_reminders"), settings = off))
        assertEquals(setOf(95L, 96L, 81L), repo.seen)
    }

    @Test
    fun `an older server's push without category still works as a price alert`() {
        assertTrue(handle("kind" to "fare_drop", "notification_id" to "97", "link" to "/alerts?kind=hotel&watch=9", "title" to "Dropped"))
        assertEquals(TravelChannel.PRICE_ALERTS, notifier.posts.single().channel)
    }

    @Test
    fun `a legacy notification block fills a missing title`() {
        val message =
            RemoteMessage.Builder("voyager@fcm").setData(mapOf("kind" to "fare_drop", "notification_id" to "98")).build()
        assertEquals("98", pushPayload(message)["notification_id"])
        assertNull(pushPayload(message)["title"])
    }

    @Test
    fun `a system-shown trip push opens the trip, and the tap is counted once with the category`() {
        val intent = Intent(Intent.ACTION_MAIN).putExtra("kind", "trip_t7").putExtra("plan_id", "12").putExtra("category", "trip_reminders")
        assertEquals("travelplanner://trip/12?tab=today&notif_cat=trip_reminders", withPushDeepLink(intent).data.toString())
        assertEquals(NotificationCategory.TRIP_REMINDERS, PushTaps.consume(intent))
        assertEquals("travelplanner://trip/12?tab=today", intent.data.toString())
        assertNull(PushTaps.consume(intent))
        val empty = Intent(Intent.ACTION_MAIN).putExtra("kind", "trip_t7").putExtra("plan_id", "")
        assertEquals(null, withPushDeepLink(empty).data)
    }

    @Test
    fun `a tap tag is only ever a known category and only on an app link`() {
        fun view(uri: String) = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
        assertNull(PushTaps.consume(view("travelplanner://alerts?notif_cat=%3Cscript%3E")))
        assertNull(PushTaps.consume(view("https://evil.example/?notif_cat=quiz")))
        assertNull(PushTaps.consume(view("travelplanner://nearby")))
        val keep = view("travelplanner://alerts?kind=hotel&watch=9&notif_cat=weather")
        assertEquals(NotificationCategory.WEATHER, PushTaps.consume(keep))
        assertEquals("travelplanner://alerts?kind=hotel&watch=9", keep.data.toString())
    }

    @Test
    fun `every category has a channel and channels are created`() {
        NotificationCategory.entries.forEach { TravelChannel.forCategory(it) }
        assertEquals(TravelChannel.ACCOUNT, TravelChannel.forCategory(null))
        NotificationChannels.createAll(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        TravelChannel.entries.forEach { assertTrue(it.id, manager.getNotificationChannel(it.id) != null) }
    }

    @Test
    fun `without google-services the push stack is inert`() =
        runBlocking {
            assertFalse(isFirebaseConfigured(context))
            assertNull(FirebasePushTokenSource(context).token())
        }
}
