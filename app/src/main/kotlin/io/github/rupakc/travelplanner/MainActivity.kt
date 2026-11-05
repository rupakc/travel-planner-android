package io.github.rupakc.travelplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.notifications.withPushDeepLink
import io.github.rupakc.travelplanner.navigation.DeepLinkIntents
import io.github.rupakc.travelplanner.navigation.TravelApp

/** AppCompatActivity (not ComponentActivity) so AppCompatDelegate per-app locales apply on API 26–32 too. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // A push shown by the system (older servers) carries only a web `link` extra: give it the app deep link.
        // Then drop Navigation's internal deep-link extras and foreign data (exported activity, S-M1).
        DeepLinkIntents.sanitize(withPushDeepLink(intent))
        super.onCreate(savedInstanceState)
        // Keep the splash until the session/profile are read from DataStore (no Login flash).
        splash.setKeepOnScreenCondition { viewModel.state.value.gate == AuthGate.LOADING }
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            TravelTheme(themeMode = state.settings.themeMode, dynamicColor = state.settings.dynamicColor) {
                TravelApp(gate = state.gate)
            }
        }
    }

    /** singleTask: the NavHost's new-intent listener sees the intent after this maps its `link` extra. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(DeepLinkIntents.sanitize(withPushDeepLink(intent)))
    }
}
