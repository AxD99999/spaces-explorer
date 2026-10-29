package com.hfspaces.explorer.data.gradio

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A file the user picked, referenced by content URI until it is uploaded. */
data class PickedFile(
    val uri: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?
)

/** The editable value behind one form control. */
sealed interface FormValue {
    data class Text(val text: String) : FormValue
    data class Bool(val checked: Boolean) : FormValue
    data class Multi(val selected: List<String>) : FormValue
    data class Files(val files: List<PickedFile>) : FormValue
}

/** A file already uploaded to the Space, as Gradio's `FileData`. */
data class UploadedFile(val serverPath: String, val source: PickedFile)

class InvalidInputException(message: String) : IllegalArgumentException(message)

fun initialFormValue(param: GradioParameter, kind: InputKind): FormValue {
    val default = param.defaultValue
    val seed = default ?: param.example
    return when (kind) {
        is InputKind.Checkbox -> FormValue.Bool((seed as? JsonPrimitive)?.booleanOrNull ?: false)
        is InputKind.MultiChoice -> FormValue.Multi(
            (default as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
        )
        is InputKind.File -> FormValue.Files(emptyList())
        // Free text starts empty and shows the example as a hint; required values get a usable seed.
        is InputKind.Text -> FormValue.Text(default?.asInputText().orEmpty())
        is InputKind.Slider -> FormValue.Text(seed?.asInputText() ?: formatNumber(kind.min, kind.integer))
        is InputKind.Number, is InputKind.Choice, is InputKind.Json -> FormValue.Text(seed?.asInputText().orEmpty())
    }
}

/**
 * Converts form state into Gradio's positional `data` array.
 *
 * @param uploads server paths for files picked in [values], keyed by content URI.
 * @throws InvalidInputException when a value can't be sent as-is (e.g. a non-numeric number).
 */
fun buildPayload(
    endpoint: SpaceEndpoint,
    values: Map<Int, FormValue>,
    uploads: Map<String, UploadedFile>
): JsonArray = JsonArray(
    endpoint.parameters.mapIndexed { index, param ->
        val kind = param.inputKind()
        toJson(param.displayLabel, kind, values[index] ?: initialFormValue(param, kind), uploads)
    }
)

private fun toJson(label: String, kind: InputKind, value: FormValue, uploads: Map<String, UploadedFile>): JsonElement =
    when (kind) {
        is InputKind.Checkbox -> JsonPrimitive((value as? FormValue.Bool)?.checked ?: false)
        is InputKind.MultiChoice -> JsonArray(((value as? FormValue.Multi)?.selected ?: emptyList()).map(::JsonPrimitive))
        is InputKind.File -> {
            val files = (value as? FormValue.Files)?.files.orEmpty().map { picked ->
                val uploaded = uploads[picked.uri]
                    ?: throw InvalidInputException("\"${picked.name}\" for “$label” was not uploaded")
                fileData(uploaded).let { if (kind.gallery) buildJsonObject { put("image", it); put("caption", JsonNull) } else it }
            }
            if (kind.multiple) JsonArray(files) else files.firstOrNull() ?: JsonNull
        }
        is InputKind.Number, is InputKind.Slider -> {
            val text = (value as? FormValue.Text)?.text?.trim().orEmpty()
            val integer = (kind as? InputKind.Number)?.integer ?: (kind as InputKind.Slider).integer
            when {
                text.isEmpty() -> JsonNull
                else -> {
                    val number = text.toDoubleOrNull() ?: throw InvalidInputException("“$label” must be a number")
                    if (integer) JsonPrimitive(number.toLong()) else JsonPrimitive(number)
                }
            }
        }
        is InputKind.Choice -> (value as? FormValue.Text)?.text?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull
        is InputKind.Text -> JsonPrimitive((value as? FormValue.Text)?.text.orEmpty())
        is InputKind.Json -> {
            val text = (value as? FormValue.Text)?.text?.trim().orEmpty()
            if (text.isEmpty()) JsonNull
            else runCatching { com.hfspaces.explorer.data.remote.AppJson.parseToJsonElement(text) }
                .getOrElse { JsonPrimitive(text) }
        }
    }

fun fileData(file: UploadedFile): JsonElement = buildJsonObject {
    put("path", file.serverPath)
    put("orig_name", file.source.name)
    file.source.mimeType?.let { put("mime_type", it) }
    file.source.sizeBytes?.let { put("size", it) }
    putJsonObject("meta") { put("_type", "gradio.FileData") }
}

/** Picks a short human prompt out of the inputs, used to title saved outputs. */
fun detectPrompt(endpoint: SpaceEndpoint, values: Map<Int, FormValue>): String? =
    endpoint.parameters.indices
        .filter { endpoint.parameters[it].inputKind() is InputKind.Text }
        .firstNotNullOfOrNull { (values[it] as? FormValue.Text)?.text?.trim()?.takeIf(String::isNotEmpty) }

fun formatNumber(value: Double, integer: Boolean): String =
    if (integer || value % 1.0 == 0.0) value.toLong().toString() else value.toString()
