package io.github.rupakc.travelplanner.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.network.AuthEvent
import io.github.rupakc.travelplanner.core.network.AuthEvents
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** App-shell actions that cross features (sign-out, session-expired events). */
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        authEvents: AuthEvents,
    ) : ViewModel() {
        /** Emits when a 401 cleared the session (shown as a snackbar). */
        val sessionExpired: Flow<AuthEvent> = authEvents.flow

        suspend fun signOut() = authRepository.logout()
    }
