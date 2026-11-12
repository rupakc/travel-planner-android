package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.NotificationCatalogRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.NotificationSettingsRepositoryImpl
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationText
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettingsUpdate
import io.github.rupakc.travelplanner.core.model.notifications.NotificationsErrorCode
import io.github.rupakc.travelplanner.core.model.notifications.notificationsErrorCode
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paths, bodies, error mapping and the per-locale cache of the notification routes against a MockWebServer. */
class NotificationRepositoriesImplTest {
    private val server = MockWebServer().apply { start() }
    private val network = TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO))
    private val settings = NotificationSettingsRepositoryImpl(network.api())
    private val catalog = NotificationCatalogRepositoryImpl(network.api())

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test
    fun `settings get and put hit the contract paths`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"categories":{"nearby":{"enabled":false}},"daily_cap":3,"devices":1}""")
            val s = (settings.settings() as AppResult.Success).value
            assertTrue(!s.isEnabled(NotificationCategory.NEARBY))
            assertEquals("/api/notification-settings", server.takeRequest().target)

            respond(200, """{"daily_cap":5}""")
            settings.update(NotificationSettingsUpdate(quietHours = QuietHours(true, "21:00", "06:30"), dailyCap = 5))
            val req = server.takeRequest()
            assertEquals("PUT", req.method)
            assertEquals("/api/notification-settings", req.target)
            assertEquals("""{"quiet_hours":{"enabled":true,"start":"21:00","end":"06:30"},"daily_cap":5}""", req.body?.utf8())
        }

    @Test
    fun `invalid settings map to notification_settings_invalid`() =
        runTest(timeout = TIMEOUT) {
            respond(422, """{"detail":"daily_cap must be 1 to 10","error_code":"notification_settings_invalid"}""")
            val error = (settings.update(NotificationSettingsUpdate(dailyCap = 11)) as AppResult.Failure).error
            assertEquals(NotificationsErrorCode.NOTIFICATION_SETTINGS_INVALID, error.notificationsErrorCode())
            assertEquals("daily_cap must be 1 to 10", error.message)
        }

    @Test
    fun `catalog is fetched once per locale and merged over bundled english`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"locale":"es","templates":{"nearby_arrival":"Estás cerca de {place}"}}""")
            val es = catalog.catalog("es-MX")
            assertEquals("es", es.locale)
            assertEquals("Estás cerca de {place}", es.templates[NotificationText.NEARBY_ARRIVAL])
            assertEquals(NotificationText.BUNDLED_EN[NotificationText.TRIP_T7], es.templates[NotificationText.TRIP_T7])
            assertEquals("/api/notifications/catalog?locale=es", server.takeRequest().target)

            assertEquals(es, catalog.catalog("es"))
            assertEquals(1, server.requestCount)

            respond(200, """{"locale":"en","templates":{}}""")
            catalog.catalog("pt-BR")
            assertEquals("/api/notifications/catalog?locale=en", server.takeRequest().target)
        }

    @Test
    fun `catalog falls back to bundled english and retries after a failure`() =
        runTest(timeout = TIMEOUT) {
            respond(500, """{"detail":"boom"}""")
            val fallback = catalog.catalog("de")
            assertEquals("en", fallback.locale)
            assertEquals(NotificationText.BUNDLED_EN, fallback.templates)
            server.takeRequest()

            respond(200, """{"locale":"de","templates":{"trip_t7":"Noch {days} Tage bis {destination}."}}""")
            assertEquals("de", catalog.catalog("de").locale)
            assertEquals(2, server.requestCount)
        }

    private companion object {
        val TIMEOUT = kotlin.time.Duration.parse("30s")
    }
}
