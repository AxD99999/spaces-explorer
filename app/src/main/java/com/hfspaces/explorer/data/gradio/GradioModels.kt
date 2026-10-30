package com.hfspaces.explorer.data.gradio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl

/**
 * Decodes text that newer Gradio versions sometimes send as an object, e.g. labels as
 * `{"key": "chat_interface.chatbot", "_type": "translation_metadata"}`. Objects become their
 * `key` (or null) instead of failing the whole API description.
 */
object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeString()
        return when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> (element["key"] as? JsonPrimitive)?.contentOrNull
                ?.substringAfterLast('.')
                ?.replace('_', ' ')
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** Raw shape of a Gradio app's `/info` response. */
@Serializable
data class GradioApiInfo(
    @SerialName("named_endpoints")
    val namedEndpoints: Map<String, GradioEndpointInfo> = emptyMap(),
    @SerialName("unnamed_endpoints")
    val unnamedEndpoints: Map<String, GradioEndpointInfo> = emptyMap()
)

@Serializable
data class GradioEndpointInfo(
    val parameters: List<GradioParameter> = emptyList(),
    val returns: List<GradioReturn> = emptyList(),
    @Serializable(with = LenientStringSerializer::class)
    val description: String? = null,
    @SerialName("api_visibility")
    @Serializable(with = LenientStringSerializer::class)
    val visibility: String? = null,
    @SerialName("code_snippets")
    val codeSnippets: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class GradioParameter(
    @Serializable(with = LenientStringSerializer::class)
    val label: String? = null,
    @SerialName("parameter_name")
    @Serializable(with = LenientStringSerializer::class)
    val parameterName: String? = null,
    @SerialName("parameter_has_default")
    val hasDefault: Boolean = false,
    @SerialName("parameter_default")
    val default: JsonElement? = null,
    @Serializable(with = LenientStringSerializer::class)
    val component: String? = null,
    val type: JsonElement? = null,
    @SerialName("python_type")
    val pythonType: GradioPythonType? = null,
    @SerialName("example_input")
    val exampleInput: JsonElement? = null
) {
    val displayLabel: String
        get() = label?.takeIf { it.isNotBlank() } ?: parameterName?.takeIf { it.isNotBlank() } ?: "Input"

    val defaultValue: JsonElement?
        get() = default.takeIf { hasDefault && it != null && it != JsonNull }

    val example: JsonElement?
        get() = exampleInput.takeIf { it != null && it != JsonNull }
}

@Serializable
data class GradioPythonType(
    @Serializable(with = LenientStringSerializer::class)
    val type: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val description: String? = null
)

@Serializable
data class GradioReturn(
    @Serializable(with = LenientStringSerializer::class)
    val label: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val component: String? = null,
    @SerialName("python_type")
    val pythonType: GradioPythonType? = null
)

data class SpaceEndpoint(
    val name: String,
    val parameters: List<GradioParameter>,
    val returns: List<GradioReturn>,
    val description: String? = null,
    val codeSnippets: Map<String, String> = emptyMap()
)

/** Everything needed to call a Space. [apiPrefix] is `/gradio_api` on Gradio 5+ and empty before. */
data class SpaceApiSpec(
    val spaceId: String,
    val host: HttpUrl,
    val apiPrefix: String,
    val endpoints: List<SpaceEndpoint>
)

fun GradioApiInfo.toEndpoints(): List<SpaceEndpoint> {
    val named = namedEndpoints
        .filterValues { it.visibility == null || it.visibility == "public" }
        .map { (name, info) -> info.toEndpoint(if (name.startsWith("/")) name else "/$name") }
    if (named.isNotEmpty()) return named
    return unnamedEndpoints.map { (index, info) -> info.toEndpoint(index) }
}

private fun GradioEndpointInfo.toEndpoint(name: String) = SpaceEndpoint(
    name = name,
    parameters = parameters,
    returns = returns,
    description = description?.takeIf { it.isNotBlank() },
    codeSnippets = codeSnippets.mapNotNull { (lang, code) -> (code as? JsonPrimitive)?.contentOrNull?.let { lang to it } }.toMap()
)
