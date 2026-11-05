package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.R as AppR
import io.github.rupakc.travelplanner.feature.auth.R as AuthR
import io.github.rupakc.travelplanner.feature.chat.R as ChatR
import io.github.rupakc.travelplanner.feature.home.R as HomeR
import io.github.rupakc.travelplanner.feature.onboarding.R as OnboardingR
import io.github.rupakc.travelplanner.feature.plan.R as PlanR
import io.github.rupakc.travelplanner.feature.settings.R as SettingsR
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flow 10: sign out clears the account's data; the second user sees none of the first user's plans, draft or chats. */
@E2eTest
@RunWith(AndroidJUnit4::class)
class SignOutE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()
    private val api = BackendApi()
    private lateinit var robot: AppRobot
    private val stamp = System.currentTimeMillis() % 100_000
    private val adminPlan = "Admin private trip $stamp"
    private val adminDraft = "Admin draft $stamp"
    private val question = "Which language is spoken in Portugal?"

    @Before fun setUp() {
        api.ensureSecondUser()
        val token = api.adminToken()
        api.resetPreferences(token)
        api.createPlan(token, adminPlan, api.lisbonSearchData())
        robot = AppRobot(rule)
    }

    @After fun cleanUp() {
        val token = api.adminToken()
        api.plans(token).map { it.jsonObject }
            .filter { it["name"]?.jsonPrimitive?.content == adminPlan }
            .forEach { api.deletePlan(token, it.getValue("id").jsonPrimitive.content) }
    }

    private fun openChatMyPlan() {
        robot.tapDescription(robot.str(ChatR.string.chat_my_plan) + ",", substring = true)
        robot.waitForText(robot.str(PlanR.string.plan_name_label))
    }

    @Test fun signOut_clearsData_secondUserSeesNothing() {
        robot.signInToHome()
        robot.openTab(AppR.string.nav_plans)
        robot.waitForText(adminPlan, timeoutMs = AppRobot.LONG_TIMEOUT)
        robot.openTab(AppR.string.nav_chat)
        robot.chatSend(question)
        openChatMyPlan()
        robot.type(robot.str(PlanR.string.plan_name_label), adminDraft)
        robot.back()
        robot.waitFor(robot.hasDescription(robot.str(ChatR.string.chat_my_plan) + ",", substring = true))

        // Sign out (confirmed) -> Login.
        robot.openSettings()
        robot.tap(SettingsR.string.settings_sign_out)
        val confirm = hasText(robot.str(SettingsR.string.settings_sign_out)) and hasAnyAncestor(isDialog())
        robot.waitFor(confirm)
        rule.onNode(confirm).performClick()

        robot.waitForText(robot.str(AuthR.string.auth_login_title), timeoutMs = AppRobot.LONG_TIMEOUT)

        // Second user.
        robot.login(E2eConfig.SECOND_USER, E2eConfig.SECOND_PASSWORD)
        rule.waitUntil(AppRobot.LONG_TIMEOUT) { !robot.exists(hasText(robot.str(AuthR.string.auth_login_title))) }
        // A different account onboards with its own traveller profile (regression: it inherited admin's).
        robot.waitForText(robot.str(OnboardingR.string.onboarding_title, robot.brand))
        robot.completeOnboarding()
        robot.finishOnboarding()

        robot.openTab(AppR.string.nav_plans)
        rule.waitForIdle()
        Thread.sleep(SYNC_GRACE_MS)
        assertFalse("admin's plan visible to the second user", robot.exists(hasText(adminPlan)))

        robot.openTab(AppR.string.nav_chat)
        robot.waitForText(robot.str(ChatR.string.chat_title, robot.brand))
        assertFalse("admin's chat visible", robot.exists(hasText(question, substring = true)))
        openChatMyPlan()
        assertFalse("admin's draft visible", robot.exists(hasText(adminDraft, substring = true)))
        robot.back()

        robot.tapDescription(robot.str(AppR.string.nav_global_search))
        robot.typeInFirstField("Admin")
        robot.waitForText(robot.str(HomeR.string.home_search_none_title, "Admin"))
        rule.onAllNodes(hasSetTextAction()).onFirst()
    }

    private companion object {
        const val SYNC_GRACE_MS = 3_000L
    }
}
