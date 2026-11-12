package io.github.rupakc.travelplanner.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.LoadingIndicator
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.DefaultTravellerClassifier
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.onboarding.ui.FeaturesStep
import io.github.rupakc.travelplanner.feature.onboarding.ui.FirstTripStep
import io.github.rupakc.travelplanner.feature.onboarding.ui.HomePassportStep
import io.github.rupakc.travelplanner.feature.onboarding.ui.NotificationsStep
import io.github.rupakc.travelplanner.feature.onboarding.ui.QuestionStep
import io.github.rupakc.travelplanner.feature.onboarding.ui.ResultStep
import io.github.rupakc.travelplanner.feature.onboarding.ui.WelcomeStep
import io.github.rupakc.travelplanner.feature.onboarding.ui.skipAllAction

/**
 * Wires [OnboardingViewModel]: system Back walks the steps, the notification permission is
 * requested from the rationale step, and [onFinished] fires once when everything is saved
 * (or when a Settings re-run is closed with ✕ — nothing is saved then).
 */
@Composable
internal fun OnboardingRoute(onFinished: (planFirstTrip: Boolean) -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onEvent(OnboardingEvent.NotificationsDone)
        }
    BackHandler(enabled = state.stage in BACKABLE && !state.saving) { viewModel.onEvent(OnboardingEvent.Back) }
    BackHandler(enabled = state.stage == OnboardingStage.NOTIFICATIONS) { viewModel.onEvent(OnboardingEvent.NotificationsDone) }
    // Resumed on step 4 although the permission is already settled (or unsupported): nothing to ask.
    LaunchedEffect(state.stage) {
        if (state.stage == OnboardingStage.NOTIFICATIONS && !context.needsNotificationPermission()) viewModel.onEvent(OnboardingEvent.NotificationsDone)
    }
    LaunchedEffect(state.finished) { if (state.finished) onFinished(state.planFirstTrip) }
    OnboardingScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onFinish = { viewModel.onEvent(OnboardingEvent.Finish) },
        askNotifications = context.needsNotificationPermission(),
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onEvent(OnboardingEvent.NotificationsDone)
            }
        },
        onClose = { onFinished(false) },
    )
}

private val BACKABLE = setOf(OnboardingStage.QUESTION, OnboardingStage.RESULT, OnboardingStage.FEATURES, OnboardingStage.HOME_PASSPORT)

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Stateless onboarding: one step at a time, cross-faded (instant under reduce-motion). */
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    onFinish: () -> Unit,
    onAllowNotifications: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    askNotifications: Boolean = false,
) {
    val reduceMotion = rememberReduceMotion()
    AnimatedContent(
        targetState = state.stage to state.questionIndex,
        transitionSpec = { if (reduceMotion) fadeIn(snap()) togetherWith fadeOut(snap()) else fadeIn() togetherWith fadeOut() },
        label = "onboardingStep",
        modifier = modifier.fillMaxSize(),
    ) { (stage, _) ->
        when (stage) {
            OnboardingStage.LOADING -> {
                Box(Modifier.fillMaxSize().background(TravelTheme.colors.background)) { LoadingIndicator() }
            }

            OnboardingStage.WELCOME -> {
                WelcomeStep(state, onEvent, onClose)
            }

            OnboardingStage.QUESTION -> {
                QuestionStep(state, onEvent, onClose)
            }

            OnboardingStage.RESULT -> {
                ResultStep(state, onEvent, onClose)
            }

            OnboardingStage.FEATURES -> {
                FeaturesStep(state, onEvent, onFinish, onClose)
            }

            OnboardingStage.HOME_PASSPORT -> {
                HomePassportStep(state, onEvent, askNotifications)
            }

            OnboardingStage.NOTIFICATIONS -> {
                NotificationsStep(
                    onAllow = onAllowNotifications,
                    onLater = { onEvent(OnboardingEvent.NotificationsDone) },
                    onSkipAll = skipAllAction(state, onEvent),
                )
            }

            OnboardingStage.FIRST_TRIP -> {
                FirstTripStep(state, onEvent)
            }
        }
    }
}

// --- previews (light + dark per main state) ---------------------------------

private val previewAnswers =
    mapOf(
        TravellerQuiz.COMPANIONS to listOf("partner"),
        TravellerQuiz.INTERESTS to listOf("food", "history_culture", "beaches"),
        TravellerQuiz.NEEDS to listOf("wheelchair"),
    )

private fun previewState(stage: OnboardingStage, index: Int = 0) =
    OnboardingUiState(
        stage = stage,
        questionIndex = index,
        answers = previewAnswers,
        classification = DefaultTravellerClassifier.classify(previewAnswers),
        recommended = listOf(FeatureId.CHAT, FeatureId.COMPARE, FeatureId.EVENTS),
        others = listOf(FeatureId.SIM, FeatureId.FOREX),
        selectedFeatures = setOf(FeatureId.CHAT, FeatureId.COMPARE, FeatureId.EVENTS),
    )

@Composable
private fun Preview(stage: OnboardingStage, dark: Boolean, index: Int = 0) {
    TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        OnboardingScreen(previewState(stage, index), {}, {}, {}, {})
    }
}

@Preview(name = "Welcome – light")
@Composable
private fun WelcomeLight() = Preview(OnboardingStage.WELCOME, dark = false)

@Preview(name = "Welcome – dark")
@Composable
private fun WelcomeDark() = Preview(OnboardingStage.WELCOME, dark = true)

@Preview(name = "Question (multi) – light")
@Composable
private fun QuestionLight() = Preview(OnboardingStage.QUESTION, dark = false, index = 2)

@Preview(name = "Question (single) – dark")
@Composable
private fun QuestionDark() = Preview(OnboardingStage.QUESTION, dark = true, index = 1)

@Preview(name = "Result – light")
@Composable
private fun ResultLight() = Preview(OnboardingStage.RESULT, dark = false)

@Preview(name = "Result – dark")
@Composable
private fun ResultDark() = Preview(OnboardingStage.RESULT, dark = true)

@Preview(name = "Features – light")
@Composable
private fun FeaturesLight() = Preview(OnboardingStage.FEATURES, dark = false)

@Preview(name = "Features – dark")
@Composable
private fun FeaturesDark() = Preview(OnboardingStage.FEATURES, dark = true)

@Preview(name = "Notifications – light")
@Composable
private fun NotificationsLight() = Preview(OnboardingStage.NOTIFICATIONS, dark = false)

@Preview(name = "Notifications – dark")
@Composable
private fun NotificationsDark() = Preview(OnboardingStage.NOTIFICATIONS, dark = true)

@Preview(name = "Home and passport – light")
@Composable
private fun HomeLight() = Preview(OnboardingStage.HOME_PASSPORT, dark = false)

@Preview(name = "First trip – dark")
@Composable
private fun FirstTripDark() = Preview(OnboardingStage.FIRST_TRIP, dark = true)
