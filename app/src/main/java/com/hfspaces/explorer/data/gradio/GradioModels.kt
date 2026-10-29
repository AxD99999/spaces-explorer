package com.hfspaces.explorer.data.gradio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.HttpUrl

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
    val description: String? = null,
    @SerialName("api_visibility")
    val visibility: String? = null,
    @SerialName("code_snippets")
    val codeSnippets: Map<String, String> = emptyMap()
)

@Serializable
data class GradioParameter(
    val label: String? = null,
    @SerialName("parameter_name")
    val parameterName: String? = null,
    @SerialName("parameter_has_default")
    val hasDefault: Boolean = false,
    @SerialName("parameter_default")
    val default: JsonElement? = null,
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
    val type: String? = null,
    val description: String? = null
)

@Serializable
data class GradioReturn(
    val label: String? = null,
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
    codeSnippets = codeSnippets
)
