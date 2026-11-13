package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.GameRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.game.GameMe
import io.github.rupakc.travelplanner.core.network.api.GameApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide bus of game deltas. Every repository returning a `game` field calls [publish]. Nothing is
 * published (or counted in telemetry) while the game is hidden ([setVisible] false: `GET /game/me` answered 404
 * or `hidden`, i.e. `hide_game` or the feature flag), so no celebration can appear.
 */
@Singleton
class GameEventsImpl
    @Inject
    constructor(private val telemetry: Telemetry) : GameEvents {
        private val flow = MutableSharedFlow<GameDelta>(extraBufferCapacity = BUFFER)

        private val shown = MutableStateFlow(false)

        override val deltas: Flow<GameDelta> = flow.asSharedFlow()

        /** Hidden until the user's `hide_game` setting (or `GET /game/me`) says otherwise. */
        override val visible: StateFlow<Boolean> = shown.asStateFlow()

        /** Called with what `GET /game/me` or `/account/settings` says. */
        override fun setVisible(value: Boolean) {
            shown.value = value
        }

        private val hiddenCommunity = MutableStateFlow(false)

        override val communityHidden: StateFlow<Boolean> = hiddenCommunity.asStateFlow()

        override fun setCommunityHidden(value: Boolean) {
            hiddenCommunity.value = value
        }

        override fun publish(delta: GameDelta?) {
            if (delta == null || delta.isEmpty || !shown.value) return
            delta.awards.forEach { telemetry.emit(TelemetryEvents.GAME_AWARD, mapOf("rule" to it.rule, "points" to it.points)) }
            delta.badges.forEach { telemetry.emit(TelemetryEvents.BADGE_EARNED, mapOf("badge" to it.id)) }
            delta.levelUp?.let { telemetry.emit(TelemetryEvents.LEVEL_UP, mapOf("level" to it.to)) }
            telemetry.emit(TelemetryEvents.GAME_VIEW, mapOf("surface" to "celebration"))
            flow.tryEmit(delta)
        }

        private companion object {
            const val BUFFER = 16
        }
    }

/** `GET /game/me` and `GET /game/catalog` (cached 5 minutes). A 404 is a `NotFound` failure: the game is off. */
@Singleton
class GameRepositoryImpl
    internal constructor(
        private val api: GameApi,
        private val events: GameEventsImpl,
        private val now: () -> Long,
    ) : GameRepository {
        @Inject
        constructor(api: GameApi, events: GameEventsImpl) : this(api, events, System::currentTimeMillis)

        private val lock = Mutex()
        private var cached: GameCatalog? = null
        private var cachedAt = 0L

        override suspend fun me(): AppResult<GameMe> {
            val result = apiCall { api.me() }
            when (result) {
                is AppResult.Success -> events.setVisible(!result.value.hidden)
                is AppResult.Failure -> if (result.error is AppError.NotFound) events.setVisible(false)
            }
            return result
        }

        override suspend fun catalog(): AppResult<GameCatalog> =
            lock.withLock {
                val hit = cached
                if (hit != null && now() - cachedAt < TTL_MS) return AppResult.Success(hit)
                apiCall { api.catalog() }.also { r ->
                    if (r is AppResult.Success) {
                        cached = r.value
                        cachedAt = now()
                    }
                }
            }

        private companion object {
            const val TTL_MS = 5 * 60 * 1000L
        }
    }
