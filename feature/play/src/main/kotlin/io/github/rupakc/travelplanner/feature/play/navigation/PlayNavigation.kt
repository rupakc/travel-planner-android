package io.github.rupakc.travelplanner.feature.play.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.PlayRoute
import io.github.rupakc.travelplanner.feature.play.PlayRoute as PlayScreenRoute
import io.github.rupakc.travelplanner.feature.play.board.BoardRoute
import io.github.rupakc.travelplanner.feature.play.h2h.H2hCreateRoute
import io.github.rupakc.travelplanner.feature.play.h2h.H2hDetailRoute
import io.github.rupakc.travelplanner.feature.play.h2h.H2hListRoute
import io.github.rupakc.travelplanner.feature.play.playH2hItems
import io.github.rupakc.travelplanner.feature.play.quiz.QuizRoute
import kotlinx.serialization.Serializable

/** The quiz session destination, internal to this feature: [mode] is `daily|pack|challenge`, [pack] only for `pack`. */
@Serializable
data class QuizDestination(val mode: String, val pack: String? = null, val sessionId: String? = null)

/** The head-to-head list. */
@Serializable
data object H2hListDestination

/** A new head-to-head challenge. */
@Serializable
data object H2hCreateDestination

/** One head-to-head match; also the target of `travelplanner://play/h2h/{matchId}` (push and in-app notification taps). */
@Serializable
data class H2hDestination(val matchId: String)

/** The leaderboards. */
@Serializable
data object BoardsDestination

/** `travelplanner://play/h2h` + `/{matchId}` (matches [DeepLinks.H2H_PATTERN]). */
internal val H2H_DEEP_LINK_BASE = DeepLinks.H2H_PATTERN.substringBefore("/{")

/**
 * Play tab (game progress, bucket list and, with [quizEnabled] = `features.play >= 1`, the quiz hub) and the quiz session screen.
 * The shell wires this; [onHidden] fires when the game is off. Head-to-head, boards and the match deep link live here too, all only
 * reachable with [quizEnabled]; [onOpenProfile] opens the caller's profile (leaderboard opt-in), null when social is off.
 */
fun NavGraphBuilder.playGraph(
    onHidden: () -> Unit,
    navController: NavController? = null,
    quizEnabled: Boolean = false,
    onOpenProfile: (() -> Unit)? = null,
) {
    composable<PlayRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.PLAY_PATTERN })) {
        PlayScreenRoute(
            onHidden = onHidden,
            quizEnabled = quizEnabled && navController != null,
            onStartQuiz = { mode, pack -> mode.wire?.let { navController?.navigate(QuizDestination(it, pack?.wire)) } },
            extraItems = { open ->
                playH2hItems(
                    openH2h = open,
                    onOpenH2h = { navController?.navigate(H2hListDestination) },
                    onOpenBoards = { navController?.navigate(BoardsDestination) },
                )
            },
        )
    }
    composable<QuizDestination> {
        QuizRoute(onBack = { navController?.popBackStack() }, onHidden = onHidden)
    }
    composable<H2hListDestination> {
        H2hListRoute(
            onBack = { navController?.popBackStack() },
            onOpen = { navController?.navigate(H2hDestination(it)) },
            onNew = { navController?.navigate(H2hCreateDestination) },
            onHidden = onHidden,
        )
    }
    composable<H2hCreateDestination> {
        H2hCreateRoute(
            onBack = { navController?.popBackStack() },
            // The creator plays at once; Back from the quiz lands on the list, not on this form.
            onPlay = { sessionId ->
                navController?.navigate(QuizDestination(mode = "h2h", sessionId = sessionId)) {
                    popUpTo<H2hCreateDestination> { inclusive = true }
                }
            },
            onHidden = onHidden,
        )
    }
    composable<H2hDestination>(deepLinks = listOf(navDeepLink<H2hDestination>(basePath = H2H_DEEP_LINK_BASE))) {
        H2hDetailRoute(
            onBack = { navController?.popBackStack() },
            onPlay = { navController?.navigate(QuizDestination(mode = "h2h", sessionId = it)) },
            onHidden = onHidden,
        )
    }
    composable<BoardsDestination> {
        BoardRoute(onBack = { navController?.popBackStack() }, onHidden = onHidden, onOpenProfile = onOpenProfile)
    }
}
