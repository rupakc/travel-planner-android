package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.network.OutboxHttp
import javax.inject.Inject

internal class NetworkOutboxSender
    @Inject
    constructor(private val http: OutboxHttp) : OutboxSender {
        override suspend fun send(method: String, path: String, body: String?): Int = http.send(method, path, body)
    }
