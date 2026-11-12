package io.github.rupakc.travelplanner.feature.alerts.story

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.feature.alerts.R

/** The card's "Price story" button; its description names the watch. */
@Composable
internal fun StoryButton(title: String, onClick: (() -> Unit)?) {
    if (onClick == null) return
    val a11y = stringResource(R.string.alerts_story_open_a11y, title)
    TpButton(
        stringResource(R.string.alerts_story_open),
        onClick,
        variant = TpButtonVariant.Text,
        tid = "alerts.story.open",
        modifier = Modifier.semantics { contentDescription = a11y },
    )
}
