package io.github.rupakc.travelplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.notifications.withPushDeepLink
import io.github.rupakc.travelplanner.navigation.TravelApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until the session/profile are read from DataStore (no Login flash).
        splash.setKeepOnScreenCondition { viewModel.state.value.gate == AuthGate.LOADING }
        enableEdgeToEdge()
        // A push shown by the system (older servers) carries only a web `link` extra: give it the app deep link.
        withPushDeepLink(intent)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            TravelTheme(themeMode = state.settings.themeMode, dynamicColor = state.settings.dynamicColor) {
                TravelApp(gate = state.gate)
            }
        }
    }

    /** singleTask: the NavHost's new-intent listener sees the intent after this maps its `link` extra. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(withPushDeepLink(intent))
    }
}
