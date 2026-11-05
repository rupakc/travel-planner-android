package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.PlansRepositoryImpl
import io.github.rupakc.travelplanner.core.database.CachedPlanEntity
import io.github.rupakc.travelplanner.core.database.PlanDao
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cached plan lookups are per user, and a 404 evicts the cached copy. */
class PlansRepositoryImplTest {
    /** In-memory [PlanDao] with the same query semantics. */
    private class MemoryPlanDao : PlanDao {
        val rows = MutableStateFlow(mapOf<Long, CachedPlanEntity>())

        override fun observeAll(username: String): Flow<List<CachedPlanEntity>> =
            rows.map { m -> m.values.filter { it.username == username }.sortedByDescending { it.updatedAt } }

        override fun observe(id: Long): Flow<CachedPlanEntity?> = rows.map { it[id] }

        override fun observeFor(id: Long, username: String): Flow<CachedPlanEntity?> = rows.map { m -> m[id]?.takeIf { it.username == username } }

        override suspend fun get(id: Long): CachedPlanEntity? = rows.value[id]

        override suspend fun upsert(plan: CachedPlanEntity) = rows.update { it + (plan.id to plan) }

        override suspend fun delete(id: Long) = rows.update { it - id }

        override suspend fun deleteAllFor(username: String) = rows.update { m -> m.filterValues { it.username != username } }

        override suspend fun clear() = rows.update { emptyMap() }
    }

    private val server = MockWebServer()
    private val dao = MemoryPlanDao()
    private val sessions = FakeSessionStore(Session("token", TestUser))

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun repository(): PlansRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), sessions, TestAppDispatchers(Dispatchers.IO))
        return PlansRepositoryImpl(net.api(), dao, sessions)
    }

    private fun cached(id: Long, username: String) =
        CachedPlanEntity(id, username, "P$id", """{"id":$id,"username":"$username","name":"P$id"}""", "2026-11-01 00:00:00")

    @Test
    fun `another account's cached plan is not visible`() =
        runTest {
            dao.upsert(cached(1, "bob"))
            dao.upsert(cached(2, TestUser.username))
            val repo = repository()
            assertNull(repo.plan(1).first())
            assertEquals("P2", repo.plan(2).first()?.name)
            sessions.state.value = null
            assertNull(repo.plan(2).first())
        }

    @Test
    fun `a 404 evicts the cached plan`() =
        runTest {
            dao.upsert(cached(2, TestUser.username))
            server.enqueue(MockResponse.Builder().code(404).body("""{"detail":"Plan not found"}""").build())
            val result = repository().get(2)
            assertTrue((result as AppResult.Failure).error is AppError.NotFound)
            assertNull(dao.get(2))
        }

    @Test
    fun `a network failure keeps the cached plan`() =
        runTest {
            dao.upsert(cached(2, TestUser.username))
            server.enqueue(MockResponse.Builder().code(500).body("""{"detail":"boom"}""").build())
            repository().get(2)
            assertEquals("P2", dao.get(2)?.name)
        }
}
