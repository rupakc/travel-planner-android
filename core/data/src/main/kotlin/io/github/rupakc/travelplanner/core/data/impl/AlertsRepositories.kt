package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.data.repository.DevicesRepository
import io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.database.SeenNotificationDao
import io.github.rupakc.travelplanner.core.database.SeenNotificationEntity
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchMatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadRequest
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadResponse
import io.github.rupakc.travelplanner.core.model.alerts.NotificationsPage
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.UpdateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.UpdatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.devices.Device
import io.github.rupakc.travelplanner.core.model.devices.RegisterDeviceRequest
import io.github.rupakc.travelplanner.core.model.devices.UnregisterDeviceRequest
import io.github.rupakc.travelplanner.core.network.api.DevicesApi
import io.github.rupakc.travelplanner.core.network.api.FareWatchApi
import io.github.rupakc.travelplanner.core.network.api.NotificationsApi
import io.github.rupakc.travelplanner.core.network.api.PriceWatchApi
import io.github.rupakc.travelplanner.core.network.apiCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import retrofit2.Response
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FareWatchesRepositoryImpl @Inject constructor(private val api: FareWatchApi) : FareWatchesRepository {
    override suspend fun list(): AppResult<List<FareWatch>> = apiCall { api.list() }

    override suspend fun match(trip: FareWatchMatchRequest): AppResult<FareWatch?> = apiCall { api.match(trip) }.map { it.watch }

    override suspend fun create(request: CreateFareWatchRequest): AppResult<FareWatch> = apiCall { api.create(request) }

    override suspend fun get(id: Long): AppResult<FareWatch> = apiCall { api.get(id) }

    override suspend fun update(id: Long, patch: UpdateFareWatchRequest): AppResult<FareWatch> = apiCall { api.update(id, patch) }

    override suspend fun delete(id: Long): AppResult<Unit> = apiCall { api.delete(id) }.map { }

    override suspend fun check(id: Long): AppResult<FareWatch> = apiCall { api.check(id) }
}

@Singleton
class PriceWatchesRepositoryImpl @Inject constructor(private val api: PriceWatchApi) : PriceWatchesRepository {
    override suspend fun list(kind: String?): AppResult<List<PriceWatch>> = apiCall { api.list(kind) }

    override suspend fun match(kind: String, spec: JsonObject): AppResult<PriceWatch?> =
        apiCall { api.match(buildJsonObject { put("kind", JsonPrimitive(kind)); put(kind, spec) }) }.map { it.watch }

    override suspend fun create(request: CreatePriceWatchRequest): AppResult<PriceWatch> = apiCall { api.create(request.toJson()) }

    override suspend fun get(id: Long): AppResult<PriceWatch> = apiCall { api.get(id) }

    override suspend fun update(id: Long, patch: UpdatePriceWatchRequest): AppResult<PriceWatch> = apiCall { api.update(id, patch) }

    override suspend fun delete(id: Long): AppResult<Unit> = apiCall { api.delete(id) }.map { }

    override suspend fun check(id: Long): AppResult<PriceWatch> = apiCall { api.check(id) }
}

/** `{"kind":"hotel","hotel":{spec},"plan_id":…,"alert_mode":…,"target_price":…,"target_currency":…,"email_enabled":…}` */
fun CreatePriceWatchRequest.toJson(): JsonObject = buildJsonObject {
    put("kind", JsonPrimitive(kind))
    put(kind, spec)
    planId?.let { put("plan_id", JsonPrimitive(it)) }
    put("alert_mode", JsonPrimitive(alertMode))
    targetPrice?.let { put("target_price", JsonPrimitive(it)) }
    targetCurrency?.let { put("target_currency", JsonPrimitive(it)) }
    put("email_enabled", JsonPrimitive(emailEnabled))
}

@Singleton
class NotificationsRepositoryImpl @Inject constructor(
    private val api: NotificationsApi,
    private val seenDao: SeenNotificationDao,
    private val clock: AppClock,
) : NotificationsRepository {
    override suspend fun list(unreadOnly: Boolean, limit: Int, offset: Int): AppResult<NotificationsPage> =
        apiCall { api.list(unreadOnly, limit, offset) }

    override suspend fun unreadCount(): AppResult<Int> = apiCall { api.unreadCount() }.map { it.count }

    override suspend fun markRead(ids: List<Long>): AppResult<MarkReadResponse> = apiCall { api.markRead(MarkReadRequest(ids = ids)) }

    override suspend fun markAllRead(): AppResult<MarkReadResponse> = apiCall { api.markRead(MarkReadRequest(all = true)) }

    override suspend fun unseen(limit: Int): AppResult<List<AppNotification>> = list(unreadOnly = true, limit = limit).map { page ->
        val ids = page.items.map { it.id }
        val seen = if (ids.isEmpty()) emptySet() else seenDao.seenAmong(ids).toSet()
        page.items.filter { it.id !in seen }
    }

    override suspend fun markSeen(ids: List<Long>) {
        val now = clock.now().toEpochMilli()
        seenDao.markSeen(ids.map { SeenNotificationEntity(it, now) })
        seenDao.prune(now - SEEN_RETENTION.toMillis())
    }

    private companion object {
        val SEEN_RETENTION: Duration = Duration.ofDays(60)
    }
}

@Singleton
class DevicesRepositoryImpl @Inject constructor(private val api: DevicesApi) : DevicesRepository {
    override suspend fun register(token: String, locale: String?): AppResult<Device> =
        apiCall { api.register(RegisterDeviceRequest(token = token, locale = locale)) }

    override suspend fun list(): AppResult<List<Device>> = apiCall { api.list().items }

    override suspend fun delete(id: Long): AppResult<Unit> = apiCall { api.delete(id).orThrow() }

    override suspend fun unregister(token: String): AppResult<Unit> = apiCall { api.unregister(UnregisterDeviceRequest(token)).orThrow() }
}

internal fun Response<Unit>.orThrow() {
    if (!isSuccessful) throw HttpException(this)
}
