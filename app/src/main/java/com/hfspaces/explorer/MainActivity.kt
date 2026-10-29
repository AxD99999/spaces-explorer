package com.hfspaces.explorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hfspaces.explorer.ui.HfSpacesApp
import com.hfspaces.explorer.ui.theme.HfSpacesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HfSpacesTheme {
                HfSpacesApp()
            }
        }
    }
}
