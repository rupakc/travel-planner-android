package io.github.rupakc.travelplanner

import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchModes
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.navigation.isTopLevelRoute
import io.github.rupakc.travelplanner.navigation.restoresTabState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home tiles / global search switch tabs instead of stacking duplicates, and keep their arguments. */
class TopLevelRoutesTest {
    @Test
    fun `top-level classes switch tabs, with or without arguments`() {
        assertTrue(isTopLevelRoute(SearchRoute(mode = SearchModes.COMPARE)))
        assertTrue(isTopLevelRoute(ChatRoute(threadId = 3)))
        assertTrue(isTopLevelRoute(AlertsRoute()))
        assertTrue(isTopLevelRoute(HomeRoute))
        assertFalse(isTopLevelRoute(PlanDetailRoute(1)))
        assertFalse(isTopLevelRoute(GlobalSearchRoute))
    }

    @Test
    fun `only plain tab routes restore saved state, so arguments such as the search mode arrive`() {
        assertTrue(restoresTabState(SearchRoute()))
        assertTrue(restoresTabState(AlertsRoute()))
        assertFalse(restoresTabState(SearchRoute(mode = SearchModes.COMPARE)))
        assertFalse(restoresTabState(SearchRoute(recentSearchId = "rs-1")))
        assertFalse(restoresTabState(ChatRoute(threadId = 3)))
    }
}
