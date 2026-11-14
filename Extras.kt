package com.glassplayer.app.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.data.AudioFx
import com.glassplayer.app.data.Song
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------
//  Equalizer
// ---------------------------------------------------------------------

private fun fmtFreq(milliHz: Int): String {
    val hz = milliHz / 1000f
    return if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "${hz.toInt()} Hz"
}

@Composable
private fun fxSliderColors() = SliderDefaults.colors(
    thumbColor = Accent,
    activeTrackColor = Accent,
    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
)

@Composable
fun EqualizerPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Equalizer", onBack) {
            Switch(checked = AudioFx.enabled, onCheckedChange = { AudioFx.switchEnabled(it) })
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!AudioFx.attached) {
                Text(
                    "Putar sebuah lagu dulu agar equalizer aktif. Pengaturan Anda tetap tersimpan.",
                    color = SubText, fontSize = 13.sp,
                )
            }
            if (AudioFx.presets.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AudioFx.presets.indices.toList()) { i ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { AudioFx.usePreset(i) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text(AudioFx.presets[i], color = Color.White, fontSize = 13.sp) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().glass().padding(horizontal = 18.dp, vertical = 14.dp)) {
                for (i in 0 until AudioFx.bandCount) {
                    val level = AudioFx.levels.getOrElse(i) { 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fmtFreq(AudioFx.freqs.getOrElse(i) { 0 }),
                            color = Color.White, fontSize = 12.sp, modifier = Modifier.width(64.dp),
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { AudioFx.setLevel(i, it.toInt()) },
                            valueRange = AudioFx.minLevel.toFloat()..AudioFx.maxLevel.toFloat(),
                            colors = fxSliderColors(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+.1f dB".format(level / 100f),
                            color = SubText, fontSize = 11.sp, modifier = Modifier.width(58.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().glass().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text("Bass boost", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Slider(
                    value = AudioFx.bassStrength.toFloat(),
                    onValueChange = { AudioFx.setBass(it.toInt()) },
                    valueRange = 0f..1000f,
                    colors = fxSliderColors(),
                )
                Text("${AudioFx.bassStrength / 10}%", color = SubText, fontSize = 12.sp)
            }
            GlassButton("Setel ulang", onClick = { AudioFx.reset() })
        }
    }
}

// ---------------------------------------------------------------------
//  Tema warna
// ---------------------------------------------------------------------

@Composable
fun ThemePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PageHeader("Tema warna", onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Palettes.indices.toList()) { i ->
                val p = Palettes[i]
                val sel = AppTheme.index == i
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(p.stops)
                        )
                        .border(
                            if (sel) 2.5.dp else 1.dp,
                            if (sel) Color.White else Color.White.copy(alpha = 0.35f),
                            RoundedCornerShape(24.dp),
                        )
                        .clickable { AppTheme.set(ctx, i) }
                        .padding(14.dp),
                ) {
                    Text(
                        p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                    if (sel) {
                        Icon(
                            Icons.Rounded.Check, null, tint = Color.Black,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Dialog kecepatan & crossfade
// ---------------------------------------------------------------------

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) Accent else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, null, tint = Accent)
    }
}

fun speedLabel(v: Float): String = if (v == 1f) "Normal (1.0x)" else "${v}x"

@Composable
fun SpeedDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    MusixDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kecepatan putar") },
        text = {
            Column {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { v ->
                    ChoiceRow(speedLabel(v), current == v) { onSelect(v); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = Accent) } },
    )
}

fun crossfadeLabel(sec: Int): String = if (sec <= 0) "Mati" else "$sec detik"

@Composable
fun CrossfadeDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    MusixDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crossfade") },
        text = {
            Column {
                Text(
                    "Volume memudar di ujung lagu dan naik perlahan di awal lagu berikutnya.",
                    color = SubText, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                listOf(0, 2, 4, 6, 8, 10).forEach { s ->
                    ChoiceRow(crossfadeLabel(s), current == s) { onSelect(s); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = Accent) } },
    )
}

// ---------------------------------------------------------------------
//  Detail lagu + nada dering
// ---------------------------------------------------------------------

private fun songInfo(ctx: Context, song: Song): List<Pair<String, String>> {
    var name = ""
    var size = 0L
    var mime = ""
    try {
        ctx.contentResolver.query(Uri.parse(song.uri), null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: "" }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE).takeIf { it >= 0 }?.let { mime = c.getString(it) ?: "" }
            }
        }
    } catch (_: Exception) {
    }
    val ext = name.substringAfterLast('.', "").uppercase().ifBlank { mime.substringAfter('/').uppercase() }
    val kbps = if (size > 0 && song.durationMs > 0) (size * 8 / song.durationMs).toInt() else 0
    val list = mutableListOf<Pair<String, String>>()
    list += "Judul" to song.title
    list += "Artis" to song.artist
    if (song.album.isNotBlank()) list += "Album" to song.album
    if (song.durationMs > 0) list += "Durasi" to fmtTime(song.durationMs)
    if (ext.isNotBlank()) list += "Format" to ext
    if (size > 0) list += "Ukuran" to "%.2f MB".format(size / 1048576.0)
    if (kbps > 0) list += "Bitrate" to "≈ $kbps kbps"
    if (name.isNotBlank()) list += "Nama file" to name
    if (song.folder.isNotBlank()) list += "Lokasi" to song.folder
    if (song.dateAdded > 0) {
        list += "Ditambahkan" to DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(song.dateAdded * 1000L))
    }
    return list
}

fun setAsRingtone(ctx: Context, song: Song) {
    try {
        if (!Settings.System.canWrite(ctx)) {
            Toast.makeText(ctx, "Izinkan \"Ubah setelan sistem\" untuk Musix, lalu ulangi", Toast.LENGTH_LONG).show()
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, Uri.parse(song.uri))
        Toast.makeText(ctx, "Nada dering diatur: ${song.title}", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(ctx, "Gagal mengatur nada dering untuk lagu ini", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(song.uri) { songInfo(ctx, song) }
    MusixDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail lagu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                info.forEach { (k, v) ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Text(k, color = SubText, fontSize = 12.sp)
                        Text(v, color = Color.White, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { setAsRingtone(ctx, song); onDismiss() }) {
                Text("Nada dering", color = Color.White)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = Accent) } },
    )
}

// ---------------------------------------------------------------------
//  Edit info lagu
// ---------------------------------------------------------------------

class EditReq(val song: Song, val title: String, val artist: String, val album: String)

/** Mengubah info lagu di database media Android (file aslinya tidak diubah). */
fun applySongEdit(ctx: Context, r: EditReq): Boolean = try {
    val cv = ContentValues().apply {
        put(MediaStore.Audio.Media.TITLE, r.title)
        put(MediaStore.Audio.Media.ARTIST, r.artist)
        put(MediaStore.Audio.Media.ALBUM, r.album)
    }
    ctx.contentResolver.update(Uri.parse(r.song.uri), cv, null, null) > 0
} catch (_: Exception) {
    false
}

@Composable
fun EditSongDialog(song: Song, onDismiss: () -> Unit, onSave: (EditReq) -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(if (song.artist == "Artis tidak dikenal") "" else song.artist) }
    var album by remember { mutableStateOf(song.album) }
    MusixDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit info lagu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Judul") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artis") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
                Text(
                    "Perubahan tersimpan di data media Android, file aslinya tidak diubah.",
                    color = SubText, fontSize = 11.sp,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Color.White) } },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onSave(EditReq(song, title.trim(), artist.trim(), album.trim()))
                onDismiss()
            }) { Text("Simpan", color = Accent) }
        },
    )
}
