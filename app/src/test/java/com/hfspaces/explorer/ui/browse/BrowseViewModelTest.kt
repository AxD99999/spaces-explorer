package com.hfspaces.explorer.ui.browse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.data.remote.HfHubClient
import com.hfspaces.explorer.data.repository.RecentSearches
import com.hfspaces.explorer.data.repository.SettingsRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowseViewModelTest {
    private val server = MockWebServer()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("browse_test", Context.MODE_PRIVATE)
    private val settings = SettingsRepository(prefs)

    /** Pages of the fake listing; each page is a list of (id, adult). */
    private var pages: List<List<Pair<String, Boolean>>> = emptyList()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs.edit().clear().commit()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val page = request.requestUrl?.queryParameter("p")?.toInt() ?: 0
                val spaces = pages.getOrNull(page) ?: return MockResponse().setResponseCode(400)
                val json = spaces.joinToString(",") { (id, adult) ->
                    val tags = if (adult) "\"gradio\",\"not-for-all-audiences\"" else "\"gradio\""
                    """{"id":"$id","sdk":"gradio","tags":[$tags]}"""
                }
                return MockResponse().setBody("""{"spaces":[$json],"numItemsPerPage":${spaces.size}}""")
            }
        }
        server.start()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun viewModel(): BrowseViewModel {
        val repository = SpacesRepository(
            HfHubClient(OkHttpClient(), server.url("/")),
            GradioClient(OkHttpClient()),
            Files.createTempDirectory("browse").toFile()
        )
        return BrowseViewModel(repository, RecentSearches(prefs), settings)
    }

    private fun BrowseViewModel.loadedState(): BrowseUiState = runBlocking {
        withTimeout(10_000) { uiState.first { !it.isLoading && !it.isLoadingMore } }
    }

    @Test
    fun `adult spaces are hidden by default and shown when the setting is on`() {
        pages = listOf(listOf("a/safe" to false, "a/adult" to true))
        val vm = viewModel()
        assertEquals(listOf("a/safe"), vm.loadedState().spaces.map { it.id })

        settings.setShowAdultSpaces(true)
        assertEquals(listOf("a/safe", "a/adult"), vm.loadedState().spaces.map { it.id })
    }

    @Test
    fun `a page of only hidden spaces makes browse fetch the next pages`() {
        val hidden = (1..6).map { "h/a$it" to true }
        val safe = (1..6).map { "s/b$it" to false }
        pages = listOf(hidden, hidden.map { it.copy(first = it.first + "x") }, safe, safe.map { it.copy(first = it.first + "y") })
        val state = viewModel().loadedState()
        // Pages 0 and 1 are fully hidden, so it keeps going until page 2 gives a screenful.
        assertEquals(safe.map { it.first }, state.spaces.map { it.id })
        assertEquals(3, server.requestCount)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `skipping hidden pages gives up after a few pages`() {
        pages = (0 until 10).map { page -> (1..6).map { "h/p${page}n$it" to true } }
        val state = viewModel().loadedState()
        assertEquals(emptyList<String>(), state.spaces.map { it.id })
        assertEquals(4, server.requestCount)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `with adult spaces shown, browse loads a single page`() {
        settings.setShowAdultSpaces(true)
        pages = listOf((1..6).map { "h/a$it" to true }, (1..6).map { "s/b$it" to false })
        val state = viewModel().loadedState()
        assertEquals(6, state.spaces.size)
        assertEquals(1, server.requestCount)
        assertFalse(state.isLoading)
    }
}
