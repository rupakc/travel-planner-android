package io.github.rupakc.travelplanner.core.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.ui.R

/**
 * Celebrates a [GameDelta]: an XP snackbar per award on [snackbarHostState], then a sheet for a level-up and for
 * each earned badge. [enabled] false (`hide_game`, game disabled) shows nothing. [onConsumed] is called once the
 * delta has been taken over (the caller then clears it). Under "remove animations"
 * (`ANIMATOR_DURATION_SCALE == 0`) the sheets are plain dialogs and nothing animates by itself.
 */
@Composable
fun GameDeltaHost(
    delta: GameDelta?,
    snackbarHostState: SnackbarHostState,
    enabled: Boolean,
    onConsumed: () -> Unit,
) {
    var sheets by remember { mutableStateOf(emptyList<GameCelebration>()) }
    val consumed by rememberUpdatedState(onConsumed)
    val resources = LocalResources.current
    val xpTemplate = stringResource(R.string.game_toast_xp)
    LaunchedEffect(delta, enabled) {
        if (delta == null) return@LaunchedEffect
        val c = GameCelebrations.of(delta, enabled)
        sheets = sheets + c.sheets
        consumed()
        c.snackbars.filterIsInstance<GameCelebration.Xp>().forEach { xp ->
            val rule = GameCopy.ruleName(xp.rule)?.let { resources.getString(it) } ?: GameCopy.words(xp.rule)
            snackbarHostState.showSnackbar(String.format(xpTemplate, xp.points, rule))
        }
    }
    sheets.firstOrNull()?.let { current ->
        CelebrationSheet(current, onDismiss = { sheets = sheets.drop(1) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CelebrationSheet(item: GameCelebration, onDismiss: () -> Unit) {
    val reduced = rememberReduceMotion()
    if (reduced) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TpTextButton(onClick = onDismiss, tid = "ui.game_celebration_ok") { Text(stringResource(R.string.game_celebration_ok)) } },
            icon = { Icon(Icons.Filled.Star, contentDescription = null, tint = TravelTheme.colors.primary) },
            title = {
                Text(
                    title(item),
                    Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            },
            text = { subtitle(item)?.let { Text(it) } },
        )
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box {
                if (item is GameCelebration.Badge) ConfettiBurst(Modifier.matchParentSize())
                Column(
                    Modifier.fillMaxWidth().padding(TravelTheme.spacing.xl).semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = TravelTheme.colors.primary, modifier = Modifier.size(48.dp))
                    Text(title(item), style = TravelTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    subtitle(item)?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
                    TpTextButton(onClick = onDismiss, tid = "ui.game_celebration_ok_2") { Text(stringResource(R.string.game_celebration_ok)) }
                }
            }
        }
    }
}

@Composable
private fun title(item: GameCelebration): String =
    when (item) {
        is GameCelebration.LevelUp -> stringResource(R.string.game_toast_level, item.to)
        is GameCelebration.Badge -> badgeNameText(item.id)
        is GameCelebration.Xp -> ruleNameText(item.rule)
    }

@Composable
private fun subtitle(item: GameCelebration): String? =
    when (item) {
        is GameCelebration.LevelUp -> levelNameText(item.to)
        is GameCelebration.Badge -> stringResource(R.string.game_toast_badge) + (badgeDescText(item.id)?.let { ": $it" } ?: "")
        is GameCelebration.Xp -> null
    }
