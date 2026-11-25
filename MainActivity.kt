package com.glassplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glassplayer.app.ui.AppRoot
import com.glassplayer.app.ui.GlassTheme
import com.glassplayer.app.ui.SplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GlassTheme {
                var revealed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(1400)
                    revealed = true
                }
                if (!revealed) {
                    SplashScreen()
                } else {
                    AppRoot(vm = viewModel, revealed = true)
                }
            }
        }
    }
}
