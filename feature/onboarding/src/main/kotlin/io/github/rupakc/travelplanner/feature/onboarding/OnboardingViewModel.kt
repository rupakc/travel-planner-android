package io.github.rupakc.travelplanner.feature.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.core.domain.profile.mergeQuizAnswers
import io.github.rupakc.travelplanner.core.domain.profile.overridesFor
import io.github.rupakc.travelplanner.core.domain.profile.withClassification
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassification
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassifier
import io.github.rupakc.travelplanner.core.model.auth.OnboardingRequest
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Where the flow is (spec §6.1). */
enum class OnboardingStage { LOADING, WELCOME, QUESTION, RESULT, FEATURES, HOME_PASSPORT, NOTIFICATIONS, FIRST_TRIP }

/** The Preferences sync failed; [message] is the backend detail (shown as-is) or null for our own copy. */
@Immutable
data class SyncError(val message: String?)

@Immutable
data class OnboardingUiState(
    val fromSettings: Boolean = false,
    val stage: OnboardingStage = OnboardingStage.LOADING,
    val questionIndex: Int = 0,
    val answers: Map<String, List<String>> = emptyMap(),
    val classification: TravellerClassification? = null,
    /** Features on by default for the result types, then the rest (catalogue order). */
    val recommended: List<FeatureId> = emptyList(),
    val others: List<FeatureId> = emptyList(),
    val selectedFeatures: Set<FeatureId> = emptySet(),
    val saving: Boolean = false,
    val syncError: SyncError? = null,
    val finished: Boolean = false,
    /** Home airport text (a picked suggestion shows its label) and the passport (nationality) text of step 3. */
    val homeAirport: String = "",
    val passport: String = "",
    val airportSuggestions: List<Airport> = emptyList(),
    val passportSuggestions: List<Nationality> = emptyList(),
    /** The server remembered an unfinished run and the flow picked up there ("Welcome back"). */
    val resumed: Boolean = false,
    /** How the first run ended: true = "Plan my first trip", false = "Just explore" or Skip setup. */
    val planFirstTrip: Boolean = false,
) {
    val questionCount: Int get() = TravellerQuiz.questions.size

    /** Option ids chosen for the question at [questionIndex]. */
    val currentAnswer: List<String> get() = answers[TravellerQuiz.questions[questionIndex].id].orEmpty()
}

sealed interface OnboardingEvent {
    data object Start : OnboardingEvent

    /** Single choice: selects [optionId]; multi choice: toggles it. */
    data class Select(val optionId: String) : OnboardingEvent

    data object Next : OnboardingEvent

    /** Clears this question's answer and moves on. */
    data object Skip : OnboardingEvent

    data object Back : OnboardingEvent

    /** "Not you? Change answers". */
    data object ChangeAnswers : OnboardingEvent

    data object ConfirmResult : OnboardingEvent

    data class ToggleFeature(val id: FeatureId) : OnboardingEvent

    /** Saves the traveller profile and feature picks; edit mode then syncs and closes, the first run continues with step 3. */
    data object Finish : OnboardingEvent

    /** Step 3: typed text (clears a previous pick and refreshes the suggestions). */
    data class SetHomeAirport(val text: String) : OnboardingEvent

    data class PickHomeAirport(val airport: Airport) : OnboardingEvent

    data class SetPassport(val text: String) : OnboardingEvent

    data class PickPassport(val nationality: Nationality) : OnboardingEvent

    /** Leaves step 3; [askNotifications]: the device still needs POST_NOTIFICATIONS (Android 13+, not granted). */
    data class HomeNext(val askNotifications: Boolean) : OnboardingEvent

    /** Step 5: "Plan my first trip" ([planTrip] true) or "Just explore". Saves everything and finishes. */
    data class Complete(val planTrip: Boolean) : OnboardingEvent

    /** "Skip setup" (first run only): `{step, done: true, skipped: true}`; counts as seen. */
    data object SkipAll : OnboardingEvent

    data object RetrySync : OnboardingEvent

    data object ContinueWithoutSync : OnboardingEvent

    /** The permission rationale was answered (allowed, denied or "Not now"). */
    data object NotificationsDone : OnboardingEvent
}

/**
 * Onboarding (spec §6): Welcome → 6 questions → result → feature picker → (notification rationale) → done.
 * Progress, answers and toggles live in [SavedStateHandle] (rotation + process death).
 * On finish: traveller profile + feature overrides go to DataStore, mapped answers are merged into
 * server Preferences (read-modify-write, nothing wiped), and — first run only — onboarding is marked complete.
 * Route argument `fromSettings` (= [io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute.fromSettings])
 * starts from the saved answers and toggles and never asks for notifications.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val profileRepository: ProfileRepository,
        private val preferencesRepository: PreferencesRepository,
        private val catalogue: FeatureCatalogue,
        private val classifier: TravellerClassifier,
        private val clock: AppClock,
        private val telemetry: Telemetry,
        private val searchRepository: SearchRepository,
        /** Absent until the account repository is bound: onboarding then stays local-only. */
        accountRepository: Optional<AccountRepository>,
    ) : ViewModel() {
        private val account: AccountRepository? = accountRepository.orElse(null)
        private val mutableState = MutableStateFlow(OnboardingUiState(fromSettings = savedState[ARG_FROM_SETTINGS] ?: false))
        val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

        private var existingOverrides = FeatureOverrides()
        private var askNotifications = savedState[KEY_ASK] ?: false
        private var planTrip = false
        private var lastStep: Int? = null
        private var homeAirportCode: String? = savedState[KEY_HOME_CODE]
        private var airportJob: Job? = null
        private var passportJob: Job? = null

        private var pendingNotificationsDone = false

        /** Classification the feature toggles were seeded for (null = not seeded yet). */
        private var featuresBasis: String? = savedState[KEY_BASIS]

        init {
            viewModelScope.launch { restore() }
        }

        fun onEvent(event: OnboardingEvent) {
            val s = mutableState.value
            if (s.stage == OnboardingStage.LOADING) {
                // A permission result can arrive before restore() finishes after process death: replay it then.
                if (event == OnboardingEvent.NotificationsDone) pendingNotificationsDone = true
                return
            }
            if (s.saving || s.finished) return
            when (event) {
                OnboardingEvent.Start -> go(OnboardingStage.QUESTION, 0)
                is OnboardingEvent.Select -> select(event.optionId)
                OnboardingEvent.Next -> next()
                OnboardingEvent.Skip -> skip()
                OnboardingEvent.Back -> back()
                OnboardingEvent.ChangeAnswers -> go(OnboardingStage.QUESTION, 0)
                OnboardingEvent.ConfirmResult -> confirmResult()
                is OnboardingEvent.ToggleFeature -> toggleFeature(event.id)
                OnboardingEvent.Finish -> finish()
                is OnboardingEvent.SetHomeAirport -> setHomeAirport(event.text)
                is OnboardingEvent.PickHomeAirport -> pickHomeAirport(event.airport)
                is OnboardingEvent.SetPassport -> setPassport(event.text)
                is OnboardingEvent.PickPassport -> pickPassport(event.nationality)
                is OnboardingEvent.HomeNext -> homeNext(event.askNotifications)
                is OnboardingEvent.Complete -> completeFirstRun(event.planTrip)
                OnboardingEvent.SkipAll -> skipAll()
                OnboardingEvent.RetrySync -> viewModelScope.launch { syncThenProceed() }
                OnboardingEvent.ContinueWithoutSync -> viewModelScope.launch { proceed() }
                OnboardingEvent.NotificationsDone -> go(OnboardingStage.FIRST_TRIP)
            }
        }

        private suspend fun restore() {
            val profile = profileRepository.profile.first()
            existingOverrides = profileRepository.featureOverrides.first()
            val savedAnswers = savedState.get<String>(KEY_ANSWERS)?.let(::decodeAnswers)
            val answers = savedAnswers ?: profile.answers
            val savedStage = savedState.get<String>(KEY_STAGE)?.let(OnboardingStage::valueOf)
            val resume = if (savedStage == null && !mutableState.value.fromSettings) resumeFromServer() else null
            val stage = savedStage ?: resume ?: OnboardingStage.WELCOME
            val passport = savedState.get<String>(KEY_PASSPORT) ?: (preferencesRepository.get() as? AppResult.Success)?.value?.nationality.orEmpty()
            val index = (savedState[KEY_QUESTION] ?: 0).coerceIn(0, TravellerQuiz.questions.lastIndex)
            val classification = classifier.classify(answers)
            val selected =
                savedState.get<ArrayList<String>>(KEY_SELECTED)?.takeIf { featuresBasis != null }?.mapNotNull(FeatureId::fromId)?.toSet()
            mutableState.update {
                withFeatureGroups(
                    it.copy(
                        stage = stage,
                        questionIndex = index,
                        answers = answers,
                        classification = classification,
                        resumed = resume != null,
                        homeAirport = savedState[KEY_HOME_TEXT] ?: profile.homeAirport.orEmpty(),
                        passport = passport,
                    ),
                    selected ?: catalogue.visible(classification.types(), existingOverrides),
                )
            }
            persist()
            if (resume != null) lastStep = stage.serverStep()
            reportStep(stage)
            if (pendingNotificationsDone) {
                pendingNotificationsDone = false
                if (stage == OnboardingStage.NOTIFICATIONS) go(OnboardingStage.FIRST_TRIP)
            }
        }

        /** The stage the server says an unfinished run stopped at (step 2..5), or null: step 1, done, a 404 or any error. */
        private suspend fun resumeFromServer(): OnboardingStage? {
            val dto = (account?.account() as? AppResult.Success)?.value?.onboarding ?: return null
            return if (dto.done) null else resumeStage(dto.step)
        }

        /** Mirrors the step to the server (fire-and-forget) and telemetry; once per step change. First run only. */
        private fun reportStep(stage: OnboardingStage) {
            val step = stage.serverStep()
            if (mutableState.value.fromSettings || step == null || step == lastStep) return
            lastStep?.takeIf { step > it }?.let { telemetry.emit(TelemetryEvents.ONBOARDING_STEP, stepProps(it, "next")) }
            lastStep = step
            telemetry.emit(TelemetryEvents.ONBOARDING_STEP, stepProps(step, "view"))
            account?.let { viewModelScope.launch { it.putOnboarding(OnboardingRequest(step = step, done = false)) } }
        }

        private fun emitResult() {
            val c = mutableState.value.classification ?: return
            telemetry.emit(TelemetryEvents.ONBOARDING_RESULT, mapOf("type" to c.primary.name.lowercase(), "secondary" to c.secondary.size))
        }

        private fun stepProps(step: Int, action: String) = mapOf("step" to step, "action" to action)

        private fun go(stage: OnboardingStage, index: Int = mutableState.value.questionIndex) {
            mutableState.update { it.copy(stage = stage, questionIndex = index, syncError = null) }
            persist()
            reportStep(stage)
        }

        private fun select(optionId: String) {
            val s = mutableState.value
            if (s.stage != OnboardingStage.QUESTION) return
            val question = TravellerQuiz.questions[s.questionIndex]
            if (question.options.none { it.id == optionId }) return
            val current = s.currentAnswer
            val updated =
                when {
                    !question.multiChoice -> listOf(optionId)
                    optionId in current -> current - optionId
                    else -> current + optionId
                }
            setAnswer(question.id, updated)
        }

        private fun setAnswer(questionId: String, options: List<String>) {
            mutableState.update { s ->
                val answers = if (options.isEmpty()) s.answers - questionId else s.answers + (questionId to options)
                s.copy(answers = answers)
            }
            persist()
        }

        private fun next() {
            val s = mutableState.value
            if (s.stage != OnboardingStage.QUESTION) return
            if (s.questionIndex < TravellerQuiz.questions.lastIndex) {
                go(OnboardingStage.QUESTION, s.questionIndex + 1)
            } else {
                mutableState.update { it.copy(classification = classifier.classify(it.answers)) }
                if (!s.fromSettings) emitResult()
                go(OnboardingStage.RESULT)
            }
        }

        private fun skip() {
            val s = mutableState.value
            if (s.stage != OnboardingStage.QUESTION) return
            setAnswer(TravellerQuiz.questions[s.questionIndex].id, emptyList())
            next()
        }

        private fun back() {
            val s = mutableState.value
            when (s.stage) {
                OnboardingStage.QUESTION -> {
                    if (s.questionIndex == 0) go(OnboardingStage.WELCOME, 0) else go(OnboardingStage.QUESTION, s.questionIndex - 1)
                }

                OnboardingStage.RESULT -> {
                    go(OnboardingStage.QUESTION, TravellerQuiz.questions.lastIndex)
                }

                OnboardingStage.FEATURES -> {
                    go(OnboardingStage.RESULT)
                }

                OnboardingStage.HOME_PASSPORT -> {
                    go(if (s.fromSettings) OnboardingStage.FEATURES else OnboardingStage.RESULT)
                }

                else -> {
                    Unit
                }
            }
        }

        /**
         * Web has no feature picker: the first run applies the type's default features and moves on to step 3 (they stay
         * adjustable in Settings). Only the retake from Settings shows the picker.
         */
        private fun confirmResult() {
            if (mutableState.value.stage != OnboardingStage.RESULT) return
            seedFeatures()
            if (mutableState.value.fromSettings) {
                mutableState.update { it.copy(stage = OnboardingStage.FEATURES) }
                persist()
            } else {
                finish()
            }
        }

        private fun seedFeatures() {
            val s = mutableState.value
            val classification = classifier.classify(s.answers)
            val basis = classification.key()
            val changed = basis != featuresBasis
            featuresBasis = basis
            savedState[KEY_BASIS] = basis
            mutableState.update {
                val base = it.copy(classification = classification)
                // Keep the user's toggles when they only peeked back at the result; re-seed when the type changed.
                withFeatureGroups(base, if (changed) catalogue.visible(classification.types(), existingOverrides) else it.selectedFeatures)
            }
            persist()
        }

        private fun toggleFeature(id: FeatureId) {
            mutableState.update { s ->
                s.copy(selectedFeatures = if (id in s.selectedFeatures) s.selectedFeatures - id else s.selectedFeatures + id)
            }
            persist()
        }

        private fun finish() {
            val s = mutableState.value
            if (s.stage != OnboardingStage.FEATURES && s.stage != OnboardingStage.RESULT) return
            mutableState.update { it.copy(saving = true, syncError = null) }
            viewModelScope.launch {
                val classification = classifier.classify(s.answers)
                val profile = profileRepository.profile.first()
                profileRepository.saveProfile(profile.withClassification(s.answers, classification))
                val overrides = catalogue.overridesFor(classification.types(), s.selectedFeatures)
                profileRepository.setFeatureOverrides(overrides)
                if (s.fromSettings) {
                    syncThenProceed()
                } else {
                    mutableState.update { it.copy(saving = false) }
                    go(OnboardingStage.HOME_PASSPORT)
                }
            }
        }

        private fun setHomeAirport(text: String) {
            homeAirportCode = null
            savedState[KEY_HOME_CODE] = null
            mutableState.update { it.copy(homeAirport = text, airportSuggestions = emptyList()) }
            persist()
            airportJob?.cancel()
            val query = text.trim()
            if (query.length < MIN_QUERY) return
            airportJob =
                viewModelScope.launch {
                    val found = (searchRepository.airports(query, SUGGESTIONS) as? AppResult.Success)?.value.orEmpty()
                    mutableState.update { if (it.homeAirport == text) it.copy(airportSuggestions = found) else it }
                }
        }

        private fun pickHomeAirport(airport: Airport) {
            airportJob?.cancel()
            val code = airport.iataCode?.takeIf { it.isNotBlank() }
            homeAirportCode = code
            savedState[KEY_HOME_CODE] = code
            mutableState.update { it.copy(homeAirport = airport.label(), airportSuggestions = emptyList()) }
            persist()
        }

        private fun setPassport(text: String) {
            mutableState.update { it.copy(passport = text, passportSuggestions = emptyList()) }
            persist()
            passportJob?.cancel()
            val query = text.trim()
            if (query.length < MIN_QUERY) return
            passportJob =
                viewModelScope.launch {
                    val found = (searchRepository.nationalities(query, SUGGESTIONS) as? AppResult.Success)?.value.orEmpty()
                    mutableState.update { if (it.passport == text) it.copy(passportSuggestions = found) else it }
                }
        }

        private fun pickPassport(nationality: Nationality) {
            passportJob?.cancel()
            val value = nationality.nationality?.takeIf { it.isNotBlank() } ?: return
            mutableState.update { it.copy(passport = value, passportSuggestions = emptyList()) }
            persist()
        }

        private fun homeNext(ask: Boolean) {
            if (mutableState.value.stage != OnboardingStage.HOME_PASSPORT) return
            askNotifications = ask
            savedState[KEY_ASK] = ask
            mutableState.update { it.copy(saving = true) }
            viewModelScope.launch {
                val home = homeAirportCode ?: mutableState.value.homeAirport.trim().ifBlank { null }
                val profile = profileRepository.profile.first()
                profileRepository.saveProfile(profile.copy(homeAirport = home))
                mutableState.update { it.copy(saving = false) }
                go(if (ask) OnboardingStage.NOTIFICATIONS else OnboardingStage.FIRST_TRIP)
            }
        }

        private fun completeFirstRun(planTrip: Boolean) {
            if (mutableState.value.stage != OnboardingStage.FIRST_TRIP) return
            this.planTrip = planTrip
            viewModelScope.launch { syncThenProceed() }
        }

        private fun skipAll() {
            val s = mutableState.value
            val step = s.stage.serverStep()
            if (s.fromSettings || step == null) return
            mutableState.update { it.copy(saving = true, syncError = null) }
            telemetry.emit(TelemetryEvents.ONBOARDING_STEP, stepProps(step, "skip"))
            viewModelScope.launch {
                account?.putOnboarding(OnboardingRequest(step = step, done = true, skipped = true))
                planTrip = false
                complete()
            }
        }

        /** Merges the quiz answers (and, on the first run, the passport) into the server preferences: read-modify-write. */
        private suspend fun syncThenProceed() {
            mutableState.update { it.copy(saving = true, syncError = null) }
            val s = mutableState.value
            val passport = s.passport.trim().takeIf { !s.fromSettings && it.isNotEmpty() }
            val result =
                preferencesRepository.update { prefs ->
                    prefs.mergeQuizAnswers(s.answers).let { if (passport != null) it.copy(nationality = passport) else it }
                }
            when (result) {
                is AppResult.Success -> proceed()
                is AppResult.Failure -> mutableState.update { it.copy(saving = false, syncError = SyncError(result.error.userFacingDetail())) }
            }
        }

        private suspend fun proceed() {
            if (!mutableState.value.fromSettings) {
                val type = mutableState.value.classification?.primary?.name?.lowercase()
                telemetry.emit(TelemetryEvents.ONBOARDING_STEP, stepProps(LAST_STEP, "done"))
                account?.putOnboarding(OnboardingRequest(step = LAST_STEP, done = true, travellerType = type))
            }
            complete()
        }

        private suspend fun complete() {
            mutableState.update { it.copy(saving = true, syncError = null) }
            if (!mutableState.value.fromSettings) {
                val profile = profileRepository.profile.first()
                profileRepository.saveProfile(profile.copy(onboardingCompleted = true, completedAtEpochMillis = clock.now().toEpochMilli()))
            }
            val plan = planTrip
            mutableState.update { it.copy(saving = false, finished = true, planFirstTrip = plan) }
        }

        private fun withFeatureGroups(s: OnboardingUiState, selected: Set<FeatureId>): OnboardingUiState {
            val defaults = catalogue.defaultsFor(s.classification?.types().orEmpty())
            val (recommended, others) = catalogue.all.map { it.id }.partition { it in defaults }
            return s.copy(recommended = recommended, others = others, selectedFeatures = selected)
        }

        private fun persist() {
            val s = mutableState.value
            savedState[KEY_STAGE] = s.stage.takeIf { it != OnboardingStage.LOADING }?.name
            savedState[KEY_QUESTION] = s.questionIndex
            savedState[KEY_ANSWERS] = Json.encodeToString(ANSWERS_SERIALIZER, s.answers)
            savedState[KEY_SELECTED] = ArrayList(s.selectedFeatures.map { it.id })
            savedState[KEY_HOME_TEXT] = s.homeAirport
            savedState[KEY_PASSPORT] = s.passport
        }

        private fun decodeAnswers(json: String): Map<String, List<String>>? = runCatching { Json.decodeFromString(ANSWERS_SERIALIZER, json) }.getOrNull()

        companion object {
            /** Type-safe route argument name of `OnboardingRoute.fromSettings`. */
            const val ARG_FROM_SETTINGS = "fromSettings"
            private const val KEY_STAGE = "onboarding_stage"
            private const val KEY_QUESTION = "onboarding_question"
            private const val KEY_ANSWERS = "onboarding_answers"
            private const val KEY_SELECTED = "onboarding_features"
            private const val KEY_ASK = "onboarding_ask_notifications"
            private const val KEY_BASIS = "onboarding_features_basis"
            private const val KEY_HOME_TEXT = "onboarding_home_airport"
            private const val KEY_HOME_CODE = "onboarding_home_airport_code"
            private const val KEY_PASSPORT = "onboarding_passport"
            private const val MIN_QUERY = 2
            private const val SUGGESTIONS = 6
            private val ANSWERS_SERIALIZER = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
        }
    }

private fun TravellerClassification.key(): String = types().joinToString(",") { it.name }

/** Primary first, then secondaries. */
internal fun TravellerClassification.types() = listOf(primary) + secondary

/** The server's step number (1 welcome, 2 how you travel, 3 home + passport, 4 notifications, 5 first trip); null while loading. */
internal fun OnboardingStage.serverStep(): Int? =
    when (this) {
        OnboardingStage.LOADING -> null
        OnboardingStage.WELCOME -> 1
        OnboardingStage.QUESTION, OnboardingStage.RESULT, OnboardingStage.FEATURES -> STEP_QUIZ
        OnboardingStage.HOME_PASSPORT -> STEP_HOME
        OnboardingStage.NOTIFICATIONS -> STEP_NOTIFICATIONS
        OnboardingStage.FIRST_TRIP -> LAST_STEP
    }

/** Where to resume for a server `onboarding.step`: nothing for step 1 (start at the welcome), else that step. */
internal fun resumeStage(step: Int): OnboardingStage? =
    when (step) {
        STEP_QUIZ -> OnboardingStage.QUESTION
        STEP_HOME -> OnboardingStage.HOME_PASSPORT
        STEP_NOTIFICATIONS -> OnboardingStage.NOTIFICATIONS
        LAST_STEP -> OnboardingStage.FIRST_TRIP
        else -> null
    }

private const val STEP_QUIZ = 2
private const val STEP_HOME = 3
private const val STEP_NOTIFICATIONS = 4
private const val LAST_STEP = 5

private fun Airport.label(): String =
    listOfNotNull(iataCode?.takeIf { it.isNotBlank() }, city?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }).joinToString(" - ")
