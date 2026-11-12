package io.github.rupakc.travelplanner.core.data.offline

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.database.TravelDatabase
import java.time.Instant
import java.time.ZoneOffset

internal fun memoryDb(): TravelDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), TravelDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal val fixedClock =
    object : AppClock {
        override fun now(): Instant = Instant.parse("2027-04-01T10:00:00Z")

        override fun zone() = ZoneOffset.UTC
    }

internal class RecordingScheduler : OutboxFlushScheduler {
    var scheduled = 0
    var cancelled = 0

    override fun schedule() {
        scheduled++
    }

    override fun cancelPeriodic() {
        cancelled++
    }
}
