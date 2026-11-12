package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.play.AnswerResult
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardWeek
import io.github.rupakc.travelplanner.core.model.play.Challenge
import io.github.rupakc.travelplanner.core.model.play.H2hList
import io.github.rupakc.travelplanner.core.model.play.Leaderboard
import io.github.rupakc.travelplanner.core.model.play.PlayHistory
import io.github.rupakc.travelplanner.core.model.play.PlayState
import io.github.rupakc.travelplanner.core.model.play.QuizMode
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.core.model.play.QuizSession

/**
 * Quizzes, head-to-head, boards and the weekly challenge. Callers must have checked `JourneyFlags.play` and that the game is not
 * hidden (`403 game_hidden` otherwise). Failures carry the server's `error_code` (`AppError.socialErrorCode()`). The app never
 * computes points: it shows what the server answers.
 */
interface PlayRepository {
    suspend fun state(): AppResult<PlayState>

    /** Starts or resumes a quiz; [pack] only for [QuizMode.PACK]. `409 already_done` for a finished daily or challenge. */
    suspend fun startQuiz(mode: QuizMode, pack: QuizPack? = null): AppResult<QuizSession>

    suspend fun quiz(sessionId: String): AppResult<QuizSession>

    /** Sent at once; the server times it. `410 session_expired`, `422 invalid_*|out_of_order`. */
    suspend fun answer(sessionId: String, index: Int, choice: String): AppResult<AnswerResult>

    suspend fun history(limit: Int? = null): AppResult<PlayHistory>

    /** The creator plays the returned session at once. `404 profile_not_found`, `422 limit_reached|cannot_challenge_self`. */
    suspend fun createH2h(handle: String, pack: QuizPack): AppResult<QuizSession>

    suspend fun h2hMatches(): AppResult<H2hList>

    /** The opponent's session (same questions). `410 match_expired`. */
    suspend fun acceptH2h(matchId: String): AppResult<QuizSession>

    suspend fun declineH2h(matchId: String): AppResult<Unit>

    suspend fun leaderboard(scope: BoardScope, week: BoardWeek = BoardWeek.CURRENT): AppResult<Leaderboard>

    suspend fun challenge(): AppResult<Challenge>
}
