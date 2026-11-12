package io.github.rupakc.travelplanner.core.domain.server

import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto

/**
 * Feature gates of Phase 1/2, derived from `GET /api/config`. Pure; every flag is false when the
 * config (or its `features` / `auth` block) is missing, i.e. against the previous backend, so the app then
 * behaves exactly as before.
 *
 * - [journey] is the tab shell and Trips hub (`features.trips && features.journey`).
 * - [offlinePack] is false when `features.offline_pack` is false or absent: the offline tab hides and every
 *   pack and the outbox are deleted.
 * - [game] hides Play and every celebration when false (the user's `hide_game` setting is applied on top).
 */
data class JourneyFlags(
    val accounts: Boolean = false,
    val game: Boolean = false,
    val trips: Boolean = false,
    val journey: Boolean = false,
    val offlinePack: Boolean = false,
    val signupAvailable: Boolean = false,
    val resetAvailable: Boolean = false,
) {
    companion object {
        /** Everything off (config not loaded, or an older backend). */
        val OFF = JourneyFlags()

        /** Derives the flags from [config]; null means everything off. */
        operator fun invoke(config: ClientConfigDto?): JourneyFlags {
            val f = config?.features ?: return OFF
            val trips = f.trips
            return JourneyFlags(
                accounts = f.accounts,
                game = f.game,
                trips = trips,
                journey = trips && f.journey,
                offlinePack = trips && f.offlinePack,
                signupAvailable = f.accounts && config.auth?.signupAvailable == true,
                resetAvailable = f.accounts && config.auth?.passwordResetAvailable == true,
            )
        }
    }
}
