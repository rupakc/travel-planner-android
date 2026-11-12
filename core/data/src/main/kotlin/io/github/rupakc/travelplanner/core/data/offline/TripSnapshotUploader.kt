package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.OfflinePackRepository
import io.github.rupakc.travelplanner.core.data.repository.Outbox
import io.github.rupakc.travelplanner.core.data.repository.OutboxRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * `PUT /trips/{id}/snapshot` after a plan save while results are shown (Task 15 calls it). Only the five allowed
 * sections are sent, each without its `verification`. `413`/`422` are swallowed (the server will never accept that
 * body); a network or server failure queues the upload in the outbox, keyed per trip so the latest body wins.
 */
@Singleton
class TripSnapshotUploader
    @Inject
    constructor(
        private val packs: OfflinePackRepository,
        private val outbox: Outbox,
    ) {
        /** Never throws and never reports: a snapshot is best effort. */
        suspend fun put(tripId: Long, sections: JsonObject) {
            val body = clean(sections)
            if (body.isEmpty()) return
            val result = packs.putSnapshot(tripId, body)
            if (result !is AppResult.Failure) return
            val error = result.error
            val retry = error is AppError.Network || error is AppError.Server || error is AppError.RateLimited
            if (retry) outbox.enqueue(OutboxRequest("PUT", "api/trips/$tripId/snapshot", body.toString(), "snapshot:$tripId"))
        }

        companion object {
            /** The only sections the server stores. */
            val ALLOWED = setOf("emergency_card", "forex", "visa", "packing_list", "weather")

            /** [sections] reduced to [ALLOWED] keys, each object without `verification`. */
            fun clean(sections: JsonObject): JsonObject =
                JsonObject(
                    sections.filterKeys { it in ALLOWED }.mapValues { (_, v) ->
                        if (v is JsonObject) JsonObject(v.filterKeys { it != "verification" }) else v
                    },
                )
        }
    }
