package io.github.rupakc.travelplanner.core.data.auth

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.SignupRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.model.auth.PasswordResetResponse
import io.github.rupakc.travelplanner.core.model.auth.SessionDto
import io.github.rupakc.travelplanner.core.model.auth.SignupRequest
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.auth.VerificationSentDto
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailRequest
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailResponse
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.SignupApi
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupRepositoryImplTest {
    private class Cleaner : SessionCleaner {
        var calls = 0

        override suspend fun clearUserData() {
            calls++
        }
    }

    private val api = mockk<SignupApi>()
    private val store = FakeSessionStore()
    private val cleaner = Cleaner()
    private val repo =
        SignupRepositoryImpl(api, store, BaseUrlProvider(FakeSettingsStore(), AppBuildInfo("http://10.0.2.2:8001", "local", "t", false, true)), cleaner)
    private val session = SessionDto("jwt", "bearer", 3600, "rt_1", User(username = "jane", id = "u1"))

    @Test
    fun `signup always sends client android`() =
        runTest {
            val sent = slot<SignupRequest>()
            coEvery { api.signup(capture(sent)) } returns VerificationSentDto(status = "verification_sent")
            val result = repo.signup(SignupRequest("jane", "j@x.com", "correct horse"))
            assertTrue(result is AppResult.Success)
            assertEquals("android", sent.captured.client)
        }

    @Test
    fun `verify with a session stores token, refresh token, expiry and user id`() =
        runTest {
            val sent = slot<VerifyEmailRequest>()
            coEvery { api.verifyEmail(capture(sent)) } returns VerifyEmailResponse(status = "verified", session = session)
            repo.verifyEmail(" tok ")
            assertEquals("tok", sent.captured.token)
            assertEquals("android", sent.captured.client)
            val saved = store.current()!!
            assertEquals("jwt", saved.token)
            assertEquals("rt_1", saved.refreshToken)
            assertEquals("u1", saved.userId)
            assertNotNull(saved.expiresAtEpochMs)
            assertEquals("http://10.0.2.2:8001", saved.baseUrl)
        }

    @Test
    fun `verify without a session stores nothing`() =
        runTest {
            coEvery { api.verifyEmail(any()) } returns VerifyEmailResponse(status = "verified", session = null)
            repo.verifyEmail("tok")
            assertNull(store.current())
        }

    @Test
    fun `reset confirm stores the session and wipes a previous user's data`() =
        runTest {
            store.lastUser = "someone_else"
            coEvery { api.confirmPasswordReset(any()) } returns PasswordResetResponse(status = "password_reset", session = session)
            repo.confirmPasswordReset("tok", "new password 1")
            assertEquals("jane", store.current()!!.user.username)
            assertEquals(1, cleaner.calls)
        }

    @Test
    fun `reset request trims the email`() =
        runTest {
            coEvery { api.requestPasswordReset(any()) } returns io.github.rupakc.travelplanner.core.model.auth.StatusResponse("reset_sent")
            repo.requestPasswordReset(" a@b.co ", "en")
            coVerify { api.requestPasswordReset(match { it.email == "a@b.co" && it.locale == "en" }) }
        }
}
