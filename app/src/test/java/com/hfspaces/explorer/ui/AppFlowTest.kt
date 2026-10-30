package com.hfspaces.explorer.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
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
import com.hfspaces.explorer.testing.TestApp
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
