package com.hfspaces.explorer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.data.repository.ThemeMode
import com.hfspaces.explorer.ui.HfSpacesApp
import com.hfspaces.explorer.ui.common.openUrl
import com.hfspaces.explorer.ui.theme.HfSpacesTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as HfSpacesApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only fresh launches carry a request: a recreated activity already handled its intent, and
        // reopening the task from Recents redelivers the old one (which would repeat a share).
        val fromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromRecents) route(intent)
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HfSpacesTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                HfSpacesApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent?) {
        // A Hub link that isn't a Space page (the filter is broader than what we can show): back to a browser.
        if (!container.deepLinks.handle(intent)) intent?.dataString?.let { openUrl(it) }
    }
}
