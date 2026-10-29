package com.hfspaces.explorer.data.gradio

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The form control used for a Gradio input, derived from its component name and JSON schema. */
sealed interface InputKind {
    data class Text(val multiline: Boolean) : InputKind
    data class Number(val min: Double?, val max: Double?, val integer: Boolean) : InputKind
    data class Slider(val min: Double, val max: Double, val integer: Boolean) : InputKind
    data object Checkbox : InputKind
    data class Choice(val options: List<String>) : InputKind
    data class MultiChoice(val options: List<String>) : InputKind
    /** [gallery] inputs wrap each file as `{image, caption}`. */
    data class File(val mimeType: String, val multiple: Boolean, val gallery: Boolean = false) : InputKind

    /** Anything else (State, custom components, dataframes...): edited as raw JSON. */
    data object Json : InputKind
}

private val RANGE = Regex("""between\s+(-?\d+(?:\.\d+)?)\s+and\s+(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

fun GradioParameter.inputKind(): InputKind {
    val comp = component.orEmpty().lowercase()
    val schema = type as? JsonObject
    val schemaType = schema.string("type")
    val pyType = pythonType?.type.orEmpty().lowercase()
    val range = RANGE.find(pythonType?.description.orEmpty())
        ?.destructured
        ?.let { (lo, hi) -> lo.toDouble() to hi.toDouble() }
    val isInteger = schemaType == "integer" || pyType == "int"

    return when {
        comp == "gallery" -> InputKind.File("image/*", multiple = true, gallery = true)
        comp == "image" || comp == "imageeditor" || comp == "imageslider" -> InputKind.File("image/*", multiple = false)
        comp == "audio" -> InputKind.File("audio/*", multiple = false)
        comp == "video" -> InputKind.File("video/*", multiple = false)
        comp == "file" || comp == "uploadbutton" || comp == "model3d" || isFileSchema(schema) ->
            InputKind.File("*/*", multiple = isFileList(schema, pyType))
        comp == "checkbox" || schemaType == "boolean" -> InputKind.Checkbox
        comp == "checkboxgroup" -> InputKind.MultiChoice(enumOptions(schema))
        comp == "dropdown" || comp == "radio" || enumOptions(schema).isNotEmpty() -> {
            val options = enumOptions(schema)
            when {
                options.isEmpty() -> InputKind.Text(multiline = false)
                schemaType == "array" -> InputKind.MultiChoice(options)
                else -> InputKind.Choice(options)
            }
        }
        comp == "slider" && range != null -> InputKind.Slider(range.first, range.second, isInteger)
        comp == "slider" || comp == "number" || schemaType == "number" || schemaType == "integer" ->
            InputKind.Number(range?.first, range?.second, isInteger)
        comp == "textbox" || comp == "code" -> InputKind.Text(multiline = true)
        schemaType == "string" -> InputKind.Text(multiline = false)
        else -> InputKind.Json
    }
}

/** Enum values from `type.enum`, `type.items.enum` or any `anyOf`/`oneOf` branch. */
private fun enumOptions(schema: JsonObject?): List<String> {
    if (schema == null) return emptyList()
    (schema["enum"] as? JsonArray)?.let { return it.strings() }
    (schema["items"] as? JsonObject)?.let { items -> enumOptions(items).let { if (it.isNotEmpty()) return it } }
    for (key in listOf("anyOf", "oneOf")) {
        (schema[key] as? JsonArray)?.forEach { branch ->
            enumOptions(branch as? JsonObject).let { if (it.isNotEmpty()) return it }
        }
    }
    return emptyList()
}

private fun JsonArray.strings(): List<String> = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun isFileSchema(schema: JsonObject?): Boolean {
    if (schema == null) return false
    val props = schema["properties"] as? JsonObject
    if (props != null && "path" in props && "meta" in props) return true
    return isFileSchema(schema["items"] as? JsonObject)
}

private fun isFileList(schema: JsonObject?, pyType: String): Boolean =
    schema.string("type") == "array" || pyType.startsWith("list[")

private fun JsonObject?.string(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull

/** Text form of a schema value for pre-filling inputs, e.g. a default or example. */
fun JsonElement.asInputText(): String = when (this) {
    is JsonPrimitive -> contentOrNull.orEmpty()
    else -> toString()
}
