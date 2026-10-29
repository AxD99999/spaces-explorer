package com.hfspaces.explorer.data.gradio

import com.hfspaces.explorer.data.model.GeneratedMediaType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl

/** One renderable piece of a Space's output. */
data class OutputItem(
    val type: GeneratedMediaType,
    /** Absolute URL for files; null for text and JSON. */
    val url: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val text: String? = null,
    val label: String? = null
)

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "avif")
private val VIDEO_EXT = setOf("mp4", "webm", "mov", "mkv", "m4v")
private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus")

/**
 * Flattens Gradio output `data` into display items: FileData objects become media, galleries and
 * tuples are walked, strings become text, anything else is shown as JSON.
 *
 * Relative file paths are resolved against [host] using the app's file route.
 */
fun parseOutputs(
    data: JsonElement?,
    host: HttpUrl,
    apiPrefix: String,
    returns: List<GradioReturn> = emptyList()
): List<OutputItem> {
    val items = mutableListOf<OutputItem>()
    val top = data as? JsonArray ?: data?.let { JsonArray(listOf(it)) } ?: return items
    top.forEachIndexed { index, element ->
        val label = returns.getOrNull(index)?.label
        val component = returns.getOrNull(index)?.component?.lowercase()
        collect(element, host, apiPrefix, label, component, items)
    }
    return items
}

private fun collect(
    element: JsonElement,
    host: HttpUrl,
    apiPrefix: String,
    label: String?,
    component: String?,
    out: MutableList<OutputItem>
) {
    when (element) {
        JsonNull -> Unit
        is JsonPrimitive -> {
            val text = element.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) return
            val type = if (element.isString) typeFor(null, text) else GeneratedMediaType.TEXT
            // Bare strings only count as files when they look like URLs or the component says so.
            val looksLikeFile = type != GeneratedMediaType.TEXT &&
                (text.startsWith("http") || text.startsWith("data:") || component in FILE_COMPONENTS)
            if (looksLikeFile) {
                out += OutputItem(type, url = resolveFileUrl(text, host, apiPrefix), fileName = text.substringAfterLast('/'), label = label)
            } else {
                out += OutputItem(GeneratedMediaType.TEXT, text = text, label = label)
            }
        }
        is JsonObject -> {
            val fileRef = fileReference(element)
            when {
                fileRef != null -> {
                    val (rawUrl, mime, name) = fileRef
                    val url = resolveFileUrl(rawUrl, host, apiPrefix)
                    out += OutputItem(typeFor(mime, name ?: rawUrl), url = url, mimeType = mime, fileName = name, label = label)
                }
                // Gallery entries: {"image": FileData, "caption": ...} / {"video": FileData, ...}
                element["image"] is JsonObject || element["video"] is JsonObject -> {
                    (element["image"] ?: element["video"])?.let { collect(it, host, apiPrefix, label, component, out) }
                }
                // Gradio wraps some outputs as {"__type__": "update", "value": ...}
                element["__type__"] != null && element.containsKey("value") ->
                    element["value"]?.let { collect(it, host, apiPrefix, label, component, out) }
                else -> out += OutputItem(GeneratedMediaType.OTHER, text = element.toString(), label = label)
            }
        }
        is JsonArray -> {
            if (component == "chatbot") {
                out += OutputItem(GeneratedMediaType.OTHER, text = element.toString(), label = label)
            } else {
                element.forEach { collect(it, host, apiPrefix, label, component, out) }
            }
        }
    }
}

private val FILE_COMPONENTS = setOf("image", "audio", "video", "file", "gallery", "model3d")

private fun fileReference(obj: JsonObject): Triple<String, String?, String?>? {
    val url = (obj["url"] as? JsonPrimitive)?.contentOrNull
    val path = (obj["path"] as? JsonPrimitive)?.contentOrNull
    val isFileData = obj["meta"] != null || url != null || (path != null && obj.containsKey("orig_name"))
    if (!isFileData) return null
    val target = url ?: path ?: return null
    val mime = (obj["mime_type"] as? JsonPrimitive)?.contentOrNull
    val name = (obj["orig_name"] as? JsonPrimitive)?.contentOrNull ?: path?.substringAfterLast('/')
    return Triple(target, mime, name)
}

fun typeFor(mimeType: String?, nameOrUrl: String): GeneratedMediaType {
    val mime = mimeType.orEmpty().lowercase()
    if (nameOrUrl.startsWith("data:")) {
        val dataMime = nameOrUrl.removePrefix("data:").substringBefore(';').lowercase()
        return typeFor(dataMime, "")
    }
    val ext = nameOrUrl.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") || ext in IMAGE_EXT -> GeneratedMediaType.IMAGE
        mime.startsWith("video/") || ext in VIDEO_EXT -> GeneratedMediaType.VIDEO
        mime.startsWith("audio/") || ext in AUDIO_EXT -> GeneratedMediaType.AUDIO
        mime.isNotEmpty() -> GeneratedMediaType.FILE
        ext.isNotEmpty() && ext.length <= 5 && nameOrUrl.contains('/') -> GeneratedMediaType.FILE
        else -> GeneratedMediaType.TEXT
    }
}

/** Absolute URL for a Gradio file reference; relative paths go through the Space's `/file=` route. */
fun resolveFileUrl(raw: String, host: HttpUrl, apiPrefix: String): String = when {
    raw.startsWith("https://") || raw.startsWith("http://") || raw.startsWith("data:") -> raw
    raw.startsWith("/file=") || raw.startsWith("$apiPrefix/file=") && apiPrefix.isNotEmpty() ->
        host.toString().trimEnd('/') + raw
    else -> host.toString().trimEnd('/') + "$apiPrefix/file=" + raw
}
