package io.github.rupakc.travelplanner.core.domain.globalsearch

import java.text.Normalizer

/** What a global-search result points at. Declaration order = group order on screen. */
enum class GlobalSearchKind { TRIP, CHAT, RECENT_SEARCH, FEATURE, SETTING }

/**
 * One indexed thing. [id] is the kind-specific identifier the UI navigates with
 * (plan id, chat thread id, recent-search id, `FeatureId.id`, `SettingsEntry.key`).
 * All text is already localised/display text; nothing here is invented.
 */
data class SearchableItem(
    val kind: GlobalSearchKind,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
)

data class GlobalSearchHit(val item: SearchableItem, val score: Int)

data class GlobalSearchGroup(val kind: GlobalSearchKind, val hits: List<GlobalSearchHit>)

/**
 * Ranked, grouped results. [counts] holds the match count of every kind (before the
 * filter), so filter chips can show "Trips (3)" while another chip is selected.
 */
data class GlobalSearchResults(
    val query: String = "",
    val groups: List<GlobalSearchGroup> = emptyList(),
    val counts: Map<GlobalSearchKind, Int> = emptyMap(),
) {
    /** Hits shown (after the filter). */
    val total: Int get() = groups.sumOf { it.hits.size }
}

/**
 * Pure, deterministic ranking for the unified search (Home → search bar).
 *
 * Every whitespace-separated query token must match the title, subtitle or a keyword
 * (accent- and case-insensitive). Per token: title word prefix 30 (first word 40),
 * title substring 20, subtitle word prefix 12, subtitle substring 8, keyword prefix 10,
 * keyword substring 6 (best one counts). A title equal to the whole query adds 100.
 * Ties sort by title, then id.
 */
object GlobalSearch {
    const val DEFAULT_LIMIT_PER_KIND = 20

    private const val EXACT_TITLE = 100
    private const val TITLE_FIRST_WORD_PREFIX = 40
    private const val TITLE_WORD_PREFIX = 30
    private const val TITLE_SUBSTRING = 20
    private const val SUBTITLE_WORD_PREFIX = 12
    private const val SUBTITLE_SUBSTRING = 8
    private const val KEYWORD_PREFIX = 10
    private const val KEYWORD_SUBSTRING = 6

    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lower-case, accents removed, punctuation collapsed to single spaces. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase()
            .replace(separators, " ")
            .trim()

    /** Score of [item] for [query], or null when some token does not match. */
    fun score(query: String, item: SearchableItem): Int? {
        val normalizedQuery = normalize(query)
        val tokens = normalizedQuery.split(' ').filter(String::isNotEmpty)
        if (tokens.isEmpty()) return null
        val title = normalize(item.title)
        val subtitle = item.subtitle?.let(::normalize).orEmpty()
        val keywords = item.keywords.map(::normalize)
        var total = 0
        for (token in tokens) {
            val best = tokenScore(token, title, subtitle, keywords)
            if (best == 0) return null
            total += best
        }
        if (title == normalizedQuery) total += EXACT_TITLE
        return total
    }

    private fun tokenScore(token: String, title: String, subtitle: String, keywords: List<String>): Int {
        val titleWords = title.split(' ')
        return listOf(
            if (titleWords.firstOrNull()?.startsWith(token) == true) TITLE_FIRST_WORD_PREFIX else 0,
            if (titleWords.any { it.startsWith(token) }) TITLE_WORD_PREFIX else 0,
            if (title.contains(token)) TITLE_SUBSTRING else 0,
            if (subtitle.split(' ').any { it.startsWith(token) }) SUBTITLE_WORD_PREFIX else 0,
            if (subtitle.contains(token)) SUBTITLE_SUBSTRING else 0,
            if (keywords.any { k -> k.split(' ').any { it.startsWith(token) } }) KEYWORD_PREFIX else 0,
            if (keywords.any { it.contains(token) }) KEYWORD_SUBSTRING else 0,
        ).max()
    }

    /**
     * Searches [items]. [filter] empty = every kind. At most [limitPerKind] hits per group;
     * counts are the full match counts.
     */
    fun search(
        query: String,
        items: List<SearchableItem>,
        filter: Set<GlobalSearchKind> = emptySet(),
        limitPerKind: Int = DEFAULT_LIMIT_PER_KIND,
    ): GlobalSearchResults {
        if (normalize(query).isEmpty()) return GlobalSearchResults(query = query)
        val hits =
            items.mapNotNull { item -> score(query, item)?.let { GlobalSearchHit(item, it) } }
                .sortedWith(compareByDescending<GlobalSearchHit> { it.score }.thenBy { it.item.title }.thenBy { it.item.id })
        val byKind = hits.groupBy { it.item.kind }
        val groups =
            GlobalSearchKind.entries
                .filter { filter.isEmpty() || it in filter }
                .mapNotNull { kind -> byKind[kind]?.let { GlobalSearchGroup(kind, it.take(limitPerKind)) } }
        return GlobalSearchResults(query = query, groups = groups, counts = byKind.mapValues { it.value.size })
    }
}
