package io.github.rupakc.travelplanner.core.database

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TravelDatabaseTest {
    private lateinit var db: TravelDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TravelDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun `plans are per user and newest first`() =
        runTest {
            val dao = db.planDao()
            dao.replaceAll(
                "alice",
                listOf(CachedPlanEntity(1, "alice", "Old", "{}", "2026-01-01 00:00:00"), CachedPlanEntity(2, "alice", "New", "{}", "2026-02-01 00:00:00")),
            )
            dao.upsert(CachedPlanEntity(3, "bob", "Bob", "{}", "2026-03-01 00:00:00"))
            assertEquals(listOf("New", "Old"), dao.observeAll("alice").first().map { it.name })
            dao.delete(2)
            assertEquals(listOf("Old"), dao.observeAll("alice").first().map { it.name })
        }

    @Test
    fun `deleting a chat thread cascades to its messages`() =
        runTest {
            val dao = db.chatDao()
            val thread = dao.insertThread(ChatThreadEntity(username = "alice", title = "Tokyo", createdAtMillis = 1, updatedAtMillis = 1))
            dao.insertMessage(ChatMessageEntity(threadId = thread, role = "user", content = "Hi", createdAtMillis = 2))
            assertEquals(1, dao.messages(thread).size)
            dao.deleteThread(thread)
            assertEquals(0, dao.messages(thread).size)
        }

    @Test
    fun `chat text search finds the user's threads by message content`() =
        runTest {
            val dao = db.chatDao()
            val japan = dao.insertThread(ChatThreadEntity(username = "alice", title = "Plan 5 days in Japan", createdAtMillis = 1, updatedAtMillis = 1))
            val lisbon = dao.insertThread(ChatThreadEntity(username = "alice", title = "Lisbon", createdAtMillis = 1, updatedAtMillis = 1))
            val bobs = dao.insertThread(ChatThreadEntity(username = "bob", title = "Kyoto", createdAtMillis = 1, updatedAtMillis = 1))
            dao.insertMessage(ChatMessageEntity(threadId = japan, role = "assistant", content = "Day 3: Kyoto temples", createdAtMillis = 2))
            dao.insertMessage(ChatMessageEntity(threadId = japan, role = "user", content = "More kyoto food?", createdAtMillis = 3))
            dao.insertMessage(ChatMessageEntity(threadId = lisbon, role = "user", content = "100% sure? Tram_28", createdAtMillis = 2))
            dao.insertMessage(ChatMessageEntity(threadId = bobs, role = "user", content = "Kyoto", createdAtMillis = 2))
            assertEquals(listOf(japan), dao.observeThreadIdsWithText("alice", "%kyoto%", 10).first())
            assertEquals(listOf(lisbon), dao.observeThreadIdsWithText("alice", "%100\\%%", 10).first())
            assertEquals(emptyList<Long>(), dao.observeThreadIdsWithText("alice", "%Tram\\_29%", 10).first())
        }

    @Test
    fun `seen notifications dedupe and the draft row is single`() =
        runTest {
            db.seenNotificationDao().markSeen(listOf(SeenNotificationEntity(5, 10), SeenNotificationEntity(5, 11)))
            assertEquals(listOf(5L), db.seenNotificationDao().seenAmong(listOf(5, 6)))
            db.planDraftDao().save(PlanDraftEntity(json = "{\"a\":1}"))
            db.planDraftDao().save(PlanDraftEntity(json = "{\"a\":2}"))
            assertEquals("{\"a\":2}", db.planDraftDao().get()?.json)
            db.planDraftDao().clear()
            assertNull(db.planDraftDao().get())
        }
}
