package io.github.rupakc.travelplanner.feature.play.h2h

import io.github.rupakc.travelplanner.core.model.play.H2hMatch
import io.github.rupakc.travelplanner.core.model.play.H2hState

private val HANDLE = Regex("^[a-z0-9_]{3,20}$")

/** A typed handle as the server wants it: trimmed, lowercase, without a leading `@`. */
fun normalizeHandle(input: String): String = input.trim().removePrefix("@").lowercase()

/** Whether [handle] (already normalized) has the shape of a handle (`3-20` of `[a-z0-9_]`); the server still decides if it exists. */
fun isHandleShape(handle: String): Boolean = HANDLE.matches(handle)

/** Display order of the list sections. A state this app does not know ([H2hState.UNKNOWN]) is not shown. */
val H2H_SECTIONS: List<H2hState> = listOf(H2hState.WAITING_YOU, H2hState.WAITING_THEM, H2hState.DONE, H2hState.DECLINED, H2hState.EXPIRED)

/** [matches] grouped by state in [H2H_SECTIONS] order; empty sections are left out, server order is kept inside a section. */
fun groupMatches(matches: List<H2hMatch>): List<Pair<H2hState, List<H2hMatch>>> =
    H2H_SECTIONS.mapNotNull { state -> matches.filter { it.matchState == state }.takeIf { it.isNotEmpty() }?.let { state to it } }

/** Scores are drawn only for a finished match where the server sent both (the opponent's score is revealed once both are done). */
fun H2hMatch.showsScores(): Boolean = matchState == H2hState.DONE && you != null && them != null

/** The copy of a pack id this app knows, or null (a newer backend's pack: the raw id is shown as words). */
@androidx.annotation.StringRes
fun packNameRes(pack: String?): Int? =
    when (io.github.rupakc.travelplanner.core.model.play.QuizPack.from(pack)) {
        io.github.rupakc.travelplanner.core.model.play.QuizPack.FLAGS -> io.github.rupakc.travelplanner.feature.play.R.string.play_pack_flags
        io.github.rupakc.travelplanner.core.model.play.QuizPack.CAPITALS -> io.github.rupakc.travelplanner.feature.play.R.string.play_pack_capitals
        io.github.rupakc.travelplanner.core.model.play.QuizPack.LANDMARKS -> io.github.rupakc.travelplanner.feature.play.R.string.play_pack_landmarks
        io.github.rupakc.travelplanner.core.model.play.QuizPack.CUISINE -> io.github.rupakc.travelplanner.feature.play.R.string.play_pack_cuisine
        null -> null
    }
