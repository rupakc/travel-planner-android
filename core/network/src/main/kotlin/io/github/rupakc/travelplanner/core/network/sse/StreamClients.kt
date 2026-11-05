package io.github.rupakc.travelplanner.core.network.sse

import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * POSTs JSON and streams SSE `data:` payloads as a cold [Flow].
 * - No read/call timeout (long silent agent phases); cancelling the collector cancels the call.
 * - HTTP errors before streaming → [AppException] with the mapped [AppError] (429 keeps Retry-After).
 * - A network failure mid-stream → [AppException] with [AppError.Interrupted].
 */
@Singleton
class SseClient @Inject constructor(
    @param:StreamingHttp private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
) {
    fun post(path: String, jsonBody: String): Flow<String> = flow {
        val request = Request.Builder()
            .url(PLACEHOLDER_BASE_URL + path.trimStart('/'))
            .header("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                throw AppException(e.toAppError(), e)
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    throw AppException(HttpErrorMapper.map(resp.code, resp.header("Retry-After"), resp.body.string()))
                }
                try {
                    for (payload in SseParser.payloads(resp.body.source())) emit(payload)
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    throw AppException(AppError.Interrupted(), e)
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(dispatchers.io)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/**
 * `POST /api/search` as typed events. Completes after [SearchEvent.Done]; a stream
 * that ends without `done` throws [AppException] with [AppError.Interrupted]
 * (the repository retries once, web parity).
 */
@Singleton
class SearchStreamClient @Inject constructor(
    private val sse: SseClient,
    private val dispatchers: AppDispatchers,
) {
    private val json: Json = TravelJson

    fun stream(request: TravelSearchRequest): Flow<SearchEvent> = flow {
        var done = false
        sse.post(SEARCH_PATH, json.encodeToString(TravelSearchRequest.serializer(), request)).collect { payload ->
            if (done) return@collect
            val event = EventDecoder.decodeSearch(payload, json) ?: return@collect
            emit(event)
            if (event is SearchEvent.Done) done = true
        }
        if (!done) throw AppException(AppError.Interrupted())
    }.flowOn(dispatchers.default)

    companion object {
        const val SEARCH_PATH = "api/search"
    }
}

/**
 * `POST /api/chat` as typed events. Completes on `done` **or** `planning_done`;
 * a stream that ends without either simply completes (web treats it as completion).
 */
@Singleton
class ChatStreamClient @Inject constructor(
    private val sse: SseClient,
    private val dispatchers: AppDispatchers,
) {
    private val json: Json = TravelJson

    fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        var finished = false
        sse.post(CHAT_PATH, json.encodeToString(ChatRequest.serializer(), request)).collect { payload ->
            if (finished) return@collect
            val event = EventDecoder.decodeChat(payload, json) ?: return@collect
            emit(event)
            if (event.isTerminal) finished = true
        }
    }.flowOn(dispatchers.default)

    companion object {
        const val CHAT_PATH = "api/chat"
    }
}
