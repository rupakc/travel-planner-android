package io.github.rupakc.travelplanner.telemetry

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryRoutes
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Not an AppRoute (sealed), and not in TelemetryRoutes: stands for any unmapped destination. */
@Serializable internal data object UnmappedTestRoute

/** Screen views carry the route template (web routes.js), never ids or tokens. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ScreenTrackerTest {
    @get:Rule val compose = createComposeRule()

    private val telemetry = FakeTelemetry()
    private lateinit var nav: TestNavHostController

    private fun setUpGraph() {
        compose.setContent {
            nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("home") }
                composable<ShareRoute> { Text("share") }
                composable<UnmappedTestRoute> { Text("unmapped") }
            }
        }
        compose.runOnUiThread { nav.addOnDestinationChangedListener(ScreenTracker(telemetry)) }
        compose.waitForIdle()
    }

    @Test
    fun `home reports the home template`() {
        setUpGraph()
        assertEquals(listOf("/home"), telemetry.screens)
    }

    @Test
    fun `share reports the share template and never the token`() {
        setUpGraph()
        compose.runOnUiThread { nav.navigate(ShareRoute("secret-token-123")) }
        compose.waitForIdle()
        assertEquals("/share/:token", telemetry.screens.last())
        assertFalse(telemetry.screens.any { "secret-token-123" in it })
    }

    @Test
    fun `an unmapped route reports other`() {
        setUpGraph()
        compose.runOnUiThread { nav.navigate(UnmappedTestRoute) }
        compose.waitForIdle()
        assertEquals(TelemetryRoutes.OTHER, telemetry.screens.last())
        assertEquals(TelemetryRoutes.OTHER, routeTemplate(null))
    }
}
