package io.github.rupakc.travelplanner.feature.search

import android.net.Uri
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.importlink.ImportImage
import io.github.rupakc.travelplanner.feature.search.di.FeatureVisibility
import io.github.rupakc.travelplanner.feature.search.importlink.PreparedShot
import io.github.rupakc.travelplanner.feature.search.importlink.ScreenshotReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** "Today" for every search test. */
val TestToday: LocalDate = LocalDate.of(2026, 11, 5)

/** A clock fixed at noon UTC on [TestToday]. */
object FixedClock : AppClock {
    override fun now(): Instant = TestToday.atTime(12, 0).toInstant(ZoneOffset.UTC)

    override fun zone(): ZoneId = ZoneOffset.UTC
}

/** Feature visibility driven by the test; null = no catalogue (all modes). */
class FakeFeatureVisibility(initial: Set<FeatureId>? = null) : FeatureVisibility {
    val state = MutableStateFlow(initial)

    override fun visible(): Flow<Set<FeatureId>?> = state
}

/** Screenshot reader that "prepares" every uri instantly. */
class FakeScreenshotReader : ScreenshotReader {
    override suspend fun read(uri: Uri, id: Long): Result<PreparedShot> =
        Result.success(PreparedShot(id, "shot$id.png", ImportImage("image/webp", "AAAA"), null))
}
