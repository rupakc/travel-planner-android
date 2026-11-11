package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.data.telemetry.ClientConfigRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentPromptState
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetrySettings
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Records emitted events and screen views. */
class FakeTelemetry : Telemetry {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    val screens = mutableListOf<String>()

    override fun emit(
        event: String,
        props: Map<String, Any?>,
    ) {
        events += event to props
    }

    override fun screenView(routeTemplate: String) {
        screens += routeTemplate
    }

    override fun currentRoute(): String = screens.lastOrNull() ?: ""
}

/** A consent repository with a settable [allowed], recording choices and reopen requests. */
class FakeConsentRepository(initiallyAllowed: Boolean = true) : ConsentRepository {
    private val state = MutableStateFlow(initiallyAllowed)
    private val choiceFlow = MutableSharedFlow<ConsentChoice>(extraBufferCapacity = 16)
    private val promptState = MutableStateFlow<ConsentPromptState>(ConsentPromptState.Hidden)

    var allowedValue: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }
    val chosen = mutableListOf<ConsentChoice>()
    var openCount = 0
        private set

    override val allowed: Flow<Boolean> = state
    override val choices: Flow<ConsentChoice> = choiceFlow
    override val prompt: StateFlow<ConsentPromptState> = promptState.asStateFlow()

    override suspend fun allowedNow(): Boolean = state.value

    override suspend fun choose(choice: ConsentChoice) {
        chosen += choice
        state.value = choice == ConsentChoice.GRANTED
        choiceFlow.emit(choice)
    }

    override fun openChoices() {
        openCount++
        promptState.value = ConsentPromptState.Reopened(state.value)
    }

    override fun closeChoices() {
        promptState.value = ConsentPromptState.Hidden
    }
}

/** Returns a fixed [settings] value. */
class FakeClientConfigRepository(var settings: TelemetrySettings?) : ClientConfigRepository {
    override suspend fun telemetrySettings(): TelemetrySettings? = settings
}

/** A language the test can change. */
class FakeAppLanguage(var code: String = "en") : AppLanguage {
    override fun current(): String = code
}
