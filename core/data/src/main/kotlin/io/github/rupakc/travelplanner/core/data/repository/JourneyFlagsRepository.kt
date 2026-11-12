package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags

/** The Phase 1/2 feature gates of `GET /api/config` (everything off against a backend without them). */
interface JourneyFlagsRepository {
    suspend fun flags(): JourneyFlags
}
