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
import io.github.rupakc.travelplanner.core.designsystem.component.TpAssistChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.Verification
import io.github.rupakc.travelplanner.core.model.section.VerificationLevel

/**
 * The verification level in the app language ("Verificado"). The server `label` is English text for the
 * same level, so it is used only when the level is one this app doesn't know.
 */
@Composable
fun verificationLabel(verification: Verification): String {
    val level = verification.levelKind
    val res =
        when {
            verification.pending == true -> R.string.verification_pending
            level == VerificationLevel.VERIFIED -> R.string.verification_verified
            level == VerificationLevel.PARTIAL -> R.string.verification_partial
            level == VerificationLevel.CONFLICT -> R.string.verification_conflict
            level == VerificationLevel.UNVERIFIED -> R.string.verification_unverified
            else -> null
        }
    return res?.let { stringResource(it) } ?: verification.label?.takeIf(String::isNotBlank) ?: stringResource(R.string.verification_unverified)
}

/**
 * Section-header verification chip (web VerificationBadge) with a localized label per level
 * ([verificationLabel]). [onClick] opens the details (summary + checks) — the popover is owned by the results feature.
 */
@Composable
fun VerificationChip(verification: Verification, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val level = verification.levelKind
    val label = verificationLabel(verification)
    val (icon, tint) =
        when (level) {
            VerificationLevel.VERIFIED -> Icons.Filled.CheckCircle to TravelTheme.extendedColors.success
            VerificationLevel.PARTIAL -> Icons.Filled.CheckCircle to TravelTheme.extendedColors.info
            VerificationLevel.CONFLICT -> Icons.Filled.Warning to TravelTheme.extendedColors.warning
            VerificationLevel.UNVERIFIED, null -> Icons.Filled.Info to TravelTheme.colors.onSurfaceVariant
        }
    TpAssistChip(
        onClick = onClick,
        label = { Text(label, style = TravelTheme.typography.labelSmall) },
        modifier = modifier,
        shape = TravelShape.Pill,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderWidth = 1.dp),
        tid = "ui.verification_chip_assist_chip",
    )
}
