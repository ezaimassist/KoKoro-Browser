package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BareTurboApp
import com.example.ui.theme.BareTurboTheme
import com.example.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by browserViewModel.uiState.collectAsStateWithLifecycle()
            BareTurboTheme(darkTheme = uiState.settings.darkTheme) {
                BareTurboApp(viewModel = browserViewModel)
            }
        }
    }
}
