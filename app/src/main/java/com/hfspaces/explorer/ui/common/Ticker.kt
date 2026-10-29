package com.hfspaces.explorer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/** Current time in millis, refreshed every [periodMillis] while [active]; for elapsed-time labels. */
@Composable
fun rememberTicker(active: Boolean, periodMillis: Long = 250): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        now.longValue = System.currentTimeMillis()
        while (active) {
            delay(periodMillis)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}
