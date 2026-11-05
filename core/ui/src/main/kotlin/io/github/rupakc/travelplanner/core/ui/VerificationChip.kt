package io.github.rupakc.travelplanner.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.Verification
import io.github.rupakc.travelplanner.core.model.section.VerificationLevel

/**
 * Section-header verification chip (web VerificationBadge). Uses the server
 * `label` when present, else a localized default per level. [onClick] opens the
 * details (summary + checks) — the popover is owned by the results feature.
 */
@Composable
fun VerificationChip(verification: Verification, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val level = verification.levelKind
    val fallback =
        stringResource(
            when {
                verification.pending == true -> R.string.verification_pending
                level == VerificationLevel.VERIFIED -> R.string.verification_verified
                level == VerificationLevel.PARTIAL -> R.string.verification_partial
                level == VerificationLevel.CONFLICT -> R.string.verification_conflict
                else -> R.string.verification_unverified
            },
        )
    val label = verification.label?.takeIf(String::isNotBlank) ?: fallback
    val (icon, tint) =
        when (level) {
            VerificationLevel.VERIFIED -> Icons.Filled.CheckCircle to TravelTheme.extendedColors.success
            VerificationLevel.PARTIAL -> Icons.Filled.CheckCircle to TravelTheme.extendedColors.info
            VerificationLevel.CONFLICT -> Icons.Filled.Warning to TravelTheme.extendedColors.warning
            VerificationLevel.UNVERIFIED, null -> Icons.Filled.Info to TravelTheme.colors.onSurfaceVariant
        }
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = TravelTheme.typography.labelSmall) },
        modifier = modifier,
        shape = TravelShape.Pill,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderWidth = 1.dp),
    )
}
