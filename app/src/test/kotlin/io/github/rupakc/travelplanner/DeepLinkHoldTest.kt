package io.github.rupakc.travelplanner

import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.navigation.DeepLinkHold
import io.github.rupakc.travelplanner.navigation.PendingDeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The gate's deep-link bookkeeping, driven the way TravelApp's gate effect and new-intent listener drive it. */
class DeepLinkHoldTest {
    private class Store : PendingDeepLinks {
        var value: String? = null

        override fun keep(link: String) {
            value = link
        }

        override fun take(): String? = value.also { value = null }

        override fun clear() {
            value = null
        }
    }

    private val plan = "travelplanner://plan/7"
    private val share = "travelplanner://share/tok"

    @Test
    fun `cold start signed out - kept on Login, replayed once after sign-in`() {
        val store = Store()
        val hold = DeepLinkHold(store, plan)
        assertNull(hold.onGateCheck(AuthGate.LOADING, null, onGateScreen = false)) // splash: PlanDetail(7) under the gate
        assertNull(hold.onGateCheck(AuthGate.SIGNED_OUT, LoginRoute, onGateScreen = false)) // redirected to Login
        assertNull(hold.onGateCheck(AuthGate.SIGNED_OUT, null, onGateScreen = true)) // Login shown
        assertEquals(plan, hold.onGateCheck(AuthGate.READY, HomeRoute, onGateScreen = true)) // signed in: Home, then the plan
        assertNull(hold.onGateCheck(AuthGate.READY, null, onGateScreen = false)) // plan shown
        assertNull(store.value)
    }

    @Test
    fun `kept through change password and onboarding`() {
        val store = Store()
        val hold = DeepLinkHold(store, plan)
        hold.onGateCheck(AuthGate.SIGNED_OUT, LoginRoute, onGateScreen = false)
        hold.onGateCheck(AuthGate.MUST_CHANGE_PASSWORD, ChangePasswordRoute, onGateScreen = true)
        hold.onGateCheck(AuthGate.MUST_CHANGE_PASSWORD, null, onGateScreen = true)
        hold.onGateCheck(AuthGate.NEEDS_ONBOARDING, OnboardingRoute(), onGateScreen = true)
        assertEquals(plan, hold.onGateCheck(AuthGate.READY, HomeRoute, onGateScreen = true))
    }

    @Test
    fun `a share link shown signed out is never replayed after Back and sign-in`() {
        val store = Store()
        val hold = DeepLinkHold(store, share)
        assertNull(hold.onGateCheck(AuthGate.SIGNED_OUT, null, onGateScreen = false)) // share viewer is public: shown
        assertNull(hold.onGateCheck(AuthGate.SIGNED_OUT, LoginRoute, onGateScreen = false)) // Back to Home -> Login
        assertNull(hold.onGateCheck(AuthGate.READY, HomeRoute, onGateScreen = true))
    }

    @Test
    fun `a link opened while signed in is not held, even if the session expires on that screen`() {
        val store = Store()
        val hold = DeepLinkHold(store, null)
        hold.onGateCheck(AuthGate.READY, null, onGateScreen = false)
        // New intent for the destination already shown: the destination id does not change, so no gate check runs.
        hold.onIntent(plan)
        hold.afterHandled(AuthGate.READY)
        // The JWT expires: Login, then a (possibly different) user signs in.
        assertNull(hold.onGateCheck(AuthGate.SIGNED_OUT, LoginRoute, onGateScreen = false))
        assertNull(hold.onGateCheck(AuthGate.READY, HomeRoute, onGateScreen = true))
    }

    @Test
    fun `a newer link while signed out replaces the held one`() {
        val store = Store()
        val hold = DeepLinkHold(store, plan)
        hold.onGateCheck(AuthGate.SIGNED_OUT, LoginRoute, onGateScreen = false)
        hold.onIntent("travelplanner://alerts?kind=fare&watch=3")
        hold.afterHandled(AuthGate.SIGNED_OUT)
        hold.onGateCheck(AuthGate.SIGNED_OUT, LoginRoute, onGateScreen = false)
        assertEquals("travelplanner://alerts?kind=fare&watch=3", hold.onGateCheck(AuthGate.READY, HomeRoute, onGateScreen = true))
    }
}
