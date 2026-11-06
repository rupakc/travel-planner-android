package io.github.rupakc.travelplanner.feature.results

import io.github.rupakc.travelplanner.core.model.section.SectionId

/**
 * Where each section sits in the flattened results list: [headerItems] items (summary, banners, tools, search),
 * then per section its header item followed by one item per card row. [sections] are the visible sections in
 * screen order (the text filter already removed the others); [rowCounts] the card rows each one shows (0 when
 * collapsed or loading).
 */
internal class ResultsListIndex(sections: List<SectionId>, rowCounts: Map<SectionId, Int>, headerItems: Int) {
    private val starts: List<Pair<SectionId, Int>> =
        buildList {
            var next = headerItems
            for (id in sections) {
                add(id to next)
                next += 1 + (rowCounts[id] ?: 0)
            }
        }

    /** Lazy index of [id]'s header item, or null when the section is not in the list. */
    fun indexOf(id: SectionId): Int? = starts.firstOrNull { it.first == id }?.second

    /** The section that lazy item [index] belongs to (its header or one of its cards); null above the first section. */
    fun sectionAt(index: Int): SectionId? = starts.lastOrNull { it.second <= index }?.first
}

/** A laid-out lazy item: its index and its top/bottom edge in the viewport (px). */
internal data class ItemSpan(val index: Int, val top: Int, val bottom: Int)

/**
 * Scroll-spy: the item (in list order) that reaches below [line], a line a quarter of the way down the list
 * ([spyLine]), so a section whose last card is only just leaving the top no longer counts as the one in view.
 */
internal fun firstVisibleIndex(items: List<ItemSpan>, line: Int): Int? = items.firstOrNull { it.bottom > line }?.index

/** The scroll-spy line for a viewport from [viewportStart] to [viewportEnd] (px): a quarter of the way down. */
internal fun spyLine(viewportStart: Int, viewportEnd: Int): Int = viewportStart + (viewportEnd - viewportStart) / SPY_LINE_FRACTION

private const val SPY_LINE_FRACTION = 4

/**
 * The chip to select: a tapped [jumpTarget] wins until the user scrolls by hand (the last, short sections cannot
 * reach the top, so the spy alone would never pick them); otherwise the section the spy sees.
 */
internal fun activeSection(jumpTarget: SectionId?, spied: SectionId?, visible: List<SectionId>): SectionId? =
    jumpTarget?.takeIf { it in visible } ?: spied
