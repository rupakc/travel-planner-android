package io.github.rupakc.travelplanner.core.data.telemetry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryQueue
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Events pending delivery, kept in `noBackupFilesDir/telemetry/queue.json` as `{"v":1,"events":[...]}`
 * (never in cloud backup). Every operation holds one mutex, so a drain's `claim` (which removes the file)
 * and an `append` made meanwhile never overwrite each other: an append merges with what is on disk.
 */
@Singleton
internal class TelemetryQueueFile
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val dispatchers: AppDispatchers,
    ) : TelemetryQueueStore {
        private val mutex = Mutex()
        private val file: File get() = File(File(context.noBackupFilesDir, DIR), FILE)

        override suspend fun append(events: List<QueuedEvent>) {
            if (events.isEmpty()) return
            mutex.withLock {
                withContext(dispatchers.io) {
                    // Oldest first (stable), so a returned batch lands ahead of events appended meanwhile.
                    val merged = (read() + events).sortedBy { it.ts }.takeLast(CAP)
                    write(merged)
                }
            }
        }

        override suspend fun claim(nowMs: Long): List<QueuedEvent> =
            mutex.withLock {
                withContext(dispatchers.io) {
                    val saved = read()
                    file.delete()
                    // The same age and token rules as the in-memory queue's prepend.
                    val queue = TelemetryQueue(CAP)
                    queue.prepend(saved, nowMs)
                    queue.pending(emptySet())
                }
            }

        override suspend fun clear() {
            mutex.withLock { withContext(dispatchers.io) { file.delete() } }
        }

        /** Stored events; a missing file is empty and a corrupt one is deleted. */
        private fun read(): List<QueuedEvent> {
            val f = file
            if (!f.exists()) return emptyList()
            return try {
                val root = TravelJson.parseToJsonElement(f.readText()).jsonObject
                check(root["v"]?.jsonPrimitive?.intOrNull == VERSION) { "unknown queue version" }
                root["events"]!!.jsonArray.mapNotNull { (it as? JsonObject)?.toQueuedEvent() }
            } catch (_: Exception) {
                f.delete()
                emptyList()
            }
        }

        private fun write(events: List<QueuedEvent>) {
            val f = file
            f.parentFile?.mkdirs()
            val body =
                JsonObject(
                    mapOf(
                        "v" to JsonPrimitive(VERSION),
                        "events" to JsonArray(events.map { it.toStoredJson() }),
                    ),
                )
            // Write beside, then rename, so a kill mid-write leaves the old file whole.
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.writeText(TravelJson.encodeToString(JsonObject.serializer(), body))
            check(tmp.renameTo(f)) { "telemetry queue rename failed" }
        }

        private companion object {
            const val DIR = "telemetry"
            const val FILE = "queue.json"
            const val CAP = 500
            const val VERSION = 1
        }
    }
