package com.glassplayer.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.glassplayer.app.MusicViewModel
import com.glassplayer.app.data.Song
import java.util.Locale

private val moodChoices = listOf(
    "Relax" to "spa", "Sad" to "sad", "Happy" to "happy", "Romantic" to "love",
    "Chill" to "chill", "Focus" to "focus", "Workout" to "workout", "Sleep" to "sleep",
    "Commute" to "commute", "Party" to "party", "Energize" to "energy"
)

@Composable
fun NewMusicScreen(vm: MusicViewModel, onOpen: (List<Song>, String) -> Unit) {
    val newest = vm.listFor(com.glassplayer.app.DetailKey.Newest)
    Column(Modifier.fillMaxSize()) {
        PageHeader("New", onBack = {})
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 210.dp)) {
            item { Text("Albums & singles", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp)) }
            items(newest.take(12).chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { song ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.08f)).clickable { vm.playList(newest, newest.indexOf(song)) }.padding(10.dp)) {
                            ArtImage(song, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            item { Text("Music videos", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp)) }
            item { Text("Video musik online belum diaktifkan pada versi lokal ini. Koleksi audio perangkat tetap bisa diputar dari sini.", color = SubText, fontSize = 13.sp) }
        }
    }
}

@Composable
fun RadioMoodScreen(vm: MusicViewModel, onOpen: (List<Song>, String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Radio", onBack = {})
        LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
            moodChoices.forEach { (mood, key) ->
                val songs = vm.allSongs.filter { s ->
                    val text = "${s.title} ${s.artist} ${s.album}".lowercase(Locale.ROOT)
                    when (key) {
                        "sad" -> listOf("sad", "patah", "rindu", "sepi", "kecewa", "galau").any(text::contains)
                        "love" -> listOf("love", "cinta", "romance", "sayang", "kasih").any(text::contains)
                        "happy" -> listOf("happy", "bahagia", "senang", "smile").any(text::contains)
                        "chill" -> listOf("chill", "acoustic", "lofi", "jazz").any(text::contains)
                        "sleep" -> listOf("sleep", "lullaby", "relax", "piano").any(text::contains)
                        "workout" -> listOf("workout", "gym", "sport", "run").any(text::contains)
                        "focus" -> listOf("focus", "study", "instrumental", "ambient").any(text::contains)
                        "party" -> listOf("party", "dance", "remix", "club").any(text::contains)
                        "commute" -> listOf("commute", "road", "drive", "jalanan").any(text::contains)
                        else -> listOf("energy", "rock", "metal", "upbeat").any(text::contains)
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("MOODS & MOMENTS", color = SubText, fontSize = 10.sp, letterSpacing = 1.5.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mood, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${songs.size} lagu", color = SubText, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f)).clickable {
                            if (songs.isNotEmpty()) vm.shuffleAll(songs)
                        }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = Accent)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$mood mix", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(if (songs.isEmpty()) "Belum ada lagu yang cocok di koleksi lokal" else "Putar acak • ${songs.size} lagu", color = SubText, fontSize = 11.sp)
                            }
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsStoragePage(vm: MusicViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val downloads = vm.listFor(com.glassplayer.app.DetailKey.Downloads)
    val root = Environment.getExternalStorageDirectory()
    val total = root.totalSpace.coerceAtLeast(1L)
    val free = root.freeSpace.coerceAtLeast(0L)
    val used = (total - free).coerceAtLeast(0L)
    fun mb(v: Long) = "%.0f MB".format(v / 1024.0 / 1024.0)
    Column(Modifier.fillMaxSize()) {
        PageHeader("Downloads and storage", onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.08f)).padding(18.dp)) {
            Text(mb(used), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Penyimpanan perangkat terpakai", color = SubText, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color.White.copy(.14f))) {
                Box(Modifier.fillMaxWidth((used.toFloat()/total).coerceIn(0f,1f)).fillMaxHeight().background(Accent))
            }
            Spacer(Modifier.height(8.dp))
            Text("Tersedia ${mb(free)} dari ${mb(total)}", color = SubText, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.07f))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Download, null, tint = Accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Downloads", color = Color.White, fontWeight = FontWeight.SemiBold); Text("File musik pada folder Download", color = SubText, fontSize = 11.sp) }
                Text("${downloads.size}", color = Color.White)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Wifi, null, tint = Accent)
                Spacer(Modifier.width(12.dp)); Text("Download over Wi-Fi only", color = Color.White, modifier = Modifier.weight(1f)); Text("—", color = SubText)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clickable {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"), "vnd.android.document/directory")) }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, null, tint = Accent); Spacer(Modifier.width(12.dp)); Text("Buka folder Download", color = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Catatan: Musix memutar file lokal. Pengelolaan unduhan streaming dan penghapusan cache khusus belum tersedia.", color = SubText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
    }
}

@Composable
fun AppSettingsPage(onBack: () -> Unit, onTheme: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PageHeader("App", onBack)
        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.08f))) {
            Row(Modifier.fillMaxWidth().clickable { onTheme() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("Appearance", color = Color.White); Text("Tema dan warna aplikasi", color = SubText, fontSize = 11.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = SubText)
            }
            Row(Modifier.fillMaxWidth().clickable { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) } }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Settings, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("System", color = Color.White); Text("Izin, baterai, dan pengaturan Android", color = SubText, fontSize = 11.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = SubText)
            }
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column { Text("Musix", color = Color.White); Text("Versi $APP_VERSION", color = SubText, fontSize = 11.sp) }
            }
        }
    }
}
