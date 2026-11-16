package com.hfspaces.explorer.ui.markdown

/**
 * A small Markdown reader for model replies and Space output: enough for what chat models write
 * (headings, lists, quotes, fenced code, tables, emphasis, links), and tolerant of text that is
 * still streaming in, where a fence or an emphasis marker may not be closed yet.
 *
 * Pure Kotlin so it can be unit tested; `MarkdownText` turns the result into Compose UI.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock

    /** [marker] is what to draw in front: a bullet, "3." or a checkbox. */
    data class ListItem(val depth: Int, val marker: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

/** A run of inline text with one style. */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null
)

private val HEADING = Regex("""^ {0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val RULE = Regex("""^ {0,3}([-*_])( *\1){2,} *$""")
private val LIST_ITEM = Regex("""^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$""")
private val TASK = Regex("""^\[([ xX])]\s+(.*)$""")
private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})\s*([^`\s]*).*$""")
private val TABLE_DIVIDER = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")

fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()

    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fence = FENCE.matchEntire(line)
        when {
            fence != null -> {
                flush()
                val marker = fence.groupValues[1]
                val code = mutableListOf<String>()
                i++
                // Runs to the closing fence, or to the end while the reply is still streaming.
                while (i < lines.size && !lines[i].trim().let { it.startsWith(marker) && it.all { c -> c == marker[0] } }) {
                    code += lines[i]
                    i++
                }
                blocks += MarkdownBlock.Code(fence.groupValues[2].ifEmpty { null }, code.joinToString("\n"))
            }
            line.isBlank() -> flush()
            HEADING.matches(line) -> {
                flush()
                val (hashes, text) = HEADING.matchEntire(line)!!.destructured
                blocks += MarkdownBlock.Heading(hashes.length, text)
            }
            RULE.matches(line) -> {
                flush()
                blocks += MarkdownBlock.Rule
            }
            line.trimStart().startsWith(">") -> {
                flush()
                val quoted = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoted += lines[i].trimStart().removePrefix(">").removePrefix(" ")
                    i++
                }
                i--
                blocks += MarkdownBlock.Quote(quoted.joinToString("\n").trim())
            }
            LIST_ITEM.matches(line) -> {
                flush()
                val (indent, bullet, rest) = LIST_ITEM.matchEntire(line)!!.destructured
                val text = StringBuilder(rest)
                // Indented lines that follow belong to this item.
                while (i + 1 < lines.size && lines[i + 1].isNotBlank() && lines[i + 1].startsWith(" ") &&
                    !LIST_ITEM.matches(lines[i + 1]) && !FENCE.matches(lines[i + 1])
                ) {
                    i++
                    text.append('\n').append(lines[i].trim())
                }
                val task = TASK.matchEntire(text)
                val depth = indent.sumOf { c -> if (c == '\t') 4 else 1 as Int } / 2
                blocks += when {
                    task != null -> MarkdownBlock.ListItem(depth, if (task.groupValues[1] == " ") "☐" else "☑", task.groupValues[2])
                    bullet[0].isDigit() -> MarkdownBlock.ListItem(depth, bullet.dropLast(1) + ".", text.toString())
                    else -> MarkdownBlock.ListItem(depth, "•", text.toString())
                }
            }
            line.contains('|') && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1]) && lines[i + 1].contains('-') -> {
                flush()
                val header = tableCells(line)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                    // Pad or trim to the header, so every row has the same number of cells.
                    rows += tableCells(lines[i]).let { cells -> List(header.size) { cells.getOrElse(it) { "" } } }
                    i++
                }
                i--
                blocks += MarkdownBlock.Table(header, rows)
            }
            else -> paragraph += line.trimEnd()
        }
        i++
    }
    flush()
    return blocks
}

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

/** Parses emphasis, code spans and links. Markers without a partner stay as plain text. */
fun parseInline(text: String): List<InlineSpan> {
    val out = mutableListOf<InlineSpan>()
    inline(text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n"), InlineSpan(""), out, depth = 0)
    // Join neighbours that ended up with the same style.
    val merged = mutableListOf<InlineSpan>()
    for (span in out) {
        val last = merged.lastOrNull()
        if (last != null && last.copy(text = "") == span.copy(text = "")) merged[merged.lastIndex] = last.copy(text = last.text + span.text)
        else if (span.text.isNotEmpty()) merged += span
    }
    return merged
}

private const val MAX_NESTING = 8
private const val ESCAPABLE = "\\`*_{}[]()#+-.!~|>"

private fun inline(text: String, style: InlineSpan, out: MutableList<InlineSpan>, depth: Int) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            out += style.copy(text = plain.toString())
            plain.clear()
        }
    }

    /** Emits [inner] with [styled] and returns true, unless nesting has gone too deep. */
    fun nested(inner: String, styled: InlineSpan): Boolean {
        if (depth >= MAX_NESTING) return false
        flush()
        inline(inner, styled, out, depth + 1)
        return true
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                plain.append(text[i + 1])
                i += 2
                continue
            }
            c == '`' -> {
                val ticks = text.countRun(i, '`')
                val close = text.indexOfRun(i + ticks, '`', ticks)
                if (close != -1) {
                    flush()
                    val code = text.substring(i + ticks, close).let { if (it.length > 2 && it.startsWith(" ") && it.endsWith(" ")) it.substring(1, it.length - 1) else it }
                    out += style.copy(text = code, code = true)
                    i = close + ticks
                    continue
                }
            }
            c == '*' || c == '_' -> {
                val run = text.countRun(i, c)
                val intraword = c == '_' && i > 0 && text[i - 1].isLetterOrDigit()
                val opens = i + run < text.length && !text[i + run].isWhitespace() && !intraword
                var matched = false
                if (opens) {
                    // Try the longest marker first: *** then ** then *.
                    for (size in run.coerceAtMost(3) downTo 1) {
                        val close = text.closingEmphasis(i + size, c, size)
                        if (close == -1) continue
                        val styled = when (size) {
                            3 -> style.copy(bold = true, italic = true)
                            2 -> style.copy(bold = true)
                            else -> style.copy(italic = true)
                        }
                        if (nested(text.substring(i + size, close), styled)) {
                            i = close + size
                            matched = true
                        }
                        break
                    }
                }
                if (matched) continue
                // No partner (yet): the markers are just text.
                plain.append(text, i, i + run)
                i += run
                continue
            }
            c == '~' && text.startsWith("~~", i) -> {
                val close = text.indexOf("~~", i + 2)
                if (close > i + 2 && nested(text.substring(i + 2, close), style.copy(strike = true))) {
                    i = close + 2
                    continue
                }
            }
            c == '[' || (c == '!' && text.startsWith("![", i)) -> {
                val start = if (c == '!') i + 1 else i
                val link = text.linkAt(start)
                if (link != null) {
                    val (label, url, end) = link
                    val shown = label.ifBlank { if (c == '!') "image" else url }
                    if (nested(shown, style.copy(link = url))) {
                        i = end
                        continue
                    }
                }
            }
            c == '<' -> {
                val close = text.indexOf('>', i + 1)
                val inside = if (close == -1) "" else text.substring(i + 1, close)
                if (inside.isWebAddress()) {
                    flush()
                    out += style.copy(text = inside, link = inside)
                    i = close + 1
                    continue
                }
            }
            c == 'h' && style.link == null && (i == 0 || !text[i - 1].isLetterOrDigit()) && (text.startsWith("https://", i) || text.startsWith("http://", i)) -> {
                var end = i
                while (end < text.length && !text[end].isWhitespace() && text[end] !in "<>\"`") end++
                // Punctuation that ends the sentence, or a bracket that closes one opened before the link.
                while (end > i && (text[end - 1] in ".,;:!?*_'" || (text[end - 1] == ')' && text.substring(i, end).count { it == '(' } < text.substring(i, end).count { it == ')' }))) end--
                val url = text.substring(i, end)
                if (url.isWebAddress()) {
                    flush()
                    out += style.copy(text = url, link = url)
                    i = end
                    continue
                }
            }
        }
        plain.append(text[i])
        i++
    }
    flush()
}

private fun String.countRun(from: Int, c: Char): Int {
    var n = 0
    while (from + n < length && this[from + n] == c) n++
    return n
}

/** Index of the next run of exactly [size] [c]s at or after [from], or -1. */
private fun String.indexOfRun(from: Int, c: Char, size: Int): Int {
    var i = from
    while (i < length) {
        if (this[i] == c) {
            val run = countRun(i, c)
            if (run == size) return i
            i += run
        } else {
            i++
        }
    }
    return -1
}

/**
 * Where an emphasis opened with [size] [marker]s closes: the next run of that size that follows a
 * non-space and (for underscores) isn't inside a word. Code spans in between are skipped.
 */
private fun String.closingEmphasis(from: Int, marker: Char, size: Int): Int {
    var i = from
    while (i < length) {
        when (this[i]) {
            '\\' -> i += 2
            '`' -> {
                val ticks = countRun(i, '`')
                val close = indexOfRun(i + ticks, '`', ticks)
                i = if (close == -1) i + ticks else close + ticks
            }
            marker -> {
                val run = countRun(i, marker)
                val afterSpace = i == from || this[i - 1].isWhitespace()
                val intraword = marker == '_' && i + run < length && this[i + run].isLetterOrDigit()
                // A run of another length belongs to a different emphasis, except *** which can close two at once.
                val fits = run == size || (run == 3 && size < 3)
                if (fits && !afterSpace && !intraword) return i + (run - size)
                i += run
            }
            else -> i++
        }
    }
    return -1
}

/** A `[label](url)` starting at [start] (the `[`): label, url and the index after the `)`. */
private fun String.linkAt(start: Int): Triple<String, String, Int>? {
    if (start >= length || this[start] != '[') return null
    var depth = 0
    var i = start
    var labelEnd = -1
    while (i < length) {
        when (this[i]) {
            '\\' -> i++
            '[' -> depth++
            ']' -> if (--depth == 0) {
                labelEnd = i
                break
            }
            '\n' -> if (i + 1 < length && this[i + 1] == '\n') return null
        }
        i++
    }
    if (labelEnd == -1 || labelEnd + 1 >= length || this[labelEnd + 1] != '(') return null
    var parens = 0
    var close = -1
    i = labelEnd + 1
    while (i < length) {
        when (this[i]) {
            '(' -> parens++
            ')' -> if (--parens == 0) {
                close = i
                break
            }
            '\n' -> return null
        }
        i++
    }
    if (close == -1) return null
    // Drop an optional "title" after the address.
    val url = substring(labelEnd + 2, close).trim().removeSurrounding("<", ">").substringBefore(' ').trim()
    if (!url.isWebAddress()) return null
    return Triple(substring(start + 1, labelEnd), url, close + 1)
}

/** Only web links become clickable; anything else in a Space's output stays text. */
private fun String.isWebAddress(): Boolean =
    (startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)) && length > 10 && none { it.isWhitespace() }

/** The text of [markdown] without its markup, e.g. for a one-line preview. */
fun plainText(markdown: String): String = parseMarkdown(markdown).joinToString("\n") { block ->
    when (block) {
        is MarkdownBlock.Heading -> parseInline(block.text).joinToString("") { it.text }
        is MarkdownBlock.Paragraph -> parseInline(block.text).joinToString("") { it.text }
        is MarkdownBlock.ListItem -> "${block.marker} " + parseInline(block.text).joinToString("") { it.text }
        is MarkdownBlock.Quote -> parseInline(block.text).joinToString("") { it.text }
        is MarkdownBlock.Code -> block.code
        is MarkdownBlock.Table -> (listOf(block.header) + block.rows).joinToString("\n") { it.joinToString("  ") }
        MarkdownBlock.Rule -> ""
    }
}
