package io.github.rupakc.travelplanner.feature.auth.server

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.server.ServerUrl
import io.github.rupakc.travelplanner.core.domain.server.ServerUrlResult
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Result of "Test connection". */
sealed interface ServerTest {
    /** `GET /health` answered; flags come straight from it. */
    data class Reachable(val serpapiConfigured: Boolean, val pushConfigured: Boolean) : ServerTest

    data class Unreachable(val message: AuthMessage) : ServerTest
}

@Immutable
data class ServerUiState(
    /** Effective base URL in use now ("" when none). */
    val current: String = "",
    /** Flavour default (`AppBuildInfo.defaultBaseUrl`, may be ""). */
    val defaultUrl: String = "",
    val isOverride: Boolean = false,
    val input: String = "",
    /** Non-[ServerUrlResult.Valid] validation result for [input], shown under the field. */
    val inputError: ServerUrlResult? = null,
    val testing: Boolean = false,
    val test: ServerTest? = null,
    /** Saved or reset: the sheet closes. */
    val done: Boolean = false,
)

sealed interface ServerEvent {
    data object Open : ServerEvent

    data class InputChanged(val value: String) : ServerEvent

    data object Test : ServerEvent

    data object Save : ServerEvent

    data object Reset : ServerEvent

    /** Sheet closed without saving: undo a tested-but-unsaved address. */
    data object Dismiss : ServerEvent
}

/**
 * Server address from Login (signed out), spec §4: validate with [ServerUrl.validate]
 * (https only when the flavour forbids cleartext), "Test connection" via `GET /health`,
 * Save to the settings datastore (`baseUrlOverride`), Reset to the flavour default.
 * Testing needs the address in effect, so it is applied temporarily and restored on Dismiss.
 */
@HiltViewModel
class ServerViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val healthRepository: HealthRepository,
        private val buildInfo: AppBuildInfo,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ServerUiState(defaultUrl = buildInfo.defaultBaseUrl))
        val state: StateFlow<ServerUiState> = mutableState.asStateFlow()

        /** Override to restore on Dismiss after a test (null inside = the default); null = nothing to undo. */
        private var restoreTo: Restore? = null

        private data class Restore(val override: String?)

        init {
            viewModelScope.launch {
                settingsRepository.effectiveBaseUrl.collect { url -> mutableState.update { it.copy(current = url) } }
            }
            viewModelScope.launch {
                settingsRepository.settings.collect { s -> mutableState.update { it.copy(isOverride = s.baseUrlOverride != null) } }
            }
        }

        fun onEvent(event: ServerEvent) {
            when (event) {
                ServerEvent.Open -> {
                    mutableState.update { it.copy(input = it.current, inputError = null, test = null, done = false) }
                }

                is ServerEvent.InputChanged -> {
                    mutableState.update { it.copy(input = event.value, inputError = null, test = null) }
                }

                ServerEvent.Test -> {
                    test()
                }

                ServerEvent.Save -> {
                    save()
                }

                ServerEvent.Reset -> {
                    viewModelScope.launch {
                        restoreTo = null
                        settingsRepository.setBaseUrl(null)
                        mutableState.update { it.copy(input = buildInfo.defaultBaseUrl, test = null, done = true) }
                    }
                }

                ServerEvent.Dismiss -> {
                    dismiss()
                }
            }
        }

        private fun validated(): String? =
            when (val result = ServerUrl.validate(mutableState.value.input, allowHttp = buildInfo.cleartextAllowed)) {
                is ServerUrlResult.Valid -> {
                    result.url
                }

                else -> {
                    mutableState.update { it.copy(inputError = result) }
                    null
                }
            }

        private fun test() {
            if (mutableState.value.testing) return
            val url = validated() ?: return
            mutableState.update { it.copy(testing = true, test = null) }
            viewModelScope.launch {
                if (restoreTo == null) restoreTo = Restore(settingsRepository.settings.first().baseUrlOverride)
                val outcome =
                    when (settingsRepository.setBaseUrl(url)) {
                        is AppResult.Failure -> {
                            ServerTest.Unreachable(AuthMessage.Local(R.string.auth_server_error_malformed))
                        }

                        is AppResult.Success -> {
                            when (val health = healthRepository.refresh()) {
                                is AppResult.Success -> ServerTest.Reachable(health.value.serpapiConfigured, health.value.pushConfigured)
                                is AppResult.Failure -> ServerTest.Unreachable(health.error.unreachableMessage())
                            }
                        }
                    }
                mutableState.update { it.copy(testing = false, test = outcome) }
            }
        }

        private fun save() {
            val url = validated() ?: return
            viewModelScope.launch {
                when (settingsRepository.setBaseUrl(url)) {
                    is AppResult.Success -> {
                        restoreTo = null
                        mutableState.update { it.copy(done = true) }
                    }

                    is AppResult.Failure -> {
                        mutableState.update { it.copy(inputError = ServerUrlResult.Malformed) }
                    }
                }
            }
        }

        private fun dismiss() {
            val undo = restoreTo ?: return
            restoreTo = null
            viewModelScope.launch {
                settingsRepository.setBaseUrl(undo.override)
                healthRepository.refresh()
            }
        }

        private fun AppError.unreachableMessage(): AuthMessage =
            userFacingDetail()?.let(AuthMessage::Server) ?: AuthMessage.Local(R.string.auth_server_unreachable)
    }
