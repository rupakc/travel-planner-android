package io.github.rupakc.travelplanner.core.model.trips

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `PrepItem` ids in served order (`offline_pack` is automatic). */
object PrepItemIds {
    val ALL: List<String> =
        listOf(
            "visa",
            "passport",
            "bookings",
            "insurance",
            "documents",
            "stress_test",
            "sim",
            "money",
            "packing",
            "emergency_card",
            "checkin",
            "offline_pack",
        )
}

/** One checklist item. [status] is `todo` | `done` | `na`; [facts] differs per item and is kept raw. */
@Serializable
data class PrepItem(
    val id: String,
    val status: String = "todo",
    val auto: Boolean = false,
    val due: String? = null,
    val overdue: Boolean = false,
    val facts: JsonObject = JsonObject(emptyMap()),
)

/** `GET /trips/{id}/prep`. */
@Serializable
data class PrepResponse(
    val items: List<PrepItem> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    val complete: Boolean = false,
)

/** `PUT /trips/{id}/prep/{item_id}` body. */
@Serializable
data class PrepToggleRequest(val done: Boolean)

/** `PUT /trips/{id}/prep/{item_id}` 200: the prep object plus the toggled [item] and the game delta. */
@Serializable
data class PrepToggleResponse(
    val items: List<PrepItem> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    val complete: Boolean = false,
    val item: PrepItem? = null,
    val game: GameDelta? = null,
)
