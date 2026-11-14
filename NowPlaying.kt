package com.glassplayer.app.ui

import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.glassplayer.app.data.Song
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.glassplayer.app.MusicViewModel
import kotlin.math.max

@Composable
fun NowPlayingScreen(
    vm: MusicViewModel,
    onClose: () -> Unit,
    onSleep: () -> Unit,
    onStyle: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onWidgets: () -> Unit,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    val song = vm.currentSong
    val fav = song != null && song.uri in vm.favorites

    ProvideBackdrop {
    Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0C))) {
        // Latar: cover yang diburamkan (Android 12+) atau gradasi. Panel kaca memburamkan latar ini lagi.
        GlassBackground {
            if (Build.VERSION.SDK_INT >= 31) {
                ArtImage(song, Modifier.fillMaxSize().blur(60.dp), RectangleShape, iconSize = 1.dp)
            } else {
                DefaultBackdrop()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0x44000000), Color(0xAA000000))))
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Tutup", onClose)
                Text(
                    "Sedang Diputar",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                GlassIconButton(Icons.Rounded.Checkroom, "Gaya pemutar", onStyle)
                Spacer(Modifier.width(8.dp))
                Box {
                    GlassIconButton(Icons.Rounded.MoreVert, "Menu lainnya", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        @Composable
                        fun Item(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
                            DropdownMenuItem(
                                text = { Text(text) },
                                leadingIcon = { Icon(icon, null) },
                                enabled = enabled,
                                onClick = { menu = false; onClick() },
                            )
                        }
                        Item("Tambah ke daftar putar", Icons.Rounded.PlaylistAdd, song != null) { song?.let(onAddToPlaylist) }
                        Item(
                            if (vm.sleepEndsAt > 0) "Timer tidur (aktif)" else "Timer tidur",
                            Icons.Rounded.Bedtime,
                        ) { onSleep() }
                        Item("Album", Icons.Rounded.Album, song != null && song.album.isNotBlank()) { song?.let { onAlbum(it.album) } }
                        Item("Artis", Icons.Rounded.Person, song != null) { song?.let { onArtist(it.artist) } }
                        Item("Berbagi", Icons.Rounded.Share, song != null) { song?.let { shareSongs(ctx, listOf(it)) } }
                        Item("Hapus dari antrian", Icons.Rounded.RemoveCircleOutline, song != null) { vm.removeCurrentFromQueue() }
                        Item("Widget Layar utama", Icons.Rounded.Widgets) { onWidgets() }
                    }
                }
            }

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                PlayerArt(song, vm.playerStyle, vm.isPlaying, side)
            }

            // Judul + favorit
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "—", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song?.artist ?: "", color = SubText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { queue = true }) {
                    Icon(Icons.Rounded.QueueMusic, "Antrian", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { song?.let(vm::toggleFavorite) }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Favorit",
                        tint = if (fav) Accent else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Seekbar
            var drag by remember { mutableStateOf<Float?>(null) }
            val dur = max(1f, vm.durationMs.toFloat())
            Slider(
                value = (drag ?: vm.positionMs.toFloat()).coerceIn(0f, dur),
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { vm.seekTo(it.toLong()) }
                    drag = null
                },
                valueRange = 0f..dur,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime((drag ?: vm.positionMs.toFloat()).toLong()), color = SubText, fontSize = 12.sp)
                Text(fmtTime(vm.durationMs), color = SubText, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))

            // Kontrol
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(36.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, "Acak", tint = if (vm.shuffle) Accent else Color.White.copy(alpha = 0.75f))
                }
                IconButton(onClick = vm::previous, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable(onClick = vm::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Putar/Jeda", tint = Color.Black, modifier = Modifier.size(42.dp),
                    )
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = vm::toggleRepeat) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Ulangi",
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.75f) else Accent,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    }

    if (queue) {
        AlertDialog(
            onDismissRequest = { queue = false },
            containerColor = Color(0xFF26241B),
            title = { Text("Antrian (${vm.queueIds.size})", color = Color.White) },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(vm.queueIds) { i, uri ->
                        val q = vm.songFor(uri)
                        val cur = i == vm.queueIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.jumpTo(i) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", color = SubText, fontSize = 12.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    q?.title ?: "Lagu",
                                    color = if (cur) Accent else Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(q?.artist ?: "", color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (cur) EqualizerBars(vm.isPlaying, modifier = Modifier.padding(horizontal = 6.dp))
                            Icon(
                                Icons.Rounded.Close, "Hapus",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(34.dp).clip(CircleShape).clickable { vm.removeFromQueueAt(i) }.padding(7.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { queue = false }) { Text("Tutup", color = Accent) } },
        )
    }
}
