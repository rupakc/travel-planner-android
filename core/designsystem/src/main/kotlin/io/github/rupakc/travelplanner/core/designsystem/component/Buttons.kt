package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme

enum class TpButtonVariant { Primary, Secondary, Tonal, Text, Destructive }

/**
 * App button. [text] is a resolved string (`stringResource(...)`). While [loading]
 * it shows a spinner and ignores clicks. Min height 48 dp.
 */
@Composable
fun TpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TpButtonVariant = TpButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val minHeight = Modifier.defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget)
    val content: @Composable () -> Unit = { ButtonContent(text, loading, leadingIcon) }
    val click = { if (!loading) onClick() }
    when (variant) {
        TpButtonVariant.Primary -> {
            Button(click, modifier.then(minHeight), enabled, shape = TravelShape.Input) { content() }
        }

        TpButtonVariant.Destructive -> {
            Button(
                click,
                modifier.then(minHeight),
                enabled,
                shape = TravelShape.Input,
                colors = ButtonDefaults.buttonColors(containerColor = TravelTheme.colors.error, contentColor = TravelTheme.colors.onError),
            ) { content() }
        }

        TpButtonVariant.Secondary -> {
            OutlinedButton(click, modifier.then(minHeight), enabled, shape = TravelShape.Input) { content() }
        }

        TpButtonVariant.Tonal -> {
            FilledTonalButton(click, modifier.then(minHeight), enabled, shape = TravelShape.Input) { content() }
        }

        TpButtonVariant.Text -> {
            TextButton(click, modifier.then(minHeight), enabled, shape = TravelShape.Input) { content() }
        }
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean, leadingIcon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(TravelTheme.spacing.sm))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(TravelTheme.spacing.sm))
        }
        Text(text, Modifier.readsWithoutEmoji(text))
    }
}
