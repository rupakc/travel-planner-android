package io.github.rupakc.travelplanner.feature.chat.markdown

import androidx.compose.runtime.Immutable

/** An inline run of a markdown line (web `InlineMarkdown`). */
@Immutable
sealed interface Inline {
    val text: String

    data class Plain(override val text: String) : Inline

    data class Bold(override val text: String) : Inline

    data class Italic(override val text: String) : Inline

    data class Code(override val text: String) : Inline

    /** A link; only http(s) [url]s are ever made clickable (web `safeUrl`). */
    data class Link(override val text: String, val url: String) : Inline
}

/** A block of the chat markdown subset the web renders (`MarkdownContent` in ChatPage.jsx). */
@Immutable
sealed interface Block {
    data class Heading(val level: Int, val inlines: List<Inline>) : Block

    data class Paragraph(val inlines: List<Inline>) : Block

    data class Bullets(val items: List<List<Inline>>) : Block

    data class Numbered(val items: List<List<Inline>>) : Block

    data class Table(val header: List<List<Inline>>, val rows: List<List<List<Inline>>>) : Block

    data object Rule : Block
}

/**
 * Parser for the chat markdown subset: `#`/`##`/`###` headings, `---` rules, pipe tables,
 * `-`/`*` and `1.` lists, paragraphs; inline bold, italics, `code` and `[links](url)`.
 * Same block splitting as the web (blank lines separate blocks; a heading is always its
 * own block). Pure; call off the main thread for long texts.
 */
object Markdown {
    private val HEADING_NEWLINE = Regex("^(#{1,6} [^\\n]+)\\n(?!\\n)", RegexOption.MULTILINE)
    private val BLOCK_SPLIT = Regex("\\n\\n+")
    private val HEADING = Regex("^(#{1,3}) (.+)", RegexOption.DOT_MATCHES_ALL)
    private val RULE = Regex("^---+$")
    private val BULLET = Regex("^\\s*[-*] ")
    private val NUMBERED = Regex("^\\s*\\d+[.)]\\s")
    private val TABLE_SEPARATOR = Regex("^\\|[\\s\\-:|]+\\|$")
    private val INLINE = Regex("(\\*\\*.*?\\*\\*|`[^`]+`|\\*[^*]+\\*|\\[([^\\]]+)]\\(([^)]+)\\))")
    private val LINK = Regex("^\\[([^\\]]+)]\\(([^)]+)\\)$")
    private const val MIN_TABLE_LINES = 2
    private const val MIN_BOLD_LENGTH = 4

    fun parse(text: String): List<Block> = chunks(text).map(::block)

    private fun chunks(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.replace(HEADING_NEWLINE, "$1\n\n").split(BLOCK_SPLIT).filter { it.isNotEmpty() }
    }

    /**
     * Same result as [parse], but blocks whose source text is unchanged since the previous
     * call are reused — a streaming reply only re-parses its growing last block.
     * Not thread-safe; one instance per rendered message.
     */
    class IncrementalParser {
        private var cache: Map<String, Block> = emptyMap()

        fun parse(text: String): List<Block> {
            val chunks = chunks(text)
            val previous = cache
            val next = HashMap<String, Block>(chunks.size)
            val blocks = chunks.map { chunk -> next.getOrPut(chunk) { previous[chunk] ?: block(chunk) } }
            cache = next
            return blocks
        }
    }

    private fun block(block: String): Block {
        val heading = HEADING.find(block)
        val lines = block.split('\n')
        val tableLines = lines.filter { it.trim().startsWith("|") }
        return when {
            heading != null -> Block.Heading(heading.groupValues[1].length, inline(heading.groupValues[2]))

            RULE.matches(block.trim()) -> Block.Rule

            tableLines.size >= MIN_TABLE_LINES -> table(tableLines)

            lines.all {
                BULLET.containsMatchIn(it) || it.isBlank()
            } -> Block.Bullets(lines.filter(String::isNotBlank).map { inline(it.replaceFirst(BULLET, "")) })

            lines.all {
                NUMBERED.containsMatchIn(it) || it.isBlank()
            } -> Block.Numbered(lines.filter(String::isNotBlank).map { inline(it.replaceFirst(NUMBERED, "")) })

            else -> Block.Paragraph(inline(block))
        }
    }

    private fun table(lines: List<String>): Block.Table {
        fun row(line: String): List<List<Inline>> = line.split('|').drop(1).dropLast(1).map { inline(it.trim()) }
        val rows = lines.drop(1).filterNot { TABLE_SEPARATOR.matches(it.trim()) }.map(::row)
        return Block.Table(row(lines.first()), rows)
    }

    /** Web `InlineMarkdown`: bold, `code`, *italic* and [label](url); everything else plain. */
    fun inline(text: String): List<Inline> {
        val out = mutableListOf<Inline>()
        var last = 0
        for (m in INLINE.findAll(text)) {
            if (m.range.first > last) out += Inline.Plain(text.substring(last, m.range.first))
            val part = m.value
            val link = LINK.matchEntire(part)
            out +=
                when {
                    link != null -> Inline.Link(link.groupValues[1], link.groupValues[2])
                    part.startsWith("**") && part.endsWith("**") && part.length >= MIN_BOLD_LENGTH -> Inline.Bold(part.substring(2, part.length - 2))
                    part.startsWith("`") -> Inline.Code(part.substring(1, part.length - 1))
                    else -> Inline.Italic(part.substring(1, part.length - 1))
                }
            last = m.range.last + 1
        }
        if (last < text.length) out += Inline.Plain(text.substring(last))
        return out.filter { it.text.isNotEmpty() || it is Inline.Link }
    }

    /** True for links the app may open (http/https only). */
    fun isSafeUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}
