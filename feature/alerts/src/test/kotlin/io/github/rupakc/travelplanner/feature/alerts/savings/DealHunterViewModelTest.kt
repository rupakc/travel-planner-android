package io.github.rupakc.travelplanner.feature.alerts.savings

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.game.DealHunterState
import io.github.rupakc.travelplanner.core.model.game.BadgeProgress
import io.github.rupakc.travelplanner.core.model.game.GameMe
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DealHunterViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val game = FakeGameRepository()
    private val events = FakeGameEvents()

    @Test
    fun `the track follows hide_game live, even when it was hidden at start`() {
        game.meResult = AppResult.Success(GameMe(badges = listOf(BadgeProgress("deal_hunter_1", progress = 1, target = 4))))
        events.setVisible(false)
        val vm = DealHunterViewModel(game, events)
        assertEquals(DealHunterState.Hidden, vm.state.value)
        events.setVisible(true)
        assertTrue(vm.state.value is DealHunterState.Next)
        events.setVisible(false)
        assertEquals(DealHunterState.Hidden, vm.state.value)
    }
}
