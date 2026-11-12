package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.ShareCardStoreImpl
import io.github.rupakc.travelplanner.core.data.impl.SharesRepositoryImpl
import io.github.rupakc.travelplanner.core.model.share.ShareDates
import io.github.rupakc.travelplanner.core.model.share.ShareErrorCode
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import io.github.rupakc.travelplanner.core.model.share.shareErrorCode
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paths, bodies and error codes of the share routes, and the card download rules, against a MockWebServer. */
class SharesRepositoryImplTest {
    private val server = MockWebServer().apply { start() }
    private val repo =
        SharesRepositoryImpl(
            TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO)).api(),
            FakeAppLanguage("fr"),
        )
    private val dir = File(System.getProperty("java.io.tmpdir"), "share-cards-test-${System.nanoTime()}")

    @After fun tearDown() {
        server.close()
        dir.deleteRecursively()
    }

    private fun respond(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test
    fun `create posts the options and the language`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"token":"abc","kind":"plan","url":"https://h/share/abc","plan_id":4}""")
            val result = repo.create(ShareKind.PLAN, 4, ShareOptions.of(ShareDates.MONTH, showOrigin = true, showName = false))
            assertEquals("abc", (result as AppResult.Success).value.token)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/shares", req.target)
            assertEquals(
                """{"kind":"plan","plan_id":4,"options":{"dates":"month","show_origin":true,"show_name":false},"locale":"fr"}""",
                req.body?.utf8(),
            )
        }

    @Test
    fun `savings sends no plan id`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"token":"s","kind":"savings","url":"u"}""")
            repo.create(ShareKind.SAVINGS, 99, ShareOptions.PRIVATE)
            assertFalse(server.takeRequest().body!!.utf8().contains("plan_id"))
        }

    @Test
    fun `an unknown kind is never sent`() =
        runTest(timeout = TIMEOUT) {
            assertTrue(repo.create(ShareKind.UNKNOWN, null, ShareOptions.PRIVATE) is AppResult.Failure)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `errors keep their machine code`() =
        runTest(timeout = TIMEOUT) {
            respond(404, """{"detail":"Plan not found","error_code":"plan_not_found"}""")
            assertEquals(ShareErrorCode.PLAN_NOT_FOUND, (repo.create(ShareKind.PLAN, 1, ShareOptions.PRIVATE) as AppResult.Failure).error.shareErrorCode())
            server.enqueue(
                MockResponse.Builder().code(429).addHeader("Retry-After", "30").addHeader("Content-Type", "application/json")
                    .body("""{"detail":"Too many requests, try later"}""").build(),
            )
            val error = (repo.create(ShareKind.PLAN, 1, ShareOptions.PRIVATE) as AppResult.Failure).error
            assertTrue(error is AppError.RateLimited)
            assertEquals("Too many requests, try later", error.message)
        }

    @Test
    fun `list and revoke`() =
        runTest(timeout = TIMEOUT) {
            respond(200, """{"shares":[{"token":"a","kind":"plan","title":"T","url":"u","plan_id":1}],"legacy_plan_shares":[]}""")
            assertEquals(1, (repo.list() as AppResult.Success).value.shares.size)
            assertEquals("/api/shares", server.takeRequest().target)

            server.enqueue(MockResponse.Builder().code(204).build())
            assertTrue(repo.revoke("a") is AppResult.Success)
            val del = server.takeRequest()
            assertEquals("DELETE", del.method)
            assertEquals("/api/shares/a", del.target)

            respond(404, """{"detail":"Share not found","error_code":"share_not_found"}""")
            assertEquals(ShareErrorCode.SHARE_NOT_FOUND, (repo.revoke("zz") as AppResult.Failure).error.shareErrorCode())
        }

    private fun store() = ShareCardStoreImpl(OkHttpClient(), TestAppDispatchers(Dispatchers.IO), dir)

    private fun png(code: Int = 200, type: String = "image/png", bytes: ByteArray = ByteArray(PNG_BYTES) { 1 }) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", type).body(Buffer().write(bytes)).build())

    @Test
    fun `card is saved to the cache folder with no token sent and replaced by the next one`() =
        runTest(timeout = TIMEOUT) {
            png()
            val file = (store().download(server.url("/api/share/abc/card.png?size=story").toString()) as AppResult.Success).value
            assertEquals(PNG_BYTES.toLong(), file.length())
            assertEquals(dir, file.parentFile)
            assertEquals(null, server.takeRequest().headers["Authorization"])
            png(bytes = ByteArray(3) { 2 })
            val second = (store().download(server.url("/x.png").toString()) as AppResult.Success).value
            assertEquals(3L, second.length())
            assertEquals(1, dir.listFiles()!!.size)
        }

    @Test
    fun `card rules reject a non png a failure status and a foreign scheme`() =
        runTest(timeout = TIMEOUT) {
            png(type = "text/html")
            assertTrue(store().download(server.url("/a.png").toString()) is AppResult.Failure)
            png(code = 503)
            assertTrue(store().download(server.url("/a.png").toString()) is AppResult.Failure)
            assertTrue(store().download("file:///etc/passwd") is AppResult.Failure)
            assertTrue(store().download("not a url") is AppResult.Failure)
            assertEquals(2, server.requestCount)
            assertFalse(File(dir, ShareCardStoreImpl.FILE_NAME).exists())
        }

    @Test
    fun `an unreachable server is a network failure and clear removes the card`() =
        runTest(timeout = TIMEOUT) {
            png()
            val s = store()
            s.download(server.url("/a.png").toString())
            s.clear()
            assertTrue(dir.listFiles().isNullOrEmpty())
            val url = server.url("/a.png").toString()
            server.close()
            assertTrue((s.download(url) as AppResult.Failure).error is AppError.Network)
        }

    private companion object {
        val TIMEOUT = kotlin.time.Duration.parse("30s")
        const val PNG_BYTES = 2048
    }
}
