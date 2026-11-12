package io.github.rupakc.travelplanner.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Query("SELECT * FROM cached_plans WHERE username = :username ORDER BY updatedAt DESC")
    fun observeAll(username: String): Flow<List<CachedPlanEntity>>

    @Query("SELECT * FROM cached_plans WHERE id = :id")
    fun observe(id: Long): Flow<CachedPlanEntity?>

    /** One cached plan of [username] only (another account's cached row never shows). */
    @Query("SELECT * FROM cached_plans WHERE id = :id AND username = :username")
    fun observeFor(id: Long, username: String): Flow<CachedPlanEntity?>

    @Query("SELECT * FROM cached_plans WHERE id = :id")
    suspend fun get(id: Long): CachedPlanEntity?

    @Upsert
    suspend fun upsert(plan: CachedPlanEntity)

    @Query("DELETE FROM cached_plans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM cached_plans WHERE username = :username")
    suspend fun deleteAllFor(username: String)

    @Transaction
    suspend fun replaceAll(username: String, plans: List<CachedPlanEntity>) {
        deleteAllFor(username)
        plans.forEach { upsert(it) }
    }

    @Query("DELETE FROM cached_plans")
    suspend fun clear()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads WHERE username = :username ORDER BY updatedAtMillis DESC")
    fun observeThreads(username: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun thread(id: Long): ChatThreadEntity?

    @Insert
    suspend fun insertThread(thread: ChatThreadEntity): Long

    @Query("UPDATE chat_threads SET title = :title, updatedAtMillis = :now WHERE id = :id")
    suspend fun renameThread(id: Long, title: String, now: Long)

    @Query("UPDATE chat_threads SET sessionContextJson = :json, updatedAtMillis = :now WHERE id = :id")
    suspend fun updateSessionContext(id: Long, json: String, now: Long)

    @Query("UPDATE chat_threads SET updatedAtMillis = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM chat_threads WHERE id = :id")
    suspend fun deleteThread(id: Long)

    /**
     * Ids of [username]'s threads with a message containing [pattern] (a LIKE pattern with
     * `\\` as escape; callers wrap the query in `%…%`). Joined through the indexed
     * `threadId`/`username` columns; capped by [limit]. LIKE is case-insensitive for ASCII.
     */
    @Query(
        "SELECT DISTINCT m.threadId FROM chat_messages m JOIN chat_threads t ON t.id = m.threadId " +
            "WHERE t.username = :username AND m.content LIKE :pattern ESCAPE '\\' LIMIT :limit",
    )
    fun observeThreadIdsWithText(username: String, pattern: String, limit: Int): Flow<List<Long>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY id ASC")
    fun observeMessages(threadId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY id ASC")
    suspend fun messages(threadId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content, extrasJson = :extrasJson WHERE id = :id")
    suspend fun updateMessage(id: Long, content: String, extrasJson: String?)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM chat_threads")
    suspend fun clear()
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches WHERE username = :username ORDER BY lastUsedAt DESC")
    fun observe(username: String): Flow<List<RecentSearchEntity>>

    @Upsert
    suspend fun upsert(item: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recent_searches WHERE username = :username")
    suspend fun deleteAllFor(username: String)

    @Transaction
    suspend fun replaceAll(username: String, items: List<RecentSearchEntity>) {
        deleteAllFor(username)
        items.forEach { upsert(it) }
    }

    @Query("DELETE FROM recent_searches")
    suspend fun clear()
}

@Dao
interface SeenNotificationDao {
    @Query("SELECT id FROM seen_notifications WHERE id IN (:ids)")
    suspend fun seenAmong(ids: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(items: List<SeenNotificationEntity>)

    @Query("DELETE FROM seen_notifications WHERE seenAtMillis < :before")
    suspend fun prune(before: Long)

    @Query("DELETE FROM seen_notifications")
    suspend fun clear()
}

@Dao
interface PlanDraftDao {
    @Query("SELECT * FROM plan_draft WHERE id = 0")
    suspend fun get(): PlanDraftEntity?

    @Upsert
    suspend fun save(draft: PlanDraftEntity)

    @Query("DELETE FROM plan_draft")
    suspend fun clear()

    /** Row by id: [PlanDraftEntity.SINGLE_ROW] = the draft, [PlanDraftEntity.SEARCH_ROW] = its search-data snapshot. */
    @Query("SELECT * FROM plan_draft WHERE id = :id")
    suspend fun getRow(id: Int): PlanDraftEntity?

    @Query("DELETE FROM plan_draft WHERE id = :id")
    suspend fun deleteRow(id: Int)
}

@Dao
interface OfflinePackDao {
    @Query("SELECT * FROM offline_pack WHERE `key` = :key")
    fun observe(key: String): Flow<OfflinePackEntity?>

    @Query("SELECT * FROM offline_pack WHERE `key` = :key")
    suspend fun get(key: String): OfflinePackEntity?

    @Upsert
    suspend fun upsert(pack: OfflinePackEntity)

    @Query("DELETE FROM offline_pack WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM offline_pack WHERE userId = :userId")
    suspend fun deleteAllFor(userId: String)

    @Query("DELETE FROM offline_pack")
    suspend fun clear()
}

@Dao
interface OutboxDao {
    /** Queues [item]; an earlier row with the same user and key is replaced (and moves to the end). */
    @Transaction
    suspend fun enqueue(item: OutboxEntity) {
        deleteKey(item.userId, item.key)
        insert(item.copy(id = 0))
    }

    @Insert
    suspend fun insert(item: OutboxEntity): Long

    @Query("DELETE FROM outbox WHERE userId = :userId AND `key` = :key")
    suspend fun deleteKey(userId: String, key: String)

    /** The oldest waiting row of [userId], or null. */
    @Query("SELECT * FROM outbox WHERE userId = :userId AND id > :afterId ORDER BY id LIMIT 1")
    suspend fun next(userId: String, afterId: Long): OutboxEntity?

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT `key` FROM outbox WHERE userId = :userId ORDER BY id")
    fun observeKeys(userId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM outbox WHERE userId = :userId")
    suspend fun count(userId: String): Int

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun countAll(): Int

    /** Only the queued offline-pack snapshot uploads (`snapshot:<tripId>`); expenses and journal writes stay. */
    @Query("DELETE FROM outbox WHERE userId = :userId AND `key` LIKE 'snapshot:%'")
    suspend fun deleteSnapshotsFor(userId: String)

    @Query("DELETE FROM outbox WHERE userId = :userId")
    suspend fun deleteAllFor(userId: String)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface TripCacheDao {
    @Query("SELECT * FROM cached_trips WHERE userId = :userId")
    suspend fun trips(userId: String): CachedTripsEntity?

    @Upsert
    suspend fun upsertTrips(entity: CachedTripsEntity)

    @Query("SELECT * FROM cached_expenses WHERE `key` = :key")
    suspend fun expenses(key: String): CachedExpensesEntity?

    @Upsert
    suspend fun upsertExpenses(entity: CachedExpensesEntity)

    @Query("DELETE FROM cached_trips")
    suspend fun clearTrips()

    @Query("DELETE FROM cached_expenses")
    suspend fun clearExpenses()
}
