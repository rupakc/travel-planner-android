package io.github.rupakc.travelplanner.core.testing

import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.EventDecoder
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.network.sse.SseParser

/**
 * Recorded SSE streams built from api-contract.md examples (resources/fixtures):
 * `search_full.sse` (every search event type, keepalives, a re-yield, an unknown type, done),
 * `search_interrupted.sse` (no done), `chat_qa.sse`, `chat_planning.sse` (ends with planning_done),
 * `chat_fallback.sse` (plan actions, section results, import_link, error, legacy message, ignored types).
 */
object Fixtures {
    const val SEARCH_FULL = "search_full.sse"
    const val SEARCH_INTERRUPTED = "search_interrupted.sse"
    const val CHAT_QA = "chat_qa.sse"
    const val CHAT_PLANNING = "chat_planning.sse"
    const val CHAT_FALLBACK = "chat_fallback.sse"

    fun read(name: String): String =
        checkNotNull(Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/$name")) { "Missing fixture $name" }
            .bufferedReader().use { it.readText() }

    fun payloads(name: String): List<String> = SseParser.payloads(read(name).lineSequence()).toList()

    fun searchEvents(name: String): List<SearchEvent> = payloads(name).mapNotNull { EventDecoder.decodeSearch(it) }

    fun chatEvents(name: String): List<ChatEvent> = payloads(name).mapNotNull { EventDecoder.decodeChat(it) }
}
