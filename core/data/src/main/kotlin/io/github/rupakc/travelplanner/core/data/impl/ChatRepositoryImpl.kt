package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.repository.ChatRepository
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.data.repository.StoredChatMessage
import io.github.rupakc.travelplanner.core.database.ChatDao
import io.github.rupakc.travelplanner.core.database.ChatMessageEntity
import io.github.rupakc.travelplanner.core.database.ChatThreadEntity
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.network.sse.ChatStreamClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val streamClient: ChatStreamClient,
    private val chatDao: ChatDao,
    private val sessionStore: SessionStore,
    private val clock: AppClock,
) : ChatRepository {
    override fun send(request: ChatRequest): Flow<ChatEvent> = streamClient.stream(request)

    override val threads: Flow<List<ChatThread>> = sessionStore.session.flatMapLatest { session ->
        if (session == null) {
            flowOf(emptyList())
        } else {
            chatDao.observeThreads(session.user.username).map { list -> list.map { ChatThread(it.id, it.title, it.createdAtMillis, it.updatedAtMillis) } }
        }
    }

    override fun messages(threadId: Long): Flow<List<StoredChatMessage>> =
        chatDao.observeMessages(threadId).map { list -> list.map { it.toModel() } }

    override suspend fun createThread(title: String): Long {
        val now = clock.now().toEpochMilli()
        val username = sessionStore.current()?.user?.username.orEmpty()
        return chatDao.insertThread(ChatThreadEntity(username = username, title = title, createdAtMillis = now, updatedAtMillis = now))
    }

    override suspend fun renameThread(threadId: Long, title: String) = chatDao.renameThread(threadId, title, clock.now().toEpochMilli())

    override suspend fun deleteThread(threadId: Long) = chatDao.deleteThread(threadId)

    override suspend fun appendMessage(threadId: Long, role: String, content: String, extrasJson: String?): Long {
        val now = clock.now().toEpochMilli()
        chatDao.touch(threadId, now)
        return chatDao.insertMessage(
            ChatMessageEntity(threadId = threadId, role = role, content = content, extrasJson = extrasJson, createdAtMillis = now),
        )
    }

    override suspend fun updateMessage(messageId: Long, content: String, extrasJson: String?) =
        chatDao.updateMessage(messageId, content, extrasJson)

    override suspend fun deleteMessage(messageId: Long) = chatDao.deleteMessage(messageId)

    override suspend fun sessionContext(threadId: Long): JsonObject =
        chatDao.thread(threadId)?.sessionContextJson
            ?.let { runCatching { TravelJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: EmptyJsonObject

    override suspend fun saveSessionContext(threadId: Long, context: JsonObject) =
        chatDao.updateSessionContext(threadId, context.toString(), clock.now().toEpochMilli())

    private fun ChatMessageEntity.toModel() = StoredChatMessage(id, threadId, role, content, extrasJson, createdAtMillis)
}
