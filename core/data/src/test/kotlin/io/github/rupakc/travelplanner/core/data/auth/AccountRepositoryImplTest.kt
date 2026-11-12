package io.github.rupakc.travelplanner.core.data.auth

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.AccountRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.auth.DeleteAccountRequest
import io.github.rupakc.travelplanner.core.model.auth.SessionInfo
import io.github.rupakc.travelplanner.core.model.auth.SessionsList
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.AccountApi
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response

class AccountRepositoryImplTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Cleaner : SessionCleaner {
        var calls = 0

        override suspend fun clearUserData() {
            calls++
        }
    }

    private val api = mockk<AccountApi>()
    private val store = FakeSessionStore(Session("jwt", User(username = "jane"), refreshToken = "rt_1"))
    private val cleaner = Cleaner()
    private val auth = FakeAuthRepository()
    private val repo = AccountRepositoryImpl(api, store, cleaner, auth, FakeGameEvents())

    @Test fun `sessions sends the refresh token header and unwraps the list`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.sessions("rt_1") } returns SessionsList(listOf(SessionInfo("a", current = true)))
            val r = repo.sessions() as AppResult.Success
            assertEquals("a", r.value.single().id)
        }

    @Test fun `sessions 404 is a NotFound failure`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.sessions(any()) } throws HttpException(Response.error<SessionsList>(404, "{}".toResponseBody()))
            val r = repo.sessions() as AppResult.Failure
            assertTrue(r.error is AppError.NotFound)
        }

    @Test fun `export is streamed into the target file`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.export() } returns """{"format":"voyager-export"}""".toResponseBody()
            val target = tmp.newFolder("cache").resolve("account-exports/voyager-export.json")
            val r = repo.exportTo(target) as AppResult.Success
            assertEquals(target, r.value)
            assertEquals("""{"format":"voyager-export"}""", target.readText())
        }

    @Test fun `a failed export leaves no file`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.export() } throws HttpException(Response.error<Any>(429, "{}".toResponseBody()))
            val target = tmp.newFolder("c2").resolve("e.json")
            val r = repo.exportTo(target) as AppResult.Failure
            assertTrue(r.error is AppError.RateLimited)
            assertTrue(!target.exists())
        }

    @Test fun `delete wipes the session and local data only on success`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.deleteAccount(any()) } throws HttpException(Response.error<Any>(403, "{}".toResponseBody()))
            repo.deleteAccount(DeleteAccountRequest(password = "x"))
            assertEquals(0, cleaner.calls)
            assertTrue(store.current() != null)
            coEvery { api.deleteAccount(any()) } returns Unit
            repo.deleteAccount(DeleteAccountRequest(password = "x"))
            assertEquals(1, cleaner.calls)
            assertEquals(null, store.current())
        }

    @Test fun `settings and patched settings drive the game visibility`() =
        runTest(timeout = 30.seconds) {
            val events = FakeGameEvents()
            val r = AccountRepositoryImpl(api, store, cleaner, auth, events)
            coEvery { api.settings() } returns AccountSettings(hideGame = true)
            r.settings()
            assertTrue(!events.visible.value)
            coEvery { api.patchSettings(any()) } returns AccountSettings(hideGame = false)
            r.patchSettings(kotlinx.serialization.json.JsonObject(emptyMap()))
            assertTrue(events.visible.value)
        }

    @Test fun `logout everywhere signs out locally after the call`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.logoutAll() } returns Unit
            repo.logoutAll()
            // Delegates to the auth repository, which unregisters the device first; a plain logout() would 401 on it.
            assertEquals(1, auth.logoutAllCalls)
            assertEquals(0, auth.logoutCalls)
        }
}
