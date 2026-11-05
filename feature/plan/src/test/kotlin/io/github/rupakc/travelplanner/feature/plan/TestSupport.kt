package io.github.rupakc.travelplanner.feature.plan

import io.github.rupakc.travelplanner.core.common.AppClock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Fixed clock: 2026-11-05 12:00 UTC. */
internal class FixedClock(private val instant: Instant = Instant.parse("2026-11-05T12:00:00Z")) : AppClock {
    override fun now(): Instant = instant

    override fun zone(): ZoneId = ZoneOffset.UTC
}
