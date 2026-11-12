package io.github.rupakc.travelplanner.feature.play.quiz

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.game.GameAward
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.play.QuizOption
import io.github.rupakc.travelplanner.core.model.play.QuizQuestion
import io.github.rupakc.travelplanner.core.model.play.QuizSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class QuizScreenTest {
    @get:Rule val compose = createComposeRule()

    private val unknown = QuizQuestion(0, "riddle", options = listOf(QuizOption("a", "Alpha"), QuizOption("b", "Beta")))
    private val running = QuizUiState(phase = QuizPhase.Running, questions = listOf(unknown), secondsLeft = 9)

    @Test(timeout = 60_000)
    fun `unknown kind shows the generic question and a pick is sent`() {
        val sent = mutableListOf<QuizEvent>()
        compose.setContent { TravelTheme { QuizScreen(running, onEvent = { sent += it }, onBack = {}) } }
        compose.onNodeWithText("Question").assertIsDisplayed()
        compose.onNodeWithText("9 seconds left").assertIsDisplayed()
        compose.onNodeWithText("Alpha").performClick()
        assertEquals(listOf<QuizEvent>(QuizEvent.Pick("a")), sent)
    }

    @Test(timeout = 60_000)
    fun `the verdict is text and the server points are shown`() {
        val state = running.copy(reveal = Reveal("a", false, "b", 0))
        compose.setContent { TravelTheme { QuizScreen(state, onEvent = {}, onBack = {}) } }
        compose.onNodeWithText("Your answer, incorrect").assertIsDisplayed()
        compose.onNodeWithText("Correct answer").assertIsDisplayed()
        compose.onNodeWithText("See result").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun `summary shows xp only while the game is visible`() {
        val summary = QuizSummary(score = 420, correct = 3, total = 5, xp = GameDelta(xp = 10, awards = listOf(GameAward("quiz_daily", 10))))
        val done = QuizUiState(phase = QuizPhase.Finished, summary = summary, gameVisible = true)
        compose.setContent { TravelTheme { QuizScreen(done, onEvent = {}, onBack = {}) } }
        compose.onNodeWithText("3 of 5 correct").assertIsDisplayed()
        compose.onNodeWithText("+10 XP").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun `hidden game summary has no xp line`() {
        val summary = QuizSummary(score = 420, correct = 3, total = 5, xp = GameDelta(xp = 10, awards = listOf(GameAward("quiz_daily", 10))))
        val done = QuizUiState(phase = QuizPhase.Finished, summary = summary, gameVisible = false)
        compose.setContent { TravelTheme { QuizScreen(done, onEvent = {}, onBack = {}) } }
        compose.onNodeWithText("3 of 5 correct").assertIsDisplayed()
        compose.onNodeWithText("+10 XP").assertDoesNotExist()
    }

    @Test(timeout = 60_000)
    fun `already done and error states offer a way out`() {
        compose.setContent { TravelTheme { QuizScreen(QuizUiState(phase = QuizPhase.AlreadyDone), onEvent = {}, onBack = {}) } }
        compose.onNodeWithText("You've already finished this quiz. Check back later for the next one.").assertIsDisplayed()
        compose.onNodeWithText("Back to Play").assertIsDisplayed()
    }
}
