package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.SearchResultsReducer
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ChatRepository
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.data.repository.StoredChatMessage
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.search.ActivityFilters
import io.github.rupakc.travelplanner.core.model.search.FlightFilters
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.LayoverPlan
import io.github.rupakc.travelplanner.core.model.search.LayoverRequest
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.testing.Fixtures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/** Default "not configured" failure for fakes: set the result you need in the test. */
val NotStubbed: AppResult.Failure = AppResult.Failure(AppError.Unknown("not stubbed"))

val TestUser = User(username = "alice", isAdmin = false, requiresPasswordChange = false, email = null)

/** Fake auth: [sessionFlow] drives [session]; login succeeds with [loginResult] and stores a session. */
class FakeAuthRepository(initial: Session? = null) : AuthRepository {
    val sessionFlow = MutableStateFlow(initial)
    override val session: Flow<Session?> = sessionFlow
    var loginResult: AppResult<User> = AppResult.Success(TestUser)
    var changePasswordResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshResult: AppResult<User> = AppResult.Success(TestUser)
    var emailResult: AppResult<String?> = AppResult.Success(null)
    var logoutCalls = 0

    override suspend fun login(username: String, password: String): AppResult<User> =
        loginResult.also { if (it is AppResult.Success) sessionFlow.value = Session("token", it.value) }

    override suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit> =
        changePasswordResult.also {
            if (it is AppResult.Success) {
                sessionFlow.value =
                    sessionFlow.value?.let { s -> s.copy(user = s.user.copy(requiresPasswordChange = false)) }
            }
        }

    override suspend fun refreshUser(): AppResult<User> = refreshResult

    override suspend fun updateEmail(email: String): AppResult<String?> = emailResult

    override suspend fun logout() {
        logoutCalls++
        sessionFlow.value = null
    }
}

/**
 * Fake search: [events] (default: the recorded `search_full.sse`) are folded with the
 * real [SearchResultsReducer]; set [failure] to fail before streaming.
 */
class FakeSearchRepository(var events: List<SearchEvent> = Fixtures.searchEvents(Fixtures.SEARCH_FULL)) : SearchRepository {
    private val state = MutableStateFlow(SearchResultsState())
    override val current: StateFlow<SearchResultsState> = state.asStateFlow()
    var failure: AppError? = null
    val started = mutableListOf<TravelSearchRequest>()
    var sectionResult: AppResult<SectionData> = NotStubbed
    var flightsResult: AppResult<FlightsData> = NotStubbed
    var hotelsResult: AppResult<HotelsData> = NotStubbed
    var activitiesResult: AppResult<ActivitiesData> = NotStubbed
    var stressTestResult: AppResult<StressTestData> = NotStubbed
    var layoverResult: AppResult<LayoverPlan> = NotStubbed
    var syncResult: AppResult<JsonObject> = AppResult.Success(EmptyJsonObject)
    var airports: List<Airport> = emptyList()
    var nationalities: List<Nationality> = emptyList()

    /** Folds all [events] synchronously into [current]. */
    override fun start(request: TravelSearchRequest) {
        started += request
        var folded = SearchResultsState(request = request, phase = StreamPhase.STREAMING)
        val error = failure
        folded =
            if (error != null) {
                folded.copy(phase = StreamPhase.FAILED, error = error)
            } else {
                events.fold(folded, SearchResultsReducer::reduce).let {
                    if (it.phase == StreamPhase.DONE) it else SearchResultsReducer.finish(it, StreamPhase.INTERRUPTED)
                }
            }
        state.value = folded
    }

    override fun cancel() {
        state.value = state.value.copy(phase = StreamPhase.CANCELLED)
    }

    override fun clear() {
        state.value = SearchResultsState()
    }

    override fun results(request: TravelSearchRequest): Flow<SearchResultsState> =
        flow {
            var folded = SearchResultsState(request = request, phase = StreamPhase.STREAMING)
            emit(folded)
            failure?.let {
                emit(folded.copy(phase = StreamPhase.FAILED, error = it))
                return@flow
            }
            for (event in events) {
                folded = SearchResultsReducer.reduce(folded, event)
                emit(folded)
            }
            if (folded.phase != StreamPhase.DONE) emit(SearchResultsReducer.finish(folded, StreamPhase.INTERRUPTED))
        }

    override fun events(request: TravelSearchRequest): Flow<SearchEvent> =
        flow {
            failure?.let { throw AppException(it) }
            events.forEach { emit(it) }
        }

    override suspend fun section(section: SectionId, request: TravelSearchRequest) = sectionResult

    override suspend fun flightsFiltered(request: TravelSearchRequest, filters: FlightFilters) = flightsResult

    override suspend fun hotelsFiltered(request: TravelSearchRequest, filters: HotelFilters) = hotelsResult

    override suspend fun activitiesFiltered(request: TravelSearchRequest, filters: ActivityFilters) = activitiesResult

    override suspend fun stressTest(payload: StressTestPayload) = stressTestResult

    override suspend fun layover(request: LayoverRequest) = layoverResult

    override suspend fun searchSync(request: TravelSearchRequest) = syncResult

    override suspend fun airports(query: String, limit: Int): AppResult<List<Airport>> = AppResult.Success(airports.take(limit))

    override suspend fun nationalities(query: String, limit: Int): AppResult<List<Nationality>> =
        AppResult.Success(
            nationalities.take(limit),
        )
}

/** Fake chat: [replies] (default `chat_qa.sse`) is the stream for every send; history is in memory. */
class FakeChatRepository(var replies: List<ChatEvent> = Fixtures.chatEvents(Fixtures.CHAT_QA)) : ChatRepository {
    val sent = mutableListOf<ChatRequest>()
    var failure: AppError? = null
    private val threadState = MutableStateFlow<List<ChatThread>>(emptyList())
    private val messageState = MutableStateFlow<List<StoredChatMessage>>(emptyList())
    private val contexts = mutableMapOf<Long, JsonObject>()
    private var nextId = 1L

    override fun send(request: ChatRequest): Flow<ChatEvent> {
        sent += request
        failure?.let { error -> return flow { throw AppException(error) } }
        return replies.asFlow()
    }

    override val threads: Flow<List<ChatThread>> = threadState

    override fun messages(threadId: Long): Flow<List<StoredChatMessage>> =
        messageState.map { all ->
            all.filter { it.threadId == threadId }
        }

    override fun threadIdsMatching(query: String): Flow<Set<Long>> =
        messageState.map { all ->
            if (query.isBlank()) emptySet() else all.filter { it.content.contains(query.trim(), ignoreCase = true) }.map { it.threadId }.toSet()
        }

    override suspend fun createThread(title: String): Long {
        val id = nextId++
        threadState.value = listOf(ChatThread(id, title, 0, 0)) + threadState.value
        return id
    }

    override suspend fun renameThread(threadId: Long, title: String) {
        threadState.value = threadState.value.map { if (it.id == threadId) it.copy(title = title) else it }
    }

    override suspend fun deleteThread(threadId: Long) {
        threadState.value = threadState.value.filterNot { it.id == threadId }
        messageState.value = messageState.value.filterNot { it.threadId == threadId }
    }

    override suspend fun appendMessage(threadId: Long, role: String, content: String, extrasJson: String?): Long {
        val id = nextId++
        messageState.value = messageState.value + StoredChatMessage(id, threadId, role, content, extrasJson, 0)
        return id
    }

    override suspend fun updateMessage(messageId: Long, content: String, extrasJson: String?) {
        messageState.value = messageState.value.map { if (it.id == messageId) it.copy(content = content, extrasJson = extrasJson) else it }
    }

    override suspend fun deleteMessage(messageId: Long) {
        messageState.value = messageState.value.filterNot { it.id == messageId }
    }

    override suspend fun sessionContext(threadId: Long): JsonObject = contexts[threadId] ?: EmptyJsonObject

    override suspend fun saveSessionContext(threadId: Long, context: JsonObject) {
        contexts[threadId] = context
    }
}
