package com.hfspaces.explorer.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import com.hfspaces.explorer.ui.common.CodeBlock
import com.hfspaces.explorer.ui.common.openUrl

/** Columns up to this many share the width; wider tables scroll sideways. */
private const val MAX_FITTED_COLUMNS = 3

/**
 * Renders Markdown as native text. Links open in a browser tab; code blocks get a copy button.
 * Wrap it in a SelectionContainer where the text should be selectable.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    onCopied: (() -> Unit)? = null
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    val openLink: (String) -> Unit = remember(context) { { url -> context.openUrl(url) } }

    @Composable
    fun Inline(text: String, textStyle: TextStyle = style, textModifier: Modifier = Modifier) {
        val annotated = remember(text, linkColor, codeBackground) { annotate(parseInline(text), linkColor, codeBackground, openLink) }
        Text(annotated, style = textStyle, color = color, modifier = textModifier)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Inline(
                    block.text,
                    when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(fontWeight = FontWeight.Bold)
                )
                is MarkdownBlock.Paragraph -> Inline(block.text)
                is MarkdownBlock.Code -> CodeBlock(block.code, onCopied = onCopied)
                is MarkdownBlock.ListItem -> Row {
                    Spacer(Modifier.width((block.depth.coerceAtMost(6) * 16).dp))
                    Text(block.marker, style = style, color = color, modifier = Modifier.widthIn(min = 22.dp))
                    Inline(block.text, textModifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.width(10.dp))
                    Inline(block.text, style.copy(fontStyle = FontStyle.Italic), Modifier.weight(1f))
                }
                is MarkdownBlock.Table -> Table(block) { text, bold, cell ->
                    Inline(text, MaterialTheme.typography.bodySmall.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), cell)
                }
                MarkdownBlock.Rule -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Table(table: MarkdownBlock.Table, cell: @Composable (text: String, bold: Boolean, modifier: Modifier) -> Unit) {
    val fits = table.header.size <= MAX_FITTED_COLUMNS
    val scroll = if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())
    Column(scroll.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        (listOf(table.header) + table.rows).forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            Row(if (fits) Modifier.fillMaxWidth() else Modifier) {
                row.forEach { text ->
                    // Equal widths keep the columns lined up without measuring every cell.
                    val width = if (fits) Modifier.weight(1f) else Modifier.width(140.dp)
                    cell(text, index == 0, width.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

private fun annotate(spans: List<InlineSpan>, linkColor: Color, codeBackground: Color, openLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
                textDecoration = if (span.strike) TextDecoration.LineThrough else null
            )
            val url = span.link
            if (url == null) {
                withStyle(style) { append(span.text) }
            } else {
                val linkStyle = TextLinkStyles(style.merge(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))
                withLink(LinkAnnotation.Clickable(tag = url, styles = linkStyle) { openLink(url) }) { append(span.text) }
            }
        }
    }
