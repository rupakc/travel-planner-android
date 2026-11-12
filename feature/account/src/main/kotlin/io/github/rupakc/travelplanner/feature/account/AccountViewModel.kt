package io.github.rupakc.travelplanner.feature.account

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.auth.IdentityDto
import io.github.rupakc.travelplanner.core.model.auth.QuotaDto
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the export is written: a private cache file. Replaceable in tests. */
internal fun interface ExportTarget {
    fun file(): File
}

internal class CacheExportTarget
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ExportTarget {
        override fun file(): File = AccountRules.exportTarget(context.cacheDir)
    }

@Immutable
internal data class DeleteDialog(val input: String = "", val busy: Boolean = false, val error: AccountMessage? = null)

internal enum class AccountPhase { Loading, Error, Loaded }

@Immutable
internal data class AccountUiState(
    val phase: AccountPhase = AccountPhase.Loading,
    val error: AccountMessage? = null,
    val user: User? = null,
    val identities: List<IdentityDto> = emptyList(),
    val settings: AccountSettings = AccountSettings(),
    val quota: QuotaDto? = null,
    val avatarBusy: Boolean = false,
    val verifyBusy: Boolean = false,
    val exporting: Boolean = false,
    /** Non-null while the name dialog is open (its text). */
    val nameDraft: String? = null,
    val nameError: AccountMessage? = null,
    val unlinking: String? = null,
    val delete: DeleteDialog? = null,
    val message: AccountMessage? = null,
)

internal sealed interface AccountEvent {
    data object Refresh : AccountEvent

    data object EditName : AccountEvent

    data class NameChanged(val text: String) : AccountEvent

    data object SaveName : AccountEvent

    data object DismissName : AccountEvent

    /** [bytes] null = the picked image could not be read. */
    data class AvatarPicked(val bytes: ByteArray?) : AccountEvent

    data object RemoveAvatar : AccountEvent

    data object VerifyEmail : AccountEvent

    /** The user tapped "Link" for [provider]; the browser flow is started by the shell (hook). */
    data class LinkRequested(val provider: String) : AccountEvent

    data class AskUnlink(val provider: String) : AccountEvent

    data object ConfirmUnlink : AccountEvent

    data object DismissUnlink : AccountEvent

    data class Change(val transform: (AccountSettings) -> AccountSettings) : AccountEvent

    data object Export : AccountEvent

    data object OpenDelete : AccountEvent

    data class DeleteInput(val text: String) : AccountEvent

    data object ConfirmDelete : AccountEvent

    data object DismissDelete : AccountEvent

    data object MessageShown : AccountEvent
}

internal sealed interface AccountEffect {
    data class ShareExport(val file: File) : AccountEffect

    data object SignedOut : AccountEffect
}

/** The account area: profile, linked sign-in methods, allowance, settings, export and deletion. */
@HiltViewModel
internal class AccountViewModel
    @Inject
    constructor(
        private val repo: AccountRepository,
        private val consent: ConsentRepository,
        private val appSettings: SettingsRepository,
        private val telemetry: Telemetry,
        private val exportTarget: ExportTarget,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(AccountUiState())
        val state: StateFlow<AccountUiState> = mutable.asStateFlow()
        private val effectChannel = Channel<AccountEffect>(Channel.BUFFERED)
        val effects = effectChannel.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: AccountEvent) {
            when (event) {
                AccountEvent.Refresh -> load()
                AccountEvent.EditName -> mutable.update { it.copy(nameDraft = it.user?.displayName ?: it.user?.username.orEmpty(), nameError = null) }
                is AccountEvent.NameChanged -> mutable.update { it.copy(nameDraft = event.text.take(AccountRules.MAX_NAME), nameError = null) }
                AccountEvent.DismissName -> mutable.update { it.copy(nameDraft = null, nameError = null) }
                AccountEvent.SaveName -> saveName()
                is AccountEvent.AvatarPicked -> uploadAvatar(event.bytes)
                AccountEvent.RemoveAvatar -> removeAvatar()
                AccountEvent.VerifyEmail -> verifyEmail()
                is AccountEvent.LinkRequested -> track("link")
                is AccountEvent.AskUnlink -> mutable.update { it.copy(unlinking = event.provider) }
                AccountEvent.DismissUnlink -> mutable.update { it.copy(unlinking = null) }
                AccountEvent.ConfirmUnlink -> unlink()
                is AccountEvent.Change -> change(event.transform)
                AccountEvent.Export -> export()
                AccountEvent.OpenDelete -> mutable.update { it.copy(delete = DeleteDialog()) }
                is AccountEvent.DeleteInput -> mutable.update { s -> s.copy(delete = s.delete?.copy(input = event.text, error = null)) }
                AccountEvent.DismissDelete -> mutable.update { it.copy(delete = null) }
                AccountEvent.ConfirmDelete -> delete()
                AccountEvent.MessageShown -> mutable.update { it.copy(message = null) }
            }
        }

        private fun track(action: String) {
            check(action in io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEnums.ACCOUNT_ACTION)
            telemetry.emit(TelemetryEvents.ACCOUNT_ACTION, mapOf("action" to action))
        }

        private fun load() {
            mutable.update { if (it.phase == AccountPhase.Loaded) it else it.copy(phase = AccountPhase.Loading) }
            viewModelScope.launch {
                when (val result = repo.account()) {
                    is AppResult.Failure -> {
                        mutable.update {
                            if (it.phase ==
                                AccountPhase.Loaded
                            ) {
                                it.copy(message = result.error.toMessage(R.string.account_err_load))
                            } else {
                                it.copy(phase = AccountPhase.Error, error = result.error.toMessage(R.string.account_err_load))
                            }
                        }
                    }

                    is AppResult.Success -> {
                        val account = result.value
                        mutable.update {
                            it.copy(
                                phase = AccountPhase.Loaded,
                                error = null,
                                user = account.user,
                                identities = account.identities,
                                settings = account.settings,
                            )
                        }
                        syncTelemetryOptOut(account.settings)
                        (repo.quota() as? AppResult.Success)?.let { q -> mutable.update { it.copy(quota = q.value) } }
                    }
                }
            }
        }

        /** The opt-out wins in both directions: a denial on either side is applied to the other. */
        private suspend fun syncTelemetryOptOut(server: AccountSettings) {
            val localAllowed = consent.allowedNow()
            if (server.telemetryOptOut && localAllowed) {
                consent.choose(ConsentChoice.DENIED)
            } else if (!server.telemetryOptOut && !localAllowed) {
                val patch = AccountRules.diff(server, server.copy(telemetryOptOut = true))
                (repo.patchSettings(patch) as? AppResult.Success)?.let { r -> mutable.update { it.copy(settings = r.value) } }
            }
        }

        private fun saveName() {
            val draft = mutable.value.nameDraft?.trim() ?: return
            if (draft.isEmpty() || draft.length > AccountRules.MAX_NAME) {
                mutable.update { it.copy(nameError = AccountMessage(R.string.account_err_name_invalid)) }
                return
            }
            viewModelScope.launch {
                when (val r = repo.updateDisplayName(draft)) {
                    is AppResult.Success -> {
                        track("name_change")
                        mutable.update { it.copy(user = r.value, nameDraft = null, nameError = null) }
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(nameError = r.error.toMessage(R.string.account_err_name_invalid)) }
                    }
                }
            }
        }

        private fun uploadAvatar(bytes: ByteArray?) {
            if (bytes == null) {
                mutable.update { it.copy(message = AccountMessage(R.string.account_err_avatar_type)) }
                return
            }
            mutable.update { it.copy(avatarBusy = true) }
            viewModelScope.launch {
                when (val r = repo.uploadAvatar(bytes, AvatarEncoder.MIME)) {
                    is AppResult.Success -> {
                        track("avatar_upload")
                        mutable.update { s -> s.copy(avatarBusy = false, user = s.user?.copy(avatarUrl = r.value)) }
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(avatarBusy = false, message = r.error.toMessage(R.string.account_err_avatar)) }
                    }
                }
            }
        }

        private fun removeAvatar() {
            mutable.update { it.copy(avatarBusy = true) }
            viewModelScope.launch {
                when (val r = repo.removeAvatar()) {
                    is AppResult.Success -> {
                        track("avatar_remove")
                        mutable.update { s -> s.copy(avatarBusy = false, user = s.user?.copy(avatarUrl = null)) }
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(avatarBusy = false, message = r.error.toMessage(R.string.account_err_avatar)) }
                    }
                }
            }
        }

        private fun verifyEmail() {
            mutable.update { it.copy(verifyBusy = true) }
            viewModelScope.launch {
                track("email_verify_request")
                when (val r = repo.requestEmailVerification(supportedLocale())) {
                    is AppResult.Success -> {
                        mutable.update { it.copy(verifyBusy = false, message = AccountMessage(R.string.account_verify_sent)) }
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(verifyBusy = false, message = r.error.toMessage(R.string.account_err_verify)) }
                        // 409 already_verified: the account moved on elsewhere, so reload it.
                        if (AccountRules.codeOf(r.error) == "already_verified") load()
                    }
                }
            }
        }

        private fun unlink() {
            val provider = mutable.value.unlinking ?: return
            mutable.update { it.copy(unlinking = null) }
            viewModelScope.launch {
                when (val r = repo.unlinkIdentity(provider)) {
                    is AppResult.Success -> {
                        track("unlink")
                        mutable.update { s -> s.copy(identities = s.identities.filterNot { it.provider == provider }) }
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(message = r.error.toMessage(R.string.account_err_unlink)) }
                    }
                }
            }
        }

        private fun change(transform: (AccountSettings) -> AccountSettings) {
            val old = mutable.value.settings
            val new = transform(old)
            val patch = AccountRules.diff(old, new)
            if (patch.isEmpty()) return
            mutable.update { it.copy(settings = new) }
            viewModelScope.launch {
                when (val r = repo.patchSettings(patch)) {
                    is AppResult.Success -> {
                        track("settings_change")
                        mutable.update { it.copy(settings = r.value) }
                        if ("telemetry_opt_out" in patch) consent.choose(if (new.telemetryOptOut) ConsentChoice.DENIED else ConsentChoice.GRANTED)
                        if ("theme" in patch) appSettings.setThemeMode(r.value.theme.toThemeMode())
                    }

                    is AppResult.Failure -> {
                        mutable.update { s ->
                            s.copy(
                                settings =
                                    if (s.settings ==
                                        new
                                    ) {
                                        old
                                    } else {
                                        s.settings
                                    },
                                message = r.error.toMessage(R.string.account_err_settings),
                            )
                        }
                    }
                }
            }
        }

        private fun export() {
            if (mutable.value.exporting) return
            mutable.update { it.copy(exporting = true) }
            viewModelScope.launch {
                when (val r = repo.exportTo(exportTarget.file())) {
                    is AppResult.Success -> {
                        track("export")
                        mutable.update { it.copy(exporting = false) }
                        effectChannel.send(AccountEffect.ShareExport(r.value))
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(exporting = false, message = r.error.toMessage(R.string.account_err_export)) }
                    }
                }
            }
        }

        private fun delete() {
            val s = mutable.value
            val dialog = s.delete ?: return
            val user = s.user ?: return
            val request = AccountRules.deleteRequest(user.hasPassword, dialog.input, user.username)
            if (request == null) {
                val res = if (user.hasPassword) R.string.account_err_password_incorrect else R.string.account_err_confirmation
                mutable.update { it.copy(delete = dialog.copy(error = AccountMessage(res))) }
                return
            }
            mutable.update { it.copy(delete = dialog.copy(busy = true, error = null)) }
            viewModelScope.launch {
                when (val r = repo.deleteAccount(request)) {
                    is AppResult.Success -> {
                        track("delete")
                        mutable.update { it.copy(delete = null) }
                        effectChannel.send(AccountEffect.SignedOut)
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(delete = dialog.copy(busy = false, error = r.error.toMessage(R.string.account_err_delete))) }
                    }
                }
            }
        }

        private fun supportedLocale(): String? = Locale.getDefault().language.takeIf { it in AccountRules.Languages }

        private fun String.toThemeMode(): ThemeMode =
            when (this) {
                "dark" -> ThemeMode.DARK
                "light" -> ThemeMode.LIGHT
                else -> ThemeMode.SYSTEM
            }
    }
