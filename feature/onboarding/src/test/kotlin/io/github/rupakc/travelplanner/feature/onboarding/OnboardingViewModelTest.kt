package io.github.rupakc.travelplanner.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import io.github.rupakc.travelplanner.core.model.auth.OnboardingDto
import io.github.rupakc.travelplanner.core.model.auth.OnboardingRequest
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val profile = FakeProfileRepository()
    private val preferences = ScriptedPreferencesRepository(Preferences(username = "alice", nationality = "Indian", interests = listOf("art")))

    private fun OnboardingViewModel.answerAll(vararg answers: Pair<String, List<String>>) {
        val map = answers.toMap()
        onEvent(OnboardingEvent.Start)
        TravellerQuiz.questions.forEach { q ->
            val chosen = map[q.id]
            if (chosen == null) {
                onEvent(OnboardingEvent.Skip)
            } else {
                chosen.forEach { onEvent(OnboardingEvent.Select(it)) }
                onEvent(OnboardingEvent.Next)
            }
        }
    }

    @Test
    fun `starts on welcome, then walks questions with next and back`() =
        runTest {
            val vm = onboardingViewModel(profile, preferences)
            vm.state.test {
                assertEquals(OnboardingStage.WELCOME, awaitItem().stage)
                vm.onEvent(OnboardingEvent.Start)
                assertEquals(OnboardingStage.QUESTION to 0, awaitItem().let { it.stage to it.questionIndex })
                vm.onEvent(OnboardingEvent.Select("work"))
                assertEquals(listOf("work"), awaitItem().currentAnswer)
                vm.onEvent(OnboardingEvent.Next)
                assertEquals(1, awaitItem().questionIndex)
                vm.onEvent(OnboardingEvent.Back)
                val back = awaitItem()
                assertEquals(0, back.questionIndex)
                assertEquals(listOf("work"), back.currentAnswer)
                vm.onEvent(OnboardingEvent.Back)
                assertEquals(OnboardingStage.WELCOME, awaitItem().stage)
            }
        }

    @Test
    fun `single choice replaces, multi choice toggles, unknown ids ignored`() {
        val vm = onboardingViewModel(profile, preferences)
        vm.onEvent(OnboardingEvent.Start)
        vm.onEvent(OnboardingEvent.Select("solo"))
        vm.onEvent(OnboardingEvent.Select("work"))
        vm.onEvent(OnboardingEvent.Select("nonsense"))
        assertEquals(listOf("work"), vm.state.value.currentAnswer)
        vm.onEvent(OnboardingEvent.Next)
        vm.onEvent(OnboardingEvent.Next) // budget unanswered → Next still moves on
        vm.onEvent(OnboardingEvent.Select("food"))
        vm.onEvent(OnboardingEvent.Select("nature"))
        vm.onEvent(OnboardingEvent.Select("food"))
        assertEquals(listOf("nature"), vm.state.value.currentAnswer)
    }

    @Test
    fun `skip clears the answer and moves on`() {
        val vm = onboardingViewModel(profile, preferences)
        vm.onEvent(OnboardingEvent.Start)
        vm.onEvent(OnboardingEvent.Select("work"))
        vm.onEvent(OnboardingEvent.Skip)
        assertEquals(1, vm.state.value.questionIndex)
        assertFalse(TravellerQuiz.COMPANIONS in vm.state.value.answers)
    }

    @Test
    fun `last question leads to the result with primary and secondary types`() {
        val vm = onboardingViewModel(profile, preferences)
        vm.answerAll(TravellerQuiz.COMPANIONS to listOf("family_kids"), TravellerQuiz.NEEDS to listOf("wheelchair"))
        val s = vm.state.value
        assertEquals(OnboardingStage.RESULT, s.stage)
        assertEquals(TravellerType.FAMILY, s.classification?.primary)
        assertEquals(listOf(TravellerType.ACCESSIBLE), s.classification?.secondary)
        vm.onEvent(OnboardingEvent.ChangeAnswers)
        assertEquals(OnboardingStage.QUESTION to 0, vm.state.value.let { it.stage to it.questionIndex })
    }

    @Test
    fun `features are pre-checked from the type and grouped`() {
        val vm = onboardingViewModel(profile, preferences)
        vm.answerAll(TravellerQuiz.COMPANIONS to listOf("work"))
        vm.onEvent(OnboardingEvent.ConfirmResult)
        val s = vm.state.value
        val defaults = DefaultFeatureCatalogue.defaultsFor(listOf(TravellerType.BUSINESS))
        assertEquals(OnboardingStage.FEATURES, s.stage)
        assertEquals(defaults, s.selectedFeatures)
        assertEquals(defaults.toList(), s.recommended)
        assertEquals(FeatureId.entries.toSet() - defaults, s.others.toSet())
    }

    @Test
    fun `toggles survive a peek back at the result but reset when the type changes`() {
        val vm = onboardingViewModel(profile, preferences)
        vm.answerAll(TravellerQuiz.COMPANIONS to listOf("work"))
        vm.onEvent(OnboardingEvent.ConfirmResult)
        vm.onEvent(OnboardingEvent.ToggleFeature(FeatureId.WALKING_TOURS))
        vm.onEvent(OnboardingEvent.Back)
        vm.onEvent(OnboardingEvent.ConfirmResult)
        assertTrue(FeatureId.WALKING_TOURS in vm.state.value.selectedFeatures)

        vm.onEvent(OnboardingEvent.Back)
        vm.onEvent(OnboardingEvent.ChangeAnswers)
        vm.onEvent(OnboardingEvent.Select("family_kids"))
        repeat(TravellerQuiz.questions.size) { vm.onEvent(OnboardingEvent.Next) }
        vm.onEvent(OnboardingEvent.ConfirmResult)
        assertEquals(DefaultFeatureCatalogue.defaultsFor(listOf(TravellerType.FAMILY)), vm.state.value.selectedFeatures)
    }

    /** First-run tail after the picker: Finish → step 3 → (step 4) → step 5. */
    private fun OnboardingViewModel.toFirstTrip(ask: Boolean = false) {
        onEvent(OnboardingEvent.ConfirmResult)
        onEvent(OnboardingEvent.Finish)
        onEvent(OnboardingEvent.HomeNext(askNotifications = ask))
        if (ask) onEvent(OnboardingEvent.NotificationsDone)
    }

    @Test
    fun `finish saves profile and overrides then moves to home and passport without completing`() =
        runTest {
            val vm = onboardingViewModel(profile, preferences)
            vm.answerAll(
                TravellerQuiz.COMPANIONS to listOf("work"),
                TravellerQuiz.NEEDS to listOf("hearing_impairment"),
            )
            vm.onEvent(OnboardingEvent.ConfirmResult)
            vm.onEvent(OnboardingEvent.ToggleFeature(FeatureId.WALKING_TOURS)) // off by default for BUSINESS → on
            vm.onEvent(OnboardingEvent.ToggleFeature(FeatureId.CHAT)) // on by default → off
            vm.onEvent(OnboardingEvent.Finish)

            val s = vm.state.value
            assertEquals(OnboardingStage.HOME_PASSPORT, s.stage)
            assertFalse(s.finished)
            val stored = profile.profileState.value
            assertFalse(stored.onboardingCompleted)
            assertEquals(s.classification!!.primary.name, stored.primaryType)
            assertTrue("ACCESSIBLE" in stored.secondaryTypes)
            assertEquals(FeatureOverrides(enabled = setOf("walking_tours"), disabled = setOf("chat")), profile.overridesState.value)
            assertTrue(preferences.saved.isEmpty())
        }

    @Test
    fun `completing syncs merged preferences and passport, puts done with the traveller type, and marks onboarding complete`() =
        runTest {
            val account = FakeAccountRepository()
            val vm = onboardingViewModel(profile, preferences, account = account)
            vm.answerAll(
                TravellerQuiz.COMPANIONS to listOf("work"),
                TravellerQuiz.BUDGET to listOf("luxury"),
                TravellerQuiz.INTERESTS to listOf("food"),
                TravellerQuiz.NEEDS to listOf("hearing_impairment"),
            )
            vm.onEvent(OnboardingEvent.ConfirmResult)
            vm.onEvent(OnboardingEvent.Finish)
            vm.onEvent(OnboardingEvent.SetPassport("French"))
            vm.onEvent(OnboardingEvent.SetHomeAirport("BLR"))
            vm.onEvent(OnboardingEvent.HomeNext(askNotifications = false))
            assertEquals("BLR", profile.profileState.value.homeAirport)
            assertEquals(OnboardingStage.FIRST_TRIP, vm.state.value.stage)
            vm.onEvent(OnboardingEvent.Complete(planTrip = true))

            val s = vm.state.value
            assertTrue(s.finished)
            assertTrue(s.planFirstTrip)
            assertTrue(profile.profileState.value.onboardingCompleted)
            assertEquals(FIXED_NOW.toEpochMilli(), profile.profileState.value.completedAtEpochMillis)
            val sent = preferences.saved.single()
            assertEquals("French", sent.nationality)
            assertEquals("high", sent.budgetCategory)
            assertEquals(listOf("art", "food"), sent.interests)
            assertEquals(listOf("hearing_impairment"), sent.accessibilityNeeds)
            assertEquals(
                OnboardingRequest(step = 5, done = true, travellerType = s.classification!!.primary.name.lowercase()),
                account.onboardingRequests.last(),
            )
            assertEquals(listOf(1, 2, 3, 5), account.onboardingRequests.filter { !it.done }.map { it.step })
        }

    @Test
    fun `a blank passport keeps the saved nationality`() {
        val vm = onboardingViewModel(profile, preferences)
        vm.answerAll()
        vm.toFirstTrip()
        vm.onEvent(OnboardingEvent.SetPassport("  "))
        vm.onEvent(OnboardingEvent.Complete(planTrip = false))
        assertEquals("Indian", preferences.saved.single().nationality)
        assertFalse(vm.state.value.planFirstTrip)
    }

    @Test
    fun `step 4 is shown only when the permission is still needed`() {
        val vm = onboardingViewModel(profile, preferences)
        vm.answerAll()
        vm.onEvent(OnboardingEvent.ConfirmResult)
        vm.onEvent(OnboardingEvent.Finish)
        vm.onEvent(OnboardingEvent.HomeNext(askNotifications = true))
        assertEquals(OnboardingStage.NOTIFICATIONS, vm.state.value.stage)
        assertFalse(profile.profileState.value.onboardingCompleted)
        vm.onEvent(OnboardingEvent.NotificationsDone)
        assertEquals(OnboardingStage.FIRST_TRIP, vm.state.value.stage)

        val other = onboardingViewModel(FakeProfileRepository(), preferences)
        other.answerAll()
        other.toFirstTrip(ask = false)
        assertEquals(OnboardingStage.FIRST_TRIP, other.state.value.stage)
    }

    @Test
    fun `typing in the airport and passport fields queries suggestions and a pick fills the text`() {
        val search =
            FakeSearchRepository().apply {
                airports = listOf(Airport(iataCode = "LHR", name = "Heathrow", city = "London", country = "United Kingdom"))
            }
        val vm = onboardingViewModel(profile, preferences, search = search)
        vm.answerAll()
        vm.onEvent(OnboardingEvent.ConfirmResult)
        vm.onEvent(OnboardingEvent.Finish)
        vm.onEvent(OnboardingEvent.SetHomeAirport("lon"))
        assertEquals(listOf("lon"), search.airportQueries)
        val airport = vm.state.value.airportSuggestions.first()
        vm.onEvent(OnboardingEvent.PickHomeAirport(airport))
        assertTrue(vm.state.value.homeAirport.startsWith(airport.iataCode.orEmpty()))
        assertTrue(vm.state.value.airportSuggestions.isEmpty())
        vm.onEvent(OnboardingEvent.HomeNext(askNotifications = false))
        assertEquals(airport.iataCode, profile.profileState.value.homeAirport)
    }

    @Test
    fun `preferences failure keeps local data and offers retry or continue`() {
        preferences.saveFailure = AppError.RateLimited("Too many requests — please wait 5 seconds and try again.", 5)
        val account = FakeAccountRepository()
        val vm = onboardingViewModel(profile, preferences, account = account)
        vm.answerAll(TravellerQuiz.COMPANIONS to listOf("solo"))
        vm.toFirstTrip()
        vm.onEvent(OnboardingEvent.Complete(planTrip = false))
        var s = vm.state.value
        assertEquals(SyncError("Too many requests — please wait 5 seconds and try again."), s.syncError)
        assertFalse(s.saving)
        assertFalse(s.finished)
        assertTrue(account.onboardingRequests.none { it.done })
        assertEquals("BUDGET_BACKPACKER", profile.profileState.value.primaryType)

        preferences.saveFailure = null
        vm.onEvent(OnboardingEvent.RetrySync)
        s = vm.state.value
        assertNull(s.syncError)
        assertTrue(s.finished)
        assertEquals(1, preferences.saved.single().adults)
    }

    @Test
    fun `continue without syncing completes`() {
        preferences.saveFailure = AppError.Network()
        val account = FakeAccountRepository()
        val vm = onboardingViewModel(profile, preferences, account = account)
        vm.answerAll()
        vm.toFirstTrip()
        vm.onEvent(OnboardingEvent.Complete(planTrip = false))
        assertEquals(SyncError(null), vm.state.value.syncError)
        vm.onEvent(OnboardingEvent.ContinueWithoutSync)
        assertTrue(vm.state.value.finished)
        assertTrue(preferences.saved.isEmpty())
        assertTrue(account.onboardingRequests.last().done)
    }

    @Test
    fun `skip setup sends skipped with the current step and counts as seen`() {
        val account = FakeAccountRepository()
        val vm = onboardingViewModel(profile, preferences, account = account)
        vm.onEvent(OnboardingEvent.Start)
        vm.onEvent(OnboardingEvent.Select("work"))
        vm.onEvent(OnboardingEvent.SkipAll)
        assertTrue(vm.state.value.finished)
        assertFalse(vm.state.value.planFirstTrip)
        assertEquals(OnboardingRequest(step = 2, done = true, skipped = true), account.onboardingRequests.last())
        assertTrue(profile.profileState.value.onboardingCompleted)
        assertTrue(preferences.saved.isEmpty())
    }

    @Test
    fun `resumes at the step the server remembers`() {
        val account = FakeAccountRepository()
        account.accountResult = AppResult.Success(AccountDto(user = TestUser, onboarding = OnboardingDto(step = 3)))
        val vm = onboardingViewModel(profile, preferences, account = account)
        val s = vm.state.value
        assertEquals(OnboardingStage.HOME_PASSPORT, s.stage)
        assertTrue(s.resumed)
        assertTrue(account.onboardingRequests.isEmpty())
        assertEquals("Indian", s.passport)
    }

    @Test
    fun `a 404 or an error from the account starts at the welcome`() {
        val account = FakeAccountRepository()
        account.accountResult = AppResult.Failure(AppError.NotFound())
        val vm = onboardingViewModel(profile, preferences, account = account)
        assertEquals(OnboardingStage.WELCOME, vm.state.value.stage)
        assertFalse(vm.state.value.resumed)
    }

    @Test
    fun `without an account repository the flow stays local`() {
        val vm = onboardingViewModel(profile, preferences, account = null)
        vm.answerAll()
        vm.toFirstTrip()
        vm.onEvent(OnboardingEvent.Complete(planTrip = false))
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `telemetry carries step views, next, done and the result with enum and int props only`() {
        val telemetry = FakeTelemetry()
        val vm = onboardingViewModel(profile, preferences, telemetry = telemetry)
        vm.answerAll(TravellerQuiz.COMPANIONS to listOf("work"))
        vm.toFirstTrip()
        vm.onEvent(OnboardingEvent.Complete(planTrip = false))
        val steps = telemetry.events.filter { it.first == "onboarding_step" }.map { it.second }
        assertTrue(mapOf("step" to 1, "action" to "view") in steps)
        assertTrue(mapOf("step" to 1, "action" to "next") in steps)
        assertTrue(mapOf("step" to 3, "action" to "view") in steps)
        assertTrue(mapOf("step" to 5, "action" to "done") in steps)
        val result = telemetry.events.single { it.first == "onboarding_result" }.second
        assertEquals("business", result["type"])
        assertTrue(result["secondary"] is Int)
        assertTrue(telemetry.events.flatMap { it.second.values }.all { it is Int || it is String })
    }

    @Test
    fun `edit mode starts from saved answers and toggles, never asks or re-completes or calls the server`() {
        val existing =
            TravellerProfile(
                onboardingCompleted = true,
                primaryType = "BUSINESS",
                answers = mapOf(TravellerQuiz.COMPANIONS to listOf("work")),
                completedAtEpochMillis = 1L,
            )
        val repo = FakeProfileRepository(existing).apply { overridesState.value = FeatureOverrides(disabled = setOf("sim")) }
        val account = FakeAccountRepository()
        val vm = onboardingViewModel(repo, preferences, SavedStateHandle(mapOf("fromSettings" to true)), account)
        assertTrue(vm.state.value.fromSettings)
        vm.onEvent(OnboardingEvent.Start)
        assertEquals(listOf("work"), vm.state.value.currentAnswer)
        repeat(TravellerQuiz.questions.size) { vm.onEvent(OnboardingEvent.Next) }
        vm.onEvent(OnboardingEvent.ConfirmResult)
        assertFalse(FeatureId.SIM in vm.state.value.selectedFeatures)
        vm.onEvent(OnboardingEvent.Finish)
        assertTrue(vm.state.value.finished)
        assertEquals(1L, repo.profileState.value.completedAtEpochMillis)
        assertEquals(FeatureOverrides(disabled = setOf("sim")), repo.overridesState.value)
        assertTrue(account.onboardingRequests.isEmpty())
        assertTrue(account.calls.isEmpty())
    }

    @Test
    fun `progress survives process death`() {
        val saved = SavedStateHandle()
        val first = onboardingViewModel(profile, preferences, saved)
        first.onEvent(OnboardingEvent.Start)
        first.onEvent(OnboardingEvent.Select("friends"))
        first.onEvent(OnboardingEvent.Next)
        first.onEvent(OnboardingEvent.Select("shoestring"))

        val restored = onboardingViewModel(profile, preferences, saved.afterProcessDeath()).state.value
        assertEquals(OnboardingStage.QUESTION, restored.stage)
        assertEquals(1, restored.questionIndex)
        assertEquals(listOf("shoestring"), restored.currentAnswer)
        assertEquals(listOf("friends"), restored.answers[TravellerQuiz.COMPANIONS])
    }

    @Test
    fun `feature toggles, step 3 text and the notification step survive process death`() {
        val saved = SavedStateHandle()
        val first = onboardingViewModel(profile, preferences, saved)
        first.answerAll(TravellerQuiz.COMPANIONS to listOf("work"))
        first.onEvent(OnboardingEvent.ConfirmResult)
        first.onEvent(OnboardingEvent.ToggleFeature(FeatureId.WALKING_TOURS))

        val secondHandle = saved.afterProcessDeath()
        val second = onboardingViewModel(profile, preferences, secondHandle)
        assertEquals(OnboardingStage.FEATURES, second.state.value.stage)
        assertTrue(FeatureId.WALKING_TOURS in second.state.value.selectedFeatures)
        second.onEvent(OnboardingEvent.Finish)
        second.onEvent(OnboardingEvent.SetPassport("Spanish"))
        second.onEvent(OnboardingEvent.HomeNext(askNotifications = true))
        assertEquals(OnboardingStage.NOTIFICATIONS, second.state.value.stage)

        val third = onboardingViewModel(profile, preferences, secondHandle.afterProcessDeath())
        assertEquals(OnboardingStage.NOTIFICATIONS, third.state.value.stage)
        assertEquals("Spanish", third.state.value.passport)
        third.onEvent(OnboardingEvent.NotificationsDone)
        assertEquals(OnboardingStage.FIRST_TRIP, third.state.value.stage)
    }

    @Test
    fun `a permission result that arrives while restoring is replayed`() {
        val saved = SavedStateHandle()
        val first = onboardingViewModel(profile, preferences, saved)
        first.answerAll()
        first.onEvent(OnboardingEvent.ConfirmResult)
        first.onEvent(OnboardingEvent.Finish)
        first.onEvent(OnboardingEvent.HomeNext(askNotifications = true))

        val gate = CompletableDeferred<Unit>()
        val restoring = onboardingViewModel(GatedProfileRepository(profile, gate), preferences, saved.afterProcessDeath())
        assertEquals(OnboardingStage.LOADING, restoring.state.value.stage)
        restoring.onEvent(OnboardingEvent.NotificationsDone)
        gate.complete(Unit)
        assertEquals(OnboardingStage.FIRST_TRIP, restoring.state.value.stage)
    }

    @Test
    fun `sync error never shows client-side exception text`() {
        preferences.saveFailure = AppError.Decoding("Unexpected JSON token at offset 0")
        val vm = onboardingViewModel(profile, preferences)
        vm.answerAll()
        vm.toFirstTrip()
        vm.onEvent(OnboardingEvent.Complete(planTrip = false))
        assertEquals(SyncError(null), vm.state.value.syncError)
    }
}
