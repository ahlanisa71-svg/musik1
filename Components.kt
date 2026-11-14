package com.glassplayer.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.MusicViewModel
import com.glassplayer.app.data.ArtLoader
import com.glassplayer.app.data.Song
import kotlin.math.abs

val GroupRadius = 26.dp

/** Bentuk baris dalam satu kelompok daftar (sudut membulat hanya di ujung atas/bawah). */
fun rowShape(index: Int, count: Int, headerAbove: Boolean): Shape {
    val top = if (index == 0 && !headerAbove) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

class SongActions(
    val onPlayNext: (Song) -> Unit,
    val onToggleFav: (Song) -> Unit,
    val onAddToPlaylist: (Song) -> Unit,
    val onRemoveFromPlaylist: ((Song) -> Unit)? = null,
)

@Composable
fun GlassIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Color.White,
) {
    Box(
        modifier
            .size(size)
            .glass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ArtImage(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    seed: String = song?.title ?: "",
    iconSize: Dp = 24.dp,
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = song?.let { ArtLoader.peek(it.uri) }, song?.uri) {
        value = if (song == null) null else ArtLoader.load(ctx, song)
    }
    val hue = (abs(seed.hashCode()) % 360).toFloat()
    Box(
        modifier
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color.hsv(hue, 0.42f, 0.62f), Color.hsv((hue + 40f) % 360f, 0.55f, 0.36f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    current: Boolean,
    favorite: Boolean,
    shape: Shape,
    actions: SongActions,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.14f))
            .background(Color.White.copy(alpha = if (current) 0.22f else 0.11f))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(song, Modifier.size(50.dp), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (current) Accent else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (song.durationMs > 0) "${song.artist} • ${fmtTime(song.durationMs)}" else song.artist,
                color = SubText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite) {
            Icon(Icons.Rounded.Favorite, null, tint = Accent, modifier = Modifier.size(16.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White.copy(alpha = 0.8f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Putar berikutnya") },
                    onClick = { menu = false; actions.onPlayNext(song) },
                )
                DropdownMenuItem(
                    text = { Text(if (favorite) "Hapus dari favorit" else "Tambah ke favorit") },
                    onClick = { menu = false; actions.onToggleFav(song) },
                )
                DropdownMenuItem(
                    text = { Text("Tambah ke playlist") },
                    onClick = { menu = false; actions.onAddToPlaylist(song) },
                )
                actions.onRemoveFromPlaylist?.let { remove ->
                    DropdownMenuItem(
                        text = { Text("Hapus dari playlist ini") },
                        onClick = { menu = false; remove(song) },
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: MusicViewModel, onClick: () -> Unit) {
    val song = vm.currentSong ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), 1.2f)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtImage(song, Modifier.size(44.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = vm::next) {
                Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        LinearProgressIndicator(
            progress = { vm.progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(3.dp)
                .clip(CircleShape),
            color = Accent,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Pustaka", Icons.Rounded.LibraryMusic, 0),
        Triple("Cari", Icons.Rounded.Search, 1),
        Triple("Favorit", Icons.Rounded.Favorite, 2),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(32.dp), 1.2f)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { (label, icon, idx) ->
            val sel = idx == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (sel) Color.White.copy(alpha = 0.24f) else Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, label, tint = if (sel) Color.White else Color.White.copy(alpha = 0.65f))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (sel) Color.White else Color.White.copy(alpha = 0.65f),
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
