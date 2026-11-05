package io.github.rupakc.travelplanner.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.network.AuthEvent
import io.github.rupakc.travelplanner.core.network.AuthEvents
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * App-shell state that crosses features: session-expired events and a deep link held behind the
 * auth gate. Sign-out lives in Settings, onboarding completion in feature:onboarding.
 */
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        authEvents: AuthEvents,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        /** Emits when a 401 cleared the session (shown as a snackbar). */
        val sessionExpired: Flow<AuthEvent> = authEvents.flow

        /**
         * Keeps the deep link (`travelplanner://…`) the gate redirected away from (e.g. a trip-reminder tap
         * after the 24 h JWT expired), so it opens once the gate is READY. Survives process death.
         */
        fun keepPendingDeepLink(link: String) {
            savedState[KEY_PENDING] = link
        }

        /** The kept deep link, cleared on read (replayed exactly once). */
        fun takePendingDeepLink(): String? = savedState.remove<String>(KEY_PENDING)

        private companion object {
            const val KEY_PENDING = "pending_deep_link"
        }
    }
