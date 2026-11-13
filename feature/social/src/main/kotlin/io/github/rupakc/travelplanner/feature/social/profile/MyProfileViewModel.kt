package io.github.rupakc.travelplanner.feature.social.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEnums
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.social.HandleReason
import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileVisibility
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.retryAfterSeconds
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ProfilePhase { Loading, Error, Content }

/** Live answer for the handle field. */
internal sealed interface HandleStatus {
    data object Idle : HandleStatus

    data object Checking : HandleStatus

    data object Available : HandleStatus

    /** Fails the local syntax check (no request is made). */
    data object BadSyntax : HandleStatus

    data class Unavailable(val reason: HandleReason) : HandleStatus

    /** The check itself failed (offline, 429): saving still tries and the server decides. */
    data object Unknown : HandleStatus
}

/** A refusal of the save that belongs to one field. */
internal enum class FieldProblem { HandleTaken, HandleInvalid, HandleReserved, HandleRateLimited, BioRejected, CountryInvalid }

internal enum class ProfileToggle { Avatar, HomeCountry, Countries, Level, Badges, Leaderboard }

@Immutable
internal data class MyProfileUiState(
    val phase: ProfilePhase = ProfilePhase.Loading,
    val error: AppError? = null,
    val original: OwnProfile = OwnProfile(),
    val form: ProfileForm = ProfileForm(),
    val handleStatus: HandleStatus = HandleStatus.Idle,
    val saving: Boolean = false,
    val problem: FieldProblem? = null,
    /** Days until the handle may change again, when the server said so. */
    val retryDays: Int? = null,
    /** A save refusal with no field of its own (backend text is shown as is). */
    val saveError: AppError? = null,
    val savedTick: Int = 0,
    /** `hide_game` is on: the level and badge switches are not offered (the server omits them anyway). */
    val hideGame: Boolean = false,
) {
    val isNew: Boolean get() = !original.hasProfile
    val handleChanged: Boolean get() = form.handle != original.handle.orEmpty()
    val dirty: Boolean get() = !form.diff(original).isEmpty()

    /** New profiles need an available handle; the rest only need a change and a complete country. */
    val canSave: Boolean
        get() =
            !saving && dirty && isCountryComplete(form.homeCountry) &&
                (!handleChanged || (isValidHandle(form.handle) && handleStatus !is HandleStatus.Unavailable && handleStatus != HandleStatus.Checking))
}

internal sealed interface MyProfileEvent {
    data object Retry : MyProfileEvent

    data class HandleChanged(val raw: String) : MyProfileEvent

    data class VisibilityChanged(val value: ProfileVisibility) : MyProfileEvent

    data class BioChanged(val raw: String) : MyProfileEvent

    data class CountryChanged(val raw: String) : MyProfileEvent

    data class Toggled(val toggle: ProfileToggle, val on: Boolean) : MyProfileEvent

    data object Save : MyProfileEvent
}

/**
 * Create or edit the caller's profile (`GET/PUT /social/profile`). A profile starts private with every opt-in off. The handle
 * field checks availability live (debounced, syntax first, only when it differs from the saved handle). Telemetry:
 * `social_profile_save` with the visibility enum.
 */
@HiltViewModel
internal class MyProfileViewModel
    @Inject
    constructor(
        private val social: SocialRepository,
        private val account: AccountRepository,
        private val telemetry: Telemetry,
        private val journeyFlags: JourneyFlagsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MyProfileUiState())
        val state: StateFlow<MyProfileUiState> = _state.asStateFlow()
        private var checkJob: Job? = null

        init {
            load()
        }

        fun onEvent(event: MyProfileEvent) {
            when (event) {
                MyProfileEvent.Retry -> load()
                is MyProfileEvent.HandleChanged -> onHandle(event.raw)
                is MyProfileEvent.VisibilityChanged -> edit { it.copy(visibility = event.value) }
                is MyProfileEvent.BioChanged -> edit { it.copy(bio = event.raw.take(BIO_MAX)) }
                is MyProfileEvent.CountryChanged -> edit { it.copy(homeCountry = normalizeCountry(event.raw)) }
                is MyProfileEvent.Toggled -> edit { it.toggled(event.toggle, event.on) }
                MyProfileEvent.Save -> save()
            }
        }

        private fun load() {
            _state.update { it.copy(phase = ProfilePhase.Loading, error = null) }
            viewModelScope.launch {
                // The user's hide_game setting, or the game is off server-side (`features.game == false`).
                val hideGame = ((account.settings() as? AppResult.Success)?.value?.hideGame ?: false) || !journeyFlags.flags().game
                when (val result = social.ownProfile()) {
                    is AppResult.Success -> {
                        _state.update {
                            it.copy(phase = ProfilePhase.Content, original = result.value, form = ProfileForm.from(result.value), hideGame = hideGame)
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(phase = ProfilePhase.Error, error = result.error) }
                    }
                }
            }
        }

        private fun edit(change: (ProfileForm) -> ProfileForm) {
            _state.update { it.copy(form = change(it.form), problem = null, saveError = null) }
        }

        private fun ProfileForm.toggled(toggle: ProfileToggle, on: Boolean): ProfileForm =
            when (toggle) {
                ProfileToggle.Avatar -> copy(showAvatar = on)
                ProfileToggle.HomeCountry -> copy(showHomeCountry = on)
                ProfileToggle.Countries -> copy(showCountries = on)
                ProfileToggle.Level -> copy(showLevel = on)
                ProfileToggle.Badges -> copy(showBadges = on)
                ProfileToggle.Leaderboard -> copy(leaderboardOptIn = on)
            }

        private fun onHandle(raw: String) {
            val handle = normalizeHandle(raw)
            checkJob?.cancel()
            val saved = _state.value.original.handle.orEmpty()
            val status =
                when {
                    handle.isEmpty() || handle == saved -> HandleStatus.Idle
                    !isValidHandle(handle) -> HandleStatus.BadSyntax
                    else -> HandleStatus.Checking
                }
            _state.update { it.copy(form = it.form.copy(handle = handle), handleStatus = status, problem = null, saveError = null) }
            if (status == HandleStatus.Checking) {
                checkJob =
                    viewModelScope.launch {
                        delay(HANDLE_CHECK_DEBOUNCE_MS)
                        val answer = social.checkHandle(handle)
                        _state.update { s ->
                            if (s.form.handle != handle) s else s.copy(handleStatus = answer.toStatus())
                        }
                    }
            }
        }

        private fun AppResult<io.github.rupakc.travelplanner.core.model.social.HandleCheck>.toStatus(): HandleStatus =
            when (this) {
                is AppResult.Success -> {
                    if (value.available) HandleStatus.Available else HandleStatus.Unavailable(value.reasonCode ?: HandleReason.TAKEN)
                }

                is AppResult.Failure -> {
                    HandleStatus.Unknown
                }
            }

        private fun save() {
            val current = _state.value
            if (!current.canSave) return
            checkJob?.cancel()
            _state.update { it.copy(saving = true, problem = null, saveError = null, retryDays = null) }
            viewModelScope.launch {
                when (val result = social.saveProfile(current.form.diff(current.original))) {
                    is AppResult.Success -> {
                        val saved = result.value
                        telemetry.emit(TelemetryEvents.SOCIAL_PROFILE_SAVE, mapOf("visibility" to visibilityToken(saved.visibilityMode)))
                        _state.update {
                            it.copy(
                                saving = false,
                                original = saved,
                                form = ProfileForm.from(saved),
                                handleStatus = HandleStatus.Idle,
                                savedTick = it.savedTick + 1,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        val problem = result.error.fieldProblem()
                        _state.update {
                            it.copy(
                                saving = false,
                                problem = problem,
                                retryDays = if (problem == FieldProblem.HandleRateLimited) retryDays(result.error.retryAfterSeconds()) else null,
                                saveError = result.error.takeIf { problem == null },
                                handleStatus =
                                    when (problem) {
                                        FieldProblem.HandleTaken -> HandleStatus.Unavailable(HandleReason.TAKEN)
                                        FieldProblem.HandleReserved -> HandleStatus.Unavailable(HandleReason.RESERVED)
                                        FieldProblem.HandleInvalid -> HandleStatus.BadSyntax
                                        else -> it.handleStatus
                                    },
                            )
                        }
                    }
                }
            }
        }

        private fun AppError.fieldProblem(): FieldProblem? =
            when (socialErrorCode()) {
                SocialErrorCode.HANDLE_TAKEN -> FieldProblem.HandleTaken
                SocialErrorCode.HANDLE_INVALID -> FieldProblem.HandleInvalid
                SocialErrorCode.HANDLE_RESERVED -> FieldProblem.HandleReserved
                SocialErrorCode.HANDLE_RATE_LIMITED -> FieldProblem.HandleRateLimited
                SocialErrorCode.CONTENT_REJECTED -> FieldProblem.BioRejected
                SocialErrorCode.COUNTRY_INVALID -> FieldProblem.CountryInvalid
                else -> null
            }

        private fun visibilityToken(visibility: ProfileVisibility): String =
            visibility.wire?.takeIf { it in TelemetryEnums.SOCIAL_VISIBILITY } ?: ProfileVisibility.PRIVATE.wire.orEmpty()
    }
