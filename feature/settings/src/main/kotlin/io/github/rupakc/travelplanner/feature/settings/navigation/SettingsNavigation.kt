package io.github.rupakc.travelplanner.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.core.model.navigation.AppearanceRoute
import io.github.rupakc.travelplanner.core.model.navigation.FeaturesRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationSettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.PreferencesRoute
import io.github.rupakc.travelplanner.core.model.navigation.ServerRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.TasteProfileRoute

/** Settings root + sub-screens. [onSignOut] must call AuthRepository.logout() (the gate then shows Login). */
fun NavGraphBuilder.settingsGraph(
    onBack: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onSignOut: () -> Unit,
) {
    composable<SettingsRoute> { SettingsScreen(onBack = onBack, onRetakeQuiz = onRetakeQuiz, onSignOut = onSignOut) }
    composable<PreferencesRoute> { SettingsSubScreen(onBack) }
    composable<TasteProfileRoute> { SettingsSubScreen(onBack) }
    composable<FeaturesRoute> { SettingsSubScreen(onBack) }
    composable<AppearanceRoute> { SettingsSubScreen(onBack) }
    composable<ServerRoute> { SettingsSubScreen(onBack) }
    composable<NotificationSettingsRoute> { SettingsSubScreen(onBack) }
}

@Composable
internal fun SettingsScreen(onBack: () -> Unit, onRetakeQuiz: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.settings_title),
        message = stringResource(R.string.settings_message),
        modifier = modifier,
        onBack = onBack,
        actions = listOf(
            PlaceholderAction(stringResource(R.string.settings_retake_quiz), onRetakeQuiz),
            PlaceholderAction(stringResource(R.string.settings_sign_out), onSignOut),
        ),
    )
}

@Composable
internal fun SettingsSubScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.settings_title),
        message = stringResource(R.string.settings_sub_message),
        modifier = modifier,
        onBack = onBack,
    )
}
