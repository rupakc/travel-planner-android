package io.github.rupakc.travelplanner.feature.auth.login

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthProvidersRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthConfigRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSignupRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry

/** A [LoginViewModel] with fakes for everything the tests do not care about. */
internal fun loginViewModel(
    auth: AuthRepository,
    saved: SavedStateHandle = SavedStateHandle(),
    providers: AuthProvidersRepository = FakeAuthProvidersRepository(),
    consent: ConsentRepository = FakeConsentRepository(),
    signup: SignupRepository = FakeSignupRepository(),
    config: AuthConfigRepository = FakeAuthConfigRepository(),
    telemetry: Telemetry = FakeTelemetry(),
    language: AppLanguage = FakeAppLanguage(),
) = LoginViewModel(auth, saved, providers, consent, signup, config, telemetry, language)
