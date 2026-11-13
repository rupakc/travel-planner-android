package io.github.rupakc.travelplanner.navigation

import android.app.Application
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavDestination
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlayRoute
import io.github.rupakc.travelplanner.feature.play.navigation.playGraph
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Play tab's deep link and the Alerts daily-quiz hand-off (`onOpenPlay`) both land on the Play destination. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlayNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun newController() =
        TestNavHostController(ApplicationProvider.getApplicationContext()).apply { navigatorProvider.addNavigator(ComposeNavigator()) }

    private fun isPlay(d: NavDestination) = d.route?.startsWith(PlayRoute.serializer().descriptor.serialName) == true

    @Test(timeout = 60_000)
    fun `the play deep link uri resolves to the Play destination`() {
        val nav = newController()
        val graph =
            nav.createGraph(startDestination = HomeRoute) {
                composable<HomeRoute> { Text("home") }
                playGraph(onHidden = {})
            }
        val match = graph.matchDeepLink(NavDeepLinkRequest.Builder.fromUri(Uri.parse(DeepLinks.PLAY_PATTERN)).build())
        assertNotNull("travelplanner://play must match a destination", match)
        assertTrue(isPlay(match!!.destination))
    }

    @Test(timeout = 60_000)
    fun `openPlay navigates to the Play destination`() {
        lateinit var nav: TestNavHostController
        compose.setContent {
            nav = newController()
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("home") }
                composable<PlayRoute> { Text("play") }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { nav.openPlay() }
        compose.waitForIdle()
        compose.runOnUiThread { assertTrue(isPlay(nav.currentDestination!!)) }
    }
}
