package io.github.rupakc.travelplanner.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Explicit schema migrations. Every table of an older version is kept; migrations only add. */
object TravelMigrations {
    /** Adds the offline pack and outbox tables (Phase 2). */
    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `offline_pack` (`key` TEXT NOT NULL, `userId` TEXT NOT NULL, " +
                        "`tripId` INTEGER NOT NULL, `json` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_pack_userId` ON `offline_pack` (`userId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, " +
                        "`method` TEXT NOT NULL, `path` TEXT NOT NULL, `body` TEXT, `key` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_userId_key` ON `outbox` (`userId`, `key`)")
            }
        }
}
