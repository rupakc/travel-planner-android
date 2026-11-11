package io.github.rupakc.travelplanner.feature.settings.root

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.types
import io.github.rupakc.travelplanner.core.domain.profile.visibleFeatures
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Languages offered (must match `locales_config.xml`); null tag = system. */
val SupportedLanguageTags: List<String?> = listOf(null, "en", "es")

@Immutable
data class SettingsUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val travellerTypes: List<TravellerType> = emptyList(),
    val featuresOn: Int = 0,
    val featuresTotal: Int = FeatureId.entries.size,
    val settings: AppSettings = AppSettings(),
    val effectiveBaseUrl: String = "",
    val versionName: String = "",
    val perAppLanguage: Boolean = true,
    /** Current app language tag (`null` = system): the system per-app locale on API 33+, else the stored setting. */
    val languageTag: String? = null,
    val choosingLanguage: Boolean = false,
    /** Privacy switch: usage data may be collected (from `ConsentRepository.allowed`). */
    val privacyAllowed: Boolean = true,
    val confirmingSignOut: Boolean = false,
    val signingOut: Boolean = false,
)

sealed interface SettingsEvent {
    data object ChooseLanguage : SettingsEvent

    data class SetLanguage(val tag: String?) : SettingsEvent

    data object DismissDialog : SettingsEvent

    data object AskSignOut : SettingsEvent

    data object ConfirmSignOut : SettingsEvent

    data class SetPrivacy(val allowed: Boolean) : SettingsEvent

    data object OpenPrivacyChoices : SettingsEvent
}

/** Settings root: account, traveller type, feature count, appearance, language, server, sign-out. */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        profileRepository: ProfileRepository,
        private val settingsRepository: SettingsRepository,
        catalogue: FeatureCatalogue,
        private val appLocales: AppLocales,
        buildInfo: AppBuildInfo,
        private val consentRepository: ConsentRepository,
        private val telemetry: Telemetry,
        private val appLanguage: AppLanguage,
    ) : ViewModel() {
        private val dialogs = MutableStateFlow(Dialogs())

        private val signedOutChannel = MutableStateFlow(false)

        /** True once sign-out finished (the route then calls `onSignOut`; the auth gate shows Login). */
        val signedOut: StateFlow<Boolean> = signedOutChannel

        val state: StateFlow<SettingsUiState> =
            combine(
                authRepository.session,
                combine(profileRepository.profile, profileRepository.featureOverrides) { p, o -> p to o },
                settingsRepository.settings,
                settingsRepository.effectiveBaseUrl,
                combine(dialogs, consentRepository.allowed) { d, allowed -> d to allowed },
            ) { session, (profile, overrides), settings, url, (d, allowed) ->
                val types = profile.types()
                val visible = catalogue.visibleFeatures(profile, overrides)
                SettingsUiState(
                    loading = false,
                    user = session?.user,
                    travellerTypes = types,
                    featuresOn = visible.size,
                    settings = settings,
                    effectiveBaseUrl = url,
                    versionName = buildInfo.versionName,
                    perAppLanguage = appLocales.perAppSupported,
                    languageTag = if (appLocales.perAppSupported) appLocales.current() else settings.localeTag,
                    privacyAllowed = allowed,
                    choosingLanguage = d.language,
                    confirmingSignOut = d.signOut,
                    signingOut = d.signingOut,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

        fun onEvent(event: SettingsEvent) {
            when (event) {
                SettingsEvent.ChooseLanguage -> {
                    dialogs.update { it.copy(language = true) }
                }

                is SettingsEvent.SetLanguage -> {
                    dialogs.update { it.copy(language = false) }
                    viewModelScope.launch {
                        settingsRepository.setLocaleTag(event.tag)
                        appLocales.apply(event.tag)
                        // After apply, so "System" reports the language now in effect. The tag is not validated here.
                        telemetry.emit(TelemetryEvents.LANGUAGE_CHANGE, mapOf("lang" to (event.tag ?: appLanguage.current())))
                    }
                }

                SettingsEvent.DismissDialog -> {
                    dialogs.update { it.copy(language = false, signOut = false) }
                }

                SettingsEvent.AskSignOut -> {
                    dialogs.update { it.copy(signOut = true) }
                }

                SettingsEvent.ConfirmSignOut -> {
                    signOut()
                }

                is SettingsEvent.SetPrivacy -> {
                    viewModelScope.launch {
                        consentRepository.choose(if (event.allowed) ConsentChoice.GRANTED else ConsentChoice.DENIED)
                    }
                }

                SettingsEvent.OpenPrivacyChoices -> {
                    consentRepository.openChoices()
                }
            }
        }

        /**
         * `AuthRepository.logout()` unregisters this device's push token with
         * `POST /api/devices/unregister` when one exists, then clears the session and per-user caches.
         */
        private fun signOut() {
            if (dialogs.value.signingOut) return
            dialogs.update { it.copy(signOut = false, signingOut = true) }
            viewModelScope.launch {
                // The auth gate pops this screen (cancelling viewModelScope) as soon as the session clears;
                // logout must still finish clearing the previous user's caches.
                withContext(NonCancellable) { authRepository.logout() }
                dialogs.update { it.copy(signingOut = false) }
                signedOutChannel.value = true
            }
        }

        private data class Dialogs(val language: Boolean = false, val signOut: Boolean = false, val signingOut: Boolean = false)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
