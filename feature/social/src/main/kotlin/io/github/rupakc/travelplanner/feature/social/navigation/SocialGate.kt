package io.github.rupakc.travelplanner.feature.social.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * Strict community hiding: when [enabled] is false (`hide_community`, or no `features.social`) a deep link or notification into a
 * social screen shows nothing and calls [onBlocked] once (the shell sends the user home) instead of opening the screen.
 */
@Composable
internal fun SocialGate(enabled: Boolean, onBlocked: () -> Unit, content: @Composable () -> Unit) {
    val blocked = rememberUpdatedState(onBlocked)
    if (enabled) {
        content()
    } else {
        LaunchedEffect(Unit) { blocked.value() }
    }
}
