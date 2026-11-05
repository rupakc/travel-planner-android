package io.github.rupakc.travelplanner.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Time source; inject it instead of calling `Instant.now()` so tests are deterministic. */
interface AppClock {
    fun now(): Instant

    fun zone(): ZoneId

    fun today(): LocalDate = LocalDate.ofInstant(now(), zone())
}

class SystemAppClock(private val clock: Clock = Clock.systemDefaultZone()) : AppClock {
    override fun now(): Instant = clock.instant()

    override fun zone(): ZoneId = clock.zone
}
