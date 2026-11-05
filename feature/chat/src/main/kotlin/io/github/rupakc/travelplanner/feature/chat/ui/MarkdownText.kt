package io.github.rupakc.travelplanner.feature.chat.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.chat.markdown.Block
import io.github.rupakc.travelplanner.feature.chat.markdown.Inline
import io.github.rupakc.travelplanner.feature.chat.markdown.Markdown

/**
 * Renders the chat markdown subset (web `MarkdownContent`). Links open through
 * [onOpenLink] (Custom Tabs); non-http(s) links render as plain text.
 * Parsing is incremental (unchanged blocks are reused) and each block's styled text is
 * remembered, so a streaming reply only re-parses and re-styles its last block.
 */
@Composable
internal fun MarkdownText(text: String, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    // Streaming appends only change the last block: unchanged blocks come from the parser's cache.
    val parser = remember { Markdown.IncrementalParser() }
    val blocks = remember(text) { parser.parse(text) }
    val linkColor = TravelTheme.colors.primary
    val codeBackground = TravelTheme.colors.surfaceVariant
    val body = TravelTheme.typography.bodyLarge.copy(color = color)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> {
                    val style =
                        when (block.level) {
                            1 -> TravelTheme.typography.titleLarge
                            2 -> TravelTheme.typography.titleMedium
                            else -> TravelTheme.typography.titleSmall
                        }
                    InlineText(
                        block.inlines,
                        linkColor,
                        codeBackground,
                        onOpenLink,
                        style = style.copy(color = color),
                        modifier = Modifier.semantics { heading() },
                    )
                }

                is Block.Paragraph -> {
                    InlineText(block.inlines, linkColor, codeBackground, onOpenLink, style = body)
                }

                is Block.Bullets -> {
                    ListBlock(block.items.map { "•" to it }, body, linkColor, codeBackground, onOpenLink)
                }

                is Block.Numbered -> {
                    ListBlock(block.items.mapIndexed { i, item -> "${i + 1}." to item }, body, linkColor, codeBackground, onOpenLink)
                }

                is Block.Table -> {
                    TableBlock(block, body, linkColor, codeBackground, onOpenLink)
                }

                Block.Rule -> {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ListBlock(
    items: List<Pair<String, List<Inline>>>,
    style: TextStyle,
    linkColor: Color,
    codeBackground: Color,
    onOpenLink: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        items.forEach { (marker, inlines) ->
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Text(marker, style = style)
                InlineText(inlines, linkColor, codeBackground, onOpenLink, style = style)
            }
        }
    }
}

@Composable
private fun TableBlock(table: Block.Table, style: TextStyle, linkColor: Color, codeBackground: Color, onOpenLink: (String) -> Unit) {
    val cell = TravelTheme.typography.bodySmall.copy(color = style.color)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            table.header.forEach { h ->
                InlineText(
                    h,
                    linkColor,
                    codeBackground,
                    onOpenLink,
                    style = cell.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(TravelTheme.spacing.sm),
                )
            }
        }
        HorizontalDivider()
        table.rows.forEach { row ->
            Row {
                row.forEach { c ->
                    InlineText(c, linkColor, codeBackground, onOpenLink, style = cell, modifier = Modifier.padding(TravelTheme.spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    inlines: List<Inline>,
    linkColor: Color,
    codeBackground: Color,
    onOpenLink: (String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val open by rememberUpdatedState(onOpenLink)
    val text = remember(inlines, linkColor, codeBackground) { annotated(inlines, linkColor, codeBackground) { open(it) } }
    Text(text, style = style, modifier = modifier)
}

private fun annotated(inlines: List<Inline>, linkColor: Color, codeBackground: Color, onOpenLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        inlines.forEach { inline ->
            when (inline) {
                is Inline.Plain -> {
                    append(inline.text)
                }

                is Inline.Bold -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline.text) }
                }

                is Inline.Italic -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline.text) }
                }

                is Inline.Code -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(inline.text) }
                }

                is Inline.Link -> {
                    if (Markdown.isSafeUrl(inline.url)) {
                        val link =
                            LinkAnnotation.Clickable(
                                tag = inline.url,
                                styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            ) { onOpenLink(inline.url) }
                        withLink(link) { append(inline.text) }
                    } else {
                        append(inline.text)
                    }
                }
            }
        }
    }
