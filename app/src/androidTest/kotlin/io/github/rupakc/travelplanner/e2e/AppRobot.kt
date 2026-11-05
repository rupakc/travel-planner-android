package io.github.rupakc.travelplanner.e2e

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.feature.auth.R as AuthR
import io.github.rupakc.travelplanner.feature.onboarding.R as OnboardingR

typealias AppRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Drives the real app through its UI. Every wait polls the semantics tree because network work is not an idling resource. */
class AppRobot(val rule: AppRule) {
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun str(
        @StringRes id: Int,
        vararg args: Any,
    ): String = rule.activity.getString(id, *args)

    fun exists(matcher: SemanticsMatcher): Boolean = rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = DEFAULT_TIMEOUT) {
        rule.waitUntil(timeoutMs) { exists(matcher) }
    }

    fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = DEFAULT_TIMEOUT) = waitFor(hasText(text, substring = substring), timeoutMs)

    fun waitForAny(vararg matchers: SemanticsMatcher, timeoutMs: Long = DEFAULT_TIMEOUT): Int {
        var index = -1
        rule.waitUntil(timeoutMs) {
            index = matchers.indexOfFirst { exists(it) }
            index >= 0
        }
        return index
    }

    fun node(text: String, substring: Boolean = false): SemanticsNodeInteraction = rule.onAllNodes(hasText(text, substring = substring)).onFirst()

    /** Clicks the first clickable node showing [text] (scrolling to it when it sits in a scrollable list). */
    fun tap(text: String, substring: Boolean = false) {
        waitForText(text, substring)
        val clickable = rule.onAllNodes(hasText(text, substring = substring) and hasClickAction())
        val target = if (clickable.fetchSemanticsNodes().isNotEmpty()) clickable.onFirst() else node(text, substring)
        runCatching { target.performScrollTo() }
        target.performClick()
        rule.waitForIdle()
    }

    fun tap(
        @StringRes id: Int,
    ) = tap(str(id))

    fun type(label: String, value: String) {
        val field = rule.onAllNodes(hasSetTextAction() and hasText(label, substring = true)).onFirst()
        runCatching { field.performScrollTo() }
        field.performTextClearance()
        field.performTextInput(value)
    }

    fun hasDescription(text: String, substring: Boolean = false) = androidx.compose.ui.test.hasContentDescription(text, substring = substring)

    fun tapDescription(text: String, substring: Boolean = false) {
        waitFor(hasDescription(text, substring))
        rule.onAllNodes(hasDescription(text, substring)).onFirst().performClick()
        rule.waitForIdle()
    }

    /**
     * Scrolls the first scrollable container that can reach a node matching [matcher]; true when found.
     * Lazy lists only compose what is on screen, so absence must be checked this way.
     */
    fun scrollToFind(matcher: SemanticsMatcher): Boolean {
        if (exists(matcher)) {
            runCatching { rule.onAllNodes(matcher, useUnmergedTree = true).onFirst().performScrollTo() }
            return true
        }
        val scrollables = rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())
        val count = scrollables.fetchSemanticsNodes().size
        for (i in 0 until count) {
            val found = runCatching { scrollables[i].performScrollToNode(matcher) }.isSuccess
            if (found) return true
        }
        return false
    }

    fun back() {
        device.pressBack()
        rule.waitForIdle()
    }

    /** Settings is opened from the top app bar on top-level screens. */
    fun openSettings() = tapDescription(str(io.github.rupakc.travelplanner.R.string.nav_settings))

    /** A top-level destination from the navigation bar / rail. */
    fun openTab(@StringRes label: Int) {
        val text = str(label)
        waitForText(text)
        rule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    // ---- flows ---------------------------------------------------------------------------------------------

    /** Points the app at the E2E backend through the Login screen's Server sheet. */
    fun setServer(url: String = E2eConfig.baseUrl) {
        waitForText(str(AuthR.string.auth_login_title))
        if (exists(hasText(str(AuthR.string.auth_server_button, url)))) return
        val unset = str(AuthR.string.auth_server_button_unset)
        if (exists(hasText(unset))) tap(unset) else tap(str(AuthR.string.auth_server_button, "").trimEnd(), substring = true)
        waitForText(str(AuthR.string.auth_server_url))
        type(str(AuthR.string.auth_server_url), url)
        tap(AuthR.string.auth_server_save)
        waitForText(str(AuthR.string.auth_server_button, url))
    }

    fun login(username: String, password: String) {
        type(str(AuthR.string.auth_username), username)
        type(str(AuthR.string.auth_password), password)
        tap(AuthR.string.auth_sign_in)
    }

    /** Server + sign-in; returns when the onboarding welcome (or anything past the gate) is shown. */
    fun signIn(username: String = E2eConfig.ADMIN, password: String = E2eConfig.adminPassword) {
        setServer()
        login(username, password)
        rule.waitUntil(LONG_TIMEOUT) { !exists(hasText(str(AuthR.string.auth_login_title))) }
    }

    /** Answers the six quiz questions with [answers] (one entry per question, empty = Skip), then finishes. */
    fun completeOnboarding(answers: List<List<Int>> = emptyList()) {
        tap(OnboardingR.string.onboarding_get_started)
        repeat(QUESTIONS) { index ->
            val picks = answers.getOrNull(index).orEmpty()
            waitForText(str(OnboardingR.string.onboarding_step, index + 1, QUESTIONS))
            if (picks.isEmpty()) {
                tap(OnboardingR.string.onboarding_skip)
            } else {
                picks.forEach { tap(it) }
                tap(if (index == QUESTIONS - 1) OnboardingR.string.onboarding_see_result else OnboardingR.string.onboarding_next)
            }
        }
        waitForText(str(OnboardingR.string.onboarding_continue))
    }

    /** From the result screen: Continue -> feature picker -> Finish -> (notification rationale). */
    fun finishOnboarding(allowNotifications: Boolean = false) {
        tap(OnboardingR.string.onboarding_continue)
        tap(OnboardingR.string.onboarding_finish)
        val rationale = hasText(str(OnboardingR.string.onboarding_notifications_title))
        val home = hasText(str(io.github.rupakc.travelplanner.R.string.nav_home))
        if (waitForAny(rationale, home, timeoutMs = LONG_TIMEOUT) == 0) {
            if (allowNotifications) {
                tap(OnboardingR.string.onboarding_notifications_allow)
                allowSystemPermission()
            } else {
                tap(OnboardingR.string.onboarding_notifications_later)
            }
        }
        waitFor(home, LONG_TIMEOUT)
    }

    /** Taps "Allow" on the system runtime-permission dialog (UiAutomator; the dialog is outside the app). */
    fun allowSystemPermission() {
        val allow =
            device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button")), 10_000)
                ?: device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button")), 2_000)
        allow?.click()
        device.waitForIdle()
    }

    /** Login + skip-everything onboarding: the fastest way to Home for flows that are not about onboarding. */
    fun signInToHome(username: String = E2eConfig.ADMIN, password: String = E2eConfig.adminPassword) {
        signIn(username, password)
        completeOnboarding()
        finishOnboarding()
    }

    companion object {
        const val QUESTIONS = 6
        const val DEFAULT_TIMEOUT = 20_000L
        const val LONG_TIMEOUT = 60_000L
    }
}
