package io.github.rupakc.travelplanner.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.profile.DefaultTravellerClassifier
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.flow.emitAll

/** Preferences fake that can fail the PUT ([saveFailure]) and records what was sent. */
class ScriptedPreferencesRepository(var current: Preferences = Preferences(username = "alice")) : PreferencesRepository {
    var saveFailure: AppError? = null
    val saved = mutableListOf<Preferences>()

    override suspend fun get(): AppResult<Preferences> = AppResult.Success(current)

    override suspend fun save(preferences: Preferences): AppResult<Preferences> {
        saveFailure?.let { return AppResult.Failure(it) }
        saved += preferences
        current = preferences
        return AppResult.Success(preferences)
    }

    override suspend fun update(transform: (Preferences) -> Preferences): AppResult<Preferences> = save(transform(current))

    override suspend fun tasteProfile(): AppResult<TasteProfile> = AppResult.Success(TasteProfile())

    override suspend fun resetTasteProfile(): AppResult<Int> = AppResult.Success(0)
}

val FIXED_NOW: Instant = Instant.parse("2026-11-05T10:00:00Z")

fun onboardingViewModel(
    profile: io.github.rupakc.travelplanner.core.data.repository.ProfileRepository = FakeProfileRepository(),
    preferences: ScriptedPreferencesRepository = ScriptedPreferencesRepository(),
    saved: SavedStateHandle = SavedStateHandle(),
    account: FakeAccountRepository? = FakeAccountRepository(),
    telemetry: FakeTelemetry = FakeTelemetry(),
    search: FakeSearchRepository = FakeSearchRepository(),
) = OnboardingViewModel(
    savedState = saved,
    profileRepository = profile,
    preferencesRepository = preferences,
    catalogue = DefaultFeatureCatalogue,
    classifier = DefaultTravellerClassifier,
    clock = SystemAppClock(Clock.fixed(FIXED_NOW, ZoneOffset.UTC)),
    telemetry = telemetry,
    searchRepository = search,
    accountRepository = Optional.ofNullable(account),
)

/** Profile repository whose reads wait for [gate] (keeps the ViewModel in LOADING). */
class GatedProfileRepository(
    private val delegate: FakeProfileRepository,
    private val gate: kotlinx.coroutines.CompletableDeferred<Unit>,
) : io.github.rupakc.travelplanner.core.data.repository.ProfileRepository by delegate {
    override val profile =
        kotlinx.coroutines.flow.flow {
            gate.await()
            emitAll(delegate.profile)
        }
}

/** Simulates process death: a fresh handle holding only what was saved (as Android restores it). */
fun SavedStateHandle.afterProcessDeath(): SavedStateHandle = SavedStateHandle(keys().associateWith { get<Any?>(it) })
