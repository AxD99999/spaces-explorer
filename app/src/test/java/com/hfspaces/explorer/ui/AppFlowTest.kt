package com.hfspaces.explorer.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.hfspaces.explorer.HfSpacesApplication
import com.hfspaces.explorer.MainActivity
import com.hfspaces.explorer.testing.FakeHub
import com.hfspaces.explorer.testing.TestApp
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Drives the real app against a fake Hub: browse → run a Space → see and keep its output. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val timeout = 10_000L

    @Test
    fun `browse run and keep an output`() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        compose.onNodeWithText("renamed-space").assertExists()
        screenshot("01-browse")

        compose.onAllNodesWithText("Run")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("name"), timeout)
        screenshot("02-runner")

        compose.onNode(hasSetTextAction() and hasText("name")).performTextInput("Ada")
        compose.onNodeWithText("Run").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Hello Ada!!"), timeout)
        compose.waitUntilAtLeastOneExists(hasText("Saved to your outputs library"), timeout)
        screenshot("03-runner-output")

        compose.onNodeWithContentDescription("Space details and API").performClick()
        compose.waitUntilAtLeastOneExists(hasText("API endpoints (1)"), timeout)
        // Fresh metadata has no top-level title or description; the listing's must survive it.
        compose.onNodeWithText("Say hello").assertExists()
        compose.onNodeWithText("Text Generation").assertExists()
        compose.onAllNodesWithText("Hello World")[0].assertExists()
        screenshot("04-details")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Space details and API"), timeout)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Open menu"), timeout)
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Saved outputs").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Ada"), timeout)
        screenshot("05-saved-outputs")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Runs").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Done"), timeout)
        screenshot("06-runs")
    }

    @Test
    @Config(qualifiers = "+night")
    fun `dark theme renders browse and filters`() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        screenshot("07-browse-dark")
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Filter & sort"), timeout)
        screenshot("08-filters-dark")
    }

    @Test
    fun `run again restores the inputs of a past run`() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        compose.onAllNodesWithText("Run")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("name"), timeout)
        compose.onNode(hasSetTextAction() and hasText("name")).performTextInput("Grace")
        compose.onNodeWithText("Run").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Hello Grace!!"), timeout)

        // Change the input, then go back to history and repeat the earlier run.
        compose.onNode(hasSetTextAction() and hasText("Grace")).performTextReplacement("Someone else")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Open menu"), timeout)
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Runs").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Run again"), timeout)
        screenshot("09-runs-run-again")
        compose.onNodeWithText("Run again").performClick()
        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("Grace"), timeout)
    }

    @Test
    fun `shared text goes into the chosen space`() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        compose.runOnUiThread {
            (compose.activity.application as HfSpacesApplication).container.deepLinks.handle(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Shared name")
            )
        }
        compose.waitUntilAtLeastOneExists(hasText("Send to a Space"), timeout)
        compose.onNode(hasSetTextAction() and hasText("Search Gradio Spaces")).performTextInput("hello")
        // Robolectric's clock only moves when told to; let the search debounce elapse.
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)) }
        compose.waitUntilAtLeastOneExists(hasText("gradio/hello_world"), timeout)
        screenshot("10-share-target")
        compose.onNodeWithText("gradio/hello_world").performClick()
        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("Shared name"), timeout)
    }

    @Test
    fun `coming back from a space keeps your place in the list`() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        compose.onNode(hasSetTextAction() and hasText("Search Spaces")).performTextInput("many")
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)) }
        compose.waitUntilAtLeastOneExists(hasText("Many 01"), timeout)

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Many 25"))
        compose.onNodeWithText("Many 25").assertIsDisplayed().performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Share Space"), timeout)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Open menu"), timeout)
        compose.waitForIdle()
        compose.onNodeWithText("Many 25").assertIsDisplayed()
    }

    @Test
    fun `a link to a space opens it`() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        val links = (compose.activity.application as HfSpacesApplication).container.deepLinks
        compose.runOnUiThread {
            links.handle(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/spaces/gradio/hello_world?logs=container")))
        }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Share Space"), timeout)
        compose.waitUntilAtLeastOneExists(hasText("API endpoints (1)"), timeout)
        // The About card, from metadata the listing doesn't have.
        compose.waitUntilAtLeastOneExists(hasText("Gradio 6.32.0"), timeout)
        compose.onNodeWithText("openai/whisper-large-v3").assertExists()
        compose.onNodeWithText("Web app").assertExists()
        screenshot("15-details-from-link")
        compose.onNodeWithContentDescription("Back").performClick()

        // Sharing a Space's page from a browser opens the Space too, rather than the "send to" picker.
        compose.runOnUiThread {
            links.handle(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Static site https://hf.co/spaces/example/static-site"))
        }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Share Space"), timeout)

        // Other Hub pages aren't ours to show.
        compose.runOnUiThread {
            assertFalse(links.handle(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/spaces/gradio"))))
        }
    }

    @Test
    fun `a pasted id opens that space from search`() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        compose.onNode(hasSetTextAction() and hasText("Search Spaces")).performTextInput("https://huggingface.co/spaces/gradio/hello_world")
        compose.waitUntilAtLeastOneExists(hasText("Open gradio/hello_world"), timeout)
        screenshot("16-search-open-link")
        compose.onNodeWithText("Open gradio/hello_world").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Share Space"), timeout)
    }

    @Test
    fun `chat remembers the conversation and renders markdown`() {
        FakeHub.chat = true
        try {
            compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
            compose.onAllNodesWithText("Run")[0].performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription("Open as chat"), timeout)
            compose.onNodeWithContentDescription("Open as chat").performClick()
            compose.waitUntilAtLeastOneExists(hasText("Start a conversation"), timeout)

            fun say(text: String) {
                compose.onNode(hasSetTextAction()).performTextInput(text)
                compose.onNodeWithContentDescription("Send").performClick()
            }
            say("hello")
            // "**hello**" arrives as Markdown and is shown as bold text, not as asterisks.
            compose.waitUntilAtLeastOneExists(hasText("You said hello (turn 1)"), timeout)
            say("and again")
            // Same Gradio session as before, or the Space would call this turn 1 too.
            compose.waitUntilAtLeastOneExists(hasText("You said and again (turn 2)"), timeout)
            screenshot("17-chat")

            // The conversation is stored: leaving and coming back shows it again.
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription("Open as chat"), timeout)
            compose.onNodeWithContentDescription("Open as chat").performClick()
            compose.waitUntilAtLeastOneExists(hasText("You said and again (turn 2)"), timeout)
            compose.onNodeWithText("The Space no longer remembers the messages above").assertDoesNotExist()

            compose.onNodeWithContentDescription("Clear conversation").performClick()
            compose.onNodeWithText("Clear").performClick()
            compose.waitUntilAtLeastOneExists(hasText("Start a conversation"), timeout)
            say("fresh")
            compose.waitUntilAtLeastOneExists(hasText("You said fresh (turn 1)"), timeout)
        } finally {
            FakeHub.chat = false
        }
    }

    @Test
    fun `new screens render`() {
        compose.waitUntilAtLeastOneExists(hasContentDescription("Open menu"), timeout)
        fun open(item: String) {
            compose.onNodeWithContentDescription("Open menu").performClick()
            compose.onNodeWithText(item).performClick()
        }
        open("Settings")
        compose.waitUntilAtLeastOneExists(hasText("Dynamic colour"), timeout)
        screenshot("11-settings")
        compose.onNodeWithContentDescription("Back").performClick()

        open("Compare")
        compose.waitUntilAtLeastOneExists(hasText("Compare Spaces"), timeout)
        screenshot("12-compare-empty")
        compose.onNodeWithContentDescription("Back").performClick()

        open("My Spaces")
        compose.waitUntilAtLeastOneExists(hasText("Add a token first"), timeout)
        screenshot("13-my-spaces-no-token")
        compose.onNodeWithContentDescription("Back").performClick()

        open("Favourites")
        compose.waitUntilAtLeastOneExists(hasText("No favourites yet"), timeout)
        screenshot("14-favourites-empty")
    }

    /** Saved under build/ for manual review; not compared against goldens. */
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        // captureToImage() can't force a redraw under Robolectric; draw the window directly instead.
        val bitmap = compose.runOnUiThread {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
