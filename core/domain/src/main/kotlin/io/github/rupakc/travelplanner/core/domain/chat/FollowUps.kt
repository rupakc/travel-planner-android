package io.github.rupakc.travelplanner.core.domain.chat

/** A finished assistant answer split into its [body] and trailing follow-up questions. */
data class FollowUps(val body: String, val followUps: List<String>)

private val DASH_PREFIX = Regex("^[—–-]\\s+")
private val DASH_LINE = Regex("^\\s*[—–-]\\s+\\S")
private const val MIN_DONE_CHIPS = 2

/**
 * Port of web `parseFollowUps` (ChatPage.jsx): trailing lines starting with "— ", "– "
 * or "- " (blank lines between them allowed) become tappable follow-up chips and are
 * removed from the displayed body. Applied to every finished assistant message.
 */
fun parseFollowUps(text: String): FollowUps {
    if (text.isEmpty()) return FollowUps(text, emptyList())
    val lines = text.split('\n')
    val followUps = ArrayDeque<String>()
    var cutAt = lines.size
    for (i in lines.indices.reversed()) {
        val trimmed = lines[i].trim()
        val isFollowUp = trimmed.startsWith("— ") || trimmed.startsWith("– ") || trimmed.startsWith("- ")
        if (trimmed.isNotEmpty() && !isFollowUp) break
        if (isFollowUp) {
            followUps.addFirst(trimmed.replace(DASH_PREFIX, ""))
            cutAt = i
        }
    }
    return FollowUps(lines.subList(0, cutAt).joinToString("\n").trimEnd(), followUps.toList())
}

/**
 * Port of the web's `done` handling: the advisor's trailing "— question" lines (no blank
 * lines between) become suggestion chips when there are at least two. Returns the
 * trimmed body and the chips, or null to leave the text as it is.
 */
fun extractDoneChips(text: String): FollowUps? {
    if (text.isEmpty()) return null
    val lines = text.split('\n').toMutableList()
    val chips = ArrayDeque<String>()
    while (lines.isNotEmpty() && DASH_LINE.containsMatchIn(lines.last())) {
        chips.addFirst(lines.removeAt(lines.lastIndex).replace(Regex("^\\s*[—–-]\\s+"), "").trim())
    }
    if (chips.size < MIN_DONE_CHIPS) return null
    return FollowUps(lines.joinToString("\n").trimEnd(), chips.toList())
}
