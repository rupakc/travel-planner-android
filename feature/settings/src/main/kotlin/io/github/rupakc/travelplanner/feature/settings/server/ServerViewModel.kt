package io.github.rupakc.travelplanner.feature.settings.server

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.model.health.Health
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a URL was rejected (codes from `SettingsRepository.setBaseUrl`). */
enum class UrlProblem { INVALID, HTTPS_REQUIRED }

/** Result of "Test connection" (`GET /health`). */
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
)

sealed interface ServerEvent {
    data class InputChanged(val text: String) : ServerEvent

    data object Save : ServerEvent

    data object UseDefault : ServerEvent

    data object Test : ServerEvent
}

/** Settings → Server: backend URL with validation and "Test connection" (spec §4 Networking). */
@HiltViewModel
class ServerViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val settingsRepository: SettingsRepository,
        private val healthRepository: HealthRepository,
        buildInfo: AppBuildInfo,
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
                    mutableState.update { it.copy(input = event.text, problem = null, saved = false) }
                }

                ServerEvent.Save -> {
                    viewModelScope.launch { save(mutableState.value.input) }
                }

                ServerEvent.UseDefault -> {
                    viewModelScope.launch {
                        savedStateHandle[KEY_INPUT] = ""
                        mutableState.update { it.copy(input = "") }
                        save("")
                    }
                }

                ServerEvent.Test -> {
                    viewModelScope.launch { test() }
                }
            }
        }

        /** Validates and stores [url]; blank = flavour default. Returns false when rejected. */
        private suspend fun save(url: String): Boolean =
            when (val result = settingsRepository.setBaseUrl(url.trim().ifEmpty { null })) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(problem = null, saved = true, usingDefault = url.isBlank(), test = null) }
                    true
                }

                is AppResult.Failure -> {
                    val problem = if ((result.error as? AppError.Client)?.code == CODE_HTTPS_REQUIRED) UrlProblem.HTTPS_REQUIRED else UrlProblem.INVALID
                    mutableState.update { it.copy(problem = problem, saved = false) }
                    false
                }
            }

        private suspend fun test() {
            val current = settingsRepository.settings.first().baseUrlOverride.orEmpty()
            // Test what is in the field: save it first when it changed.
            if (mutableState.value.input.trim() != current && !save(mutableState.value.input)) return
            mutableState.update { it.copy(test = ConnectionTest.Running) }
            val outcome =
                when (val result = healthRepository.refresh()) {
                    is AppResult.Success -> ConnectionTest.Ok(result.value)
                    is AppResult.Failure -> ConnectionTest.Failed(result.error)
                }
            mutableState.update { it.copy(test = outcome) }
        }

        private companion object {
            const val KEY_INPUT = "server_input"
            const val CODE_HTTPS_REQUIRED = "https_required"
        }
    }
