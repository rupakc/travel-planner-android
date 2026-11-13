package io.github.rupakc.travelplanner.feature.search.importlink

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.ImageProblem
import io.github.rupakc.travelplanner.core.domain.search.ImportAction
import io.github.rupakc.travelplanner.core.domain.search.ImportErrorView
import io.github.rupakc.travelplanner.core.domain.search.ImportInput
import io.github.rupakc.travelplanner.core.domain.search.ImportLimits
import io.github.rupakc.travelplanner.feature.search.R
import io.github.rupakc.travelplanner.feature.search.components.SearchDimens
import java.text.NumberFormat

/** Characters shown once the pasted text is this long (web counter threshold). */
private const val COUNTER_FROM = 400

/** Web ImportPanel heading + the smart box, screenshots, examples and submit; or the progress story while busy. */
@Composable
internal fun ImportPanel(state: ImportUiState, onEvent: (ImportEvent) -> Unit, onPickImages: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(
                stringResource(R.string.search_import_heading),
                style = TravelTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.search_import_sub), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        }
        val busy = state.busy
        if (busy != null) ImportProgress(busy, { onEvent(ImportEvent.Cancel) }) else ImportInputCard(state, onEvent, onPickImages)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportInputCard(state: ImportUiState, onEvent: (ImportEvent) -> Unit, onPickImages: () -> Unit) {
    TpCard(modifier = Modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            TpOutlinedTextField(
                value = state.text,
                onValueChange = { onEvent(ImportEvent.TextChanged(it)) },
                modifier = Modifier.fillMaxWidth().testTag("import_input"),
                label = { Text(stringResource(R.string.search_import_input_label)) },
                placeholder = { Text(stringResource(R.string.search_import_placeholder)) },
                enabled = state.shots.isEmpty(),
                minLines = 4,
                isError = state.tooLong,
                shape = TravelShape.Input,
                supportingText = { InputHint(state) },
                tid = "search.search_import_input_label",
            )
            ScreenshotRow(state, onEvent, onPickImages)
            state.error?.let { ErrorCard(it, onEvent) }
            Text(stringResource(R.string.search_import_examples), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
            val examples =
                listOf(
                    R.string.search_import_example_lisbon_label to R.string.search_import_example_lisbon,
                    R.string.search_import_example_tokyo_label to R.string.search_import_example_tokyo,
                    R.string.search_import_example_rome_label to R.string.search_import_example_rome,
                )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                examples.forEach { (label, text) ->
                    val body = stringResource(text)
                    TpChip(label = stringResource(label), selected = state.text == body, onClick = { onEvent(ImportEvent.Example(body)) })
                }
            }
            val submitLabel =
                if (state.shots.isNotEmpty()) {
                    pluralStringResource(R.plurals.search_import_submit_shots, state.shots.size, state.shots.size)
                } else {
                    stringResource(R.string.search_import_submit)
                }
            TpButton(
                submitLabel,
                { onEvent(ImportEvent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().testTag("import_submit"),
                tid = "search.import_panel_tp_button",
            )
        }
    }
}

@Composable
private fun InputHint(state: ImportUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        val hint =
            when {
                state.shots.isNotEmpty() -> pluralStringResource(R.plurals.search_import_hint_shots, state.shots.size, state.shots.size)
                state.input is ImportInput.Url -> stringResource(R.string.search_import_hint_link)
                state.input is ImportInput.Text -> stringResource(R.string.search_import_hint_text)
                else -> ""
            }
        Text(hint, modifier = Modifier.weight(1f))
        if (state.shots.isEmpty() && state.text.length > COUNTER_FROM) {
            val nf = NumberFormat.getIntegerInstance()
            Text(
                stringResource(R.string.search_import_count, nf.format(state.text.length), nf.format(ImportLimits.MAX_TEXT)),
                color = if (state.tooLong) TravelTheme.colors.error else TravelTheme.colors.onSurfaceVariant,
                fontWeight = if (state.tooLong) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenshotRow(state: ImportUiState, onEvent: (ImportEvent) -> Unit, onPickImages: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            state.shots.forEach { shot ->
                Box(Modifier.size(SearchDimens.Thumbnail).clip(TravelShape.Input)) {
                    shot.thumbnail?.let {
                        Image(it, contentDescription = shot.name, contentScale = ContentScale.Crop, modifier = Modifier.size(SearchDimens.Thumbnail))
                    }
                    Surface(shape = CircleShape, color = TravelTheme.colors.inverseSurface, modifier = Modifier.align(Alignment.TopEnd)) {
                        TpIconButton(onClick = { onEvent(ImportEvent.RemoveShot(shot.id)) }, tid = "search.import_panel_icon_button") {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_import_remove_shot, shot.name),
                                tint = TravelTheme.colors.inverseOnSurface,
                            )
                        }
                    }
                }
            }
            if (state.shots.size < ImportLimits.MAX_IMAGES) {
                TpButton(
                    stringResource(if (state.shots.isEmpty()) R.string.search_import_add_shots else R.string.search_import_add_another),
                    onPickImages,
                    variant = TpButtonVariant.Secondary,
                    modifier = Modifier.testTag("import_pick"),
                    tid = "search.search_import_add_shots",
                )
            }
        }
        Text(
            pluralStringResource(R.plurals.search_import_up_to, ImportLimits.MAX_IMAGES, ImportLimits.MAX_IMAGES),
            style = TravelTheme.typography.bodySmall,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        val messages =
            buildList {
                state.keptShots?.let { add(pluralStringResource(R.plurals.search_import_kept, ImportLimits.MAX_IMAGES, ImportLimits.MAX_IMAGES, it)) }
                state.shotProblems.forEach { add(shotProblemText(it)) }
            }
        if (messages.isNotEmpty()) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                messages.forEach { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.error) }
            }
        }
    }
}

@Composable
private fun shotProblemText(problem: ShotProblem): String =
    when (problem) {
        is ShotProblem.Invalid -> {
            when (problem.problem) {
                ImageProblem.HEIC -> stringResource(R.string.search_import_heic, problem.name)
                ImageProblem.UNSUPPORTED_TYPE -> stringResource(R.string.search_import_not_image, problem.name)
                ImageProblem.TOO_BIG_TO_READ -> stringResource(R.string.search_import_too_big_read, problem.name)
                ImageProblem.TOO_BIG -> stringResource(R.string.search_import_too_big, problem.name)
            }
        }

        is ShotProblem.StillTooBig -> {
            stringResource(R.string.search_import_still_big, problem.name)
        }

        is ShotProblem.Unreadable -> {
            stringResource(R.string.search_import_unreadable, problem.name)
        }
    }

/** Web error card: the message (backend text as-is) and the next steps it offers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorCard(error: ImportErrorView, onEvent: (ImportEvent) -> Unit) {
    val message =
        error.message ?: stringResource(
            when (error.code) {
                "network" -> R.string.search_import_network
                "too_large" -> R.string.search_import_too_large
                else -> R.string.search_import_unknown
            },
            stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name),
        )
    Surface(shape = TravelShape.ItemCard, color = TravelTheme.colors.errorContainer, contentColor = TravelTheme.colors.onErrorContainer) {
        Column(
            Modifier.padding(TravelTheme.spacing.md).semantics { liveRegion = LiveRegionMode.Assertive }.testTag("import_error"),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(message, style = TravelTheme.typography.bodyMedium)
            }
            if (error.actions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    error.actions.forEach { action ->
                        when (action) {
                            ImportAction.TEXT -> {
                                TpButton(stringResource(R.string.search_import_action_text), {
                                    onEvent(ImportEvent.RunAction(action))
                                }, variant = TpButtonVariant.Secondary, tid = "search.search_import_action_text")
                            }

                            ImportAction.IMAGES -> {
                                TpButton(stringResource(R.string.search_import_action_images), {
                                    onEvent(ImportEvent.RunAction(action))
                                }, variant = TpButtonVariant.Secondary, tid = "search.search_import_action_images")
                            }

                            ImportAction.RETRY -> {
                                TpButton(
                                    stringResource(R.string.search_import_action_retry),
                                    { onEvent(ImportEvent.RunAction(action)) },
                                    variant = TpButtonVariant.Secondary,
                                    leadingIcon = Icons.Filled.Refresh,
                                    tid = "search.search_import_action_retry",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The staged story: three steps (done / active / waiting), live announcement of the current step, timing and Cancel. */
@Composable
private fun ImportProgress(busy: ImportBusy, onCancel: () -> Unit) {
    val steps =
        listOf(
            stringResource(
                when (busy.kind) {
                    ImportSourceKind.URL -> R.string.search_import_step_url
                    ImportSourceKind.TEXT -> R.string.search_import_step_text
                    ImportSourceKind.IMAGES -> R.string.search_import_step_images
                },
            ),
            stringResource(R.string.search_import_step_places),
            stringResource(R.string.search_import_step_map),
        )
    val current = stringResource(R.string.search_import_step_active, steps[busy.step.coerceIn(0, steps.lastIndex)])
    TpCard(modifier = Modifier.fillMaxWidth().testTag("import_progress"), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            Text(current, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = TravelTheme.typography.titleMedium)
            Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                steps.forEachIndexed { i, label -> StepRow(i, label, done = i < busy.step, active = i == busy.step) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typical =
                    stringResource(if (busy.kind == ImportSourceKind.TEXT) R.string.search_import_typical_short else R.string.search_import_typical_long)
                Text(
                    if (busy.elapsedSeconds > 0) stringResource(R.string.search_import_elapsed, typical, busy.elapsedSeconds) else typical,
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TpButton(
                    stringResource(R.string.search_import_cancel),
                    onCancel,
                    variant = TpButtonVariant.Text,
                    modifier = Modifier.testTag("import_cancel"),
                    tid = "search.search_import_cancel",
                )
            }
            SkeletonList(lines = 3)
        }
    }
}

@Composable
private fun StepRow(index: Int, label: String, done: Boolean, active: Boolean) {
    val (container, content) =
        when {
            done -> TravelTheme.colors.primary to TravelTheme.colors.onPrimary
            active -> TravelTheme.extendedColors.warningContainer to TravelTheme.extendedColors.onWarningContainer
            else -> TravelTheme.colors.surfaceVariant to TravelTheme.colors.onSurfaceVariant
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.size(SearchDimens.StepBadge)) {
            Box(contentAlignment = Alignment.Center) {
                if (done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                    )
                } else {
                    Text(NumberFormat.getIntegerInstance().format(index + 1), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            if (active) "$label…" else label,
            style = TravelTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (done || active) TravelTheme.colors.onSurface else TravelTheme.colors.onSurfaceVariant,
        )
    }
}
