package com.hfspaces.explorer.data.gradio

import com.hfspaces.explorer.data.remote.AppJson
import com.hfspaces.explorer.testing.Fixtures
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradioSchemaTest {
    private fun param(json: String) = AppJson.decodeFromString<GradioParameter>(json)

    @Test
    fun `real info responses map to the right controls`() {
        val hello = Fixtures.endpoints("info_hello-world.json").single()
        assertEquals("/predict", hello.name)
        assertEquals(InputKind.Text(multiline = true), hello.parameters.single().inputKind())
        assertTrue(hello.codeSnippets.containsKey("python"))

        val calc = Fixtures.endpoints("info_calculator.json").single()
        assertEquals(
            listOf(
                InputKind.Number(null, null, integer = false),
                InputKind.Choice(listOf("add", "subtract", "multiply", "divide")),
                InputKind.Number(null, null, integer = false)
            ),
            calc.parameters.map { it.inputKind() }
        )

        val image = Fixtures.endpoints("info_image-mod.json")
        assertEquals(listOf("/predict", "/lambda_4"), image.map { it.name })
        assertEquals(InputKind.File("image/*", multiple = false), image[0].parameters.single().inputKind())
    }

    @Test
    fun `slider range comes from the python type description`() {
        val p = param(
            """{"label":"Steps","parameter_name":"steps","parameter_has_default":true,"parameter_default":28,
               "type":{"type":"number"},"python_type":{"type":"float","description":"numeric value between 1 and 50"},"component":"Slider"}"""
        )
        assertEquals(InputKind.Slider(1.0, 50.0, integer = false), p.inputKind())
        assertEquals(FormValue.Text("28"), initialFormValue(p, p.inputKind()))
    }

    @Test
    fun `slider without a range is a number field`() {
        val p = param("""{"component":"Slider","type":{"type":"integer"},"python_type":{"type":"int","description":""}}""")
        assertEquals(InputKind.Number(null, null, integer = true), p.inputKind())
    }

    @Test
    fun `dropdowns checkboxes galleries and unknowns`() {
        assertEquals(
            InputKind.MultiChoice(listOf("a", "b")),
            param("""{"component":"Dropdown","type":{"type":"array","items":{"enum":["a","b"],"type":"string"}}}""").inputKind()
        )
        assertEquals(
            InputKind.Choice(listOf("x", "y")),
            param("""{"component":"Dropdown","type":{"anyOf":[{"enum":["x","y"],"type":"string"},{"type":"null"}]}}""").inputKind()
        )
        assertEquals(InputKind.MultiChoice(listOf("1", "2")), param("""{"component":"Checkboxgroup","type":{"type":"array","items":{"enum":["1","2"]}}}""").inputKind())
        assertEquals(InputKind.Checkbox, param("""{"component":"Checkbox","type":{"type":"boolean"}}""").inputKind())
        assertEquals(InputKind.File("image/*", multiple = true, gallery = true), param("""{"component":"Gallery"}""").inputKind())
        assertEquals(InputKind.File("audio/*", multiple = false), param("""{"component":"Audio"}""").inputKind())
        assertEquals(InputKind.Json, param("""{"component":"State","type":{}}""").inputKind())
        assertEquals(InputKind.Text(multiline = false), param("""{"component":"Dropdown","type":{"type":"string"}}""").inputKind())
    }

    @Test
    fun `payload uses json types gradio expects`() {
        val endpoint = Fixtures.endpoints("info_calculator.json").single()
        val payload = buildPayload(
            endpoint,
            mapOf(0 to FormValue.Text("3"), 1 to FormValue.Text("add"), 2 to FormValue.Text("4.5")),
            emptyMap()
        )
        assertEquals(JsonArray(listOf(JsonPrimitive(3.0), JsonPrimitive("add"), JsonPrimitive(4.5))), payload)
    }

    @Test
    fun `invalid numbers are reported with the field name`() {
        val endpoint = Fixtures.endpoints("info_calculator.json").single()
        val error = runCatching {
            buildPayload(endpoint, mapOf(0 to FormValue.Text("three")), emptyMap())
        }.exceptionOrNull()
        assertTrue(error is InvalidInputException)
        assertTrue(error!!.message!!.contains("num1"))
    }

    @Test
    fun `empty optional values are sent as null and missing values use defaults`() {
        val endpoint = Fixtures.endpoints("info_calculator.json").single()
        // Field 0 is empty, field 1 untouched (falls back to its example), field 2 set.
        val payload = buildPayload(endpoint, mapOf(0 to FormValue.Text(""), 2 to FormValue.Text("1")), emptyMap())
        assertEquals(JsonNull, payload[0])
        assertEquals(JsonPrimitive("add"), payload[1])
    }

    @Test
    fun `files become gradio file data after upload`() {
        val endpoint = Fixtures.endpoints("info_image-mod.json").first()
        val picked = PickedFile("file:///cache/a.png", "a.png", "image/png", 10)
        val payload = buildPayload(
            endpoint,
            mapOf(0 to FormValue.Files(listOf(picked))),
            mapOf(picked.uri to UploadedFile("/tmp/gradio/abc/a.png", picked))
        )
        val file = payload.single().jsonObject
        assertEquals("/tmp/gradio/abc/a.png", file["path"]!!.jsonPrimitive.content)
        assertEquals("gradio.FileData", file["meta"]!!.jsonObject["_type"]!!.jsonPrimitive.content)
        assertEquals("a.png", file["orig_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a file that was not uploaded fails loudly instead of sending base64`() {
        val endpoint = Fixtures.endpoints("info_image-mod.json").first()
        val picked = PickedFile("file:///cache/a.png", "a.png", "image/png", 10)
        val error = runCatching { buildPayload(endpoint, mapOf(0 to FormValue.Files(listOf(picked))), emptyMap()) }.exceptionOrNull()
        assertTrue(error is InvalidInputException)
    }

    @Test
    fun `gallery inputs wrap each image`() {
        val endpoint = SpaceEndpoint("/run", listOf(param("""{"label":"imgs","component":"Gallery"}""")), emptyList())
        val a = PickedFile("u1", "a.png", "image/png", 1)
        val b = PickedFile("u2", "b.png", "image/png", 1)
        val payload = buildPayload(
            endpoint,
            mapOf(0 to FormValue.Files(listOf(a, b))),
            mapOf("u1" to UploadedFile("/p/a.png", a), "u2" to UploadedFile("/p/b.png", b))
        )
        val list = payload.single() as JsonArray
        assertEquals(2, list.size)
        assertEquals("/p/b.png", (list[1].jsonObject["image"] as JsonObject)["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun `json fields accept raw json and fall back to strings`() {
        val endpoint = SpaceEndpoint("/run", listOf(param("""{"component":"State"}"""), param("""{"component":"State"}""")), emptyList())
        val payload = buildPayload(endpoint, mapOf(0 to FormValue.Text("[[1,2]]"), 1 to FormValue.Text("not json")), emptyMap())
        assertEquals(AppJson.parseToJsonElement("[[1,2]]"), payload[0])
        assertEquals(JsonPrimitive("not json"), payload[1])
    }

    @Test
    fun `prompt detection picks the first non-empty text input`() {
        val endpoint = Fixtures.endpoints("info_hello-world.json").single()
        assertEquals("Ada", detectPrompt(endpoint, mapOf(0 to FormValue.Text("  Ada "))))
    }

    @Test
    fun `hidden endpoints are skipped`() {
        val info = AppJson.decodeFromString<GradioApiInfo>(
            """{"named_endpoints":{"/a":{"api_visibility":"public"},"/b":{"api_visibility":"private"},"c":{}}}"""
        )
        assertEquals(listOf("/a", "/c"), info.toEndpoints().map { it.name })
    }
}
