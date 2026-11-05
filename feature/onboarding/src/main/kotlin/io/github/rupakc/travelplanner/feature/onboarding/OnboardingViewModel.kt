package io.github.rupakc.travelplanner.feature.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.core.domain.profile.mergeQuizAnswers
import io.github.rupakc.travelplanner.core.domain.profile.overridesFor
import io.github.rupakc.travelplanner.core.domain.profile.withClassification
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassification
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassifier
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import javax.inject.Inject
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
enum class OnboardingStage { LOADING, WELCOME, QUESTION, RESULT, FEATURES, NOTIFICATIONS }

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

    /** Saves everything. [askNotifications]: the device still needs POST_NOTIFICATIONS (Android 13+, not granted). */
    data class Finish(val askNotifications: Boolean) : OnboardingEvent

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
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(OnboardingUiState(fromSettings = savedState[ARG_FROM_SETTINGS] ?: false))
        val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

        private var existingOverrides = FeatureOverrides()
        private var askNotifications = savedState[KEY_ASK] ?: false

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
                OnboardingEvent.ConfirmResult -> enterFeatures()
                is OnboardingEvent.ToggleFeature -> toggleFeature(event.id)
                is OnboardingEvent.Finish -> finish(event.askNotifications)
                OnboardingEvent.RetrySync -> viewModelScope.launch { syncThenProceed() }
                OnboardingEvent.ContinueWithoutSync -> viewModelScope.launch { proceed() }
                OnboardingEvent.NotificationsDone -> viewModelScope.launch { complete() }
            }
        }

        private suspend fun restore() {
            val profile = profileRepository.profile.first()
            existingOverrides = profileRepository.featureOverrides.first()
            val savedAnswers = savedState.get<String>(KEY_ANSWERS)?.let(::decodeAnswers)
            val answers = savedAnswers ?: profile.answers
            val stage = savedState.get<String>(KEY_STAGE)?.let(OnboardingStage::valueOf) ?: OnboardingStage.WELCOME
            val index = (savedState[KEY_QUESTION] ?: 0).coerceIn(0, TravellerQuiz.questions.lastIndex)
            val classification = classifier.classify(answers)
            val selected =
                savedState.get<ArrayList<String>>(KEY_SELECTED)?.takeIf { featuresBasis != null }?.mapNotNull(FeatureId::fromId)?.toSet()
            mutableState.update {
                withFeatureGroups(
                    it.copy(stage = stage, questionIndex = index, answers = answers, classification = classification),
                    selected ?: catalogue.visible(classification.types(), existingOverrides),
                )
            }
            persist()
            if (pendingNotificationsDone) {
                pendingNotificationsDone = false
                if (stage == OnboardingStage.NOTIFICATIONS) complete()
            }
        }

        private fun go(stage: OnboardingStage, index: Int = mutableState.value.questionIndex) {
            mutableState.update { it.copy(stage = stage, questionIndex = index, syncError = null) }
            persist()
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

                else -> {
                    Unit
                }
            }
        }

        private fun enterFeatures() {
            val s = mutableState.value
            if (s.stage != OnboardingStage.RESULT) return
            val classification = classifier.classify(s.answers)
            val basis = classification.key()
            val changed = basis != featuresBasis
            featuresBasis = basis
            savedState[KEY_BASIS] = basis
            mutableState.update {
                val base = it.copy(classification = classification, stage = OnboardingStage.FEATURES)
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

        private fun finish(ask: Boolean) {
            val s = mutableState.value
            if (s.stage != OnboardingStage.FEATURES) return
            askNotifications = ask
            savedState[KEY_ASK] = ask
            mutableState.update { it.copy(saving = true, syncError = null) }
            viewModelScope.launch {
                val classification = classifier.classify(s.answers)
                val profile = profileRepository.profile.first()
                profileRepository.saveProfile(profile.withClassification(s.answers, classification))
                val overrides = catalogue.overridesFor(classification.types(), s.selectedFeatures)
                profileRepository.setFeatureOverrides(overrides)
                syncThenProceed()
            }
        }

        private suspend fun syncThenProceed() {
            mutableState.update { it.copy(saving = true, syncError = null) }
            val answers = mutableState.value.answers
            when (val result = preferencesRepository.update { it.mergeQuizAnswers(answers) }) {
                is AppResult.Success -> proceed()
                is AppResult.Failure -> mutableState.update { it.copy(saving = false, syncError = SyncError(result.error.serverDetail())) }
            }
        }

        private suspend fun proceed() {
            if (!mutableState.value.fromSettings && askNotifications) {
                mutableState.update { it.copy(saving = false, syncError = null) }
                go(OnboardingStage.NOTIFICATIONS)
            } else {
                complete()
            }
        }

        private suspend fun complete() {
            mutableState.update { it.copy(saving = true, syncError = null) }
            if (!mutableState.value.fromSettings) {
                val profile = profileRepository.profile.first()
                profileRepository.saveProfile(profile.copy(onboardingCompleted = true, completedAtEpochMillis = clock.now().toEpochMilli()))
            }
            mutableState.update { it.copy(saving = false, finished = true) }
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
            private val ANSWERS_SERIALIZER = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
        }
    }

private fun TravellerClassification.key(): String = types().joinToString(",") { it.name }

/** Backend `detail` for HTTP errors only; client-side exception text never reaches the UI. */
private fun AppError.serverDetail(): String? =
    when (this) {
        is AppError.Client, is AppError.RateLimited, is AppError.Unauthorized, is AppError.Forbidden,
        is AppError.NotFound, is AppError.Server,
        -> message?.takeIf { it.isNotBlank() }

        else -> null
    }

/** Primary first, then secondaries. */
internal fun TravellerClassification.types() = listOf(primary) + secondary
