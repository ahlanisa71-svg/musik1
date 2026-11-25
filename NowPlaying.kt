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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.glassplayer.app.download.YouTubeDownloader
import com.glassplayer.app.lyrics.LyricsOverlay

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val currentItem by viewModel.currentItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val showLyrics by viewModel.showLyrics.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val videoMode by viewModel.videoMode.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()

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

    val item = currentItem ?: return
    val videoId = item.youtubeVideoId
    val dlState = videoId?.let { activeDownloads[it] }
    val alreadyDownloaded = videoId?.let { YouTubeDownloader.isDownloaded(it) } == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1F2C), Color(0xFF0D1017))
                )
            )
    ) {
        if (isFullScreenLandscape && videoMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                ExoVideoSurface(
                    player = viewModel.getPlayer(),
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { isFullScreenLandscape = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Rounded.FullscreenExit, contentDescription = "Keluar Layar Penuh", tint = Color.White)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup", tint = Color.White)
                    }

                    if (item.isVideoCapable) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(4.dp)
                        ) {
                            ModeChip(
                                label = "Audio",
                                selected = !videoMode,
                                onClick = { if (videoMode) viewModel.toggleVideoMode() }
                            )
                            ModeChip(
                                label = "Video",
                                selected = videoMode,
                                onClick = { if (!videoMode) viewModel.toggleVideoMode() }
                            )
                        }
                    }

                    if (item.isYouTube) {
                        IconButton(
                            onClick = {
                                if (!alreadyDownloaded && dlState == null) {
                                    viewModel.downloadCurrentOrItem(item, asVideo = videoMode)
                                }
                            }
                        ) {
                            when {
                                dlState != null && !dlState.isCompleted && dlState.error == null -> {
                                    CircularProgressIndicator(
                                        progress = { dlState.progress },
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                }
                                alreadyDownloaded || dlState?.isCompleted == true -> {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Tersimpan", tint = Color(0xFF4ADE80))
                                }
                                else -> {
                                    Icon(Icons.Rounded.Download, contentDescription = "Unduh", tint = Color.White)
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        videoMode -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black)
                            ) {
                                ExoVideoSurface(
                                    player = viewModel.getPlayer(),
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { isFullScreenLandscape = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
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
                                onSeek = { viewModel.seekTo(it) }
                            )
                        }
                        else -> {
                            AsyncImage(
                                model = item.artworkUri,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(290.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.artist,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleLyrics() },
                        modifier = Modifier
                            .background(
                                if (showLyrics) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            contentDescription = "Lirik",
                            tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.75f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val safeDuration = durationMs.coerceAtLeast(1L)
                Slider(
                    value = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f),
                    onValueChange = { frac -> viewModel.seekTo((frac * safeDuration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text(formatDuration(durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(34.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Putar / Jeda",
                            tint = Color(0xFF0D1017),
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF0D1017) else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
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
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier
    )
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
