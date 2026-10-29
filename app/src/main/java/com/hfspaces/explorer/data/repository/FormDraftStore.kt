package com.hfspaces.explorer.data.repository

import com.hfspaces.explorer.data.gradio.FormValue
import java.util.concurrent.ConcurrentHashMap

/** Keeps runner inputs per Space and endpoint for the life of the process. */
class FormDraftStore {
    private val drafts = ConcurrentHashMap<String, Map<Int, FormValue>>()

    fun get(spaceId: String, endpoint: String): Map<Int, FormValue>? = drafts[key(spaceId, endpoint)]

    fun put(spaceId: String, endpoint: String, values: Map<Int, FormValue>) {
        drafts[key(spaceId, endpoint)] = values
    }

    fun clear(spaceId: String, endpoint: String) {
        drafts.remove(key(spaceId, endpoint))
    }

    private fun key(spaceId: String, endpoint: String) = "$spaceId\u0000$endpoint"
}
