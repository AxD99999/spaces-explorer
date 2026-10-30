package com.hfspaces.explorer.ui

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hfspaces.explorer.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** On-device check of the main path against the fake Hub (see TestApp). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val timeout = 20_000L

    @Test
    fun browseRunAndSeeOutput() {
        compose.waitUntilAtLeastOneExists(hasText("Hello World"), timeout)
        compose.onAllNodesWithText("Run")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("name"), timeout)
        compose.onNode(hasSetTextAction() and hasText("name")).performTextInput("Ada")
        compose.onNodeWithText("Run").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Hello Ada!!"), timeout)
    }

    @Test
    fun drawerReachesSettingsAndRuns() {
        compose.waitUntilAtLeastOneExists(hasContentDescription("Open menu"), timeout)
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Dynamic colour"), timeout)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Runs").performClick()
        compose.waitUntilAtLeastOneExists(hasText("No runs yet"), timeout)
    }
}
