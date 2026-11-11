package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.datastore.TelemetryPrefsStore
import io.github.rupakc.travelplanner.core.domain.telemetry.ConsentRules
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Consent from the stored choice, else from the device zone read at call time (so a flight into the EU with
 * no stored choice turns collection off and shows the prompt; web `consent.js`).
 */
@Singleton
class ConsentRepositoryImpl(
    private val prefs: TelemetryPrefsStore,
    private val zone: () -> String,
    scope: CoroutineScope,
) : ConsentRepository {
    @Inject
    constructor(
        prefs: TelemetryPrefsStore,
        @ApplicationScope scope: CoroutineScope,
    ) : this(prefs, { ZoneId.systemDefault().id }, scope)

    private val reopened = MutableStateFlow(false)
    private val choiceFlow = MutableSharedFlow<ConsentChoice>(replay = 0, extraBufferCapacity = 16)

    private fun allowedFor(choice: ConsentChoice?): Boolean =
        if (choice != null) choice == ConsentChoice.GRANTED else !ConsentRules.required(zone())

    override val allowed: Flow<Boolean> = prefs.consent.map(::allowedFor)

    override suspend fun allowedNow(): Boolean = allowedFor(prefs.consent.first())

    override val choices: Flow<ConsentChoice> = choiceFlow

    override val prompt: StateFlow<ConsentPromptState> =
        combine(prefs.consent, reopened) { choice, reopen ->
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
