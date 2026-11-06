package io.github.rupakc.travelplanner.feature.share

import android.app.Application
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.share.navigation.SHARE_APP_LINK_BASE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ShareScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `unavailable link shows the web copy and plan-your-own`() {
        var planOwn = false
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                ShareScreen(ShareUiState(isLoading = false, error = AppError.NotFound()), {}, {}, { planOwn = true }, {}, {})
            }
        }
        compose.onNodeWithText("This shared trip isn't available").assertIsDisplayed()
        compose.onNodeWithText("Plan your own trip").performClick()
        assertTrue(planOwn)
    }

    @Test
    fun `shared trip renders poster, cards and save a copy when signed in`() {
        val events = mutableListOf<ShareEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.DARK) {
                ShareScreen(ShareUiState(isLoading = false, trip = SharedTrip.from(sharedFixture), canSaveCopy = true), { events += it }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Tokyo").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save a copy"))
        compose.onNodeWithText("Save a copy").performClick()
        assertEquals(ShareEvent.SaveCopy, events.single())
        compose.onNodeWithText("Shared read-only — personal details are never included.").assertExists()
    }

    @Test
    fun `the poster shows the city of an airport code`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                ShareScreen(ShareUiState(isLoading = false, trip = SharedTrip.from(sharedFixture, mapOf("NYC" to "New York"))), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("from New York", substring = true).assertExists()
        compose.onNodeWithText("from NYC", substring = true).assertDoesNotExist()
    }

    @Test
    fun `shared itinerary renders through the shared day cards with derived times and notes`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                ShareScreen(ShareUiState(isLoading = false, trip = SharedTrip.from(sharedFixture)), {}, {}, {}, {}, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Market"))
        compose.onNodeWithText("Market").assertExists()
        compose.onNodeWithText("09:00–11:00").assertExists()
        compose.onNodeWithText("📝 Go early", substring = true).assertExists()
    }

    @Test
    fun `app share deep links open the share route with the token`() {
        lateinit var nav: TestNavHostController
        compose.setContent {
            nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("home") }
                composable<ShareRoute>(
                    deepLinks = listOf(navDeepLink<ShareRoute>(basePath = SHARE_APP_LINK_BASE)),
                ) { Text("share") }
            }
        }
        compose.runOnUiThread { nav.navigate(Uri.parse("travelplanner://share/abc123")) }
        assertEquals("abc123", nav.currentBackStackEntry!!.toRoute<ShareRoute>().token)
    }
}
