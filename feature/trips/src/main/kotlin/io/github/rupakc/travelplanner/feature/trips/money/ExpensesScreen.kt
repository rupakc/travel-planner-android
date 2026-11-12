package io.github.rupakc.travelplanner.feature.trips.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.trips.ExpenseCategories
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.feature.trips.R
import io.github.rupakc.travelplanner.feature.trips.trip.TripTabContent

/** The Money tab for `TripTabContent` (`tripsGraph(tabContent = mapOf(TripTab.Money to moneyTabContent()))`). */
fun moneyTabContent(): TripTabContent = { _: Trip, modifier -> ExpensesRoute(modifier) }

@Composable
fun ExpensesRoute(modifier: Modifier = Modifier, viewModel: ExpensesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExpensesScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun ExpensesScreen(state: ExpensesUiState, onEvent: (ExpensesEvent) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> SkeletonList(modifier.padding(TravelTheme.spacing.gutter))
        state.failed -> ErrorState(null, modifier, stringResource(R.string.money_load_failed), onRetry = { onEvent(ExpensesEvent.Retry) })
        else -> ExpenseList(state, onEvent, modifier)
    }
}

@Composable
private fun ExpenseList(state: ExpensesUiState, onEvent: (ExpensesEvent) -> Unit, modifier: Modifier) {
    val days = ExpenseRules.groupByDay(state.rows)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        item(key = "heading") {
            Text(stringResource(R.string.money_title), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        state.notice?.let { notice -> item(key = "notice") { Notice(notice) { onEvent(ExpensesEvent.DismissNotice) } } }
        item(key = "totals") { Totals(state) }
        val form = state.form
        if (form != null) {
            item(key = "form") { ExpenseFormCard(form, state, onEvent) }
        } else {
            item(key = "add") { TpButton(stringResource(R.string.money_add), { onEvent(ExpensesEvent.Add) }, Modifier.fillMaxWidth(), tid = "money.add") }
        }
        if (days.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.money_empty)) }
        }
        days.forEach { day ->
            item(key = "day-${day.day}") {
                Text(
                    day.day ?: stringResource(R.string.money_no_date),
                    style = TravelTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(day.rows, key = { "row-${it.expense.clientId ?: it.expense.id}" }) { row -> ExpenseItem(row, onEvent) }
        }
    }
}

@Composable
private fun Totals(state: ExpensesUiState) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(stringResource(R.string.money_totals), style = TravelTheme.typography.titleSmall)
            if (state.totals.byCurrency.isEmpty()) {
                Text(stringResource(R.string.money_no_totals), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            // One line per currency: amounts of different currencies are never added together.
            state.totals.byCurrency.forEach { (currency, amount) ->
                Text(ExpenseRules.label(amount, currency), style = TravelTheme.typography.bodyLarge)
            }
            state.totals.usdEstimate?.let { Text(stringResource(R.string.money_usd_estimate, it), style = TravelTheme.typography.bodyMedium) }
            if (state.totals.unconverted.isNotEmpty()) {
                Text(
                    stringResource(R.string.money_unconverted, state.totals.unconverted.joinToString(", ")),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
            val rate = state.rate
            val rateValue = rate?.usdToLocal
            if (rate?.local != null && rateValue != null) {
                val date = ExpenseRules.dateOf(rate.asOf)
                Text(
                    if (date !=
                        null
                    ) {
                        stringResource(R.string.money_rate_dated, rateValue.toString(), rate.local!!, date)
                    } else {
                        stringResource(R.string.money_rate, rateValue.toString(), rate.local!!)
                    },
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
            if (state.pending.isNotEmpty()) {
                Text(stringResource(R.string.money_pending_not_counted), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExpenseItem(row: ExpenseRow, onEvent: (ExpensesEvent) -> Unit) {
    val e = row.expense
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ExpenseRules.label(e.amount, e.currency), style = TravelTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(categoryLabel(e.category), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            e.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
            if (row.pending) {
                Text(
                    stringResource(R.string.money_waiting_sync),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.extendedColors.warning,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    TpButton(stringResource(R.string.money_edit), { onEvent(ExpensesEvent.Edit(e.id)) }, variant = TpButtonVariant.Text, tid = "money.edit")
                    TpButton(stringResource(R.string.money_delete), {
                        onEvent(ExpensesEvent.Delete(e.id))
                    }, variant = TpButtonVariant.Text, tid = "money.delete")
                }
            }
        }
    }
}

@Composable
private fun Notice(notice: ExpenseNotice, onDismiss: () -> Unit) {
    val text =
        when (notice) {
            ExpenseNotice.DeleteFailed -> stringResource(R.string.money_delete_failed)
            ExpenseNotice.QueueFailed -> stringResource(R.string.money_queue_failed)
            is ExpenseNotice.Backend -> notice.message
        }
    Surface(color = TravelTheme.colors.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                color = TravelTheme.colors.onErrorContainer,
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TpButton(stringResource(R.string.trip_cancel), onDismiss, variant = TpButtonVariant.Text, tid = "money.notice.dismiss")
        }
    }
}

@Composable
private fun categoryLabel(category: String): String =
    when (category) {
        "flights" -> stringResource(R.string.money_cat_flights)
        "lodging" -> stringResource(R.string.money_cat_lodging)
        "food" -> stringResource(R.string.money_cat_food)
        "transport" -> stringResource(R.string.money_cat_transport)
        "activities" -> stringResource(R.string.money_cat_activities)
        "shopping" -> stringResource(R.string.money_cat_shopping)
        "fees" -> stringResource(R.string.money_cat_fees)
        else -> stringResource(R.string.money_cat_other)
    }

@Composable
private fun problemText(form: ExpenseForm): String? =
    when (form.problem) {
        null -> null
        ExpenseRules.Problem.Amount -> stringResource(R.string.money_err_amount, form.currency)
        ExpenseRules.Problem.Currency -> stringResource(R.string.money_err_currency)
        ExpenseRules.Problem.Category -> stringResource(R.string.money_err_category)
        ExpenseRules.Problem.NoteTooLong -> stringResource(R.string.money_err_note)
        ExpenseRules.Problem.SpentOn -> stringResource(R.string.money_err_date)
        ExpenseRules.Problem.ClientId -> stringResource(R.string.money_err_generic)
        ExpenseRules.Problem.Limit -> stringResource(R.string.money_err_limit)
        ExpenseRules.Problem.Invalid -> form.message ?: stringResource(R.string.money_err_generic)
        ExpenseRules.Problem.Other -> form.message ?: stringResource(R.string.money_err_generic)
    }

@Composable
private fun ExpenseFormCard(form: ExpenseForm, state: ExpensesUiState, onEvent: (ExpensesEvent) -> Unit) {
    val problem = form.problem
    val error = problemText(form)
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(
                stringResource(if (form.editingId == null) R.string.money_add else R.string.money_edit_title),
                style = TravelTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            FormFields(form, state, onEvent, problem, error)
        }
    }
}

@Composable
private fun FormFields(form: ExpenseForm, state: ExpensesUiState, onEvent: (ExpensesEvent) -> Unit, problem: ExpenseRules.Problem?, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = form.amount,
                onValueChange = { onEvent(ExpensesEvent.Amount(it)) },
                label = { Text(stringResource(R.string.money_amount)) },
                isError = problem == ExpenseRules.Problem.Amount,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            CurrencyPicker(form.currency, ExpenseRules.currencyChoices(state.rate?.local, form.currency)) { onEvent(ExpensesEvent.Currency(it)) }
        }
        CategoryChips(form.category) { onEvent(ExpensesEvent.Category(it)) }
        OutlinedTextField(
            value = form.note,
            onValueChange = { onEvent(ExpensesEvent.Note(it)) },
            label = { Text(stringResource(R.string.money_note)) },
            isError = problem == ExpenseRules.Problem.NoteTooLong,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.spentOn,
            onValueChange = { onEvent(ExpensesEvent.SpentOn(it)) },
            label = { Text(stringResource(R.string.money_date)) },
            placeholder = { Text(stringResource(R.string.trips_date_hint)) },
            isError = problem == ExpenseRules.Problem.SpentOn,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                error,
                color = TravelTheme.colors.error,
                style = TravelTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            TpButton(stringResource(R.string.trip_save), { onEvent(ExpensesEvent.Submit) }, loading = form.saving, tid = "money.form.save")
            TpButton(stringResource(R.string.trip_cancel), { onEvent(ExpensesEvent.DismissForm) }, variant = TpButtonVariant.Text, tid = "money.form.cancel")
        }
    }
}

@Composable
private fun CurrencyPicker(current: String, choices: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        TpButton(current, { open = true }, variant = TpButtonVariant.Tonal, tid = "money.currency")
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = {
                    open = false
                    onPick(c)
                })
            }
        }
    }
}

@Composable
private fun CategoryChips(selected: String, onPick: (String) -> Unit) {
    // Rows of two keep long labels readable at large font sizes without a scroller inside the dialog.
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        ExpenseCategories.ALL.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                pair.forEach { c ->
                    TpChip(categoryLabel(c), selected == c, { onPick(c) }, Modifier.weight(1f), tid = "money.category.$c")
                }
            }
        }
    }
}
