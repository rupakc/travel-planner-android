package io.github.rupakc.travelplanner

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.OneShotPreDrawListener
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.rupakc.travelplanner.core.data.repository.OauthInbox
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.notifications.PushTaps
import io.github.rupakc.travelplanner.core.notifications.withPushDeepLink
import io.github.rupakc.travelplanner.navigation.DeepLinkIntents
import io.github.rupakc.travelplanner.navigation.TravelApp
import io.github.rupakc.travelplanner.telemetry.ColdStart
import io.github.rupakc.travelplanner.telemetry.ColdStartGate
import javax.inject.Inject

/** AppCompatActivity (not ComponentActivity) so AppCompatDelegate per-app locales apply on API 26–32 too. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Receives the browser's OAuth return (`voyager://auth/callback`); the login screen consumes it. */
    @Inject lateinit var oauthInbox: OauthInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // A push shown by the system (older servers) carries only a web `link` extra: give it the app deep link.
        // Then drop Navigation's internal deep-link extras and foreign data (exported activity, S-M1).
        DeepLinkIntents.sanitize(withPushDeepLink(intent))
        // Only a fresh launch counts the tap (a recreation replays the same intent).
        if (savedInstanceState == null) PushTaps.consume(intent)?.let(viewModel::reportPushOpen)
        super.onCreate(savedInstanceState)
        // Only a fresh launch: a recreation would replay the same (single-use) return.
        if (savedInstanceState == null) DeepLinkIntents.oauthCallback(intent)?.let(oauthInbox::post)
        val firstActivity = ColdStart.claimFirstActivity()
        val restored = savedInstanceState != null
        // Keep the splash until the session/profile are read from DataStore (no Login flash).
        splash.setKeepOnScreenCondition { viewModel.state.value.let { it.gate == AuthGate.LOADING || it.hold } }
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Status/navigation bar icons follow the app's theme setting, not only the system's.
            EdgeToEdgeSystemBars(dark = state.settings.themeMode.isDark(isSystemInDarkTheme()))
            TravelTheme(themeMode = state.settings.themeMode, dynamicColor = state.settings.dynamicColor) {
                val consentPrompt by viewModel.consentPrompt.collectAsStateWithLifecycle()
                TravelApp(
                    // `hold` keeps the shell undecided until the config / trip count that pick the start screen are known (<= 2 s).
                    gate = if (state.hold) AuthGate.LOADING else state.gate,
                    shell = state.shellMode(),
                    uiEventSink = viewModel.uiEventSink,
                    screenTracker = viewModel.screenTracker,
                    consentPrompt = consentPrompt,
                    onConsentChoice = viewModel::chooseConsent,
                    onCloseConsent = viewModel::closeConsentChoices,
                )
            }
        }
        // Cold start = process start → first frame of the first activity (only foreground, unrestored launches).
        OneShotPreDrawListener.add(window.decorView) {
            val ms = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
            if (ColdStartGate.shouldReport(ColdStart.startedInForeground, firstActivity, restored, ms)) viewModel.reportColdStart(ms)
        }
    }

    /** singleTask: the NavHost's new-intent listener sees the intent after this maps its `link` extra. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(DeepLinkIntents.sanitize(withPushDeepLink(intent)))
        PushTaps.consume(intent)?.let(viewModel::reportPushOpen)
        DeepLinkIntents.oauthCallback(intent)?.let(oauthInbox::post)
    }
}
