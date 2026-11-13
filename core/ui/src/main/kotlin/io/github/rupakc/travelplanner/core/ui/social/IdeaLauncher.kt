package io.github.rupakc.travelplanner.core.ui.social

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import io.github.rupakc.travelplanner.core.ui.R

/**
 * Opens the "Share as an idea" screen (implemented by `feature:social`, wired by the app shell as a navigation to
 * `PublishIdeaRoute`). Features never depend on each other, so an entry point reads [LocalIdeaLauncher]: `null` means the
 * backend has no `features.social`, and the entry point draws nothing.
 */
fun interface IdeaLauncher {
    /** Opens the publish screen for the caller's plan [planId] (a trip's plan id for a recap) as [kind]. */
    fun open(planId: Long, kind: IdeaKind)
}

/** The launcher of the current shell, or null when social is off. */
val LocalIdeaLauncher = staticCompositionLocalOf<IdeaLauncher?> { null }

/** "Share as an idea" button; draws nothing without a launcher or an unsaved plan ([planId] null). */
@Composable
fun IdeaEntryButton(
    planId: Long?,
    kind: IdeaKind,
    modifier: Modifier = Modifier,
    variant: TpButtonVariant = TpButtonVariant.Tonal,
    tid: String = "social.idea_entry",
) {
    val launcher = LocalIdeaLauncher.current ?: return
    if (planId == null) return
    TpButton(
        stringResource(R.string.idea_entry_button),
        { launcher.open(planId, kind) },
        modifier,
        variant,
        leadingIcon = Icons.AutoMirrored.Filled.Send,
        tid = tid,
    )
}
