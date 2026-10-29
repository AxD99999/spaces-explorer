package com.hfspaces.explorer.data.gradio

import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.remote.AppJson
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputParserTest {
    private val host = "https://gradio-image-mod.hf.space/".toHttpUrl()

    private fun parse(json: String, returns: List<GradioReturn> = emptyList()) =
        parseOutputs(AppJson.parseToJsonElement(json), host, "/gradio_api", returns)

    @Test
    fun `real image output becomes one image`() {
        // Captured from gradio/image_mod.
        val items = parse(
            """[{"path": "/tmp/gradio/c9d6/image.webp", "url": "https://gradio-image-mod.hf.space/gradio_api/file=/tmp/gradio/c9d6/image.webp",
                "size": null, "orig_name": "image.webp", "mime_type": null, "is_stream": false, "meta": {"_type": "gradio.FileData"}}]"""
        )
        assertEquals(1, items.size)
        assertEquals(GeneratedMediaType.IMAGE, items[0].type)
        assertEquals("https://gradio-image-mod.hf.space/gradio_api/file=/tmp/gradio/c9d6/image.webp", items[0].url)
        assertEquals("image.webp", items[0].fileName)
    }

    @Test
    fun `relative file paths resolve through the file route`() {
        val items = parse("""[{"path": "/tmp/gradio/x/out.wav", "meta": {"_type": "gradio.FileData"}}]""")
        assertEquals(GeneratedMediaType.AUDIO, items.single().type)
        assertEquals("https://gradio-image-mod.hf.space/gradio_api/file=/tmp/gradio/x/out.wav", items.single().url)
    }

    @Test
    fun `numbers and plain strings are text`() {
        val items = parse("""[7, "Hello Ada!", "3.14", ""]""")
        assertEquals(listOf(GeneratedMediaType.TEXT, GeneratedMediaType.TEXT, GeneratedMediaType.TEXT), items.map { it.type })
        assertEquals(listOf("7", "Hello Ada!", "3.14"), items.map { it.text })
    }

    @Test
    fun `galleries are flattened and labels come from returns`() {
        val items = parse(
            """[[{"image": {"path": "a.png", "url": "https://x.hf.space/file=a.png", "meta": {"_type": "gradio.FileData"}}, "caption": null},
                 {"image": {"path": "b.jpg", "url": "https://x.hf.space/file=b.jpg", "meta": {"_type": "gradio.FileData"}}, "caption": "b"}],
                "seed: 42"]""",
            returns = listOf(GradioReturn(label = "Result", component = "Gallery"), GradioReturn(label = "Info", component = "Textbox"))
        )
        assertEquals(listOf(GeneratedMediaType.IMAGE, GeneratedMediaType.IMAGE, GeneratedMediaType.TEXT), items.map { it.type })
        assertEquals(listOf("Result", "Result", "Info"), items.map { it.label })
    }

    @Test
    fun `bare file strings count as files only for file components or urls`() {
        assertEquals(GeneratedMediaType.TEXT, parse("""["see notes/readme.txt"]""").single().type)
        assertEquals(
            GeneratedMediaType.VIDEO,
            parse("""["/tmp/gradio/v.mp4"]""", listOf(GradioReturn(component = "Video"))).single().type
        )
        assertEquals(GeneratedMediaType.IMAGE, parse("""["https://example.com/cat.png?x=1"]""").single().type)
    }

    @Test
    fun `objects that are not files are kept as json`() {
        val items = parse("""[{"label": "cat", "confidences": [{"label": "cat", "confidence": 0.9}]}, null]""")
        assertEquals(GeneratedMediaType.OTHER, items.single().type)
    }

    @Test
    fun `gradio update wrappers are unwrapped`() {
        val items = parse("""[{"__type__": "update", "value": "done"}]""")
        assertEquals("done", items.single().text)
    }

    @Test
    fun `data urls keep their mime type`() {
        assertEquals(GeneratedMediaType.IMAGE, typeFor(null, "data:image/png;base64,AAAA"))
        assertEquals(GeneratedMediaType.AUDIO, typeFor(null, "data:audio/wav;base64,AAAA"))
    }
}
