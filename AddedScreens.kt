package com.glassplayer.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.asImageBitmap
import com.glassplayer.app.MusicViewModel
import com.glassplayer.app.MediaItemModel
import com.glassplayer.app.RadioMoodSection
import com.glassplayer.app.data.Song

private val moodChoices = listOf(
    "Campuran" to "all", "Relax" to "spa", "Sad" to "sad", "Happy" to "happy", "Romantic" to "love",
    "Chill" to "chill", "Focus" to "focus", "Workout" to "workout", "Sleep" to "sleep",
    "Commute" to "commute", "Party" to "party", "Energize" to "energy"
)

@Composable
fun NewMusicScreen(vm: MusicViewModel, onOpen: (List<Song>, String) -> Unit) {
    val newest = vm.listFor(com.glassplayer.app.DetailKey.Newest)
    val ctx = LocalContext.current
    val activeDownloads by vm.activeDownloads.collectAsState()
    var videos by remember { mutableStateOf<List<com.glassplayer.app.data.YtVideo>?>(null) }
    var videosErr by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (com.glassplayer.app.data.YouTubeApi.hasKey) {
            val r = com.glassplayer.app.data.YouTubeApi.musicVideos()
            videos = r.getOrNull()
            videosErr = r.exceptionOrNull()?.message
        }
    }
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
            if (videosErr != null) {
                item { Text(videosErr!!, color = SubText, fontSize = 13.sp) }
            } else if (videos == null) {
                item { Text("Memuat video musik…", color = SubText, fontSize = 13.sp) }
            } else {
                items(videos!!, key = { "yt:" + it.id }) { v ->
                    val download = activeDownloads[v.id]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(.08f))
                            .clickable { vm.playYouTubeVideo(v.id, v.title, v.channel, v.thumb) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteImage(v.thumb, Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(v.channel, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    download?.isCompleted == true -> "Unduhan selesai"
                                    download?.error != null -> "Gagal: ${download.error}"
                                    download != null -> "Mengunduh ${(download.progress * 100).toInt()}%"
                                    else -> "Putar di Musix"
                                },
                                color = if (download?.error != null) Color(0xFFFF8A80) else Accent,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { vm.downloadYouTube(v.id, v.title, asVideo = true) },
                            enabled = download == null || download.isCompleted || download.error != null,
                        ) {
                            Icon(
                                if (download?.isCompleted == true) Icons.Rounded.Check else Icons.Rounded.Download,
                                contentDescription = "Unduh video ${v.title}",
                                tint = if (download?.isCompleted == true) Accent else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RadioMoodScreen(vm: MusicViewModel) {
    val radioResults by vm.radioResults.collectAsState()
    val isSearching by vm.isSearchingRadio.collectAsState()
    val searchError by vm.radioSearchError.collectAsState()
    val radioMixSections by vm.radioMixSections.collectAsState()
    val isLoadingMix by vm.isLoadingRadioMix.collectAsState()
    val radioMixError by vm.radioMixError.collectAsState()
    var selectedMood by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.loadRadioMix()
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Radio", onBack = {})
        LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
            item {
                Text(
                    "Pilih suasana",
                    color = SubText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(moodChoices, key = { it.first }) { (mood, _) ->
                        val selected = if (mood == "Campuran") selectedMood == null else selectedMood == mood
                        Text(
                            mood,
                            color = if (selected) Color.Black else Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) Accent else Color.White.copy(.12f))
                                .clickable {
                                    if (mood == "Campuran") {
                                        selectedMood = null
                                        vm.loadRadioMix()
                                    } else {
                                        selectedMood = mood
                                        vm.searchRadioMood(mood)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            if (selectedMood == null) {
                item { RadioDisplayHeader("Campuran", vm) }
                if (radioMixSections.isEmpty()) {
                    item {
                        Text(
                            when {
                                isLoadingMix -> "Memuat pilihan Radio…"
                                radioMixError != null -> radioMixError.orEmpty()
                                else -> "Belum ada lagu online untuk Radio."
                            },
                            color = SubText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    radioMixSections.forEach { section ->
                        item(key = "radio-mix-${section.mood}") {
                            RadioMoodTrackSection(
                                section,
                                vm.radioLayout,
                            ) { track -> vm.playItem(track, section.tracks) }
                        }
                    }
                    if (isLoadingMix) {
                        item {
                            Text("Memuat kategori lainnya…", color = SubText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                    radioMixError?.let { error ->
                        item {
                            Text(error, color = SubText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }
            }
            selectedMood?.let { mood ->
                item {
                    RadioDisplayHeader(mood, vm)
                }
                when {
                    isSearching -> item {
                        Text("Mencari musik online…", color = SubText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                    searchError != null -> item {
                        Text(searchError.orEmpty(), color = SubText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                    radioResults.isEmpty() -> item {
                        Text("Tidak ada hasil online untuk suasana ini.", color = SubText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                    else -> {
                        val tracks = radioResults.take(3)
                        when (vm.radioLayout) {
                            1 -> items(tracks, key = { it.id }) { track ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 3.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(.07f))
                                        .clickable { vm.playItem(track, radioResults) }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RemoteImage(
                                        track.artworkUri?.toString().orEmpty(),
                                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, null, tint = Accent)
                                }
                            }
                            2 -> item {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    tracks.forEach { track ->
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color.White.copy(.07f))
                                                .clickable { vm.playItem(track, radioResults) }
                                                .padding(7.dp),
                                        ) {
                                            RemoteImage(
                                                track.artworkUri?.toString().orEmpty(),
                                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)),
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            else -> item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(tracks, key = { it.id }) { track ->
                                        Column(
                                            Modifier
                                                .width(220.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(.07f))
                                                .clickable { vm.playItem(track, radioResults) }
                                                .padding(10.dp),
                                        ) {
                                            RemoteImage(
                                                track.artworkUri?.toString().orEmpty(),
                                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(track.artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioDisplayHeader(title: String, vm: MusicViewModel) {
    val icons = listOf(
        Icons.AutoMirrored.Rounded.ViewList,
        Icons.Rounded.ViewAgenda,
        Icons.Rounded.GridView,
    )
    val labels = listOf("Detail", "Ringkas", "Petak")
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("RADIO ONLINE", color = Accent, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        icons.forEachIndexed { index, icon ->
            val selected = vm.radioLayout == index
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { vm.updateRadioLayout(index) }
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = labels[index], tint = if (selected) Accent else SubText)
                Text(labels[index], color = if (selected) Accent else SubText, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun RadioMoodTrackSection(
    section: RadioMoodSection,
    layout: Int,
    onPlay: (MediaItemModel) -> Unit,
) {
    val tracks = section.tracks.take(3)
    if (tracks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(section.mood, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        when (layout) {
            1 -> tracks.forEach { track ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(.07f))
                        .clickable { onPlay(track) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(track.artworkUri?.toString().orEmpty(), Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.PlayArrow, null, tint = Accent)
                }
            }
            2 -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tracks.forEach { track ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(.07f))
                            .clickable { onPlay(track) }
                            .padding(7.dp),
                    ) {
                        RemoteImage(track.artworkUri?.toString().orEmpty(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)))
                        Spacer(Modifier.height(6.dp))
                        Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tracks, key = { it.id }) { track ->
                    Column(
                        Modifier
                            .width(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(.07f))
                            .clickable { onPlay(track) }
                            .padding(10.dp),
                    ) {
                        RemoteImage(track.artworkUri?.toString().orEmpty(), Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Column(Modifier.weight(1f)) { Text("Unduhan Musix", color = Color.White, fontWeight = FontWeight.SemiBold); Text("File audio tersimpan di folder aplikasi", color = SubText, fontSize = 11.sp) }
                Text("${downloads.size}", color = Color.White)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Wifi, null, tint = Accent)
                Spacer(Modifier.width(12.dp)); Text("Download over Wi-Fi only", color = Color.White, modifier = Modifier.weight(1f)); Text("—", color = SubText)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, null, tint = Accent); Spacer(Modifier.width(12.dp)); Text("Penyimpanan Musik/MusixDownloads aplikasi", color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Audio yang selesai diunduh muncul di pustaka Lagu Terunduh. File video tetap bisa diputar dari pemutar Musix.", color = SubText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
    }
}

@Composable
fun AppSettingsPage(onBack: () -> Unit, onTheme: () -> Unit) {
    val ctx = LocalContext.current
    var showLang by remember { mutableStateOf(false) }
    if (showLang) {
        MusixDialog(
            onDismissRequest = { showLang = false },
            confirmButton = { TextButton(onClick = { showLang = false }) { Text("Tutup", color = Accent) } },
            title = { Text("Language") },
            text = {
                Column {
                    AppLang.options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { AppLang.set(ctx, o.code); showLang = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = AppLang.code == o.code, onClick = { AppLang.set(ctx, o.code); showLang = false })
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Text(o.native, color = Color.White)
                        }
                    }
                }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("App", onBack)
        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.08f))) {
            Row(Modifier.fillMaxWidth().clickable { showLang = true }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("Language", color = Color.White); androidx.compose.material3.Text(AppLang.currentName, color = SubText, fontSize = 11.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = SubText)
            }
            Row(Modifier.fillMaxWidth().clickable { onTheme() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("Appearance", color = Color.White); Text("Tema dan warna aplikasi", color = SubText, fontSize = 11.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = SubText)
            }
            val canBlack = AppTheme.palette.dark
            Row(
                Modifier.fillMaxWidth().clickable { if (canBlack) AppTheme.setPureBlack(ctx, !AppTheme.pureBlack) }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Contrast, null, tint = Accent); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hitam murni (OLED)", color = Color.White)
                    Text(if (canBlack) "Latar hitam total, piksel OLED mati" else "Hanya bisa dipakai di tema gelap (Malam / Hitam)", color = SubText, fontSize = 11.sp)
                }
                Switch(
                    checked = AppTheme.pureBlack && canBlack,
                    enabled = canBlack,
                    onCheckedChange = { AppTheme.setPureBlack(ctx, it) },
                )
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


private val thumbCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

/** Gambar dari internet (thumbnail video), dimuat sekali lalu disimpan di memori. */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = thumbCache[url], url) {
        if (value == null && url.isNotBlank()) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 8000; c.readTimeout = 8000
                    c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()?.also { thumbCache[url] = it }
            }
        }
    }
    Box(modifier.background(Color.White.copy(.10f))) {
        bmp?.let {
            androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
}
