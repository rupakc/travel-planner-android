package io.github.rupakc.travelplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentPromptState
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventSink
import io.github.rupakc.travelplanner.core.domain.journey.StartGate
import io.github.rupakc.travelplanner.core.domain.journey.StartRoute
import io.github.rupakc.travelplanner.core.domain.journey.resolveStartRoute
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripsRoute
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.network.api.ClientConfigApi
import io.github.rupakc.travelplanner.navigation.ShellMode
import io.github.rupakc.travelplanner.telemetry.ScreenTracker
import io.github.rupakc.travelplanner.telemetry.TelemetryUiBridge
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the auth gate sends the user (spec §4 Networking + task brief). */
enum class AuthGate { LOADING, SIGNED_OUT, MUST_CHANGE_PASSWORD, NEEDS_ONBOARDING, READY }

/**
 * [flags] are the Phase 1/2 feature gates (all off against the previous backend), [startRoute] the home rule's answer and
 * [hold] keeps the splash while the config / trip count that decide the start screen are still unknown (at most 2 s).
 */
data class MainUiState(
    val gate: AuthGate = AuthGate.LOADING,
    val settings: AppSettings = AppSettings(),
    val flags: JourneyFlags = JourneyFlags.OFF,
    val startRoute: StartRoute = StartRoute.Classic,
    val hold: Boolean = false,
)

/** The shell's view of [MainUiState]: tabs, start screen, celebrations and the Offline tab. */
fun MainUiState.shellMode(): ShellMode =
    ShellMode(
        journey = flags.journey,
        home =
            when (startRoute) {
                StartRoute.Classic -> HomeRoute
                StartRoute.Trips -> TripsRoute
                StartRoute.Explore -> SearchRoute()
            },
        gameEnabled = flags.game,
        offlinePack = flags.offlinePack,
    )

private const val HTTP_NOT_FOUND = 404

/** Pure gate rule, unit-tested. */
fun gateFor(session: Session?, profile: TravellerProfile): AuthGate =
    when {
        session == null -> AuthGate.SIGNED_OUT
        session.user.requiresPasswordChange -> AuthGate.MUST_CHANGE_PASSWORD
        !profile.onboardingCompleted -> AuthGate.NEEDS_ONBOARDING
        else -> AuthGate.READY
    }

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        profileRepository: ProfileRepository,
        settingsRepository: SettingsRepository,
        private val consentRepository: ConsentRepository,
        private val telemetry: Telemetry,
        private val configApi: ClientConfigApi,
        private val tripsRepository: TripsRepository,
        private val flagsCache: JourneyFlagsCache,
    ) : ViewModel() {
        /** Null until `GET /api/config` answered (or a cached answer exists). */
        private val flags = MutableStateFlow(flagsCache.read())

        /** The signed-in user's trip count for the home rule; null = unknown. */
        private val tripCount = MutableStateFlow<Int?>(null)
        private val tripCountKnown = MutableStateFlow(false)
        private val waitOver = MutableStateFlow(false)

        private val base =
            combine(
                authRepository.session,
                profileRepository.profile,
                settingsRepository.settings,
            ) { session, profile, settings -> Triple(session, gateFor(session, profile), settings) }

        val state: StateFlow<MainUiState> =
            combine(base, flags, tripCount, tripCountKnown, waitOver) { (session, gate, settings), f, count, countKnown, over ->
                val resolved = f ?: JourneyFlags.OFF
                val signedIn = session != null
                val hold =
                    gate != AuthGate.LOADING &&
                        StartGate.hold(f != null, resolved, signedIn && gate == AuthGate.READY, countKnown, if (over) StartGate.MAX_WAIT_MS else 0L)
                MainUiState(gate, settings, resolved, resolveStartRoute(resolved, signedIn, count), hold)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

        init {
            viewModelScope.launch {
                flags.value = loadFlags() ?: flags.value ?: JourneyFlags.OFF
            }
            viewModelScope.launch {
                delay(StartGate.MAX_WAIT_MS)
                waitOver.value = true
            }
            viewModelScope.launch {
                // The home rule needs the trip count once: when signed in and the journey UI is on.
                state.first { it.gate == AuthGate.READY && it.flags.journey && it.flags.trips }
                val result = tripsRepository.list(LocalDate.now().toString())
                tripCount.value = (result as? AppResult.Success)?.value?.trips?.size
                tripCountKnown.value = true
            }
        }

        /** `GET /api/config`; null when it could not be read (offline, older backend without it keeps every flag off). */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun loadFlags(): JourneyFlags? =
            try {
                val response = configApi.config()
                if (response.isSuccessful) {
                    JourneyFlags(response.body()).also(flagsCache::write)
                } else if (response.code() == HTTP_NOT_FOUND) {
                    JourneyFlags.OFF.also(flagsCache::write)
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("MainViewModel", "GET /api/config failed; keeping the cached journey flags", e)
                null
            }

        /** What the shell's consent host shows (first-run prompt, reopened choices, or nothing). */
        val consentPrompt: StateFlow<ConsentPromptState> = consentRepository.prompt

        /** Design-system taps and toggles → `ui_tap` / `ui_toggle`. */
        val uiEventSink: UiEventSink = TelemetryUiBridge(telemetry)

        /** Destination changes → `screen_view` by route template. */
        val screenTracker: ScreenTracker = ScreenTracker(telemetry)

        fun chooseConsent(choice: ConsentChoice) {
            viewModelScope.launch { consentRepository.choose(choice) }
        }

        fun closeConsentChoices() = consentRepository.closeChoices()

        /** `perf cold_start` for a launch [io.github.rupakc.travelplanner.telemetry.ColdStartGate] accepted. */
        fun reportColdStart(ms: Long) {
            telemetry.emit(
                TelemetryEvents.PERF,
                mapOf("metric" to "cold_start", "value" to ms.toDouble(), "route" to telemetry.currentRoute()),
            )
        }
    }
