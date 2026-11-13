package io.github.rupakc.travelplanner

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.domain.journey.StartRoute
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripsRoute
import io.github.rupakc.travelplanner.navigation.TopLevelDestination
import io.github.rupakc.travelplanner.navigation.gateTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression: right after sign-in the gate sent a journey user to HomeRoute, which is not one of the journey tabs, so the shell
 * (top and bottom bar) stayed hidden until relaunch, when the start destination was the shell's home.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class HomeBarsAfterSignInTest {
    @get:Rule val compose = createComposeRule()

    private fun gateTargetFromLogin(home: AppRoute): AppRoute? {
        lateinit var nav: TestNavHostController
        compose.setContent {
            nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            NavHost(nav, startDestination = LoginRoute) {
                composable<LoginRoute> { Text("login") }
            }
        }
        compose.waitForIdle()
        var target: AppRoute? = null
        compose.runOnUiThread { target = gateTarget(AuthGate.READY, nav.currentDestination!!, retakingQuiz = false, home = home) }
        return target
    }

    @Test
    fun `with the journey on, signing in lands on a tab of the journey shell`() {
        val shell = MainUiState(flags = JourneyFlags(trips = true, journey = true), startRoute = StartRoute.Trips).shellMode()
        val target = gateTargetFromLogin(shell.home)
        assertEquals(TripsRoute, target)
        val tabs = TopLevelDestination.forJourney(shell.journey)
        assertTrue("target must be a tab so both bars show", tabs.any { it.routeClass.isInstance(target) })
    }

    @Test
    fun `with the journey off, signing in lands on Home`() {
        assertEquals(HomeRoute, gateTargetFromLogin(MainUiState().shellMode().home))
    }
}
