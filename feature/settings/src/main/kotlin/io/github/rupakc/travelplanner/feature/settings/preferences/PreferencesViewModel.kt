package io.github.rupakc.travelplanner.feature.settings.preferences

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Outcome line next to "Save Preferences" (web: "Preferences saved!" / "Error: {detail}" / "Save failed"). */
sealed interface SaveStatus {
    data object Saved : SaveStatus

    data class Failed(val error: AppError) : SaveStatus
}

/** Outcome of the separate "Save email" action (web AlertEmailField). */
sealed interface EmailStatus {
    data object Saved : EmailStatus

    data object Invalid : EmailStatus

    data class Failed(val error: AppError) : EmailStatus
}

@Immutable
data class PreferencesUiState(
    val loading: Boolean = true,
    /** GET failed: shown with Retry (we never save defaults over the server's values). */
    val loadError: AppError? = null,
    val prefs: Preferences = Preferences(),
    val saving: Boolean = false,
    val saveStatus: SaveStatus? = null,
    val nationalitySuggestions: List<Nationality> = emptyList(),
    val email: String = "",
    val emailSaving: Boolean = false,
    val emailStatus: EmailStatus? = null,
    /** `/health.email_alerts_available`. */
    val emailAlertsAvailable: Boolean = false,
)

sealed interface PreferencesEvent {
    data object Retry : PreferencesEvent

    data class SetBudget(val category: String) : PreferencesEvent

    data class NationalityChanged(val text: String) : PreferencesEvent

    data class NationalityPicked(val nationality: Nationality) : PreferencesEvent

    data object DismissSuggestions : PreferencesEvent

    data class ResidenceChanged(val text: String) : PreferencesEvent

    data class AddPermits(val raw: String) : PreferencesEvent

    data class RemovePermit(val tag: String) : PreferencesEvent

    data class AddVisas(val raw: String) : PreferencesEvent

    data class RemoveVisa(val tag: String) : PreferencesEvent

    data class ChangeCount(val field: TravellerField, val delta: Int) : PreferencesEvent

    data class ToggleAccessibility(val id: String) : PreferencesEvent

    data class ToggleInterest(val id: String) : PreferencesEvent

    data object Save : PreferencesEvent

    data class EmailChanged(val text: String) : PreferencesEvent

    data object SaveEmail : PreferencesEvent
}

/** Settings → Travel preferences: the web PreferencesPage (`GET/PUT /api/preferences`, `PUT /api/auth/email`). */
@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val preferencesRepository: PreferencesRepository,
        private val searchRepository: SearchRepository,
        private val authRepository: AuthRepository,
        private val healthRepository: HealthRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PreferencesUiState())
        val state: StateFlow<PreferencesUiState> = mutableState.asStateFlow()

        private var nationalityJob: Job? = null
        private var savedMessageJob: Job? = null

        init {
            val draft = savedStateHandle.get<String>(KEY_DRAFT)?.let { runCatching { TravelJson.decodeFromString(Preferences.serializer(), it) }.getOrNull() }
            if (draft != null) mutableState.update { it.copy(loading = false, prefs = draft) } else load()
            viewModelScope.launch {
                val email = authRepository.session.first()?.user?.email.orEmpty()
                mutableState.update { it.copy(email = savedStateHandle.get<String>(KEY_EMAIL) ?: email) }
            }
            viewModelScope.launch {
                val health = healthRepository.capabilities.value ?: (healthRepository.refresh() as? AppResult.Success)?.value
                mutableState.update { it.copy(emailAlertsAvailable = health?.emailAlertsAvailable == true) }
            }
        }

        fun onEvent(event: PreferencesEvent) {
            when (event) {
                PreferencesEvent.Retry -> {
                    load()
                }

                is PreferencesEvent.SetBudget -> {
                    edit { it.copy(budgetCategory = event.category) }
                }

                is PreferencesEvent.NationalityChanged -> {
                    edit { it.copy(nationality = event.text) }
                    searchNationalities(event.text)
                }

                is PreferencesEvent.NationalityPicked -> {
                    nationalityJob?.cancel()
                    edit { it.copy(nationality = event.nationality.nationality.orEmpty()) }
                    mutableState.update { it.copy(nationalitySuggestions = emptyList()) }
                }

                PreferencesEvent.DismissSuggestions -> {
                    mutableState.update { it.copy(nationalitySuggestions = emptyList()) }
                }

                is PreferencesEvent.ResidenceChanged -> {
                    edit { it.copy(currentResidence = event.text) }
                }

                is PreferencesEvent.AddPermits -> {
                    edit { it.copy(residencePermits = PreferencesRules.addTags(it.residencePermits, event.raw)) }
                }

                is PreferencesEvent.RemovePermit -> {
                    edit { it.copy(residencePermits = it.residencePermits - event.tag) }
                }

                is PreferencesEvent.AddVisas -> {
                    edit { it.copy(existingVisas = PreferencesRules.addTags(it.existingVisas, event.raw)) }
                }

                is PreferencesEvent.RemoveVisa -> {
                    edit { it.copy(existingVisas = it.existingVisas - event.tag) }
                }

                is PreferencesEvent.ChangeCount -> {
                    edit { PreferencesRules.change(it, event.field, event.delta) }
                }

                is PreferencesEvent.ToggleAccessibility -> {
                    edit { it.copy(accessibilityNeeds = PreferencesRules.toggle(it.accessibilityNeeds, event.id)) }
                }

                is PreferencesEvent.ToggleInterest -> {
                    edit { it.copy(interests = PreferencesRules.toggle(it.interests, event.id)) }
                }

                PreferencesEvent.Save -> {
                    save()
                }

                is PreferencesEvent.EmailChanged -> {
                    savedStateHandle[KEY_EMAIL] = event.text
                    mutableState.update { it.copy(email = event.text, emailStatus = null) }
                }

                PreferencesEvent.SaveEmail -> {
                    saveEmail()
                }
            }
        }

        private fun edit(transform: (Preferences) -> Preferences) {
            mutableState.update { it.copy(prefs = transform(it.prefs), saveStatus = null) }
            savedStateHandle[KEY_DRAFT] = TravelJson.encodeToString(Preferences.serializer(), mutableState.value.prefs)
        }

        private fun load() {
            mutableState.update { it.copy(loading = true, loadError = null) }
            viewModelScope.launch {
                when (val result = preferencesRepository.get()) {
                    is AppResult.Success -> mutableState.update { it.copy(loading = false, prefs = result.value) }
                    is AppResult.Failure -> mutableState.update { it.copy(loading = false, loadError = result.error) }
                }
            }
        }

        private fun searchNationalities(query: String) {
            nationalityJob?.cancel()
            if (query.isBlank()) {
                mutableState.update { it.copy(nationalitySuggestions = emptyList()) }
                return
            }
            nationalityJob =
                viewModelScope.launch {
                    delay(NATIONALITY_DEBOUNCE_MILLIS)
                    val found = (searchRepository.nationalities(query.trim(), NATIONALITY_LIMIT) as? AppResult.Success)?.value.orEmpty()
                    mutableState.update { it.copy(nationalitySuggestions = found.filter { n -> !n.nationality.isNullOrBlank() }) }
                }
        }

        private fun save() {
            if (mutableState.value.saving) return
            mutableState.update { it.copy(saving = true, saveStatus = null) }
            viewModelScope.launch {
                when (val result = preferencesRepository.save(PreferencesRules.forSave(mutableState.value.prefs))) {
                    is AppResult.Success -> {
                        savedStateHandle.remove<String>(KEY_DRAFT)
                        mutableState.update { it.copy(saving = false, prefs = result.value, saveStatus = SaveStatus.Saved) }
                        savedMessageJob?.cancel()
                        savedMessageJob =
                            launch {
                                delay(SAVED_MESSAGE_MILLIS)
                                mutableState.update { s -> if (s.saveStatus == SaveStatus.Saved) s.copy(saveStatus = null) else s }
                            }
                    }

                    is AppResult.Failure -> {
                        mutableState.update { it.copy(saving = false, saveStatus = SaveStatus.Failed(result.error)) }
                    }
                }
            }
        }

        private fun saveEmail() {
            val email = mutableState.value.email.trim()
            if (email.isNotEmpty() && !PreferencesRules.isEmail(email)) {
                mutableState.update { it.copy(emailStatus = EmailStatus.Invalid) }
                return
            }
            mutableState.update { it.copy(emailSaving = true, emailStatus = null) }
            viewModelScope.launch {
                when (val result = authRepository.updateEmail(email)) {
                    is AppResult.Success -> {
                        savedStateHandle.remove<String>(KEY_EMAIL)
                        mutableState.update { it.copy(emailSaving = false, email = result.value.orEmpty(), emailStatus = EmailStatus.Saved) }
                    }

                    is AppResult.Failure -> {
                        mutableState.update { it.copy(emailSaving = false, emailStatus = EmailStatus.Failed(result.error)) }
                    }
                }
            }
        }

        private companion object {
            const val KEY_DRAFT = "preferences_draft"
            const val KEY_EMAIL = "preferences_email"
            const val NATIONALITY_DEBOUNCE_MILLIS = 200L
            const val NATIONALITY_LIMIT = 10
            const val SAVED_MESSAGE_MILLIS = 3_000L
        }
    }
