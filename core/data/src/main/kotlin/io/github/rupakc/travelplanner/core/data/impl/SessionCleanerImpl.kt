package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.database.PlanDao
import io.github.rupakc.travelplanner.core.database.RecentSearchDao
import io.github.rupakc.travelplanner.core.database.SeenNotificationDao
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Wipes everything that belongs to the signed-in user on this device (review I6): the My Plan draft
 * (memory + Room), the app-wide search, the nearby cache and the last Near me location, cached plans,
 * recent searches, posted notification ids, every contributed [UserStateCleaner] (e.g. the alert
 * poll baseline) and the traveller profile (type, quiz answers, feature toggles), so the next account on this
 * phone onboards with its own. Chat history is kept: it is stored per username and only that user sees it.
 * Each step is independent and failures are logged, so one broken table can't block sign-out.
 */
@Singleton
class SessionCleanerImpl
    @Inject
    constructor(
        private val currentPlanStore: CurrentPlanStore,
        private val searchRepository: SearchRepository,
        private val nearbyRepository: NearbyRepositoryImpl,
        private val planDao: PlanDao,
        private val recentSearchDao: RecentSearchDao,
        private val seenNotificationDao: SeenNotificationDao,
        private val extraCleaners: Set<@JvmSuppressWildcards UserStateCleaner>,
        private val profileRepository: ProfileRepository,
    ) : SessionCleaner {
        override suspend fun clearUserData() {
            step("search") { searchRepository.clear() }
            step("nearby cache") { nearbyRepository.clearCache() }
            // The next user's background "near you" checks must never use this user's whereabouts (security S-I1).
            step("last location") { nearbyRepository.forgetLocation() }
            step("plan draft") { currentPlanStore.reset() }
            step("cached plans") { planDao.clear() }
            step("recent searches") { recentSearchDao.clear() }
            step("seen notifications") { seenNotificationDao.clear() }
            extraCleaners.forEach { step(it::class.java.simpleName) { it.clear() } }
            step("traveller profile") {
                profileRepository.saveProfile(TravellerProfile())
                profileRepository.resetFeatures()
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun step(what: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SessionCleaner", "Could not clear $what", e)
            }
        }
    }
