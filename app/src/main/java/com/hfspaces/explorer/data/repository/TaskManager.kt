package com.hfspaces.explorer.data.repository

import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.PickedFile
import com.hfspaces.explorer.data.gradio.SpaceApiSpec
import com.hfspaces.explorer.data.gradio.SpaceEndpoint
import com.hfspaces.explorer.data.gradio.UploadedFile
import com.hfspaces.explorer.data.gradio.buildPayload
import com.hfspaces.explorer.data.gradio.detectPrompt
import com.hfspaces.explorer.data.gradio.parseOutputs
import com.hfspaces.explorer.data.local.dao.TaskDao
import com.hfspaces.explorer.data.local.entity.TaskEntity
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceTask
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.data.remote.AppJson
import com.hfspaces.explorer.data.remote.CallUpdate
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.data.remote.UploadSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Reads a picked file's bytes; backed by the ContentResolver on device. */
fun interface ContentOpener {
    fun open(uri: String): InputStream
}

/**
 * Runs Space calls in an application-wide scope so they keep going while the user browses, and
 * keeps a persisted history of them.
 */
class TaskManager(
    private val scope: CoroutineScope,
    private val gradio: GradioClient,
    private val mediaRepository: MediaRepository,
    private val taskDao: TaskDao,
    private val contentOpener: ContentOpener
) {
    private val _tasks = MutableStateFlow<List<SpaceTask>>(emptyList())
    val tasks: StateFlow<List<SpaceTask>> = _tasks.asStateFlow()

    private val _finished = MutableSharedFlow<SpaceTask>(extraBufferCapacity = 8)

    /** Emits each task when it stops running, for "done" notifications. */
    val finished: SharedFlow<SpaceTask> = _finished.asSharedFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    /** Loads history; new tasks wait for it so their rows aren't marked interrupted. */
    private val historyLoaded: Job = scope.launch {
        taskDao.markInterrupted("Interrupted: the app was closed while this was running", System.currentTimeMillis())
        val history = taskDao.getAll().mapNotNull { it.toModel() }
        _tasks.update { current -> (current + history.filter { h -> current.none { it.id == h.id } }).sortedByDescending { it.startTime } }
    }

    fun latestForSpace(spaceId: String): SpaceTask? = _tasks.value.firstOrNull { it.space.id == spaceId }

    fun start(
        space: HfSpace,
        spec: SpaceApiSpec,
        endpoint: SpaceEndpoint,
        values: Map<Int, FormValue>,
        token: String?
    ): String {
        val task = SpaceTask(id = "task_${UUID.randomUUID()}", space = space, endpoint = endpoint.name)
        put(task)
        val job = scope.launch {
            historyLoaded.join()
            persist(task)
            try {
                val uploads = uploadFiles(spec, values, token)
                val payload = buildPayload(endpoint, values, uploads)
                val result = gradio.predict(spec.host, spec.apiPrefix, endpoint.name, payload, token) { update ->
                    when (update) {
                        CallUpdate.Queued -> modify(task.id) { it.copy(progressMessage = "Waiting in the Space's queue…") }
                        is CallUpdate.Generating -> modify(task.id) { it.copy(progressMessage = "Generating…", result = update.partial ?: it.result) }
                    }
                }
                modify(task.id) { it.copy(progressMessage = "Saving outputs…", result = result) }
                val elapsed = (System.currentTimeMillis() - task.startTime) / 1000.0
                val outputs = parseOutputs(result, spec.host, spec.apiPrefix, endpoint.returns)
                val mediaIds = mediaRepository.saveOutputs(outputs, space, endpoint.name, detectPrompt(endpoint, values), elapsed, token)
                finish(task.id) { it.copy(status = TaskStatus.COMPLETED, result = result, savedMediaIds = mediaIds) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { finish(task.id) { it.copy(status = TaskStatus.CANCELLED, error = "Stopped") } }
                throw e
            } catch (e: Exception) {
                finish(task.id) { it.copy(status = TaskStatus.ERROR, error = e.message ?: e.javaClass.simpleName) }
            } finally {
                jobs.remove(task.id)
            }
        }
        jobs[task.id] = job
        return task.id
    }

    /** Cancels the coroutine, which cancels the in-flight HTTP call too. */
    fun cancel(taskId: String) {
        jobs[taskId]?.cancel()
    }

    fun remove(taskId: String) {
        jobs.remove(taskId)?.cancel()
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        scope.launch { taskDao.delete(taskId) }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.isActive } }
        scope.launch { taskDao.deleteFinished() }
    }

    private suspend fun uploadFiles(spec: SpaceApiSpec, values: Map<Int, FormValue>, token: String?): Map<String, UploadedFile> {
        val picked: List<PickedFile> = values.values.filterIsInstance<FormValue.Files>()
            .flatMap { it.files }
            .distinctBy { it.uri }
        if (picked.isEmpty()) return emptyMap()
        val sources = picked.map { file -> UploadSource(file.name, file.mimeType, file.sizeBytes) { contentOpener.open(file.uri) } }
        val paths = gradio.upload(spec.host, spec.apiPrefix, sources, token)
        return picked.zip(paths).associate { (file, path) -> file.uri to UploadedFile(path, file) }
    }

    private fun put(task: SpaceTask) {
        _tasks.update { list -> listOf(task) + list.filterNot { it.id == task.id } }
    }

    private fun modify(taskId: String, change: (SpaceTask) -> SpaceTask) {
        _tasks.update { list -> list.map { if (it.id == taskId) change(it) else it } }
    }

    private suspend fun finish(taskId: String, change: (SpaceTask) -> SpaceTask) {
        modify(taskId) { change(it).copy(endTime = System.currentTimeMillis(), progressMessage = null) }
        _tasks.value.firstOrNull { it.id == taskId }?.let { done ->
            persist(done)
            taskDao.trim(HISTORY_LIMIT)
            _finished.tryEmit(done)
        }
    }

    private suspend fun persist(task: SpaceTask) {
        taskDao.upsert(
            TaskEntity(
                id = task.id,
                spaceId = task.space.id,
                spaceJson = AppJson.encodeToString(HfSpace.serializer(), task.space),
                endpoint = task.endpoint,
                status = task.status.name,
                startTime = task.startTime,
                endTime = task.endTime,
                error = task.error,
                resultJson = task.result?.toString()?.takeIf { it.length <= MAX_STORED_RESULT_CHARS },
                savedMediaIds = task.savedMediaIds.joinToString(",")
            )
        )
    }

    private fun TaskEntity.toModel(): SpaceTask? = runCatching {
        SpaceTask(
            id = id,
            space = AppJson.decodeFromString(HfSpace.serializer(), spaceJson),
            endpoint = endpoint,
            status = TaskStatus.valueOf(status),
            startTime = startTime,
            endTime = endTime,
            error = error,
            result = resultJson?.let { AppJson.parseToJsonElement(it) as JsonElement },
            savedMediaIds = savedMediaIds.split(',').filter { it.isNotBlank() }
        )
    }.getOrNull()

    private companion object {
        const val HISTORY_LIMIT = 100
        const val MAX_STORED_RESULT_CHARS = 200_000
    }
}
