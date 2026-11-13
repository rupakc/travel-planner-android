package io.github.rupakc.travelplanner.feature.alerts.savings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.GameRepository
import io.github.rupakc.travelplanner.core.domain.game.DealHunterState
import io.github.rupakc.travelplanner.core.domain.game.Delight
import io.github.rupakc.travelplanner.core.ui.game.DealHunterTrack
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Deal-hunter progress track of the Alerts header (web `DealHunterTrack`): `GET /game/me` + the cached catalog.
 * [DealHunterState.Hidden] while the game is hidden (`hide_game`, disabled) or either call fails; nothing is shown then.
 */
@HiltViewModel
class DealHunterViewModel
    @Inject
    constructor(
        private val game: GameRepository,
        private val events: GameEvents,
    ) : ViewModel() {
        private val mutable = MutableStateFlow<DealHunterState>(DealHunterState.Hidden)
        val state: StateFlow<DealHunterState> = mutable.asStateFlow()

        init {
            viewModelScope.launch {
                val me = (game.me() as? AppResult.Success)?.value
                val catalog = (game.catalog() as? AppResult.Success)?.value
                // Reactive: toggling hide_game updates the track without recreating the screen.
                events.visible.collect { visible -> mutable.value = Delight.dealHunter(me, catalog, visible) }
            }
        }
    }

/** Route of the track; renders nothing unless there is progress to show. */
@Composable
fun DealHunterRoute(modifier: Modifier = Modifier, viewModel: DealHunterViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    DealHunterTrack(state, modifier)
}
