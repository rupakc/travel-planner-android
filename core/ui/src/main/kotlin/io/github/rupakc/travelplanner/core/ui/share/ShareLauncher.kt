package io.github.rupakc.travelplanner.core.ui.share

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.ui.R

/**
 * Opens the Share links 2.0 dialog (implemented by `feature:share`, wired by the app shell as a navigation to
 * `ShareCreateRoute`). Features never depend on each other, so an entry point reads [LocalShareLauncher]: `null` means the
 * backend has no `features.sharing`, and the entry point keeps its legacy behaviour (plan link) or hides itself.
 */
fun interface ShareLauncher {
    /** Opens the dialog for [kind]; [planId] is the plan (or the trip's plan) and is null for [ShareKind.SAVINGS]. */
    fun open(kind: ShareKind, planId: Long?)

    /** Opens the dialog for a finished quiz session ([ShareKind.QUIZ]). A launcher that predates quizzes ignores it. */
    fun openQuiz(sessionId: String) {}
}

/** The launcher of the current shell, or null when sharing 2.0 is off. */
val LocalShareLauncher = staticCompositionLocalOf<ShareLauncher?> { null }

/**
 * "Share link" button that opens the Share links 2.0 dialog for [kind]. Draws nothing when [LocalShareLauncher] is null
 * (backend without `features.sharing`) or when a plan-bound kind has no [planId] yet (an unsaved plan).
 */
@Composable
fun ShareEntryButton(
    kind: ShareKind,
    planId: Long?,
    modifier: Modifier = Modifier,
    variant: TpButtonVariant = TpButtonVariant.Secondary,
    tid: String = "share.entry",
    sessionId: String? = null,
    @StringRes label: Int = R.string.share_entry_button,
) {
    val launcher = LocalShareLauncher.current ?: return
    if (kind.needsPlan && planId == null) return
    if (kind.needsSession && sessionId == null) return
    TpButton(
        stringResource(label),
        { if (kind.needsSession) launcher.openQuiz(sessionId.orEmpty()) else launcher.open(kind, planId) },
        modifier,
        variant,
        leadingIcon = Icons.Filled.Share,
        tid = tid,
    )
}
