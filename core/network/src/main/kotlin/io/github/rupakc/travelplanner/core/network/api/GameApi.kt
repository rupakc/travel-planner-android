package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameMe
import retrofit2.http.GET

/** Game engine reads (`404` = `GAME_ENABLED=false`, which hides every game element). */
interface GameApi {
    @GET("api/game/me")
    suspend fun me(): GameMe

    @GET("api/game/catalog")
    suspend fun catalog(): GameCatalog
}
