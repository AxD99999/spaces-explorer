package com.hfspaces.explorer.ui

import android.graphics.Bitmap
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
