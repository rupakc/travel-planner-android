package io.github.rupakc.travelplanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last good [JourneyFlags] (from `GET /api/config`), so the shell does not fall back to the classic tabs while offline or
 * while the config request is slow. A bitmask in plain preferences: server feature flags, no user data, nothing to purge.
 */
@Singleton
class JourneyFlagsCache
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("shell_cache", Context.MODE_PRIVATE)

        fun read(): JourneyFlags? = if (prefs.contains(KEY)) decode(prefs.getInt(KEY, 0)) else null

        fun write(flags: JourneyFlags) = prefs.edit().putInt(KEY, encode(flags)).apply()

        private companion object {
            const val KEY = "journey_flags"
        }
    }

internal fun encode(f: JourneyFlags): Int =
    listOf(f.accounts, f.game, f.trips, f.journey, f.offlinePack, f.signupAvailable, f.resetAvailable, f.sharing)
        .foldIndexed(0) { i, acc, on -> if (on) acc or (1 shl i) else acc }

internal fun decode(bits: Int): JourneyFlags =
    JourneyFlags(
        accounts = bits and 1 != 0,
        game = bits and 2 != 0,
        trips = bits and 4 != 0,
        journey = bits and 8 != 0,
        offlinePack = bits and 16 != 0,
        signupAvailable = bits and 32 != 0,
        resetAvailable = bits and 64 != 0,
        sharing = bits and 128 != 0,
    )
