package io.github.rupakc.travelplanner

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.di.AppModule
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * E2E finding O3: a signed-out cold start composed Home before the gate redirected to Login, so the app sent
 * `GET /api/plans`, `/api/recent-searches` and `/api/taste-profile` (401s). Signed out, the app must not call any
 * endpoint that needs a session.
 */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class SignedOutStartTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    /**
     * The real build info, except cleartext is allowed: MockWebServer speaks plain HTTP, and the prod flavour
     * (HTTPS only, `CLEARTEXT_ALLOWED=false`) rightly refuses an http server URL, which failed `setBaseUrl` under
     * `testProdDebugUnitTest`. What this test checks (the signed-out gate) does not depend on the scheme.
     */
    @BindValue
    @JvmField
    val buildInfo: AppBuildInfo =
        AppBuildInfo(
            defaultBaseUrl = BuildConfig.DEFAULT_BASE_URL,
            flavor = BuildConfig.FLAVOR,
            versionName = BuildConfig.VERSION_NAME,
            debug = BuildConfig.DEBUG,
            cleartextAllowed = true,
            webBaseUrl = BuildConfig.WEB_BASE_URL,
        )

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var auth: AuthRepository

    private val server = MockWebServer()

    @Before fun setUp() {
        hilt.inject()
        server.start()
        // Anything that does reach the server gets a 401, as the real backend answers without a token.
        repeat(QUEUED) { server.enqueue(MockResponse.Builder().code(401).body("""{"detail":"Not authenticated"}""").build()) }
        runBlocking {
            assertTrue(settings.setBaseUrl(server.url("/").toString().trimEnd('/')) is AppResult.Success)
            assertNull(auth.session.first())
        }
    }

    @After fun tearDown() = server.close()

    @Test
    fun `a signed-out start makes no request that needs a session`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        // Let the gate decide, the first screen compose and any ViewModel start its loads (they run off the main thread).
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(SETTLE_STEP_MS)
        }
        val paths = generateSequence { server.takeRequest(1, TimeUnit.MILLISECONDS) }.map { it.url.encodedPath }.toList()
        val authed = paths.filter { it.startsWith("/api/") && !it.startsWith("/api/auth/") }
        assertTrue("authed requests while signed out: $authed", authed.isEmpty())
        controller.pause().stop().destroy()
    }

    private companion object {
        const val QUEUED = 10
        const val SETTLE_ROUNDS = 20
        const val SETTLE_STEP_MS = 100L
    }
}
