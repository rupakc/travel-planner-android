package io.github.rupakc.travelplanner.core.model.play

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Quiz pack ids. */
enum class QuizPack(val wire: String) {
    FLAGS("flags"),
    CAPITALS("capitals"),
    LANDMARKS("landmarks"),
    CUISINE("cuisine"),
    ;

    companion object {
        fun from(wire: String?): QuizPack? = entries.firstOrNull { it.wire == wire }
    }
}

/** `POST /play/quiz` `mode` (`h2h` sessions come from the head-to-head routes, never from here). */
enum class QuizMode(val wire: String?) {
    DAILY("daily"),
    PACK("pack"),
    CHALLENGE("challenge"),
    H2H("h2h"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): QuizMode = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** Question kind. A new kind is [UNKNOWN]: render the raw prompt fields or skip. */
enum class QuestionKind(val wire: String?) {
    FLAG("flag"),
    CAPITAL_OF("capital_of"),
    COUNTRY_OF_CAPITAL("country_of_capital"),
    LANDMARK_COUNTRY("landmark_country"),
    DISH_COUNTRY("dish_country"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): QuestionKind = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** A head-to-head match state. */
enum class H2hState(val wire: String?) {
    WAITING_YOU("waiting_you"),
    WAITING_THEM("waiting_them"),
    DONE("done"),
    DECLINED("declined"),
    EXPIRED("expired"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): H2hState = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** Who won a finished match. */
enum class H2hWinner(val wire: String?) {
    YOU("you"),
    THEM("them"),
    DRAW("draw"),
    UNKNOWN(null),
    ;

    companion object {
        fun from(wire: String?): H2hWinner = entries.firstOrNull { it.wire != null && it.wire == wire } ?: UNKNOWN
    }
}

/** Leaderboard scope. */
enum class BoardScope(val wire: String) {
    GLOBAL("global"),
    FRIENDS("friends"),
}

/** Leaderboard week. */
enum class BoardWeek(val wire: String) {
    CURRENT("current"),
    PREVIOUS("previous"),
}

/** Daily-quiz streak of `GET /play/state`. */
@Serializable
data class PlayStreak(
    val current: LenientInt = null,
    val longest: LenientInt = null,
    @SerialName("freeze_available") val freezeAvailable: Boolean = false,
)

/** The daily quiz card. [score] is null until done. */
@Serializable
data class DailyState(
    val available: Boolean = false,
    val done: Boolean = false,
    val score: LenientInt = null,
    val streak: PlayStreak = PlayStreak(),
)

/** A pack of the hub: [titleKey] is a translation key the app maps to its own copy. [questions] is the pool size. */
@Serializable
data class PackInfo(
    val id: String,
    @SerialName("title_key") val titleKey: String? = null,
    val questions: LenientInt = null,
) {
    val pack: QuizPack? get() = QuizPack.from(id)
}

/** The weekly challenge ("Know before you go"). [kind] is `destination` or `featured`; [country] is an ISO alpha-2 or null. */
@Serializable
data class Challenge(
    val id: String,
    val kind: String? = null,
    val country: String? = null,
    @SerialName("title_key") val titleKey: String? = null,
    val done: Boolean = false,
)

/** `GET /play/state`. */
@Serializable
data class PlayState(
    val daily: DailyState = DailyState(),
    val packs: List<PackInfo> = emptyList(),
    val challenge: Challenge? = null,
    @SerialName("open_h2h") val openH2h: LenientInt = null,
)

/** An answer option. [code] (ISO alpha-2) is present on country-labelled options so the name can be localised. */
@Serializable
data class QuizOption(val id: String, val label: String = "", val code: String? = null)

/** A question without its answer. [prompt] is raw (`template` plus fields such as `flag`, `country`, `code`, `capital`, `landmark`, `dish`). */
@Serializable
data class QuizQuestion(
    val index: Int,
    val kind: String? = null,
    val prompt: JsonObject = JsonObject(emptyMap()),
    val options: List<QuizOption> = emptyList(),
) {
    val questionKind: QuestionKind get() = QuestionKind.from(kind)

    /** A string field of [prompt], or null. */
    fun promptText(key: String): String? = (prompt[key] as? JsonPrimitive)?.contentOrNull
}

/** An answer already given (resume). */
@Serializable
data class GivenAnswer(
    val index: Int,
    val choice: String? = null,
    val correct: Boolean = false,
    @SerialName("correct_choice") val correctChoice: String? = null,
    val points: LenientInt = null,
    @SerialName("elapsed_ms") val elapsedMs: LenientInt = null,
)

/** Streak echoed in a finished daily's summary. */
@Serializable
data class SummaryStreak(
    val current: LenientInt = null,
    val longest: LenientInt = null,
    @SerialName("freeze_available") val freezeAvailable: Boolean? = null,
    val freezes: LenientInt = null,
)

/** A finished quiz. [xp] is a game delta (absent when the game is off or nothing was earned). Never recompute the score. */
@Serializable
data class QuizSummary(
    val score: LenientInt = null,
    val correct: LenientInt = null,
    val total: LenientInt = null,
    val xp: GameDelta? = null,
    val streak: SummaryStreak? = null,
)

/** `POST /play/quiz`, `GET /play/quiz/{id}`, and the h2h create and accept answers (which add [id], the match id). */
@Serializable
data class QuizSession(
    @SerialName("session_id") val sessionId: String,
    val id: String? = null,
    val mode: String? = null,
    val pack: String? = null,
    val questions: List<QuizQuestion> = emptyList(),
    @SerialName("expires_at") val expiresAt: String? = null,
    val answers: List<GivenAnswer> = emptyList(),
    val done: Boolean = false,
    val resumed: Boolean = false,
    val summary: QuizSummary? = null,
) {
    val quizMode: QuizMode get() = QuizMode.from(mode)
}

/** `POST /play/quiz` body. [pack] only for `pack` mode. */
@Serializable
data class StartQuizRequest(val mode: String, val pack: String? = null, val locale: String? = null)

/** `POST /play/quiz/{id}/answer` body: the 0-based question index and the option id. */
@Serializable
data class AnswerRequest(val index: Int, val choice: String)

/** Server verdict of one answer. The points and elapsed time are the server's. */
@Serializable
data class AnswerResult(
    val correct: Boolean = false,
    @SerialName("correct_choice") val correctChoice: String? = null,
    val points: LenientInt = null,
    @SerialName("elapsed_ms") val elapsedMs: LenientInt = null,
    val done: Boolean = false,
    val summary: QuizSummary? = null,
)

/** One finished session of `GET /play/history`. */
@Serializable
data class HistoryEntry(
    @SerialName("session_id") val sessionId: String,
    val mode: String? = null,
    val pack: String? = null,
    val score: LenientInt = null,
    val correct: LenientInt = null,
    @SerialName("finished_at") val finishedAt: String? = null,
)

/** `GET /play/history`. */
@Serializable
data class PlayHistory(val sessions: List<HistoryEntry> = emptyList())

/** `POST /play/h2h` body. */
@Serializable
data class CreateH2hRequest(val handle: String, val pack: String)

/** The other player of a match: the handle only. */
@Serializable
data class H2hOpponent(val handle: String = "")

/** One match of `GET /play/h2h`. [them] and [winner] are null until both are done; [you] is null until the caller finished. */
@Serializable
data class H2hMatch(
    val id: String,
    val opponent: H2hOpponent = H2hOpponent(),
    val pack: String? = null,
    val state: String? = null,
    val you: LenientInt = null,
    val them: LenientInt = null,
    val winner: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
) {
    val matchState: H2hState get() = H2hState.from(state)
    val matchWinner: H2hWinner? get() = winner?.let(H2hWinner::from)
}

/** `GET /play/h2h`. */
@Serializable
data class H2hList(val matches: List<H2hMatch> = emptyList())

/** One leaderboard row (handles only). [level] only when the user opted in. */
@Serializable
data class BoardRow(
    val rank: LenientInt = null,
    val handle: String = "",
    val score: LenientInt = null,
    val level: LenientInt = null,
    val you: Boolean = false,
)

/** The caller's own place. [rank] is null when not on the board; [optedIn] says whether the global board can list them. */
@Serializable
data class BoardYou(
    val rank: LenientInt = null,
    val score: LenientInt = null,
    @SerialName("opted_in") val optedIn: Boolean = false,
)

/** `GET /play/leaderboard`. [you] is null without a handle. */
@Serializable
data class Leaderboard(
    val week: String? = null,
    val rows: List<BoardRow> = emptyList(),
    val you: BoardYou? = null,
)
