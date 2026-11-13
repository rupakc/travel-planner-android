package io.github.rupakc.travelplanner.feature.play.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.PlayRoute
import io.github.rupakc.travelplanner.feature.play.PlayRoute as PlayScreenRoute
import io.github.rupakc.travelplanner.feature.play.quiz.QuizRoute
import kotlinx.serialization.Serializable

/** The quiz session destination, internal to this feature: [mode] is `daily|pack|challenge`, [pack] only for `pack`. */
@Serializable
data class QuizDestination(val mode: String, val pack: String? = null)

/**
 * Play tab (game progress, bucket list and, with [quizEnabled] = `features.play >= 1`, the quiz hub) and the quiz session screen.
 * The shell wires this; [onHidden] fires when the game is off. T5.5 registers its head-to-head and board destinations here.
 */
fun NavGraphBuilder.playGraph(onHidden: () -> Unit, navController: NavController? = null, quizEnabled: Boolean = false) {
    composable<PlayRoute> {
        PlayScreenRoute(
            onHidden = onHidden,
            quizEnabled = quizEnabled && navController != null,
            onStartQuiz = { mode, pack -> mode.wire?.let { navController?.navigate(QuizDestination(it, pack?.wire)) } },
        )
    }
    composable<QuizDestination> {
        QuizRoute(onBack = { navController?.popBackStack() }, onHidden = onHidden)
    }
}
