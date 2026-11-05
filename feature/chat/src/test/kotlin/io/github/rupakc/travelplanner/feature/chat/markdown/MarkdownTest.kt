package io.github.rupakc.travelplanner.feature.chat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun `inline bold italic code and links`() {
        val inlines = Markdown.inline("Visit **Kyoto** in *spring*, use `IC cards`, see [JNTO](https://www.japan.travel).")
        assertEquals(
            listOf(
                Inline.Plain("Visit "),
                Inline.Bold("Kyoto"),
                Inline.Plain(" in "),
                Inline.Italic("spring"),
                Inline.Plain(", use "),
                Inline.Code("IC cards"),
                Inline.Plain(", see "),
                Inline.Link("JNTO", "https://www.japan.travel"),
                Inline.Plain("."),
            ),
            inlines,
        )
    }

    @Test
    fun `heading followed directly by text is split into two blocks`() {
        val blocks = Markdown.parse("## Day 1\nArrive and rest.")
        assertEquals(Block.Heading(2, listOf(Inline.Plain("Day 1"))), blocks[0])
        assertEquals(Block.Paragraph(listOf(Inline.Plain("Arrive and rest."))), blocks[1])
    }

    @Test
    fun `lists, rules and tables`() {
        val blocks = Markdown.parse("- one\n- two\n\n1. first\n2) second\n\n---\n\n| City | Nights |\n|---|---|\n| Rome | 3 |")
        assertEquals(Block.Bullets(listOf(listOf(Inline.Plain("one")), listOf(Inline.Plain("two")))), blocks[0])
        assertEquals(Block.Numbered(listOf(listOf(Inline.Plain("first")), listOf(Inline.Plain("second")))), blocks[1])
        assertEquals(Block.Rule, blocks[2])
        val table = blocks[3] as Block.Table
        assertEquals(listOf(listOf(Inline.Plain("City")), listOf(Inline.Plain("Nights"))), table.header)
        assertEquals(1, table.rows.size)
        assertEquals(listOf(Inline.Plain("3")), table.rows[0][1])
    }

    @Test
    fun `mixed lines are a paragraph`() {
        assertTrue(Markdown.parse("- a\nplain")[0] is Block.Paragraph)
    }

    @Test
    fun `only http links are safe`() {
        assertTrue(Markdown.isSafeUrl("https://x.y"))
        assertTrue(Markdown.isSafeUrl("http://x.y"))
        assertFalse(Markdown.isSafeUrl("javascript:alert(1)"))
        assertFalse(Markdown.isSafeUrl("intent://x"))
    }

    @Test
    fun `blank text has no blocks`() {
        assertEquals(emptyList<Block>(), Markdown.parse("  "))
    }
}
