package io.github.rupakc.travelplanner.feature.social.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.component.tpSelectable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.feature.social.R

/**
 * The report dialog shared by profiles and (T5.3) ideas and comments: pick one of the fixed [ReportReason]s, then send.
 * It holds only the picked reason; [sending] disables the buttons while the call runs and [error] is shown as text
 * (backend text as is, or a resolved string) so a failure can be retried without losing the pick.
 */
@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReportReason) -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
    error: String? = null,
) {
    var picked by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        modifier = modifier,
        title = { Text(stringResource(R.string.social_report_title)) },
        text = {
            Column {
                Text(stringResource(R.string.social_report_body), style = TravelTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup().padding(top = TravelTheme.spacing.sm)) {
                    ReportReason.entries.forEach { reason ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = TravelTheme.spacing.minTouchTarget)
                                .tpSelectable(
                                    tid = "social.report_reason",
                                    selected = picked == reason,
                                    enabled = !sending,
                                    role = Role.RadioButton,
                                    onClick = { picked = reason },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = picked == reason, onClick = null, enabled = !sending)
                            Text(stringResource(reason.labelRes()), Modifier.padding(start = TravelTheme.spacing.sm))
                        }
                    }
                }
                if (error != null) {
                    Text(
                        error,
                        Modifier.padding(top = TravelTheme.spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
                        style = TravelTheme.typography.bodySmall,
                        color = TravelTheme.colors.error,
                    )
                }
            }
        },
        confirmButton = {
            TpTextButton(onClick = { picked?.let(onSubmit) }, enabled = picked != null && !sending, tid = "social.social_report_send") {
                Text(stringResource(R.string.social_report_send))
            }
        },
        dismissButton = {
            TpTextButton(onClick = onDismiss, enabled = !sending, tid = "social.social_cancel") { Text(stringResource(R.string.social_cancel)) }
        },
    )
}
