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
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationsFlags
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadResponse
import io.github.rupakc.travelplanner.core.model.alerts.NotificationsPage
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.notifications.CategorySetting
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollIntervals
import io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollWorker
import io.github.rupakc.travelplanner.core.notifications.alerts.PollBaseline
import io.github.rupakc.travelplanner.core.notifications.alerts.PollBaselineCleaner
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbyArrival
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbyArrivalSource
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbyArrivalText
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbyNotificationPolicy
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbyPostLog
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbySuggestionWorker
import io.github.rupakc.travelplanner.core.notifications.reminders.ReminderPostLog
import io.github.rupakc.travelplanner.core.notifications.reminders.TripReminderWorker
import io.github.rupakc.travelplanner.core.notifications.reminders.WorkManagerTripReminderScheduler
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeJourneyFlagsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNearbyRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationCatalogRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** Already past the first poll for alice (the normal case). */
    private val baseline =
        object : PollBaseline {
            var user: String? = TestUser.username

            override fun user() = user

            override fun set(user: String?) {
                this.user = user
            }
        }

    private val postLog =
        object : ReminderPostLog {
            val names = mutableSetOf<String>()

            override fun wasPosted(name: String) = name in names

            override fun markPosted(name: String) {
                names += name
            }
        }

    private var zone: ZoneId = ZoneId.of("Europe/Lisbon")

    @Test
    fun `alert poll posts unseen alerts once and dedupes by id`() =
        runBlocking {
            val repo = FakeNotificationsRepository(listOf(alert(1), alert(2), alert(3, read = true)))
            val settings = FakeSettingsRepository()
            val worker = { build({ c, p -> AlertPollWorker(c, p, signedIn, repo, settings, notifier, baseline) }) }
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(listOf(1L, 2L), notifier.priceAlerts.map { it.id })
            assertEquals(setOf(1L, 2L), repo.seen)
            worker().doWork()
            assertEquals(2, notifier.priceAlerts.size)
            assertEquals("travelplanner://alerts?kind=hotel&watch=9", notificationLinkOf(alert(1)))
        }

    @Test
    fun `after the session wipe the next poll marks alerts seen instead of posting them all`() =
        runBlocking {
            val repo = FakeNotificationsRepository(listOf(alert(1), alert(2)))
            PollBaselineCleaner(baseline).clear() // what SessionCleaner runs on sign-out (security S-M5)
            assertNull(baseline.user)
            build({ c, p -> AlertPollWorker(c, p, signedIn, repo, FakeSettingsRepository(), notifier, baseline) }).doWork()
            assertTrue(notifier.priceAlerts.isEmpty())
            assertEquals(setOf(1L, 2L), repo.seen)
            assertEquals(TestUser.username, baseline.user)
        }

    @Test
    fun `alert poll respects the price alerts toggle but still marks alerts seen`() =
        runBlocking {
            val repo = FakeNotificationsRepository(listOf(alert(1)))
            val settings = FakeSettingsRepository(AppSettings(priceAlertNotifications = false))
            build({ c, p -> AlertPollWorker(c, p, signedIn, repo, settings, notifier, baseline) }).doWork()
            assertTrue(notifier.priceAlerts.isEmpty())
            assertEquals(setOf(1L), repo.seen)
        }

    @Test
    fun `alert poll does nothing signed out and retries on network errors`() =
        runBlocking {
            val repo = FakeNotificationsRepository(listOf(alert(1)))
            build({ c, p -> AlertPollWorker(c, p, FakeAuthRepository(null), repo, FakeSettingsRepository(), notifier, baseline) }).doWork()
            assertTrue(notifier.priceAlerts.isEmpty())
            val failing = FailingNotifications(AppError.Network())
            val result = build({ c, p -> AlertPollWorker(c, p, signedIn, failing, FakeSettingsRepository(), notifier, baseline) }).doWork()
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
        val scheduler = WorkManagerTripReminderScheduler(context, clock) { zone }
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
            build({ c, p -> TripReminderWorker(c, p, FakeSettingsRepository(), notifier, postLog, PlaceNameLookup(FakeSearchRepository())) }, input).doWork()
            val posted = notifier.posts.single()
            assertEquals(TravelChannel.TRIP_REMINDERS, posted.channel)
            assertEquals("Your Lisbon trip starts today", posted.title)
            assertEquals("travelplanner://plan/4", posted.deepLink)
            assertEquals(listOf("travelplanner://plan/4", "travelplanner://plan/4", "travelplanner://nearby"), posted.actions.map { it.deepLink })
            notifier.posts.clear()
            build({ c, p ->
                TripReminderWorker(
                    c,
                    p,
                    FakeSettingsRepository(AppSettings(tripNotifications = false)),
                    notifier,
                    postLog,
                    PlaceNameLookup(FakeSearchRepository()),
                )
            }, input).doWork()
            assertTrue(notifier.posts.isEmpty())
        }

    @Test
    fun `trip reminder names the city of an airport code, or keeps the code when the lookup has nothing`() =
        runBlocking {
            val input =
                workDataOf(
                    TripReminderWorker.KEY_PLAN_ID to 5L,
                    TripReminderWorker.KEY_TYPE to "TRIP_START",
                    TripReminderWorker.KEY_DAY to 1,
                    TripReminderWorker.KEY_DESTINATION to "LIS",
                )
            val search = FakeSearchRepository().apply { airports = listOf(Airport(iataCode = "LIS", name = "Humberto Delgado", city = "Lisbon")) }
            build({ c, p -> TripReminderWorker(c, p, FakeSettingsRepository(), notifier, postLog, PlaceNameLookup(search)) }, input).doWork()
            assertEquals("Your Lisbon trip starts today", notifier.posts.single().title)
            notifier.posts.clear()
            build({ c, p -> TripReminderWorker(c, p, FakeSettingsRepository(), notifier, postLog, PlaceNameLookup(FakeSearchRepository())) }, input).doWork()
            assertEquals("Your LIS trip starts today", notifier.posts.single().title)
        }

    private class MemoryLog(var last: Instant? = null) : NearbyPostLog {
        override fun lastPostedAt() = last

        override fun markPosted(at: Instant) {
            last = at
        }
    }

    /** Every nearby repository the worker got; none may ever see a `/api/nearby` call. */
    private val nearbyRepos = mutableListOf<FakeNearbyRepository>()

    private val nearbyCatalog = FakeNotificationCatalogRepository()
    private val nearbyServer = FakeNotificationSettingsRepository()
    private var nearbyArrival: NearbyArrival? = null
    private val nearbyTelemetry = io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry()
    private var nearbyFlags = FakeJourneyFlagsRepository(JourneyFlags(notifications = NotificationsFlags(notificationsApi = 1)))
    private var nearbyAuth = signedIn

    private fun nearbyWorker(settings: AppSettings, plans: List<Plan>, log: NearbyPostLog, cellAgeHours: Long? = 1) =
        build({ c, p ->
            NearbySuggestionWorker(
                c,
                p,
                FakeSettingsRepository(settings),
                FakeCurrentPlanStore(),
                FakePlansRepository(plans),
                FakeNearbyRepository(AppResult.Success(NearbyResponse(NearbyPlace(name = "Lisbon")))).apply {
                    last = cellAgeHours?.let { LocationCell(38.725, -9.125, clock.instant.minus(Duration.ofHours(it)).toEpochMilli()) }
                    nearbyRepos += this
                },
                log,
                notifier,
                clock,
                nearbyCatalog,
                nearbyServer,
                nearbyFlags,
                nearbyAuth,
                object : NearbyArrivalSource {
                    override suspend fun arrival(cell: LocationCell) = nearbyArrival
                },
                nearbyTelemetry,
            )
        })

    @Test
    fun `nearby text comes from the catalog template when an arrival is known`() =
        runBlocking {
            nearbyArrival = NearbyArrival("Lisbon", "Time Out Market", 7)
            nearbyCatalog.templates = mapOf("nearby_arrival" to "Cerca de {place}: {item}, {minutes} min")
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true, localeTag = "es"), emptyList(), MemoryLog()).doWork()
            assertEquals("Cerca de Lisbon: Time Out Market, 7 min", notifier.posts.single().body)
            assertEquals(listOf<String?>("es"), nearbyCatalog.requests)
            assertTrue(nearbyRepos.all { it.requests.isEmpty() })
        }

    @Test
    fun `without minutes the text names the pick and its category, and telemetry says personalized`() =
        runBlocking {
            val on = AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true)
            nearbyArrival = NearbyArrival("Lisbon", "Time Out Market", reason = "food")
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertEquals("Time Out Market is near you — food", notifier.posts.single().body)
            nearbyArrival = NearbyArrival("Lisbon", "Alfama")
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertEquals("Alfama is near you", notifier.posts.last().body)
            nearbyArrival = null
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertEquals("Places to see, eat, shop and stay around you. Tap to look.", notifier.posts.last().body)
            assertEquals(
                listOf<Pair<String, Map<String, Any?>>>(
                    "nearby_notif_post" to mapOf("kind" to "personalized"),
                    "nearby_notif_post" to mapOf("kind" to "personalized"),
                    "nearby_notif_post" to mapOf("kind" to "generic"),
                ),
                nearbyTelemetry.events,
            )
        }

    @Test
    fun `a missing catalog key falls back to the bundled English template`() {
        val text = NearbyArrivalText.body(emptyMap(), NearbyArrival("Lisbon", "Time Out Market", 7))
        assertEquals("You're near Lisbon: Time Out Market is 7 min away", text)
    }

    @Test
    fun `the server settings are fetched only with the api, a session and passing local rules`() =
        runBlocking {
            val on = AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true)
            nearbyFlags.flags = JourneyFlags()
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertEquals(0, nearbyServer.settingsCalls)
            assertEquals(1, notifier.posts.size)
            nearbyFlags.flags = JourneyFlags(notifications = NotificationsFlags(notificationsApi = 1))
            nearbyAuth = FakeAuthRepository(null)
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertEquals(0, nearbyServer.settingsCalls)
            nearbyAuth = signedIn
            nearbyWorker(AppSettings(nearbyNotifications = false), emptyList(), MemoryLog()).doWork()
            assertEquals(0, nearbyServer.settingsCalls)
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertEquals(1, nearbyServer.settingsCalls)
        }

    @Test
    fun `the server nearby switch and the user's quiet hours are honoured on the device`() =
        runBlocking {
            val on = AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true)
            nearbyServer.current = NotificationSettings(categories = mapOf("nearby" to CategorySetting(false)))
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertTrue(notifier.posts.isEmpty())
            val zone = clock.zone()
            val local = clock.instant.atZone(zone).toLocalTime()
            val around = QuietHours(true, local.minusHours(1).toString().take(5), local.plusHours(1).toString().take(5))
            nearbyServer.current = NotificationSettings(quietHours = around)
            nearbyWorker(on, emptyList(), MemoryLog()).doWork()
            assertTrue(notifier.posts.isEmpty())
            assertTrue(NearbyArrivalText.inUserQuietHours(QuietHours(true, "20:00", "07:00"), LocalTime.of(23, 0)))
            assertTrue(NearbyArrivalText.inUserQuietHours(QuietHours(true, "20:00", "07:00"), LocalTime.of(6, 0)))
            assertFalse(NearbyArrivalText.inUserQuietHours(QuietHours(true, "20:00", "07:00"), LocalTime.of(12, 0)))
            assertFalse(NearbyArrivalText.inUserQuietHours(QuietHours(false, "20:00", "07:00"), LocalTime.of(23, 0)))
            assertFalse(NearbyArrivalText.inUserQuietHours(QuietHours(true, "09:00", "09:00"), LocalTime.of(9, 0)))
            assertFalse(NearbyArrivalText.inUserQuietHours(QuietHours(true, "9am", "07:00"), LocalTime.of(9, 0)))
        }

    @Test
    fun `nearby invitation only when enabled, during a trip and at most once a day`() =
        runBlocking {
            val onTrip = listOf(plan(1, "2026-12-10", "2026-12-14"))
            val log = MemoryLog()
            nearbyWorker(AppSettings(nearbyNotifications = false), onTrip, log).doWork()
            assertTrue(notifier.posts.isEmpty())
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, log).doWork()
            assertEquals("See what’s near you", notifier.posts.single().title)
            assertEquals("Places to see, eat, shop and stay around you. Tap to look.", notifier.posts.single().body)
            assertEquals("travelplanner://nearby", notifier.posts.single().deepLink)
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, log).doWork()
            assertEquals(1, notifier.posts.size)
            // 4 h later is still the same day: nothing.
            clock.instant = clock.instant.plus(Duration.ofHours(4))
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, log).doWork()
            assertEquals(1, notifier.posts.size)
            // Next morning (10:00 Lisbon): allowed again.
            clock.instant = Instant.parse("2026-12-12T10:00:00Z")
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, log).doWork()
            assertEquals(2, notifier.posts.size)
        }

    @Test
    fun `nearby suggestion outside a trip only with always on, and never without a location`() =
        runBlocking {
            val noTrip = listOf(plan(1, "2027-01-10", "2027-01-14"))
            nearbyWorker(AppSettings(nearbyNotifications = true), noTrip, MemoryLog()).doWork()
            assertTrue(notifier.posts.isEmpty())
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true), noTrip, MemoryLog(), cellAgeHours = null).doWork()
            assertTrue(notifier.posts.isEmpty())
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true), noTrip, MemoryLog()).doWork()
            assertEquals(1, notifier.posts.size)
        }

    @Test
    fun `nearby suggestions ignore a remembered location older than 24 h`() =
        runBlocking {
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true), emptyList(), MemoryLog(), cellAgeHours = 25).doWork()
            assertTrue(notifier.posts.isEmpty())
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true), emptyList(), MemoryLog(), cellAgeHours = 23).doWork()
            assertEquals(1, notifier.posts.size)
        }

    @Test
    fun `the nearby invitation never calls the nearby API`() =
        runBlocking {
            val onTrip = listOf(plan(1, "2026-12-10", "2026-12-14"))
            nearbyWorker(AppSettings(nearbyNotifications = true), onTrip, MemoryLog()).doWork()
            nearbyWorker(AppSettings(nearbyNotifications = true, nearbyNotificationsAlways = true), emptyList(), MemoryLog()).doWork()
            nearbyWorker(AppSettings(nearbyNotifications = false), onTrip, MemoryLog()).doWork()
            assertEquals(2, notifier.posts.size)
            assertEquals(3, nearbyRepos.size)
            assertTrue(nearbyRepos.all { it.requests.isEmpty() })
        }

    @Test
    fun `nearby policy rules`() {
        val zone = ZoneId.of("Europe/Lisbon")
        val now = Instant.parse("2026-12-11T10:00:00Z")
        assertFalse(NearbyNotificationPolicy.shouldNotify(enabled = true, always = false, onTrip = false, lastPostedAt = null, now = now, zone = zone))
        assertTrue(NearbyNotificationPolicy.shouldNotify(enabled = true, always = true, onTrip = false, lastPostedAt = null, now = now, zone = zone))
        // Once per local day.
        assertFalse(NearbyNotificationPolicy.shouldNotify(true, false, true, Instant.parse("2026-12-11T08:30:00Z"), now, zone))
        assertTrue(NearbyNotificationPolicy.shouldNotify(true, false, true, Instant.parse("2026-12-10T21:00:00Z"), now, zone))
        // Quiet hours 22:00–08:00 local (Tokyo is UTC+9: 10:00Z is 19:00, 14:00Z is 23:00, 22:59Z is 07:59).
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertTrue(NearbyNotificationPolicy.shouldNotify(true, true, false, null, now, tokyo))
        assertFalse(NearbyNotificationPolicy.shouldNotify(true, true, false, null, Instant.parse("2026-12-11T14:00:00Z"), tokyo))
        assertFalse(NearbyNotificationPolicy.shouldNotify(true, true, false, null, Instant.parse("2026-12-11T22:59:00Z"), tokyo))
        assertTrue(NearbyNotificationPolicy.shouldNotify(true, true, false, null, Instant.parse("2026-12-11T23:00:00Z"), tokyo))
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

    @Test
    fun `first poll after sign-in seeds the seen table without posting, later ones post only newer alerts`() =
        runBlocking {
            baseline.user = null
            val repo = FakeNotificationsRepository(listOf(alert(1), alert(2)))
            val poll: suspend () -> Unit = { build({ c, p -> AlertPollWorker(c, p, signedIn, repo, FakeSettingsRepository(), notifier, baseline) }).doWork() }
            poll()
            assertTrue(notifier.priceAlerts.isEmpty())
            assertEquals(setOf(1L, 2L), repo.seen)
            assertEquals("alice", baseline.user)
            repo.items.value = listOf(alert(3)) + repo.items.value
            poll()
            assertEquals(listOf(3L), notifier.priceAlerts.map { it.id })
        }

    @Test
    fun `trip reminder and morning brief pushes honour the local trip switch`() =
        runBlocking {
            val off = FakeSettingsRepository(AppSettings(tripNotifications = false))
            for ((i, category) in listOf("trip_reminders", "morning_brief").withIndex()) {
                val data =
                    mapOf(
                        "kind" to "trip_phase",
                        "category" to category,
                        "notification_id" to "${70 + i}",
                        "plan_id" to "3",
                        "title" to "T",
                        "body" to "B",
                    )
                assertFalse(PushAlertHandler(signedIn, FakeNotificationsRepository(), off, notifier).handle(data))
                assertTrue(PushAlertHandler(signedIn, FakeNotificationsRepository(), FakeSettingsRepository(), notifier).handle(data))
            }
            assertEquals(2, notifier.posts.size)
        }

    @Test
    fun `a data-only push is marked seen, honours the toggle and opens the watch`() =
        runBlocking {
            val repo = FakeNotificationsRepository()
            val data =
                mapOf(
                    "kind" to "hotel_drop",
                    "notification_id" to "57",
                    "link" to "/alerts?kind=hotel&watch=9",
                    "watch_id" to "9",
                    "watch_kind" to "hotel",
                    "title" to "Lumen dropped to €90",
                    "body" to "For 10–14 Dec",
                )
            assertTrue(PushAlertHandler(signedIn, repo, FakeSettingsRepository(), notifier).handle(data))
            val post = notifier.posts.single()
            assertEquals(TravelChannel.PRICE_ALERTS, post.channel)
            assertEquals("travelplanner://alerts?kind=hotel&watch=9&notif_cat=price_drops", post.deepLink)
            assertEquals("Lumen dropped to €90", post.title)
            assertEquals(setOf(57L), repo.seen)
            // The poller won't post it again.
            assertTrue((repo.unseen() as AppResult.Success).value.none { it.id == 57L })
            notifier.posts.clear()
            val off = FakeSettingsRepository(AppSettings(priceAlertNotifications = false))
            assertFalse(PushAlertHandler(signedIn, repo, off, notifier).handle(data + ("notification_id" to "58")))
            assertTrue(notifier.posts.isEmpty())
            assertTrue(58L in repo.seen)
            assertTrue(
                PushAlertHandler(signedIn, repo, FakeSettingsRepository(), notifier).handle(data + ("kind" to "watch_expired") + ("notification_id" to "59")),
            )
            assertTrue(notifier.posts.single().quiet)
        }

    @Test
    fun `a system-shown push opens the watch from its link extra`() {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).putExtra("link", "/alerts?kind=trip&watch=3").putExtra("watch_id", "3")
        assertEquals("travelplanner://alerts?kind=trip&watch=3&notif_cat=price_drops", withPushDeepLink(intent).data.toString())
        val fare = android.content.Intent().putExtra("watch_id", "7")
        assertEquals("travelplanner://alerts?watch=7&notif_cat=price_drops", withPushDeepLink(fare).data.toString())
        val plain = android.content.Intent(android.content.Intent.ACTION_MAIN)
        assertEquals(null, withPushDeepLink(plain).data)
    }

    @Test
    fun `rescheduling keeps a pending reminder that is due, and never posts it twice`() =
        runBlocking {
            // 08:05 Lisbon on the first day: the 08:00 trip-start reminder is overdue but within the grace window.
            clock.instant = Instant.parse("2026-12-10T08:05:00Z")
            val scheduler = WorkManagerTripReminderScheduler(context, clock) { zone }
            val plans = listOf(plan(1, "2026-12-10", "2026-12-12"))
            scheduler.reschedule(plans)

            fun all() = WorkManager.getInstance(context).getWorkInfosByTag(WorkManagerTripReminderScheduler.TAG).get()
            // The overdue start-of-trip reminder is (re)scheduled to run at once, not dropped.
            val start = all().first { info -> info.tags.any { it.contains("TRIP_START") } }
            // A pending future reminder is kept as it is (same work) when the plans re-emit.
            val lastDay = reminders().single { info -> info.tags.any { it.contains("LAST_DAY") } }
            scheduler.reschedule(plans)
            assertEquals(lastDay.id, reminders().single { info -> info.tags.any { it.contains("LAST_DAY") } }.id)
            val name = start.tags.first { it.startsWith("trip-reminder-name:") }.removePrefix("trip-reminder-name:")
            val input =
                workDataOf(
                    TripReminderWorker.KEY_PLAN_ID to 1L,
                    TripReminderWorker.KEY_TYPE to "TRIP_START",
                    TripReminderWorker.KEY_DAY to 1,
                    TripReminderWorker.KEY_DESTINATION to "Lisbon",
                    TripReminderWorker.KEY_UNIQUE to name,
                )
            repeat(2) {
                build({ c, p ->
                    TripReminderWorker(c, p, FakeSettingsRepository(), notifier, postLog, PlaceNameLookup(FakeSearchRepository()))
                }, input).doWork()
            }
            assertEquals(1, notifier.posts.size)
        }

    @Test
    fun `a time-zone change re-times reminders to 08 00 in the new zone`() {
        clock.instant = Instant.parse("2026-11-05T10:00:00Z")
        val scheduler = WorkManagerTripReminderScheduler(context, clock) { zone }
        val plans = listOf(plan(1, "2026-12-10", "2026-12-11"))
        scheduler.reschedule(plans)
        val before = reminders().map { it.id }.toSet()
        zone = ZoneId.of("Asia/Tokyo")
        scheduler.reschedule(plans, clockChanged = true)
        val after = reminders()
        assertEquals(before.size, after.size)
        assertTrue(after.none { it.id in before })
    }

    @Test
    fun `a push posts synchronously on the FCM thread`() {
        val repo = FakeNotificationsRepository()
        val data = mapOf("kind" to "fare_drop", "notification_id" to "70", "link" to "/alerts?watch=7", "title" to "Berlin → Lisbon dropped")
        // No coroutine scope involved: the notification exists when the call returns.
        assertTrue(handleBlocking(PushAlertHandler(signedIn, repo, FakeSettingsRepository(), notifier), data))
        assertEquals("travelplanner://alerts?watch=7&notif_cat=price_drops", notifier.posts.single().deepLink)
    }

    private class RecordingScheduler : io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollScheduler {
        val calls = mutableListOf<Boolean?>()

        override fun schedule(pushActive: Boolean) {
            calls += pushActive
        }

        override fun cancel() {
            calls += null
        }
    }

    /** Health whose capabilities the test sets; counts the network-backed refreshes. */
    private class TestHealth(initial: io.github.rupakc.travelplanner.core.model.health.Health? = null) :
        io.github.rupakc.travelplanner.core.data.repository.HealthRepository {
        val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
        override val capabilities: kotlinx.coroutines.flow.StateFlow<io.github.rupakc.travelplanner.core.model.health.Health?> = state
        var refreshes = 0

        override suspend fun refresh(): AppResult<io.github.rupakc.travelplanner.core.model.health.Health> = AppResult.Failure(AppError.Network())

        override suspend fun refreshIfStale(): io.github.rupakc.travelplanner.core.model.health.Health? {
            refreshes++
            return state.value
        }

        override suspend fun probe(baseUrl: String): AppResult<io.github.rupakc.travelplanner.core.model.health.Health> = refresh()
    }

    private class MemoryRegistrationLog : PushRegistrationLog {
        var value: PushRegistration? = null

        override fun last() = value

        override fun record(registration: PushRegistration) {
            value = registration
        }

        override fun clear() {
            value = null
        }
    }

    private fun initializer(
        auth: FakeAuthRepository,
        scope: kotlinx.coroutines.CoroutineScope,
        health: io.github.rupakc.travelplanner.core.data.repository.HealthRepository = TestHealth(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        scheduler: RecordingScheduler = RecordingScheduler(),
        nearbyRepo: FakeNearbyRepository = FakeNearbyRepository(),
        devices: io.github.rupakc.travelplanner.core.testing.fake.FakeDevicesRepository =
            io.github.rupakc.travelplanner.core.testing.fake.FakeDevicesRepository(),
        foreground: AppForeground = AppForeground { },
    ) = NotificationsInitializer(
        context,
        scope,
        auth,
        health,
        FakePlansRepository(),
        settings,
        scheduler,
        object : io.github.rupakc.travelplanner.core.notifications.reminders.TripReminderScheduler {
            override fun reschedule(plans: List<Plan>, clockChanged: Boolean) = Unit
        },
        io.github.rupakc.travelplanner.core.notifications.nearby.NearbySuggestionsToggle(
            object : io.github.rupakc.travelplanner.core.notifications.nearby.NearbySuggestionScheduler {
                override fun setEnabled(enabled: Boolean) = Unit
            },
            nearbyRepo,
        ),
        PushRegistrar(auth, { "fcm-1" }, devices, MemoryRegistrationLog(), clock, foreground),
        baseline,
        foreground,
    )

    @Test
    fun `turning nearby suggestions off forgets the remembered location`() =
        runBlocking {
            val settings = FakeSettingsRepository(AppSettings(nearbyNotifications = true))
            val nearbyRepo = FakeNearbyRepository().apply { last = LocationCell(38.725, -9.125, 0) }
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            initializer(FakeAuthRepository(null), scope, settings = settings, nearbyRepo = nearbyRepo).start()
            assertTrue(nearbyRepo.last != null)
            settings.setNotificationToggles(nearby = false)
            assertNull(nearbyRepo.last)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            Unit
        }

    @Test
    fun `the poll is scheduled once, only after capabilities are known`() =
        runBlocking {
            val health = TestHealth(initial = null)
            val scheduler = RecordingScheduler()
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            initializer(signedIn, scope, health = health, scheduler = scheduler).apply { firebaseConfigured = { true } }.start()
            assertTrue("nothing scheduled from unknown capabilities: ${scheduler.calls}", scheduler.calls.isEmpty())
            health.state.value =
                io.github.rupakc.travelplanner.core.model.health
                    .Health(pushConfigured = true)
            assertEquals(listOf<Boolean?>(true), scheduler.calls)
            health.state.value =
                io.github.rupakc.travelplanner.core.model.health
                    .Health(pushConfigured = true, serpapiConfigured = true)
            assertEquals(listOf<Boolean?>(true), scheduler.calls)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            Unit
        }

    @Test
    fun `a background process start makes no health or device calls`() =
        runBlocking {
            val health = TestHealth()
            val devices = io.github.rupakc.travelplanner.core.testing.fake.FakeDevicesRepository()
            val never = AppForeground { kotlinx.coroutines.awaitCancellation() }
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            initializer(signedIn, scope, health = health, devices = devices, foreground = never).start()
            assertEquals(0, health.refreshes)
            assertTrue(devices.devices.value.isEmpty())
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            val shown = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            initializer(signedIn, shown, health = health, devices = devices).start()
            assertEquals(1, health.refreshes)
            assertEquals(1, devices.devices.value.size)
            shown.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            Unit
        }

    @Test
    fun `the device is registered again only when user, token, locale or server change, or after 7 days`() =
        runBlocking {
            val devices = io.github.rupakc.travelplanner.core.testing.fake.FakeDevicesRepository()
            val log = MemoryRegistrationLog()
            var token = "fcm-1"
            val registrar = PushRegistrar(signedIn, { token }, devices, log, clock) { }
            assertTrue(registrar.register())
            assertFalse(registrar.register())
            token = "fcm-2"
            assertTrue(registrar.register())
            assertFalse(registrar.register("fcm-2"))
            clock.instant = clock.instant.plus(PushRegistrar.REREGISTER_AFTER)
            assertTrue(registrar.register())
            PushRegistrationCleaner(log).clear() // sign-out: the server forgot this token
            assertTrue(registrar.register())
            assertEquals(4, devices.devices.value.size)
            assertFalse(PushRegistrar(FakeAuthRepository(null), { token }, devices, MemoryRegistrationLog(), clock) { }.register("fcm-3"))
            val other = Session("t", TestUser.copy(username = "bob"))
            assertTrue(PushRegistrar(FakeAuthRepository(other), { token }, devices, log, clock) { }.register())
            assertTrue(
                pushRegistrationFingerprint("alice", "t", "en", "https://a") != pushRegistrationFingerprint("alice", "t", "en", "https://b"),
            )
        }

    @Test
    fun `a push posts nothing without a session but is still marked seen`() =
        runBlocking {
            val repo = FakeNotificationsRepository()
            val data = mapOf("kind" to "fare_drop", "notification_id" to "80", "title" to "Berlin → Lisbon dropped")
            assertFalse(PushAlertHandler(FakeAuthRepository(null), repo, FakeSettingsRepository(), notifier).handle(data))
            assertTrue(notifier.posts.isEmpty())
            assertEquals(setOf(80L), repo.seen)
        }

    @Test
    fun `a rate-limited poll waits for the next period instead of retrying`() =
        runBlocking {
            val limited = FailingNotifications(AppError.RateLimited(retryAfterSeconds = 120, message = "slow down"))
            val result = build({ c, p -> AlertPollWorker(c, p, signedIn, limited, FakeSettingsRepository(), notifier, baseline) }).doWork()
            assertEquals(ListenableWorker.Result.success(), result)
            val server = FailingNotifications(AppError.Server(503, "down"))
            assertEquals(
                ListenableWorker.Result.retry(),
                build({ c, p ->
                    AlertPollWorker(c, p, signedIn, server, FakeSettingsRepository(), notifier, baseline)
                }).doWork(),
            )
        }

    private class MemoryIntervalStore : io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollIntervalStore {
        var value: Duration? = null

        override fun applied() = value

        override fun set(interval: Duration?) {
            value = interval
        }
    }

    @Test
    fun `the poll interval is persisted, kept when unchanged and updated when it changes`() {
        val store = MemoryIntervalStore()

        fun poll() =
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(
                io.github.rupakc.travelplanner.core.notifications.alerts.WorkManagerAlertPollScheduler.UNIQUE,
            ).get().single { it.state == WorkInfo.State.ENQUEUED }
        io.github.rupakc.travelplanner.core.notifications.alerts.WorkManagerAlertPollScheduler(context, store).schedule(pushActive = true)
        val first = poll()
        assertEquals(Duration.ofHours(6), store.value)
        assertEquals(Duration.ofHours(6).toMillis(), first.periodicityInfo?.repeatIntervalMillis)
        // A new process with the same interval keeps the schedule as it is.
        io.github.rupakc.travelplanner.core.notifications.alerts.WorkManagerAlertPollScheduler(context, store).schedule(pushActive = true)
        assertEquals(first.id, poll().id)
        assertEquals(first.nextScheduleTimeMillis, poll().nextScheduleTimeMillis)
        io.github.rupakc.travelplanner.core.notifications.alerts.WorkManagerAlertPollScheduler(context, store).schedule(pushActive = false)
        assertEquals(Duration.ofMinutes(30).toMillis(), poll().periodicityInfo?.repeatIntervalMillis)
        assertEquals(Duration.ofMinutes(30), store.value)
        assertEquals(
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            AlertPollIntervals.policy(Duration.ofMinutes(30), Duration.ofMinutes(30)),
        )
        assertEquals(androidx.work.ExistingPeriodicWorkPolicy.UPDATE, AlertPollIntervals.policy(null, Duration.ofMinutes(30)))
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
    data class Post(
        val channel: TravelChannel,
        val id: Int,
        val title: String,
        val body: String,
        val deepLink: String,
        val actions: List<NotificationAction>,
        val quiet: Boolean = false,
    )

    val posts = mutableListOf<Post>()
    val priceAlerts = mutableListOf<AppNotification>()

    override fun canPost(): Boolean = true

    override fun post(
        channel: TravelChannel,
        id: Int,
        title: String,
        body: String,
        deepLink: String,
        actions: List<NotificationAction>,
        quiet: Boolean,
    ) {
        posts += Post(channel, id, title, body, deepLink, actions, quiet)
    }

    override fun postPriceAlert(notification: AppNotification) {
        priceAlerts += notification
    }

    override fun cancel(id: Int) = Unit
}
