package io.github.rupakc.travelplanner.core.network.sse

import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.EventDecoder
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.network.HttpErrorMapper
import io.github.rupakc.travelplanner.core.network.PLACEHOLDER_BASE_URL
import io.github.rupakc.travelplanner.core.network.di.StreamingHttp
import io.github.rupakc.travelplanner.core.network.toAppError
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

/** One item of a materialised SSE stream: a payload, or the failure that ended it. */
sealed interface SseItem<out T> {
    data class Payload<T>(val value: T) : SseItem<T>

    data class Failure(val error: AppError) : SseItem<Nothing>
}

/**
 * POSTs JSON and streams SSE `data:` payloads as a cold [Flow].
 * - No read/call timeout (long silent agent phases); cancelling the collector cancels the call.
 * - HTTP errors before streaming → [AppException] with the mapped [AppError] (429 keeps Retry-After).
 * - A network failure mid-stream → [AppException] with [AppError.Interrupted].
 *
 * Failures travel through the IO boundary as [SseItem.Failure] and are thrown only
 * in the collector's context, so every payload received before a failure is delivered.
 */
@Singleton
class SseClient
    @Inject
    constructor(
        @param:StreamingHttp private val client: OkHttpClient,
        private val dispatchers: AppDispatchers,
    ) {
        fun post(path: String, jsonBody: String): Flow<String> = items(path, jsonBody).dematerialize()

        /**
         * Materialised stream (never throws except cancellation); the blocking read runs on the IO dispatcher.
         * Cancelling the collector cancels the OkHttp call immediately (`awaitClose`), which unblocks a read
         * that is waiting through a long silent phase (review I1).
         */
        fun items(path: String, jsonBody: String): Flow<SseItem<String>> =
            channelFlow {
                val request =
                    Request.Builder()
                        .url(PLACEHOLDER_BASE_URL + path.trimStart('/'))
                        .header("Accept", "text/event-stream")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                val call = client.newCall(request)
                launch(dispatchers.io) {
                    try {
                        readInto(call)
                    } finally {
                        channel.close()
                    }
                }
                awaitClose { call.cancel() }
            }

        private suspend fun ProducerScope<SseItem<String>>.readInto(call: Call) {
            val response =
                try {
                    call.execute()
                } catch (e: IOException) {
                    if (isActive) send(SseItem.Failure(e.toAppError()))
                    return
                }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    send(SseItem.Failure(HttpErrorMapper.map(resp.code, resp.header("Retry-After"), resp.body.string())))
                    return
                }
                readPayloads(resp.body.source(), call)
            }
        }

        private suspend fun ProducerScope<SseItem<String>>.readPayloads(source: BufferedSource, call: Call) {
            val payloads = SseParser.payloads(source).iterator()
            while (true) {
                val next =
                    try {
                        if (payloads.hasNext()) payloads.next() else null
                    } catch (e: IOException) {
                        // Cancelled by the collector → stop quietly; otherwise the connection died or idled out.
                        if (!isActive || call.isCanceled()) return
                        AppLogger.w(TAG, "SSE stream interrupted", e)
                        send(SseItem.Failure(AppError.Interrupted()))
                        return
                    } ?: return
                send(SseItem.Payload(next))
            }
        }

        companion object {
            private const val TAG = "SseClient"
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }

/** Re-throws [SseItem.Failure] as [AppException] in the collector's context. */
fun <T> Flow<SseItem<T>>.dematerialize(): Flow<T> =
    transform { item ->
        when (item) {
            is SseItem.Payload -> emit(item.value)
            is SseItem.Failure -> throw AppException(item.error)
        }
    }

/**
 * `POST /api/search` as typed events (decoded on the Default dispatcher). Completes after
 * [SearchEvent.Done]; a stream that ends without `done` throws [AppException] with
 * [AppError.Interrupted] (the repository retries once, web parity).
 */
@Singleton
class SearchStreamClient
    @Inject
    constructor(
        private val sse: SseClient,
        private val dispatchers: AppDispatchers,
    ) {
        private val json: Json = TravelJson

        fun stream(request: TravelSearchRequest): Flow<SearchEvent> =
            flow {
                var done = false
                sse.items(SEARCH_PATH, json.encodeToString(TravelSearchRequest.serializer(), request))
                    .map<SseItem<String>, SseItem<SearchEvent?>> { item ->
                        when (item) {
                            is SseItem.Payload -> SseItem.Payload(EventDecoder.decodeSearch(item.value, json))
                            is SseItem.Failure -> item
                        }
                    }
                    .flowOn(dispatchers.default)
                    .dematerialize()
                    .collect { event ->
                        if (done || event == null) return@collect
                        emit(event)
                        if (event is SearchEvent.Done) done = true
                    }
                if (!done) throw AppException(AppError.Interrupted())
            }

        companion object {
            const val SEARCH_PATH = "api/search"
        }
    }

/**
 * `POST /api/chat` as typed events. Completes on `done` **or** `planning_done`;
 * a stream that ends without either simply completes (web treats it as completion).
 */
@Singleton
class ChatStreamClient
    @Inject
    constructor(
        private val sse: SseClient,
        private val dispatchers: AppDispatchers,
    ) {
        private val json: Json = TravelJson

        fun stream(request: ChatRequest): Flow<ChatEvent> =
            flow {
                var finished = false
                sse.items(CHAT_PATH, json.encodeToString(ChatRequest.serializer(), request))
                    .map<SseItem<String>, SseItem<ChatEvent?>> { item ->
                        when (item) {
                            is SseItem.Payload -> SseItem.Payload(EventDecoder.decodeChat(item.value, json))
                            is SseItem.Failure -> item
                        }
                    }
                    .flowOn(dispatchers.default)
                    .dematerialize()
                    .collect { event ->
                        if (finished || event == null) return@collect
                        emit(event)
                        if (event.isTerminal) finished = true
                    }
            }

        companion object {
            const val CHAT_PATH = "api/chat"
        }
    }
