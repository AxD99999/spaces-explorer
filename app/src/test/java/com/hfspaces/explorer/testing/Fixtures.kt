package com.hfspaces.explorer.testing

import com.hfspaces.explorer.data.gradio.GradioApiInfo
import com.hfspaces.explorer.data.gradio.SpaceEndpoint
import com.hfspaces.explorer.data.gradio.toEndpoints
import com.hfspaces.explorer.data.remote.AppJson

/** Real responses captured from public Spaces, under `src/test/resources/fixtures`. */
object Fixtures {
    fun read(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }.readText()

    fun endpoints(infoFixture: String): List<SpaceEndpoint> =
        AppJson.decodeFromString<GradioApiInfo>(read(infoFixture)).toEndpoints()
}
