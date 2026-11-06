package io.github.rupakc.travelplanner

import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.navigation.startDestinationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthGateTest {
    private val done = TravellerProfile(onboardingCompleted = true)

    @Test
    fun `gate order is login, change password, onboarding, ready`() {
        assertEquals(AuthGate.SIGNED_OUT, gateFor(null, done))
        assertEquals(AuthGate.MUST_CHANGE_PASSWORD, gateFor(Session("t", User("a", requiresPasswordChange = true)), TravellerProfile()))
        assertEquals(AuthGate.NEEDS_ONBOARDING, gateFor(Session("t", User("a")), TravellerProfile()))
        assertEquals(AuthGate.READY, gateFor(Session("t", User("a")), done))
    }

    @Test
    fun `the nav graph starts where the decided gate points, and is not composed while loading`() {
        assertNull(startDestinationFor(AuthGate.LOADING))
        assertEquals(LoginRoute, startDestinationFor(AuthGate.SIGNED_OUT))
        assertEquals(ChangePasswordRoute, startDestinationFor(AuthGate.MUST_CHANGE_PASSWORD))
        assertEquals(OnboardingRoute(), startDestinationFor(AuthGate.NEEDS_ONBOARDING))
        assertEquals(HomeRoute, startDestinationFor(AuthGate.READY))
    }
}
