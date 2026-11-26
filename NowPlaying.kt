package com.glassplayer.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.glassplayer.app.data.Song
import com.glassplayer.app.lyrics.LyricsOverlay
import com.glassplayer.app.ui.ArtImage
import com.glassplayer.app.ui.GlassBackground
import com.glassplayer.app.ui.PlayerArt
import com.glassplayer.app.ui.SubText
import com.glassplayer.app.ui.Text
import com.glassplayer.app.ui.fmtTime

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingScreen(
    vm: MusicViewModel,
    onClose: () -> Unit,
    onSleep: () -> Unit = {},
    onStyle: () -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onAlbum: (String) -> Unit = {},
    onArtist: (String) -> Unit = {},
    onWidgets: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onDetails: (Song) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val song = vm.currentSong ?: return
    val isPlaying = vm.isPlaying
    val positionMs = vm.positionMs
    val durationMs = vm.durationMs
    val showLyrics by vm.showLyrics.collectAsState()
    val lyrics by vm.currentLyrics.collectAsState()
    val isLoadingLyrics by vm.isLoadingLyrics.collectAsState()
    val videoMode by vm.videoMode.collectAsState()
    val currentItem by vm.currentItem.collectAsState()
    val fav = song.uri in vm.favorites

    var isFullScreenLandscape by remember { mutableStateOf(false) }

    DisposableEffect(isFullScreenLandscape) {
        if (isFullScreenLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()

        if (isFullScreenLandscape && videoMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                ExoVideoSurface(
                    player = vm.getPlayer(),
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { isFullScreenLandscape = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(Icons.Rounded.FullscreenExit, contentDescription = "Keluar Layar Penuh", tint = Color.White)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.78f))
                        )
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup", tint = Color.White)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEqualizer) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = Color.White)
                        }
                        IconButton(onClick = onStyle) {
                            Icon(Icons.Rounded.Palette, contentDescription = "Gaya pemutar", tint = Color.White)
                        }
                        IconButton(onClick = onSleep) {
                            Icon(Icons.Rounded.Bedtime, contentDescription = "Timer tidur", tint = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        videoMode -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black),
                            ) {
                                ExoVideoSurface(
                                    player = vm.getPlayer(),
                                    modifier = Modifier.fillMaxSize(),
                                )
                                IconButton(
                                    onClick = { isFullScreenLandscape = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                ) {
                                    Icon(Icons.Rounded.Fullscreen, contentDescription = "Layar Penuh", tint = Color.White)
                                }
                            }
                        }
                        showLyrics -> {
                            LyricsOverlay(
                                lyrics = lyrics,
                                isLoading = isLoadingLyrics,
                                positionMs = positionMs,
                                onSeek = { vm.seekTo(it) },
                            )
                        }
                        else -> {
                            if (vm.playerStyle == 0) {
                                ArtImage(
                                    song = song,
                                    modifier = Modifier.size(286.dp),
                                    shape = RoundedCornerShape(28.dp),
                                )
                            } else {
                                PlayerArt(
                                    song = song,
                                    style = vm.playerStyle,
                                    playing = isPlaying,
                                    size = 286.dp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            color = SubText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(onClick = { vm.toggleFavorite(song) }) {
                        Icon(
                            imageVector = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorit",
                            tint = if (fav) Color(0xFFE3A93A) else Color.White,
                        )
                    }

                    IconButton(onClick = { vm.toggleLyrics() }) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = "Lirik",
                            tint = if (showLyrics) Color(0xFFE3A93A) else Color.White.copy(alpha = 0.78f),
                        )
                    }
                    if (currentItem?.isYouTube == true) {
                        IconButton(onClick = { vm.toggleVideoMode() }) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = if (videoMode) "Putar audio" else "Putar video",
                                tint = if (videoMode) Color(0xFFE3A93A) else Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                val safeDuration = durationMs.coerceAtLeast(1L)
                Slider(
                    value = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f),
                    onValueChange = { frac -> vm.seekTo((frac * safeDuration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(fmtTime(positionMs), color = SubText, fontSize = 12.sp)
                    Text(fmtTime(durationMs), color = SubText, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.toggleRepeat() }) {
                        Icon(
                            imageVector = if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Ulangi",
                            tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.7f) else Color(0xFFE3A93A),
                        )
                    }

                    IconButton(onClick = { vm.previous() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(34.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { vm.togglePlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Putar / Jeda",
                            tint = Color(0xFF0F0F0C),
                            modifier = Modifier.size(38.dp),
                        )
                    }

                    IconButton(onClick = { vm.next() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(34.dp))
                    }

                    IconButton(onClick = { onAddToPlaylist(song) }) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Tambah ke daftar putar", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ExoVideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier,
    )
}
