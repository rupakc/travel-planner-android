package io.github.rupakc.travelplanner.core.database

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TravelMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TravelDatabase::class.java)

    @Test
    fun `1 to 2 keeps the old tables and rows and adds the offline tables`() {
        val name = "migration-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO cached_plans (id, username, name, json, updatedAt) VALUES (7, 'alice', 'Tokyo', '{}', '2026-01-01 00:00:00')")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 2, true, TravelMigrations.MIGRATION_1_2)
        db.query("SELECT name FROM cached_plans WHERE id = 7").use {
            assertTrue(it.moveToFirst())
            assertEquals("Tokyo", it.getString(0))
        }
        for (table in listOf("chat_threads", "chat_messages", "recent_searches", "seen_notifications", "plan_draft", "offline_pack", "outbox")) {
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertTrue(table, it.getInt(0) >= 0)
            }
        }
        db.execSQL("INSERT INTO outbox (userId, method, path, body, key, createdAt) VALUES ('u', 'POST', 'p', NULL, 'k', 1)")
        db.close()
    }
}
