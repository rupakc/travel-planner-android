package io.github.rupakc.travelplanner.core.data.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.datastore.TelemetryPrefsStore
import io.github.rupakc.travelplanner.core.domain.telemetry.ConsentRules
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Consent from the stored choice, else from the device zone read at call time (so a flight into the EU with
 * no stored choice turns collection off and shows the prompt; web `consent.js`).
 */
@Singleton
class ConsentRepositoryImpl(
    private val prefs: TelemetryPrefsStore,
    private val zone: () -> String,
    zoneChanges: Flow<Unit>,
    scope: CoroutineScope,
) : ConsentRepository {
    @Inject
    constructor(
        prefs: TelemetryPrefsStore,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : this(prefs, { ZoneId.systemDefault().id }, timeZoneChanges(context), scope)

    /** Emits once at start and on every zone change, so a zone read is never stale. */
    private val zoneTicks: Flow<Unit> = zoneChanges.onStart { emit(Unit) }

    private val reopened = MutableStateFlow(false)
    private val choiceFlow = MutableSharedFlow<ConsentChoice>(replay = 0, extraBufferCapacity = 16)

    private fun allowedFor(choice: ConsentChoice?): Boolean =
        if (choice != null) choice == ConsentChoice.GRANTED else !ConsentRules.required(zone())

    override val allowed: Flow<Boolean> = combine(prefs.consent, zoneTicks) { choice, _ -> allowedFor(choice) }

    override suspend fun allowedNow(): Boolean = allowedFor(prefs.consent.first())

    override val choices: Flow<ConsentChoice> = choiceFlow

    override val prompt: StateFlow<ConsentPromptState> =
        combine(prefs.consent, reopened, zoneTicks) { choice, reopen, _ ->
            when {
                reopen -> ConsentPromptState.Reopened(allowedFor(choice))
                choice == null && ConsentRules.required(zone()) -> ConsentPromptState.FirstRun
                else -> ConsentPromptState.Hidden
            }
        }.stateIn(scope, SharingStarted.Eagerly, ConsentPromptState.Hidden)

    override suspend fun choose(choice: ConsentChoice) {
        prefs.setConsent(choice)
        reopened.value = false
        choiceFlow.emit(choice)
    }

    override fun openChoices() {
        reopened.value = true
    }

    override fun closeChoices() {
        reopened.value = false
    }
}

/** Emits whenever the device time zone changes (system broadcast; registered only while collected). */
internal fun timeZoneChanges(context: Context): Flow<Unit> =
    callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    trySend(Unit)
                }
            }
        val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        awaitClose { context.unregisterReceiver(receiver) }
    }
