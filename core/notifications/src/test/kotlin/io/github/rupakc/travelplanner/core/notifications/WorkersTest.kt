package io.github.rupakc.travelplanner.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadResponse
import io.github.rupakc.travelplanner.core.model.alerts.NotificationsPage
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollIntervals
import io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollWorker
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbyNotificationPolicy
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbyPostLog
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbySuggestionWorker
import io.github.rupakc.travelplanner.core.notifications.reminders.TripReminderWorker
import io.github.rupakc.travelplanner.core.notifications.reminders.WorkManagerTripReminderScheduler
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeNearbyRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Workers and schedulers on the WorkManager test driver (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class WorkersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = RecordingNotifier()
    private val clock =
        object : AppClock {
            var instant: Instant = Instant.parse("2026-12-11T10:00:00Z")

            override fun now(): Instant = instant

            override fun zone(): ZoneId = ZoneId.of("Europe/Lisbon")
        }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private inline fun <reified W : ListenableWorker> build(
        crossinline create: (Context, WorkerParameters) -> W,
        input: androidx.work.Data = androidx.work.Data.EMPTY,
    ): W =
        TestListenableWorkerBuilder<W>(context)
            .setInputData(input)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                        create(appContext, workerParameters)
                },
            ).build()

    private fun alert(id: Long, read: Boolean = false) =
        AppNotification(
            id = id,
            kind = "hotel_drop",
            title = "Lumen dropped to €90",
            body = "For 10–14 Dec",
            readAt = if (read) "2026-12-01T00:00:00Z" else null,
            data = TravelJson.parseToJsonElement("""{"watch_id":9,"watch_kind":"hotel","links":{"alerts":"/alerts?kind=hotel&watch=9"}}""") as JsonObject,
        )

    private val signedIn = FakeAuthRepository(Session("t", TestUser))

    @Test
    fun `alert poll posts unseen alerts once and dedupes by id`() =
        runBlocking {
            val repo = FakeNotificationsRepository(listOf(alert(1), alert(2), alert(3, read = true)))
            val settings = FakeSettingsRepository()
            val worker = { build({ c, p -> AlertPollWorker(c, p, signedIn, repo, settings, notifier) }) }
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(listOf(1L, 2L), notifier.priceAlerts.map { it.id })
            assertEquals(setOf(1L, 2L), repo.seen)
            worker().doWork()
            assertEquals(2, notifier.priceAlerts.size)
            assertEquals("travelplanner://alerts?kind=hotel&watch=9", notificationLinkOf(alert(1)))
        }

    @Test
    fun `alert poll respects the price alerts toggle but still marks alerts seen`() =
        runBlocking {
            val repo = FakeNotificationsRepository(listOf(alert(1)))
            val settings = FakeSettingsRepository(AppSettings(priceAlertNotifications = false))
            build({ c, p -> AlertPollWorker(c, p, signedIn, repo, settings, notifier) }).doWork()
            assertTrue(notifier.priceAlerts.isEmpty())
            assertEquals(setOf(1L), repo.seen)
        }

    @Test
    fun `alert poll does nothing signed out and retries on network errors`() =
        runBlocking {
            val repo = FakeNotificationsRepository(listOf(alert(1)))
            build({ c, p -> AlertPollWorker(c, p, FakeAuthRepository(null), repo, FakeSettingsRepository(), notifier) }).doWork()
            assertTrue(notifier.priceAlerts.isEmpty())
            val failing = FailingNotifications(AppError.Network())
            val result = build({ c, p -> AlertPollWorker(c, p, signedIn, failing, FakeSettingsRepository(), notifier) }).doWork()
            assertEquals(ListenableWorker.Result.retry(), result)
            assertEquals(Duration.ofMinutes(30), AlertPollIntervals.of(pushActive = false))
            assertEquals(Duration.ofHours(6), AlertPollIntervals.of(pushActive = true))
        }

    private fun plan(id: Long, dep: String, ret: String) =
        Plan(
            id = id,
            name = "Lisbon trip",
            searchData =
                TravelJson.parseToJsonElement(
                    """{"origin":"BER","destination":"Lisbon, Portugal","departure_date":"$dep","return_date":"$ret","nationality":""}""",
                ) as JsonObject,
        )

    private fun reminders(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosByTag(WorkManagerTripReminderScheduler.TAG).get().filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `trip reminders are rescheduled from saved plans and cancelled with them`() {
        clock.instant = Instant.parse("2026-11-05T10:00:00Z")
        val scheduler = WorkManagerTripReminderScheduler(context, clock)
        scheduler.reschedule(listOf(plan(1, "2026-12-10", "2026-12-12"), plan(2, "2026-12-20", "2026-12-21")))
        // Plan 1: week, day before, 3 days; plan 2: week, day before, 2 days.
        assertEquals(9, reminders().size)
        assertEquals(5, WorkManager.getInstance(context).getWorkInfosByTag("${WorkManagerTripReminderScheduler.TAG}-plan-1").get().size)
        scheduler.reschedule(listOf(plan(2, "2026-12-20", "2026-12-21")))
        assertEquals(4, reminders().size)
        scheduler.reschedule(emptyList())
        assertTrue(reminders().isEmpty())
    }

    @Test
    fun `trip start reminder links to the plan with itinerary, emergency card and near me`() =
        runBlocking {
            val input =
                workDataOf(
                    TripReminderWorker.KEY_PLAN_ID to 4L,
                    TripReminderWorker.KEY_TYPE to "TRIP_START",
                    TripReminderWorker.KEY_DAY to 1,
                    TripReminderWorker.KEY_DESTINATION to "Lisbon",
                )
            build({ c, p -> TripReminderWorker(c, p, FakeSettingsRepository(), notifier) }, input).doWork()
            val posted = notifier.posts.single()
            assertEquals(TravelChannel.TRIP_REMINDERS, posted.channel)
            assertEquals("Your Lisbon trip starts today", posted.title)
            assertEquals("travelplanner://plan/4", posted.deepLink)
            assertEquals(listOf("travelplanner://plan/4", "travelplanner://plan/4", "travelplanner://nearby"), posted.actions.map { it.deepLink })
            notifier.posts.clear()
            build({ c, p -> TripReminderWorker(c, p, FakeSettingsRepository(AppSettings(tripNotifications = false)), notifier) }, input).doWork()
            assertTrue(notifier.posts.isEmpty())
        }

    private class MemoryLog(var last: Instant? = null) : NearbyPostLog {
        override fun lastPostedAt() = last

        override fun markPosted(at: Instant) {
            last = at
        }
    }

    private fun nearbyWorker(settings: AppSettings, plans: List<Plan>, log: NearbyPostLog, location: Pair<Double, Double>? = 38.7 to -9.1) =
        build({ c, p ->
            NearbySuggestionWorker(
                c,
                p,
                FakeSettingsRepository(settings),
                FakeCurrentPlanStore(),
                FakePlansRepository(plans),
                FakeNearbyRepository(
                    AppResult.Success(NearbyResponse(NearbyPlace(name = "Lisbon"), listOf(Activity(name = "Tram 28", description = "Ride the old tram")))),
                ),
                { location },
                log,
                notifier,
                clock,
            )
        })

    @Test
    fun `nearby suggestion only when enabled, during a trip and at most every 4 h`() =
        runBlocking {
            val onTrip = listOf(plan(1, "2026-12-10", "2026-12-14"))
            val log = MemoryLog()
            nearbyWorker(AppSettings(nearbyNotifications = false), onTrip, log).doWork()
            assertTrue(notifier.posts.isEmpty())
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, log).doWork()
            assertEquals("Something to do near you in Lisbon", notifier.posts.single().title)
            assertEquals("travelplanner://nearby", notifier.posts.single().deepLink)
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, log).doWork()
            assertEquals(1, notifier.posts.size)
            clock.instant = clock.instant.plus(Duration.ofHours(4))
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, log).doWork()
            assertEquals(2, notifier.posts.size)
        }

    @Test
    fun `nearby suggestion outside a trip only with always on, and never without a location`() =
        runBlocking {
            val noTrip = listOf(plan(1, "2027-01-10", "2027-01-14"))
            nearbyWorker(AppSettings(nearbyNotifications = true), noTrip, MemoryLog()).doWork()
            assertTrue(notifier.posts.isEmpty())
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true), noTrip, MemoryLog(), location = null).doWork()
            assertTrue(notifier.posts.isEmpty())
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true), noTrip, MemoryLog()).doWork()
            assertEquals(1, notifier.posts.size)
        }

    @Test
    fun `nearby policy rules`() {
        val now = Instant.parse("2026-12-11T10:00:00Z")
        assertFalse(NearbyNotificationPolicy.shouldNotify(enabled = true, always = false, onTrip = false, lastPostedAt = null, now = now))
        assertTrue(NearbyNotificationPolicy.shouldNotify(enabled = true, always = true, onTrip = false, lastPostedAt = null, now = now))
        assertFalse(NearbyNotificationPolicy.shouldNotify(true, false, true, now.minus(Duration.ofHours(3)), now))
        assertTrue(NearbyNotificationPolicy.shouldNotify(true, false, true, now.minus(Duration.ofHours(4)), now))
        val trip = plan(1, "2026-12-10", "2026-12-14").searchRequest!!
        assertTrue(NearbyNotificationPolicy.isOnTrip(listOf(trip), LocalDate.parse("2026-12-14")))
        assertFalse(NearbyNotificationPolicy.isOnTrip(listOf(trip), LocalDate.parse("2026-12-15")))
    }

    @Test
    fun `push payload links to the watch`() {
        assertEquals("travelplanner://alerts?kind=hotel&watch=9", pushAlertLink(mapOf("link" to "/alerts?kind=hotel&watch=9")).toDeepLink())
        assertEquals("travelplanner://alerts?watch=7", pushAlertLink(mapOf("watch_id" to "7")).toDeepLink())
        assertEquals("travelplanner://alerts", pushAlertLink(emptyMap()).toDeepLink())
        assertTrue(NotificationIds.priceAlert(42) != NotificationIds.tripReminder("x"))
    }

    private fun notificationLinkOf(n: AppNotification) =
        io.github.rupakc.travelplanner.core.domain.alerts
            .notificationLink(n)
            .toDeepLink()

    private class FailingNotifications(private val error: AppError) : NotificationsRepository {
        override suspend fun list(unreadOnly: Boolean, limit: Int, offset: Int): AppResult<NotificationsPage> = AppResult.Failure(error)

        override suspend fun unreadCount(): AppResult<Int> = AppResult.Failure(error)

        override suspend fun markRead(ids: List<Long>): AppResult<MarkReadResponse> = AppResult.Failure(error)

        override suspend fun markAllRead(): AppResult<MarkReadResponse> = AppResult.Failure(error)

        override suspend fun unseen(limit: Int): AppResult<List<AppNotification>> = AppResult.Failure(error)

        override suspend fun markSeen(ids: List<Long>) = Unit
    }
}

/** Records what would be posted. */
class RecordingNotifier : Notifier {
    data class Post(val channel: TravelChannel, val id: Int, val title: String, val body: String, val deepLink: String, val actions: List<NotificationAction>)

    val posts = mutableListOf<Post>()
    val priceAlerts = mutableListOf<AppNotification>()

    override fun canPost(): Boolean = true

    override fun post(channel: TravelChannel, id: Int, title: String, body: String, deepLink: String, actions: List<NotificationAction>) {
        posts += Post(channel, id, title, body, deepLink, actions)
    }

    override fun postPriceAlert(notification: AppNotification) {
        priceAlerts += notification
    }

    override fun cancel(id: Int) = Unit
}
