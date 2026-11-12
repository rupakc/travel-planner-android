package io.github.rupakc.travelplanner.feature.onboarding

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether to send the user to onboarding: signed in, not yet shown in this process, and the server says
 * `onboarding.done == false`. A missing answer (404, any error, no repository) never shows it.
 */
fun shouldShowOnboarding(signedIn: Boolean, shownThisProcess: Boolean, account: AppResult<AccountDto>?): Boolean {
    if (!signedIn || shownThisProcess) return false
    val dto = (account as? AppResult.Success)?.value ?: return false
    return !dto.onboarding.done
}

/**
 * The once-per-process onboarding gate for the app shell (Task 17 calls [shouldShow] after sign-in and
 * navigates to `OnboardingRoute()` when it answers true). It answers true at most once per process start;
 * skipping or finishing sends `done: true` to the server, so later launches stay quiet.
 */
@Singleton
class OnboardingGate
    @Inject
    constructor(accountRepository: Optional<AccountRepository>) {
        private val account: AccountRepository? = accountRepository.orElse(null)
        private val shown = AtomicBoolean(false)

        /** [signedIn]: there is a session. Only asks the server when it could matter. */
        suspend fun shouldShow(signedIn: Boolean): Boolean {
            val repository = account
            if (!signedIn || shown.get() || repository == null) return false
            val show = shouldShowOnboarding(signedIn, shownThisProcess = false, account = repository.account())
            return show && shown.compareAndSet(false, true)
        }
    }
