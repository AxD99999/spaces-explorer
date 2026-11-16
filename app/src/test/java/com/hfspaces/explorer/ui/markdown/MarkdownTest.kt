package com.hfspaces.explorer.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    private fun spans(text: String) = parseInline(text)
    private fun plain(text: String) = InlineSpan(text)

    @Test
    fun `blocks of a typical model reply`() {
        val blocks = parseMarkdown(
            """
            # Plan
            Here is what to do:
            first line
            second line

            1. Install it
               with pip
            2) Run it
            - [x] done
            - [ ] todo
              * nested

            > Careful
            > now

            ---
            ```python
            print("hi")

            # not a heading
            ```
            Done.
            """.trimIndent()
        )
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "Plan"),
                MarkdownBlock.Paragraph("Here is what to do:\nfirst line\nsecond line"),
                MarkdownBlock.ListItem(0, "1.", "Install it\nwith pip"),
                MarkdownBlock.ListItem(0, "2.", "Run it"),
                MarkdownBlock.ListItem(0, "☑", "done"),
                MarkdownBlock.ListItem(0, "☐", "todo"),
                MarkdownBlock.ListItem(1, "•", "nested"),
                MarkdownBlock.Quote("Careful\nnow"),
                MarkdownBlock.Rule,
                MarkdownBlock.Code("python", "print(\"hi\")\n\n# not a heading"),
                MarkdownBlock.Paragraph("Done.")
            ),
            blocks
        )
    }

    @Test
    fun `a reply that is still streaming keeps its open code block`() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("Sure:"), MarkdownBlock.Code("kotlin", "val x = 1\nval y")),
            parseMarkdown("Sure:\n```kotlin\nval x = 1\nval y")
        )
        assertEquals(listOf(MarkdownBlock.Code(null, "")), parseMarkdown("```"))
        assertEquals(listOf(plain("an **unfinished bold")), spans("an **unfinished bold"))
        assertEquals(listOf(plain("a `tick and [a link](htt")), spans("a `tick and [a link](htt"))
        // The address of a half-written link is already usable.
        assertEquals(
            listOf(plain("[a link]("), InlineSpan("https://example.com/pa", link = "https://example.com/pa")),
            spans("[a link](https://example.com/pa")
        )
    }

    @Test
    fun `tables keep their columns`() {
        val table = parseMarkdown("| Model | Size |\n|:--|--:|\n| A | 1 GB |\n| B |\n\nafter").first()
        assertEquals(MarkdownBlock.Table(listOf("Model", "Size"), listOf(listOf("A", "1 GB"), listOf("B", ""))), table)
        // A lone pipe isn't a table.
        assertEquals(listOf(MarkdownBlock.Paragraph("a | b\nc")), parseMarkdown("a | b\nc"))
    }

    @Test
    fun `hashes and dashes that are not markup stay text`() {
        assertEquals(listOf(MarkdownBlock.Paragraph("#hashtag and C# code")), parseMarkdown("#hashtag and C# code"))
        assertEquals(listOf(MarkdownBlock.Paragraph("-5 degrees")), parseMarkdown("-5 degrees"))
        assertEquals(listOf(MarkdownBlock.Paragraph("1.5 litres")), parseMarkdown("1.5 litres"))
        assertEquals(listOf(MarkdownBlock.Heading(3, "Title")), parseMarkdown("### Title ###"))
    }

    @Test
    fun `emphasis and code`() {
        assertEquals(
            listOf(plain("a "), InlineSpan("bold", bold = true), plain(" and "), InlineSpan("italic", italic = true), plain(" and "), InlineSpan("code *x*", code = true)),
            spans("a **bold** and *italic* and `code *x*`")
        )
        assertEquals(
            listOf(InlineSpan("a ", italic = true), InlineSpan("b", bold = true, italic = true), InlineSpan(" c", italic = true)),
            spans("*a **b** c*")
        )
        assertEquals(listOf(InlineSpan("both", bold = true, italic = true)), spans("***both***"))
        assertEquals(listOf(InlineSpan("gone", strike = true), plain(" kept")), spans("~~gone~~ kept"))
        assertEquals(listOf(InlineSpan("under", italic = true), plain(" "), InlineSpan("strong", bold = true)), spans("_under_ __strong__"))
    }

    @Test
    fun `markers that are not emphasis are left alone`() {
        assertEquals(listOf(plain("2 * 3 * 4 = 24")), spans("2 * 3 * 4 = 24"))
        assertEquals(listOf(plain("snake_case_name and my_var")), spans("snake_case_name and my_var"))
        assertEquals(listOf(plain("*not italic*")), spans("\\*not italic\\*"))
        assertEquals(listOf(plain("5 ~ 6 and ~~ alone")), spans("5 ~ 6 and ~~ alone"))
        assertEquals(listOf(plain("[1] a footnote, an array[0]")), spans("[1] a footnote, an array[0]"))
        assertEquals(listOf(plain("line one\nline two")), spans("line one<br>line two"))
    }

    @Test
    fun `only web links are links`() {
        assertEquals(
            listOf(plain("see "), InlineSpan("the docs", link = "https://example.com/a_(b)"), plain(".")),
            spans("see [the docs](https://example.com/a_(b) \"title\").")
        )
        assertEquals(
            listOf(plain("at "), InlineSpan("https://example.com/x?y=1", link = "https://example.com/x?y=1"), plain(". Or ("), InlineSpan("http://a.example/b", link = "http://a.example/b"), plain(")")),
            spans("at https://example.com/x?y=1. Or (http://a.example/b)")
        )
        assertEquals(listOf(InlineSpan("https://example.com", link = "https://example.com")), spans("<https://example.com>"))
        assertEquals(listOf(InlineSpan("bold link", bold = true, link = "https://example.com")), spans("[**bold link**](https://example.com)"))
        assertEquals(listOf(InlineSpan("a cat", link = "https://example.com/cat.png")), spans("![a cat](https://example.com/cat.png)"))
        // Not the web: stays as written.
        assertEquals(listOf(plain("[x](javascript:alert(1)) and [y](intent://z) and [f](file:///etc/passwd)")), spans("[x](javascript:alert(1)) and [y](intent://z) and [f](file:///etc/passwd)"))
        assertEquals(listOf(plain("<script>alert(1)</script>")), spans("<script>alert(1)</script>"))
    }

    @Test
    fun `pathological input finishes and loses nothing`() {
        val nasty = "*".repeat(400) + "_".repeat(400) + "[".repeat(400) + "`".repeat(401) + "(".repeat(50)
        assertEquals(nasty, spans(nasty).joinToString("") { it.text })
        val deep = "*a ".repeat(60) + "x" + " a*".repeat(60)
        assertTrue(spans(deep).joinToString("") { it.text }.contains("x"))
        assertEquals("", spans("").joinToString("") { it.text })
    }

    @Test
    fun `plain text drops the markup`() {
        assertEquals("Title\nSome bold text and a link\n• item", plainText("## Title\nSome **bold** text and [a link](https://example.com)\n- item"))
    }
}
