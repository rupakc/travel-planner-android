package io.github.rupakc.travelplanner.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.datastore.CoachMarkStore
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingGate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The key coach-mark state is stored under: one per signed-in user (their id, else their username). */
internal fun coachUserKey(session: Session): String = session.userId ?: session.user.id ?: session.user.username

/** Shell services that need DI: coach marks (per user), the server-driven onboarding check and the game-delta stream. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ShellServicesViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val coachStore: CoachMarkStore,
        private val onboardingGate: OnboardingGate,
        gameEvents: GameEvents,
    ) : ViewModel() {
        /** Tips the signed-in user has seen; null while unknown or signed out. */
        val coachSeen: StateFlow<Set<String>?> =
            authRepository.session
                .flatMapLatest { session -> if (session == null) flowOf(null) else coachStore.seen(coachUserKey(session)) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val gameDeltas: Flow<GameDelta> = gameEvents.deltas

        /** Marks [ids] seen for the current user (one tip, or all of them for "Skip"). */
        fun markCoachSeen(ids: Set<String>) {
            viewModelScope.launch {
                val session = authRepository.session.first() ?: return@launch
                coachStore.markSeen(coachUserKey(session), ids)
            }
        }

        /** True once per process for a signed-in user the server says has not finished onboarding. */
        suspend fun shouldShowOnboarding(): Boolean = onboardingGate.shouldShow(authRepository.session.first() != null)
    }
