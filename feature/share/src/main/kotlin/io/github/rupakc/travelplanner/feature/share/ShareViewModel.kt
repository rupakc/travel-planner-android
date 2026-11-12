package io.github.rupakc.travelplanner.feature.share

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.plan.SharedSearchData
import io.github.rupakc.travelplanner.core.model.share.ShareCardSnapshot
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

internal sealed interface ShareMessage {
    data class Saved(val planId: Long) : ShareMessage

    data class SaveFailed(val error: AppError) : ShareMessage
}

@Immutable
internal data class ShareUiState(
    val isLoading: Boolean = true,
    val trip: SharedTrip? = null,
    /** Load failure: [AppError.Network] = offline, anything else = "isn't available". */
    val error: AppError? = null,
    /** "Save a copy" is offered only to signed-in users. */
    val canSaveCopy: Boolean = false,
    val isSaving: Boolean = false,
    /** Set after "Save a copy" succeeded; the button then opens the copy (no duplicates). */
    val savedPlanId: Long? = null,
    val message: ShareMessage? = null,
    /** Which Phase 4 kind the link is; a legacy plan token is [ShareKind.PLAN]. */
    val kind: ShareKind = ShareKind.PLAN,
    /** Recap and savings shares: the server's card snapshot (headline, count chips); never holds exact dates. */
    val card: ShareCardSnapshot? = null,
    /** Only when the sharer chose to show their name. */
    val ownerName: String? = null,
    /** A kind this app does not know: the plain https link (opened in a Custom Tab), or null when no web host is configured. */
    val unknownKind: Boolean = false,
    val plainLink: String? = null,
)

internal sealed interface ShareEvent {
    data object Retry : ShareEvent

    data object SaveCopy : ShareEvent

    data object MessageShown : ShareEvent

    /** The footer "Privacy choices" button (web AttributionFooter on the share page). */
    data object OpenPrivacyChoices : ShareEvent
}

/**
 * Public share viewer: `GET /api/share/{token}` without auth (works signed out). Signed-in users
 * can "Save a copy" — `POST /api/plans` with the shared name, search data and selections (the
 * API accepts any objects; the web has no such button). Personal fields were stripped by the server.
 */
@HiltViewModel
internal class ShareViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val plans: PlansRepository,
        auth: AuthRepository,
        private val dispatchers: AppDispatchers,
        private val placeNames: PlaceNameLookup,
        private val consent: ConsentRepository,
        private val telemetry: Telemetry,
        private val build: AppBuildInfo,
    ) : ViewModel() {
        private val token: String? = savedStateHandle.get<String>(TOKEN_ARG)
        private val status = MutableStateFlow(ShareUiState())
        private var shared: SharedPlan? = null

        val state: StateFlow<ShareUiState> =
            combine(status, auth.session.map { it != null }) { s, signedIn -> s.copy(canSaveCopy = signedIn && s.trip != null) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShareUiState())

        init {
            load()
        }

        fun onEvent(event: ShareEvent) {
            when (event) {
                ShareEvent.Retry -> load()
                ShareEvent.SaveCopy -> saveCopy()
                ShareEvent.MessageShown -> status.update { it.copy(message = null) }
                ShareEvent.OpenPrivacyChoices -> consent.openChoices()
            }
        }

        private fun load() {
            val t = token?.takeIf(ShareLinks::isValidToken)
            if (t == null) {
                status.update { it.copy(isLoading = false, error = AppError.NotFound("Shared plan not found")) }
                return
            }
            status.update { it.copy(isLoading = true, error = null) }
            viewModelScope.launch {
                when (val result = plans.shared(t)) {
                    is AppResult.Success -> {
                        shared = result.value
                        val plan = result.value
                        val kind = if (plan.kind == null) ShareKind.PLAN else ShareKind.from(plan.kind)
                        // Only the three known kinds are in the closed telemetry enum; a legacy token is a plan.
                        kind.wire?.let { telemetry.emit(TelemetryEvents.SHARE_VIEW, mapOf("kind" to it)) }
                        if (kind != ShareKind.PLAN) {
                            status.update {
                                it.copy(
                                    isLoading = false,
                                    kind = kind,
                                    ownerName = plan.ownerName?.takeIf(String::isNotBlank),
                                    card = plan.card.takeIf { kind != ShareKind.UNKNOWN },
                                    unknownKind = kind == ShareKind.UNKNOWN || plan.card == null,
                                    plainLink = ShareLinks.plainLink(build.webBaseUrl, t),
                                )
                            }
                            return@launch
                        }
                        val known = placeNames.names.value
                        status.update {
                            it.copy(
                                isLoading = false,
                                ownerName = plan.ownerName?.takeIf(String::isNotBlank),
                                trip = withContext(dispatchers.default) { SharedTrip.from(plan, known) },
                            )
                        }
                        // Airport codes → cities (shared lookup); a code it cannot name stays a code.
                        val names = placeNames.resolve(SharedTrip.places(plan))
                        if (!known.entries.containsAll(names.entries)) {
                            val named = withContext(dispatchers.default) { SharedTrip.from(plan, known + names) }
                            status.update { it.copy(trip = named) }
                        }
                    }

                    is AppResult.Failure -> {
                        status.update { it.copy(isLoading = false, error = result.error) }
                    }
                }
            }
        }

        private fun saveCopy() {
            val plan = shared ?: return
            if (status.value.isSaving || status.value.savedPlanId != null) return
            status.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val searchData = TravelJson.encodeToJsonElement(SharedSearchData.serializer(), plan.searchData) as JsonObject
                val result = plans.create(plan.name, searchData, plan.selections)
                status.update {
                    it.copy(
                        isSaving = false,
                        savedPlanId = (result as? AppResult.Success)?.value?.id,
                        message =
                            when (result) {
                                is AppResult.Success -> ShareMessage.Saved(result.value.id)
                                is AppResult.Failure -> ShareMessage.SaveFailed(result.error)
                            },
                    )
                }
            }
        }

        companion object {
            /** Type-safe navigation stores `ShareRoute.token` under its property name. */
            const val TOKEN_ARG = "token"
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
