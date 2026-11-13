package io.github.rupakc.travelplanner.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant

/**
 * Toggle for "Add to plan" / "In your plan". [itemName] makes the accessible label
 * specific ("Add Hotel Lisboa to My Plan"). Wire [onToggle] to `CurrentPlanStore.toggle`.
 */
@Composable
fun AddToPlanButton(selected: Boolean, itemName: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val a11y = stringResource(if (selected) R.string.plan_remove_a11y else R.string.plan_add_a11y, itemName)
    TpButton(
        text = stringResource(if (selected) R.string.plan_added else R.string.plan_add),
        onClick = onToggle,
        modifier =
            modifier.semantics {
                contentDescription = a11y
                role = Role.Switch
                this.selected = selected
            },
        variant = if (selected) TpButtonVariant.Tonal else TpButtonVariant.Secondary,
        leadingIcon = if (selected) Icons.Filled.Check else Icons.Filled.Add,
        tid = "ui.plan_added",
    )
}
