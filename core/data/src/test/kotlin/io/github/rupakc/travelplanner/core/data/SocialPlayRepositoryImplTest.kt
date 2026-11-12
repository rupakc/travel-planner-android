package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.PlayRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.SocialRepositoryImpl
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardWeek
import io.github.rupakc.travelplanner.core.model.play.QuizMode
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.core.model.social.FeedScope
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import io.github.rupakc.travelplanner.core.model.social.ProfileUpdate
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.core.model.social.ReportTarget
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.retryAfterSeconds
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paths, bodies and error codes of the social and play routes against a MockWebServer. */
class SocialPlayRepositoryImplTest {
    private val server = MockWebServer().apply { start() }
    private val net = TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO))
    private val social = SocialRepositoryImpl(net.api(), FakeAppLanguage("de"))
    private val play = PlayRepositoryImpl(net.api(), FakeAppLanguage("de"))

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    private fun next() = server.takeRequest().let { "${it.method} ${it.target}" to it.body?.utf8().orEmpty() }

    @Test
    fun `profile save sends only the set fields and maps handle errors`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"handle":"ana","visibility":"public"}""")
            val ok = social.saveProfile(ProfileUpdate(handle = "ana", visibility = "public"))
            assertEquals("ana", (ok as AppResult.Success).value.handle)
            assertEquals("PUT /api/social/profile" to """{"handle":"ana","visibility":"public"}""", next())

            respond(409, """{"detail":"Handle taken","error_code":"handle_taken"}""")
            assertEquals(SocialErrorCode.HANDLE_TAKEN, (social.saveProfile(ProfileUpdate(handle = "x")) as AppResult.Failure).error.socialErrorCode())
            next()
            respond(422, """{"detail":"Wait","error_code":"handle_rate_limited","retry_after":3600}""")
            val limited = (social.saveProfile(ProfileUpdate(handle = "y")) as AppResult.Failure).error
            assertEquals(SocialErrorCode.HANDLE_RATE_LIMITED, limited.socialErrorCode())
            assertEquals(3600L, limited.retryAfterSeconds())
        }

    @Test
    fun `follow, unfollow and block use the contract paths`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"state":"pending"}""")
            assertTrue(social.follow("bob") is AppResult.Success)
            assertEquals("PUT /api/social/follow/bob" to "", next())
            respond(204)
            assertTrue(social.unfollow("bob") is AppResult.Success)
            assertEquals("DELETE /api/social/follow/bob", next().first)
            respond(204)
            social.block("bob")
            assertEquals("PUT /api/social/block/bob", next().first)
            respond(204)
            social.acceptRequest("bob")
            assertEquals("POST /api/social/follow-requests/bob/accept", next().first)
            respond(404, """{"detail":"No","error_code":"profile_not_found"}""")
            assertEquals(SocialErrorCode.PROFILE_NOT_FOUND, (social.follow("zed") as AppResult.Failure).error.socialErrorCode())
        }

    @Test
    fun `publish sends the language, feed sends scope and cursor`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"id":5,"kind":"plan","title":"T","author":{"handle":"a"},"snapshot":{},"status":"held"}""")
            val idea = (social.publishIdea(3, IdeaKind.PLAN, "T", "C") as AppResult.Success).value
            assertEquals("held", idea.status)
            assertEquals("POST /api/social/ideas" to """{"plan_id":3,"kind":"plan","title":"T","caption":"C","locale":"de"}""", next())
            respond(200, """{"ideas":[],"next":null}""")
            social.feed(FeedScope.FOR_YOU, cursor = "o20", limit = 10)
            assertEquals("GET /api/social/feed?scope=for_you&cursor=o20&limit=10", next().first)
            assertTrue(social.publishIdea(3, IdeaKind.UNKNOWN, "T", "C") is AppResult.Failure)
            assertEquals(0, server.requestCount - 2)
        }

    @Test
    fun `like toggles, save returns the plan id, helpful and report`() =
        runTest(timeout = TIMEOUT) {
            respond(204)
            social.setLiked(5, true)
            assertEquals("PUT /api/social/ideas/5/like", next().first)
            respond(204)
            social.setLiked(5, false)
            assertEquals("DELETE /api/social/ideas/5/like", next().first)
            respond(200, """{"plan_id":42}""")
            assertEquals(42L, (social.saveIdea(5) as AppResult.Success).value)
            assertEquals("POST /api/social/ideas/5/save", next().first)
            respond(204)
            social.setHelpful(8, true)
            assertEquals("PUT /api/social/comments/8/helpful", next().first)
            respond(204)
            social.report(ReportTarget.COMMENT, "8", ReportReason.SPAM, " ")
            assertEquals("POST /api/social/reports" to """{"target":"comment","id":"8","reason":"spam"}""", next())
        }

    @Test
    fun `quiz start sends the pack only for pack mode`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"session_id":"s1","mode":"daily","questions":[]}""")
            play.startQuiz(QuizMode.DAILY, QuizPack.FLAGS)
            assertEquals("POST /api/play/quiz" to """{"mode":"daily","locale":"de"}""", next())
            respond(200, """{"session_id":"s2","mode":"pack","questions":[]}""")
            play.startQuiz(QuizMode.PACK, QuizPack.CAPITALS)
            assertEquals("""{"mode":"pack","pack":"capitals","locale":"de"}""", next().second)
            respond(409, """{"detail":"Done","error_code":"already_done"}""")
            assertEquals(SocialErrorCode.ALREADY_DONE, (play.startQuiz(QuizMode.DAILY) as AppResult.Failure).error.socialErrorCode())
        }

    @Test
    fun `answer, h2h, board and hidden-game error`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"correct":true,"correct_choice":"b","points":120,"elapsed_ms":3000,"done":false}""")
            val a = (play.answer("s1", 0, "b") as AppResult.Success).value
            assertEquals(120, a.points)
            assertEquals("POST /api/play/quiz/s1/answer" to """{"index":0,"choice":"b"}""", next())
            respond(200, """{"id":"m1","session_id":"s3","questions":[]}""")
            play.createH2h(" @Bob ", QuizPack.FLAGS)
            assertEquals("POST /api/play/h2h" to """{"handle":"bob","pack":"flags"}""", next())
            respond(204)
            play.declineH2h("m1")
            assertEquals("POST /api/play/h2h/m1/decline", next().first)
            respond(200, """{"week":"2026-W46","rows":[],"you":null}""")
            play.leaderboard(BoardScope.FRIENDS, BoardWeek.PREVIOUS)
            assertEquals("GET /api/play/leaderboard?scope=friends&week=previous", next().first)
            respond(403, """{"detail":"Hidden","error_code":"game_hidden"}""")
            assertEquals(SocialErrorCode.GAME_HIDDEN, (play.state() as AppResult.Failure).error.socialErrorCode())
        }

    private companion object {
        val TIMEOUT = kotlin.time.Duration.parse("30s")
    }
}
