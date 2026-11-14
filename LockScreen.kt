package com.glassplayer.app.ui

import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.Player
import com.glassplayer.app.MusicViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar kunci: wallpaper HP tetap terlihat. Jam di atas, lalu kartu musik KACA BURAM.
 *
 * Kartu ditaruh di jendela tersendiri (Dialog) agar bisa memakai blur level-jendela Android 12+
 * (Window.setBackgroundBlurRadius) — itu yang benar-benar memburamkan wallpaper di belakang kartu.
 */
@Composable
fun LockScreenContent(vm: MusicViewModel, onClose: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val locale = remember { Locale("id", "ID") }
    val time = remember(now / 60_000) { SimpleDateFormat("HH:mm", locale).format(Date(now)) }
    val date = remember(now / 3_600_000) { SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now)) }

    // Latar: jam. Ketuk area kosong untuk menutup.
    Column(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(time, color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Normal)
        Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
    }

    BlurWindowCard(topOffsetDp = 262, sideMarginDp = 14) {
        MusicCard(vm)
    }
}

/** Jendela kecil di atas activity dengan blur latar sungguhan (Android 12+) dan sudut membulat. */
@Composable
private fun BlurWindowCard(topOffsetDp: Int, sideMarginDp: Int, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val dm = view.resources.displayMetrics
            val d = dm.density

            val blurOk = Build.VERSION.SDK_INT >= 31 &&
                (view.context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true)

            // Latar jendela: sudut membulat. Dengan blur → putih tipis; tanpa blur → gelap agar tetap terbaca.
            val bg = GradientDrawable().apply {
                cornerRadius = 22 * d
                setColor(if (blurOk) AColor.argb(46, 255, 255, 255) else AColor.argb(190, 24, 24, 24))
            }
            window.setBackgroundDrawable(bg)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            if (blurOk) window.setBackgroundBlurRadius((46 * d).toInt())

            window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            window.setLayout(
                dm.widthPixels - (2 * sideMarginDp * d).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            window.attributes = window.attributes.apply { y = (topOffsetDp * d).toInt() }
        }
        content()
    }
}

@Composable
private fun MusicCard(vm: MusicViewModel) {
    val song = vm.currentSong
    val fav = song != null && song.uri in vm.favorites
    val shape = RoundedCornerShape(22.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.35f))
                ),
                shape,
            )
    ) {
        // Warna cover sebagai semburat halus di atas kaca buram
        Box(Modifier.matchParentSize()) {
            if (Build.VERSION.SDK_INT >= 31) {
                ArtImage(
                    song,
                    Modifier.fillMaxSize().alpha(0.55f).blur(22.dp, BlurredEdgeTreatment.Unbounded),
                    RectangleShape, iconSize = 1.dp,
                )
            }
            // kilau putih di atas, sedikit gelap di bawah (supaya teks tetap terbaca)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.32f)))
                )
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "Musik",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                song?.title ?: "—",
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                song?.artist ?: "",
                color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                LockBtn(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Ulangi",
                    tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color.White else Accent,
                    onClick = vm::toggleRepeat,
                )
                LockBtn(Icons.Rounded.SkipPrevious, "Sebelumnya", onClick = vm::previous)
                LockBtn(
                    if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    "Putar/Jeda", size = 34, onClick = vm::togglePlay,
                )
                LockBtn(Icons.Rounded.SkipNext, "Berikutnya", onClick = vm::next)
                LockBtn(
                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Favorit",
                    tint = if (fav) Accent else Color.White,
                    onClick = { song?.let(vm::toggleFavorite) },
                )
            }
            Spacer(Modifier.height(10.dp))

            BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
                LinearProgressIndicator(
                    progress = { vm.progress },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = Color.White.copy(alpha = 0.9f),
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (maxWidth - 10.dp) * vm.progress)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(vm.positionMs), color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                Text(fmtTime(vm.durationMs), color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun LockBtn(
    icon: ImageVector,
    desc: String,
    size: Int = 26,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Icon(
        icon, desc, tint = tint,
        modifier = Modifier
            .size(size.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}
