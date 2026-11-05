package io.github.rupakc.travelplanner.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved plan as last seen from the server, stored as its JSON (offline read of saved plans). */
@Entity(tableName = "cached_plans", indices = [Index("username")])
data class CachedPlanEntity(
    @PrimaryKey val id: Long,
    val username: String,
    val name: String,
    /** Full `Plan` JSON exactly as the API returned it. */
    val json: String,
    /** Server `updated_at` (SQLite UTC string) for ordering; newest first. */
    val updatedAt: String,
)

@Entity(tableName = "chat_threads", indices = [Index("username")])
data class ChatThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val title: String,
    /** Last `session_context_update.context` JSON, resent as `session_context`. */
    val sessionContextJson: String = "{}",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    /** "user" | "assistant" */
    val role: String,
    /** Markdown text. */
    val content: String,
    /** Feature-owned JSON for rich content (chips, trip map, itinerary, section cards). */
    val extrasJson: String? = null,
    val createdAtMillis: Long,
)

@Entity(tableName = "recent_searches", indices = [Index("username")])
data class RecentSearchEntity(
    @PrimaryKey val id: String,
    val username: String,
    /** `RecentSearch` JSON. */
    val json: String,
    val lastUsedAt: String,
)

/** Notification ids already posted on this device (dedupe for AlertPollWorker). */
@Entity(tableName = "seen_notifications")
data class SeenNotificationEntity(
    @PrimaryKey val id: Long,
    val seenAtMillis: Long,
)

/** The single My Plan draft (CurrentPlanStore), so it survives process death. */
@Entity(tableName = "plan_draft")
data class PlanDraftEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val json: String,
) {
    companion object {
        const val SINGLE_ROW = 0
    }
}
