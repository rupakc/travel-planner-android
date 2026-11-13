package io.github.rupakc.travelplanner.feature.play

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme

/** The Play tab's head-to-head and leaderboard cards, drawn after the quiz hub (the `extraItems` of `PlayScreen`). */
fun LazyListScope.playH2hItems(openH2h: Int, onOpenH2h: () -> Unit, onOpenBoards: () -> Unit) {
    item(key = "h2h", contentType = "h2h") { H2hCard(openH2h, onOpenH2h) }
    item(key = "boards", contentType = "boards") { BoardsCard(onOpenBoards) }
}

@Composable
private fun H2hCard(open: Int, onOpen: () -> Unit) {
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.play_extra_h2h_title), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.play_extra_h2h_hint), style = TravelTheme.typography.bodyMedium)
        if (open > 0) Text(pluralStringResource(R.plurals.play_extra_h2h_open, open, open), style = TravelTheme.typography.labelLarge)
        TpButton(stringResource(R.string.play_extra_h2h_open_button), onOpen, variant = TpButtonVariant.Tonal, tid = "play.h2h.open")
    }
}

@Composable
private fun BoardsCard(onOpen: () -> Unit) {
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.play_extra_board_title), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.play_extra_board_hint), style = TravelTheme.typography.bodyMedium)
        TpButton(stringResource(R.string.play_extra_board_button), onOpen, variant = TpButtonVariant.Tonal, tid = "play.board.open")
    }
}
