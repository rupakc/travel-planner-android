package io.github.rupakc.travelplanner

import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import org.junit.Assert.assertEquals
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
}
