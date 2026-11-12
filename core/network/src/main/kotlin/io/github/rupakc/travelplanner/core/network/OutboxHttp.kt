package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.network.api.OutboxApi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Sends one queued write and returns the HTTP status. Network failures are thrown as [java.io.IOException]. */
@Singleton
class OutboxHttp
    @Inject
    constructor(private val api: OutboxApi) {
        /** [method] is POST, PUT, PATCH or DELETE; [body] is JSON (null sends `{}` for the body methods). */
        suspend fun send(method: String, path: String, body: String?): Int {
            val json = (body ?: "{}").toRequestBody("application/json".toMediaType())
            val response =
                when (method.uppercase()) {
                    "POST" -> api.post(path, json)
                    "PUT" -> api.put(path, json)
                    "PATCH" -> api.patch(path, json)
                    "DELETE" -> api.delete(path)
                    else -> throw IllegalArgumentException("Unsupported outbox method")
                }
            response.errorBody()?.close()
            return response.code()
        }
    }
