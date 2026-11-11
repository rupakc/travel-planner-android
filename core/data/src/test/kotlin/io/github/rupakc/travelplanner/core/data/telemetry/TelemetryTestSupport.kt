package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.datastore.StoredSession
import io.github.rupakc.travelplanner.core.datastore.TelemetryPrefsStore
import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal const val NOW_MS = 1_800_000_000_000L

internal class FixedClock(private val ms: Long = NOW_MS) : AppClock {
    override fun now(): Instant = Instant.ofEpochMilli(ms)

    override fun zone(): ZoneId = ZoneId.of("UTC")
}

internal class FixedIdPrefs(private val id: String = "11111111-1111-4111-8111-111111111111") : TelemetryPrefsStore {
    override suspend fun anonId(): String = id

    override val consent: Flow<ConsentChoice?> = flowOf(null)

    override suspend fun setConsent(choice: ConsentChoice) = Unit

    override suspend fun session(): StoredSession? = null

    override suspend fun setSession(session: StoredSession) = Unit
}

internal fun event(
    name: String = "ui_tap",
    ts: Long = NOW_MS,
    sid: String = "s1",
    screen: String = "/search",
    props: Map<String, Any> = emptyMap(),
) = QueuedEvent(event = name, ts = ts, props = props, screen = screen, sessionId = sid)

internal class RecordingSender(var outcome: () -> SendOutcome = { SendOutcome.Sent }) : TelemetrySender {
    val sent = mutableListOf<Pair<String, List<QueuedEvent>>>()

    override suspend fun send(
        sessionId: String,
        events: List<QueuedEvent>,
    ): SendOutcome {
        sent += sessionId to events
        return outcome()
    }
}
