package io.github.rupakc.travelplanner.core.data.auth

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.OauthRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.domain.auth.Pkce
import io.github.rupakc.travelplanner.core.model.auth.LinkTicketResponse
import io.github.rupakc.travelplanner.core.model.auth.OauthCompleteRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthExchangeRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import io.github.rupakc.travelplanner.core.model.auth.SessionDto
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.game.GameAward
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.OauthApi
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OauthRepositoryImplTest {
    private class Cleaner : SessionCleaner {
        var calls = 0

        override suspend fun clearUserData() {
            calls++
        }
    }

    private val api = mockk<OauthApi>()
    private val store = FakeSessionStore()
    private val cleaner = Cleaner()
    private val games = FakeGameEvents()
    private val repo =
        OauthRepositoryImpl(
            api,
            store,
            BaseUrlProvider(FakeSettingsStore(), AppBuildInfo("http://10.0.2.2:8001", "local", "t", false, true)),
            cleaner,
            games,
        )
    private val session = SessionDto("jwt", "bearer", 3600, "rt_1", User(username = "jane", id = "u1"))

    private fun queryOf(url: String): Map<String, String> =
        url.substringAfter('?').split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    @Test
    fun `begin builds the start url with an S256 challenge and the app redirect`() =
        runTest(timeout = 30.seconds) {
            val url = (repo.begin("github") as AppResult.Success).value
            assertTrue(url.startsWith("http://10.0.2.2:8001/api/auth/oauth/github/start?"))
            val q = queryOf(url)
            assertEquals("android", q["client"])
            assertEquals("voyager://auth/callback", q["redirect_uri"])
            assertEquals(43, q["challenge"]!!.length)
            assertTrue(repo.hasPendingAttempt())
        }

    @Test
    fun `apple starts like the others and unknown providers are refused`() =
        runTest(timeout = 30.seconds) {
            assertTrue(repo.begin("apple") is AppResult.Success)
            repo.cancelPendingAttempt()
            assertTrue(repo.begin("../x") is AppResult.Failure)
            assertFalse(repo.hasPendingAttempt())
        }

    @Test
    fun `exchange without a pending attempt never reaches the server`() =
        runTest(timeout = 30.seconds) {
            assertTrue(repo.exchange("code") is AppResult.Failure)
            coVerify(exactly = 0) { api.exchange(any()) }
        }

    @Test
    fun `exchange sends the verifier whose challenge was started, stores the session and forwards the game delta`() =
        runTest(timeout = 30.seconds) {
            val challenge = queryOf((repo.begin("google") as AppResult.Success).value)["challenge"]
            val sent = slot<OauthExchangeRequest>()
            coEvery { api.exchange(capture(sent)) } returns
                OauthResult(status = "signed_in", session = session, game = GameDelta(xp = 50, awards = listOf(GameAward("signup", 50))))
            val result = repo.exchange("abc") as AppResult.Success
            assertEquals("signed_in", result.value.status)
            assertEquals("abc", sent.captured.code)
            assertEquals(challenge, Pkce.challenge(sent.captured.verifier!!))
            assertEquals("jwt", store.current()!!.token)
            assertEquals("rt_1", store.current()!!.refreshToken)
            assertEquals(50, games.published.single().xp)
            // Single use: the verifier is gone.
            assertFalse(repo.hasPendingAttempt())
            assertTrue(repo.exchange("abc") is AppResult.Failure)
        }

    @Test
    fun `needs_username stores no session`() =
        runTest(timeout = 30.seconds) {
            repo.begin("google")
            coEvery { api.exchange(any()) } returns OauthResult(status = "needs_username", signupToken = "t", suggestedUsername = "jane")
            repo.exchange("abc")
            assertNull(store.current())
        }

    @Test
    fun `a failed exchange still drops the verifier`() =
        runTest(timeout = 30.seconds) {
            repo.begin("google")
            coEvery { api.exchange(any()) } throws java.io.IOException("down")
            assertTrue(repo.exchange("abc") is AppResult.Failure)
            assertFalse(repo.hasPendingAttempt())
        }

    @Test
    fun `complete stores the session and wipes a previous user's data`() =
        runTest(timeout = 30.seconds) {
            store.lastUser = "someone_else"
            val sent = slot<OauthCompleteRequest>()
            coEvery { api.complete(capture(sent)) } returns OauthResult(status = "signed_in", session = session)
            repo.complete(OauthCompleteRequest("tok", "jane"))
            assertEquals("android", sent.captured.client)
            assertEquals("jane", store.current()!!.user.username)
            assertEquals(1, cleaner.calls)
        }

    @Test
    fun `link ticket start url is checked and gets the android PKCE parameters`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.linkTicket(any()) } returns LinkTicketResponse("/api/auth/oauth/github/start?link=T1&client=web")
            val url = ((repo.linkTicket("github") as AppResult.Success).value).startUrl!!
            val q = queryOf(url)
            assertTrue(url.startsWith("http://10.0.2.2:8001/api/auth/oauth/github/start?"))
            assertEquals("T1", q["link"])
            assertEquals("android", q["client"])
            assertNotNull(q["challenge"])
            assertEquals(1, url.split("client=").size - 1)
            assertTrue(repo.hasPendingAttempt())
        }

    @Test
    fun `an off-site link ticket url is refused`() =
        runTest(timeout = 30.seconds) {
            coEvery { api.linkTicket(any()) } returns LinkTicketResponse("https://evil.example/api/auth/oauth/github/start?link=T1")
            assertTrue(repo.linkTicket("github") is AppResult.Failure)
            assertFalse(repo.hasPendingAttempt())
        }
}
