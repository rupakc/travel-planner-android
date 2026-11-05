package io.github.rupakc.travelplanner.feature.plan.myplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.domain.plan.BudgetStatus
import io.github.rupakc.travelplanner.core.domain.plan.CostCategory
import io.github.rupakc.travelplanner.core.domain.plan.CostPart
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.model.PlanItineraryUi
import io.github.rupakc.travelplanner.feature.plan.model.PlanCategory
import io.github.rupakc.travelplanner.feature.plan.model.SelectionEntry
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroup
import io.github.rupakc.travelplanner.feature.plan.ui.ConfirmDialog
import io.github.rupakc.travelplanner.feature.plan.ui.CostCard
import io.github.rupakc.travelplanner.feature.plan.ui.itineraryItems
import io.github.rupakc.travelplanner.feature.plan.ui.selectionGroups

/**
 * My Plan (web drawer as a screen): plan name, saved / unsaved status, cost and budget,
 * grouped selections with remove (Undo), itinerary, and Save / Save as new / New + export.
 */
@Composable
internal fun MyPlanScreen(
    state: MyPlanUiState,
    onEvent: (MyPlanEvent) -> Unit,
    onOpenLink: (String) -> Unit,
    onBack: () -> Unit,
    onOpenTrips: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state.content
    Scaffold(
        modifier = modifier,
        topBar = {
            TpTopBar(
                title = stringResource(R.string.plan_my_plan_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenTrips) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.plan_open_trips))
                    }
                },
            )
        },
        bottomBar = { if (content != null) SaveBar(content, state.isSaving, onEvent) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (content == null) {
                SkeletonList(Modifier.padding(TravelTheme.spacing.gutter))
            } else {
                PlanBody(content, onEvent, onOpenLink)
            }
        }
    }
    MyPlanMessages(state.message, snackbar, onEvent)
    if (state.confirmNewPlan) {
        ConfirmDialog(
            stringResource(R.string.plan_confirm_new_title),
            stringResource(R.string.plan_confirm_new_message),
            stringResource(R.string.plan_confirm_new),
            onConfirm = { onEvent(MyPlanEvent.ConfirmNewPlan) },
            onDismiss = { onEvent(MyPlanEvent.DismissNewPlan) },
        )
    }
}

@Composable
private fun PlanBody(content: MyPlanContent, onEvent: (MyPlanEvent) -> Unit, onOpenLink: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
    ) {
        item(key = "name", contentType = "name") { NameBlock(content, onEvent) }
        if (content.isEmpty) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    title = stringResource(R.string.plan_my_plan_empty_title),
                    message = stringResource(R.string.plan_my_plan_empty_message),
                )
            }
            return@LazyColumn
        }
        item(key = "cost", contentType = "cost") {
            CostCard(content.costParts, content.budgetStatus, content.hasTransport, Modifier.padding(top = TravelTheme.spacing.md))
        }
        item(key = "export", contentType = "export") { ExportRow(onEvent) }
        selectionGroups(content.groups, onOpenLink, onRemove = { removal, title -> onEvent(MyPlanEvent.Remove(removal, title)) })
        itineraryItems(content.itinerary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameBlock(content: MyPlanContent, onEvent: (MyPlanEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        OutlinedTextField(
            value = content.name,
            onValueChange = { onEvent(MyPlanEvent.NameChanged(it)) },
            label = { Text(stringResource(R.string.plan_name_label)) },
            singleLine = true,
            shape = TravelShape.Input,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            if (content.planId != null) StatusTag(stringResource(R.string.plan_editing_saved), warning = true)
            if (!content.isEmpty) {
                StatusTag(stringResource(if (content.dirty) R.string.plan_unsaved else R.string.plan_all_saved), warning = content.dirty)
            }
            content.budgetUsd?.takeIf { it > 0 }?.let {
                Text(
                    stringResource(R.string.plan_budget, formatPrice(it)),
                    style = TravelTheme.typography.labelLarge,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, warning: Boolean) {
    Surface(
        color = if (warning) TravelTheme.extendedColors.warningContainer else TravelTheme.extendedColors.successContainer,
        contentColor = if (warning) TravelTheme.extendedColors.onWarningContainer else TravelTheme.extendedColors.onSuccessContainer,
        shape = TravelShape.Pill,
    ) {
        Text(
            text,
            style = TravelTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportRow(onEvent: (MyPlanEvent) -> Unit) {
    FlowRow(
        Modifier.padding(top = TravelTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        TpButton(
            stringResource(R.string.plan_action_calendar),
            onClick = { onEvent(MyPlanEvent.ExportCalendar) },
            variant = TpButtonVariant.Tonal,
            leadingIcon = Icons.Filled.DateRange,
        )
        TpButton(
            stringResource(R.string.plan_action_share_text),
            onClick = { onEvent(MyPlanEvent.ShareAsText) },
            variant = TpButtonVariant.Tonal,
            leadingIcon = Icons.Filled.Share,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveBar(content: MyPlanContent, saving: Boolean, onEvent: (MyPlanEvent) -> Unit) {
    Surface(tonalElevation = TravelTheme.spacing.xxs) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TpButton(
                    stringResource(
                        when {
                            saving -> R.string.plan_saving
                            content.planId != null -> R.string.plan_update
                            else -> R.string.plan_save
                        },
                    ),
                    onClick = { onEvent(MyPlanEvent.Save) },
                    loading = saving,
                    enabled = !content.isEmpty,
                    modifier = Modifier.weight(1f),
                )
                if (content.planId != null) {
                    TpButton(
                        stringResource(R.string.plan_save_as_new),
                        onClick = { onEvent(MyPlanEvent.SaveAsNew) },
                        variant = TpButtonVariant.Secondary,
                        enabled = !saving,
                    )
                }
                TpButton(stringResource(R.string.plan_new), onClick = { onEvent(MyPlanEvent.NewPlan) }, variant = TpButtonVariant.Text, enabled = !saving)
            }
        }
    }
}

@Composable
private fun MyPlanMessages(message: MyPlanMessage?, snackbar: SnackbarHostState, onEvent: (MyPlanEvent) -> Unit) {
    val undo = stringResource(R.string.plan_undo)
    val text =
        when (message) {
            null -> null
            MyPlanMessage.Saved -> stringResource(R.string.plan_saved)
            MyPlanMessage.Updated -> stringResource(R.string.plan_updated)
            MyPlanMessage.LoginToSave -> stringResource(R.string.plan_login_to_save)
            is MyPlanMessage.SaveFailed -> message.error.message ?: stringResource(R.string.plan_save_failed)
            is MyPlanMessage.Removed -> message.title?.let { stringResource(R.string.plan_removed, it) } ?: stringResource(R.string.plan_removed_generic)
            MyPlanMessage.NothingToExport -> stringResource(R.string.plan_nothing_to_export)
        }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        val removed = message is MyPlanMessage.Removed
        val result =
            snackbar.showSnackbar(
                text,
                actionLabel = if (removed) undo else null,
                duration = if (removed) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        if (removed && result == SnackbarResult.ActionPerformed) onEvent(MyPlanEvent.UndoRemove)
        onEvent(MyPlanEvent.MessageShown)
    }
}

// --- previews --------------------------------------------------------------------------------

private val previewPlan =
    MyPlanContent(
        planId = 3,
        name = "The Foodie Tokyo Journey",
        dirty = true,
        destination = "Tokyo",
        budgetUsd = 3000.0,
        groups =
            listOf(
                SelectionGroup(PlanCategory.HOTEL, listOf(SelectionEntry("h", "Park Hyatt Tokyo", listOf("Shinjuku"), 450.0, perNight = true))),
                SelectionGroup(PlanCategory.ACTIVITIES, listOf(SelectionEntry("a", "Sushi making class", listOf("Tsukiji"), 80.0))),
            ),
        itinerary = PlanItineraryUi(),
        costParts = listOf(CostPart(CostCategory.HOTEL, 1800.0), CostPart(CostCategory.ACTIVITIES, 80.0)),
        totalUsd = 1880.0,
        budgetStatus = BudgetStatus(over = false, amount = 1120),
        hasTransport = true,
        itemCount = 2,
    )

@Preview(name = "My Plan — light")
@Composable
private fun MyPlanLightPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { MyPlanScreen(MyPlanUiState(content = previewPlan), {}, {}, {}, {}) }
}

@Preview(name = "My Plan — dark")
@Composable
private fun MyPlanDarkPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { MyPlanScreen(MyPlanUiState(content = previewPlan), {}, {}, {}, {}) }
}

@Preview(name = "My Plan — empty")
@Composable
private fun MyPlanEmptyPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        MyPlanScreen(
            MyPlanUiState(content = previewPlan.copy(planId = null, groups = emptyList(), costParts = emptyList(), itemCount = 0, dirty = false)),
            {},
            {},
            {},
            {},
        )
    }
}
