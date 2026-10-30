package com.hfspaces.explorer.data.gradio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The subset of a Gradio app's `/config` needed to use its queue. */
@Serializable
data class GradioConfig(
    val version: String? = null,
    val protocol: String? = null,
    @SerialName("api_prefix")
    val apiPrefix: String? = null,
    val dependencies: List<GradioDependency> = emptyList(),
    val components: List<GradioComponent> = emptyList()
) {
    /** Function index for an endpoint, or null when it isn't served through the queue. */
    fun queuedFnIndex(apiName: String): Int? = findDependency(apiName)?.let { (index, dep) -> dep.id ?: index }

    /**
     * Lines the API payload up with the function's real inputs. Hidden `state` inputs (which the
     * `/info` description omits) get null, as the official clients send; their outputs are dropped.
     * Returns null when the shapes don't match, so the caller can use `/call` instead.
     */
    fun prepareQueuedCall(apiName: String, data: JsonArray): QueuedCall? {
        val (index, dep) = findDependency(apiName) ?: return null
        val fnIndex = dep.id ?: index
        if (dep.inputs.isEmpty() && dep.outputs.isEmpty()) return QueuedCall(fnIndex, data, null)
        val types = components.associate { it.id to it.type?.lowercase() }
        val visibleInputs = dep.inputs.count { types[it] != STATE }
        if (visibleInputs != data.size) return null
        var next = 0
        val aligned = dep.inputs.map { id -> if (types[id] == STATE) JsonNull else data[next++] }
        val keep = dep.outputs.indices.filter { types[dep.outputs[it]] != STATE }
        return QueuedCall(fnIndex, JsonArray(aligned), keep.takeIf { it.size != dep.outputs.size })
    }

    private fun findDependency(apiName: String): Pair<Int, GradioDependency>? {
        val name = apiName.trim('/')
        dependencies.forEachIndexed { index, dep ->
            val depName = (dep.apiName as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (depName == name) return if (dep.queue == false) null else index to dep
        }
        return null
    }

    /** Since sse_v2.1, streamed outputs after the first message arrive as diffs. */
    val streamsDiffs: Boolean
        get() = protocol == "sse_v2.1" || protocol == "sse_v3"
}

private const val STATE = "state"

@Serializable
data class GradioDependency(
    val id: Int? = null,
    @SerialName("api_name")
    val apiName: JsonElement? = null,
    val queue: Boolean? = null,
    val inputs: List<Int> = emptyList(),
    val outputs: List<Int> = emptyList()
)

@Serializable
data class GradioComponent(val id: Int, val type: String? = null)

/** A queue call ready to send; [keepOutputs] selects the outputs the API exposes (null = all). */
data class QueuedCall(val fnIndex: Int, val data: JsonArray, val keepOutputs: List<Int>?) {
    fun visible(outputs: JsonArray): JsonArray =
        keepOutputs?.let { keep -> JsonArray(keep.mapNotNull { outputs.getOrNull(it) }) } ?: outputs
}

/** One message from `/queue/data`, normalised. */
sealed interface QueueMessage {
    data class Estimation(val rank: Int?, val queueSize: Int?, val etaSeconds: Double?) : QueueMessage
    data class Starts(val etaSeconds: Double?) : QueueMessage
    data class Progress(val fraction: Float?, val description: String?) : QueueMessage
    data class Generating(val data: JsonArray) : QueueMessage
    data class Completed(val data: JsonArray) : QueueMessage
    data class Failed(val message: String?) : QueueMessage
    data object Heartbeat : QueueMessage
    data object Closed : QueueMessage
}

fun parseQueueMessage(obj: JsonObject): QueueMessage? {
    val output = obj["output"] as? JsonObject
    return when (obj.string("msg")) {
        "estimation" -> QueueMessage.Estimation(obj.int("rank"), obj.int("queue_size"), obj.double("rank_eta"))
        "process_starts" -> QueueMessage.Starts(obj.double("eta"))
        "progress" -> {
            val step = (obj["progress_data"] as? JsonArray)?.lastOrNull() as? JsonObject
            val index = step?.double("index")
            val length = step?.double("length")
            val fraction = step?.double("progress")?.toFloat()
                ?: if (index != null && length != null && length > 0) (index / length).toFloat() else null
            QueueMessage.Progress(fraction?.coerceIn(0f, 1f), step?.string("desc"))
        }
        "process_generating" -> (output?.get("data") as? JsonArray)?.let { QueueMessage.Generating(it) }
        "process_completed" -> {
            val success = (obj["success"] as? JsonPrimitive)?.booleanOrNull ?: true
            val data = output?.get("data") as? JsonArray
            if (success && data != null) QueueMessage.Completed(data)
            else QueueMessage.Failed((output?.get("error") as? JsonPrimitive)?.contentOrNull ?: obj.string("title")?.takeIf { it != "Error" })
        }
        "unexpected_error" -> QueueMessage.Failed(obj.string("message"))
        "heartbeat" -> QueueMessage.Heartbeat
        "close_stream" -> QueueMessage.Closed
        else -> null
    }
}

/**
 * Applies a Gradio streaming diff (a list of `[action, path, value]` edits) to [target], as the
 * official clients do. Supported actions: replace, append, add, delete.
 */
fun applyDiff(target: JsonElement, diff: JsonArray): JsonElement =
    diff.fold(target) { acc, edit ->
        val parts = edit as? JsonArray ?: throw IllegalArgumentException("Bad diff entry: $edit")
        val action = parts[0].jsonPrimitive.content
        val path = (parts.getOrNull(1) as? JsonArray).orEmpty()
        applyEdit(acc, path, action, parts.getOrNull(2) ?: JsonNull)
    }

private fun applyEdit(target: JsonElement, path: List<JsonElement>, action: String, value: JsonElement): JsonElement {
    if (path.isEmpty()) {
        return when (action) {
            "replace" -> value
            "append" -> concat(target, value)
            else -> throw IllegalArgumentException("Unsupported root edit: $action")
        }
    }
    val key = path.first()
    val rest = path.drop(1)
    return when (target) {
        is JsonArray -> {
            val list = target.toMutableList()
            val index = key.jsonPrimitive.intOrNull ?: throw IllegalArgumentException("Array index expected")
            if (rest.isEmpty()) {
                when (action) {
                    "replace" -> list[index] = value
                    "append" -> list[index] = concat(list[index], value)
                    "add" -> list.add(index.coerceAtMost(list.size), value)
                    "delete" -> list.removeAt(index)
                    else -> throw IllegalArgumentException("Unknown action $action")
                }
            } else {
                list[index] = applyEdit(list[index], rest, action, value)
            }
            JsonArray(list)
        }
        is JsonObject -> {
            val map = target.toMutableMap()
            val name = key.jsonPrimitive.content
            if (rest.isEmpty()) {
                when (action) {
                    "replace", "add" -> map[name] = value
                    "append" -> map[name] = concat(map[name] ?: JsonPrimitive(""), value)
                    "delete" -> map.remove(name)
                    else -> throw IllegalArgumentException("Unknown action $action")
                }
            } else {
                map[name] = applyEdit(map[name] ?: JsonNull, rest, action, value)
            }
            JsonObject(map)
        }
        else -> throw IllegalArgumentException("Can't descend into $target")
    }
}

private fun concat(a: JsonElement, b: JsonElement): JsonElement = when {
    a is JsonArray && b is JsonArray -> JsonArray(a + b)
    a is JsonArray -> JsonArray(a + b)
    else -> JsonPrimitive(((a as? JsonPrimitive)?.contentOrNull ?: "") + ((b as? JsonPrimitive)?.contentOrNull ?: ""))
}

private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()
private fun JsonObject.double(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
