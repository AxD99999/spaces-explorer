package com.hfspaces.explorer.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hfspaces.explorer.data.model.*
import com.hfspaces.explorer.data.remote.HfApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TaskManager(
    private val api: HfApiClient,
    private val mediaRepository: MediaRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val tasksMap = ConcurrentHashMap<String, ActiveSpaceTask>()
    private val jobMap = ConcurrentHashMap<String, Job>()
    private val gson = Gson()

    private val _tasksFlow = MutableStateFlow<List<ActiveSpaceTask>>(emptyList())
    val tasksFlow: StateFlow<List<ActiveSpaceTask>> = _tasksFlow.asStateFlow()

    init {
        // Background ticker for live elapsed time updates
        coroutineScope.launch {
            while (isActive) {
                delay(250)
                var hasRunning = false
                val now = System.currentTimeMillis()
                for ((id, task) in tasksMap) {
                    if (task.status == TaskStatus.RUNNING) {
                        hasRunning = true
                        val elapsed = ((now - task.startTime) / 100.0).toLong() / 10.0
                        tasksMap[id] = task.copy(elapsedSeconds = elapsed)
                    }
                }
                if (hasRunning) {
                    emitUpdates()
                }
            }
        }
    }

    private fun emitUpdates() {
        _tasksFlow.value = tasksMap.values.sortedByDescending { it.startTime }
    }

    fun getTask(taskId: String): ActiveSpaceTask? = tasksMap[taskId]

    fun getTaskForSpace(spaceId: String): ActiveSpaceTask? {
        return tasksMap.values.find { it.space.id == spaceId && it.status == TaskStatus.RUNNING }
    }

    fun startTask(
        space: HfSpace,
        endpoint: String,
        formValues: Map<String, Any>,
        payloadData: List<Any?>,
        token: String? = null,
        subdomain: String? = null
    ): String {
        val taskId = "task_${space.id.replace(Regex("[^a-zA-Z0-9]"), "_")}_${System.currentTimeMillis()}"
        val task = ActiveSpaceTask(
            id = taskId,
            space = space,
            endpoint = endpoint,
            formValues = formValues,
            status = TaskStatus.RUNNING,
            startTime = System.currentTimeMillis(),
            elapsedSeconds = 0.0
        )
        tasksMap[taskId] = task
        emitUpdates()

        val job = coroutineScope.launch {
            try {
                val spaceSubdomain = subdomain ?: space.subdomain ?: space.id.replace('/', '-').lowercase()
                val (rawResult, host) = api.runSpaceInference(spaceSubdomain, endpoint, payloadData, token)
                val elapsed = ((System.currentTimeMillis() - task.startTime) / 100.0).toLong() / 10.0

                // Extract prompt text from form values if present
                var detectedPrompt: String? = null
                for (v in formValues.values) {
                    if (v is String && v.length > 3 && detectedPrompt == null) {
                        detectedPrompt = v
                    }
                }

                // Extract media items
                val mediaItems = extractMediaItems(rawResult, space, endpoint, detectedPrompt, elapsed, host)
                for (m in mediaItems) {
                    mediaRepository.saveMedia(m)
                }

                tasksMap[taskId] = task.copy(
                    status = TaskStatus.COMPLETED,
                    result = rawResult,
                    elapsedSeconds = elapsed,
                    savedMediaIds = mediaItems.map { it.id }
                )
            } catch (e: Exception) {
                val elapsed = ((System.currentTimeMillis() - task.startTime) / 100.0).toLong() / 10.0
                tasksMap[taskId] = task.copy(
                    status = TaskStatus.ERROR,
                    error = e.message ?: "Execution failed",
                    elapsedSeconds = elapsed
                )
            } finally {
                jobMap.remove(taskId)
                emitUpdates()
            }
        }
        jobMap[taskId] = job
        return taskId
    }

    fun cancelTask(taskId: String) {
        jobMap[taskId]?.cancel()
        val task = tasksMap[taskId]
        if (task != null && task.status == TaskStatus.RUNNING) {
            tasksMap[taskId] = task.copy(status = TaskStatus.ERROR, error = "Cancelled by user")
            emitUpdates()
        }
    }

    fun removeTask(taskId: String) {
        jobMap[taskId]?.cancel()
        jobMap.remove(taskId)
        tasksMap.remove(taskId)
        emitUpdates()
    }

    fun clearCompleted() {
        val toRemove = tasksMap.filter { it.value.status != TaskStatus.RUNNING }.keys
        for (k in toRemove) {
            tasksMap.remove(k)
        }
        emitUpdates()
    }

    private fun extractMediaItems(
        raw: Any?,
        space: HfSpace,
        endpoint: String,
        prompt: String?,
        elapsedSeconds: Double,
        host: String
    ): List<GeneratedMediaItem> {
        val list = mutableListOf<GeneratedMediaItem>()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        fun resolveUrl(url: String): String {
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) return url
            if (url.startsWith("/")) return "$host$url"
            return "$host/gradio_api/file=$url"
        }

        fun processValue(value: Any?, index: Int) {
            if (value == null) return

            if (value is String) {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) return

                // Check if string is a URL to image, video, audio or data URL
                val isImg = trimmed.matches(Regex(".*\\.(png|jpg|jpeg|webp|gif)$", RegexOption.IGNORE_CASE)) || trimmed.startsWith("data:image/")
                val isVid = trimmed.matches(Regex(".*\\.(mp4|webm|mov)$", RegexOption.IGNORE_CASE)) || trimmed.startsWith("data:video/")
                val isAud = trimmed.matches(Regex(".*\\.(mp3|wav|ogg)$", RegexOption.IGNORE_CASE)) || trimmed.startsWith("data:audio/")

                if (isImg) {
                    list.add(
                        GeneratedMediaItem(
                            id = "media_${System.currentTimeMillis()}_$index",
                            type = GeneratedMediaType.IMAGE,
                            url = resolveUrl(trimmed),
                            title = prompt?.take(40) ?: "${space.displayTitle} Image",
                            prompt = prompt,
                            spaceId = space.id,
                            spaceTitle = space.displayTitle,
                            spaceEmoji = space.displayEmoji,
                            createdAt = dateStr,
                            endpoint = endpoint,
                            elapsedSeconds = elapsedSeconds
                        )
                    )
                } else if (isVid) {
                    list.add(
                        GeneratedMediaItem(
                            id = "media_${System.currentTimeMillis()}_$index",
                            type = GeneratedMediaType.VIDEO,
                            url = resolveUrl(trimmed),
                            title = prompt?.take(40) ?: "${space.displayTitle} Video",
                            prompt = prompt,
                            spaceId = space.id,
                            spaceTitle = space.displayTitle,
                            spaceEmoji = space.displayEmoji,
                            createdAt = dateStr,
                            endpoint = endpoint,
                            elapsedSeconds = elapsedSeconds
                        )
                    )
                } else if (isAud) {
                    list.add(
                        GeneratedMediaItem(
                            id = "media_${System.currentTimeMillis()}_$index",
                            type = GeneratedMediaType.AUDIO,
                            url = resolveUrl(trimmed),
                            title = prompt?.take(40) ?: "${space.displayTitle} Audio",
                            prompt = prompt,
                            spaceId = space.id,
                            spaceTitle = space.displayTitle,
                            spaceEmoji = space.displayEmoji,
                            createdAt = dateStr,
                            endpoint = endpoint,
                            elapsedSeconds = elapsedSeconds
                        )
                    )
                } else {
                    // Text output
                    list.add(
                        GeneratedMediaItem(
                            id = "media_${System.currentTimeMillis()}_$index",
                            type = GeneratedMediaType.TEXT,
                            textContent = trimmed,
                            title = prompt?.take(40) ?: "${space.displayTitle} Text",
                            prompt = prompt,
                            spaceId = space.id,
                            spaceTitle = space.displayTitle,
                            spaceEmoji = space.displayEmoji,
                            createdAt = dateStr,
                            endpoint = endpoint,
                            elapsedSeconds = elapsedSeconds
                        )
                    )
                }
                return
            }

            if (value is List<*>) {
                value.forEachIndexed { idx, item -> processValue(item, idx) }
                return
            }

            // Other JSON objects
            list.add(
                GeneratedMediaItem(
                    id = "media_${System.currentTimeMillis()}_$index",
                    type = GeneratedMediaType.OTHER,
                    textContent = value.toString(),
                    title = "${space.displayTitle} Output",
                    prompt = prompt,
                    spaceId = space.id,
                    spaceTitle = space.displayTitle,
                    spaceEmoji = space.displayEmoji,
                    createdAt = dateStr,
                    endpoint = endpoint,
                    elapsedSeconds = elapsedSeconds
                )
            )
        }

        if (raw is String) {
            try {
                val jsonArr = gson.fromJson(raw, JsonArray::class.java)
                for (i in 0 until jsonArr.size()) {
                    val elem = jsonArr.get(i)
                    if (elem.isJsonObject && elem.asJsonObject.has("url")) {
                        processValue(elem.asJsonObject.get("url").asString, i)
                    } else if (elem.isJsonPrimitive) {
                        processValue(elem.asString, i)
                    }
                }
            } catch (e: Exception) {
                processValue(raw, 0)
            }
        } else {
            processValue(raw, 0)
        }

        return list
    }
}
