package io.github.rupakc.travelplanner.feature.settings.server

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.server.ServerUrl
import io.github.rupakc.travelplanner.core.domain.server.ServerUrlResult
import io.github.rupakc.travelplanner.core.model.health.Health
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why a URL was rejected (`ServerUrl.validate` results, or the repository's codes on save). */
enum class UrlProblem { INVALID, UNSUPPORTED_SCHEME, MISSING_HOST, HTTPS_REQUIRED }

/** Result of "Test connection" (`GET <candidate>/health`, no token, settings unchanged). */
sealed interface ConnectionTest {
    data object Running : ConnectionTest

    data class Ok(val health: Health) : ConnectionTest

    data class Failed(val error: AppError) : ConnectionTest
}

@Immutable
data class ServerUiState(
    val input: String = "",
    /** The URL requests go to now (override or flavour default; "" when none). */
    val effectiveUrl: String = "",
    val defaultUrl: String = "",
    val usingDefault: Boolean = true,
    val problem: UrlProblem? = null,
    val saved: Boolean = false,
    val test: ConnectionTest? = null,
    /** Switching servers signs you out: asked before saving while signed in. [pendingUrl] null = default. */
    val confirmingSwitch: Boolean = false,
    val pendingUrl: String? = null,
)

sealed interface ServerEvent {
    data class InputChanged(val text: String) : ServerEvent

    data object Save : ServerEvent

    data object UseDefault : ServerEvent

    data object ConfirmSwitch : ServerEvent

    data object CancelSwitch : ServerEvent

    data object Test : ServerEvent
}

/**
 * Settings → Server. Input is checked with `ServerUrl.validate`; "Test connection" probes the
 * candidate with a one-off unauthenticated request (the session token never goes to an unsaved
 * server, and settings don't change). Only Save (after a sign-out warning when signed in)
 * calls `setBaseUrl`, which owns the stored override key.
 */
@HiltViewModel
class ServerViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val settingsRepository: SettingsRepository,
        private val healthRepository: HealthRepository,
        private val authRepository: AuthRepository,
        private val buildInfo: AppBuildInfo,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ServerUiState(defaultUrl = buildInfo.defaultBaseUrl))
        val state: StateFlow<ServerUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository.effectiveBaseUrl.collect { url -> mutableState.update { it.copy(effectiveUrl = url) } }
            }
            viewModelScope.launch {
                val override = settingsRepository.settings.first().baseUrlOverride
                val restored = savedStateHandle.get<String>(KEY_INPUT)
                mutableState.update { it.copy(input = restored ?: override.orEmpty(), usingDefault = override == null) }
            }
        }

        fun onEvent(event: ServerEvent) {
            when (event) {
                is ServerEvent.InputChanged -> {
                    savedStateHandle[KEY_INPUT] = event.text
                    mutableState.update { it.copy(input = event.text, problem = null, saved = false, test = null) }
                }

                ServerEvent.Save -> {
                    val input = mutableState.value.input
                    if (input.isBlank()) requestSwitch(null) else validated(input)?.let(::requestSwitch)
                }

                ServerEvent.UseDefault -> {
                    requestSwitch(null)
                }

                ServerEvent.ConfirmSwitch -> {
                    val url = mutableState.value.pendingUrl
                    mutableState.update { it.copy(confirmingSwitch = false, pendingUrl = null) }
                    viewModelScope.launch { switchServer(url) }
                }

                ServerEvent.CancelSwitch -> {
                    mutableState.update { it.copy(confirmingSwitch = false, pendingUrl = null) }
                }

                ServerEvent.Test -> {
                    test()
                }
            }
        }

        /** The normalised URL, or null after recording the problem. */
        private fun validated(input: String): String? =
            when (val result = ServerUrl.validate(input, allowHttp = buildInfo.cleartextAllowed)) {
                is ServerUrlResult.Valid -> {
                    result.url
                }

                else -> {
                    mutableState.update { it.copy(problem = result.toProblem(), saved = false) }
                    null
                }
            }

        private fun requestSwitch(url: String?) {
            viewModelScope.launch {
                val current = settingsRepository.settings.first().baseUrlOverride
                when {
                    url == current -> mutableState.update { it.copy(problem = null, saved = true) }
                    authRepository.session.first() != null -> mutableState.update { it.copy(confirmingSwitch = true, pendingUrl = url) }
                    else -> store(url)
                }
            }
        }

        /**
         * Signs out on the current server first (unregisters this device's push token there and wipes the
         * session and per-user caches), then stores the new URL. Both survive the auth gate popping this
         * screen when the session clears. Logout is idempotent, so a later sign-out triggered by the URL
         * change itself is harmless.
         */
        private suspend fun switchServer(url: String?) {
            // Check with the store's own rules first (review M12): a URL setBaseUrl would reject must not sign the user out.
            if (url != null) {
                val checked = settingsRepository.checkBaseUrl(url)
                if (checked is AppResult.Failure) {
                    showStoreFailure(checked.error)
                    return
                }
            }
            withContext(NonCancellable) {
                if (authRepository.session.first() != null) authRepository.logout()
                store(url)
            }
        }

        private fun showStoreFailure(error: AppError) {
            val problem = if ((error as? AppError.Client)?.code == CODE_HTTPS_REQUIRED) UrlProblem.HTTPS_REQUIRED else UrlProblem.INVALID
            mutableState.update { it.copy(problem = problem, saved = false) }
        }

        private suspend fun store(url: String?) {
            when (val result = settingsRepository.setBaseUrl(url)) {
                is AppResult.Success -> {
                    savedStateHandle[KEY_INPUT] = url.orEmpty()
                    mutableState.update { it.copy(input = url.orEmpty(), problem = null, saved = true, usingDefault = url == null, test = null) }
                }

                is AppResult.Failure -> {
                    showStoreFailure(result.error)
                }
            }
        }

        private fun test() {
            val input = mutableState.value.input
            // Blank field = test the server in use now (override or flavour default).
            val candidate = if (input.isBlank()) mutableState.value.effectiveUrl.ifEmpty { buildInfo.defaultBaseUrl } else validated(input) ?: return
            mutableState.update { it.copy(test = ConnectionTest.Running) }
            viewModelScope.launch {
                val outcome =
                    when (val result = healthRepository.probe(candidate)) {
                        is AppResult.Success -> ConnectionTest.Ok(result.value)
                        is AppResult.Failure -> ConnectionTest.Failed(result.error)
                    }
                mutableState.update { it.copy(test = outcome) }
            }
        }

        private fun ServerUrlResult.toProblem(): UrlProblem =
            when (this) {
                ServerUrlResult.UnsupportedScheme -> UrlProblem.UNSUPPORTED_SCHEME
                ServerUrlResult.MissingHost -> UrlProblem.MISSING_HOST
                ServerUrlResult.HttpsRequired -> UrlProblem.HTTPS_REQUIRED
                else -> UrlProblem.INVALID
            }

        private companion object {
            const val KEY_INPUT = "server_input"
            const val CODE_HTTPS_REQUIRED = "https_required"
        }
    }
