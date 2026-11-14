package com.glassplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.glassplayer.app.ui.AppRoot
import com.glassplayer.app.ui.GlassTheme
import com.glassplayer.app.ui.SplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)

        // Mulai memindai musik di latar belakang SELAMA animasi pembuka berjalan,
        // supaya beranda langsung penuh begitu animasi selesai.
        val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED) {
            vm.ensureLoaded()
        }

        setContent {
            GlassTheme {
                // 0 = hanya animasi | 1 = aplikasi disusun di belakang animasi | 2 = animasi hilang
                var phase by rememberSaveable { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    if (phase == 0) {
                        delay(1600)
                        phase = 1
                    }
                    if (phase == 1) {
                        delay(350)
                        phase = 2
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    // Layar utama baru disusun SETELAH animasi hampir selesai,
                    // supaya animasi tidak patah-patah karena kerja berat di awal.
                    if (phase >= 1) AppRoot(vm)
                    AnimatedVisibility(visible = phase < 2, exit = fadeOut(tween(300))) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { vm.playExternal(it) }
    }
}
