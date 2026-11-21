package com.glassplayer.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import com.glassplayer.app.data.AudioFx
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.MusicViewModel
import com.glassplayer.app.R
import com.glassplayer.app.data.Song
import com.glassplayer.app.widget.CapsuleWidget
import com.glassplayer.app.widget.DiscWideWidget
import com.glassplayer.app.widget.PlayerSearchWidget
import com.glassplayer.app.widget.RecentWidget
import com.glassplayer.app.widget.RecommendWidget
import com.glassplayer.app.widget.pinWidget

sealed interface Page {
    data object Settings : Page
    data object Style : Page
    data object Widgets : Page
    data object Playlists : Page
    data object Equalizer : Page
    data object Theme : Page
    data object DownloadsStorage : Page
    data object App : Page
}

const val APP_NAME = "Musix"
const val APP_VERSION = "1.9.7"
const val CONTACT_EMAIL = "rifqihisyammuddin429@gmail.com"
const val IG_URL = "https://www.instagram.com/rifsy_am"
const val IG_NAME = "rifsy_am"
const val FB_URL = "https://www.facebook.com/profile.php?id=61555140984359"
const val FB_NAME = "El Mahmud Rifqi"

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Tidak bisa membuka tautan", Toast.LENGTH_SHORT).show()
    }
}

fun sendBugReport(ctx: Context) {
    try {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Laporan bug Musix")
            putExtra(
                Intent.EXTRA_TEXT,
                "Jelaskan bug yang Anda temui:\n\n\n---\nPerangkat: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            )
        }
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Tidak ada aplikasi email. Kirim ke $CONTACT_EMAIL", Toast.LENGTH_LONG).show()
    }
}

fun composeEmail(ctx: Context) {
    try {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Tidak ada aplikasi email. Kirim ke $CONTACT_EMAIL", Toast.LENGTH_LONG).show()
    }
}

fun shareSongs(ctx: Context, songs: List<Song>) {
    if (songs.isEmpty()) return
    try {
        val intent = if (songs.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(songs[0].uri))
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(songs.map { Uri.parse(it.uri) }))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "Bagikan lagu").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Tidak bisa membagikan lagu", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------------
//  Kerangka halaman
// ---------------------------------------------------------------------

@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.6f))
    },
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        if (iconRes != null) Image(painterResource(iconRes), null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = SubText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
}

// ---------------------------------------------------------------------
//  Setelan
// ---------------------------------------------------------------------

@Composable
fun SettingsPage(
    vm: MusicViewModel,
    onBack: () -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onSleep: () -> Unit,
    onRescan: () -> Unit,
    onPick: () -> Unit,
    onPage: (Page) -> Unit,
) {
    val ctx = LocalContext.current
    var showCf by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Setelan", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 200.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.cascade(0).fillMaxWidth().glass()) {
                SettingRow("Downloads and storage", "Unduhan dan ruang penyimpanan", Icons.Rounded.Download, onClick = { onPage(Page.DownloadsStorage) })
                RowDivider()
                SettingRow("App", "Language, appearance, system and links", Icons.Rounded.Settings, onClick = { onPage(Page.App) })
                RowDivider()
                SettingRow(
                    "Layar kunci kaca", "Tampil saat layar mati & musik jalan", Icons.Rounded.Lock,
                    onClick = { onToggleLock(!vm.lockScreenEnabled) },
                    trailing = { Switch(checked = vm.lockScreenEnabled, onCheckedChange = onToggleLock) },
                )
                RowDivider()
                SettingRow("Timer tidur", null, Icons.Rounded.Bedtime, onClick = onSleep)
                RowDivider()
                SettingRow("Pindai ulang musik", null, Icons.Rounded.Refresh, onClick = onRescan)
                RowDivider()
                SettingRow("Tambah file musik manual", null, Icons.Rounded.FolderOpen, onClick = onPick)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.cascade(1).fillMaxWidth().glass()) {
                SettingRow("Equalizer", if (AudioFx.enabled) "Aktif" else "Mati", Icons.Rounded.GraphicEq, onClick = { onPage(Page.Equalizer) })
                RowDivider()
                SettingRow("Kecepatan putar", speedLabel(vm.playbackSpeed), Icons.Rounded.Speed, onClick = { showSpeed = true })
                RowDivider()
                SettingRow("Crossfade", crossfadeLabel(vm.crossfadeSec), Icons.Rounded.SwapHoriz, onClick = { showCf = true })
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.cascade(2).fillMaxWidth().glass()) {
                SettingRow(
                    "Versi aplikasi", "$APP_NAME $APP_VERSION", Icons.Rounded.Info, onClick = {},
                    trailing = { Text("v$APP_VERSION", color = SubText, fontSize = 14.sp) },
                )
                RowDivider()
                SettingRow("Pembaruan aplikasi", "Periksa dan pasang versi terbaru", Icons.Rounded.SystemUpdate, onClick = { showUpdate = true })
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Ikuti & hubungi pembuat",
                color = SubText,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            Column(Modifier.cascade(3).fillMaxWidth().glass()) {
                SettingRow(IG_NAME, "Instagram", iconRes = R.drawable.ic_instagram, onClick = { openUrl(ctx, IG_URL) })
                RowDivider()
                SettingRow(FB_NAME, "Facebook", iconRes = R.drawable.ic_facebook, onClick = { openUrl(ctx, FB_URL) })
            }
        }
    }

    if (showSpeed) SpeedDialog(vm.playbackSpeed, { vm.setSpeed(it) }) { showSpeed = false }
    if (showCf) CrossfadeDialog(vm.crossfadeSec, { vm.setCrossfade(it) }) { showCf = false }
    if (showUpdate) UpdateDialog(APP_VERSION) { showUpdate = false }
}

// ---------------------------------------------------------------------
//  Gaya pemutar
// ---------------------------------------------------------------------

@Composable
fun PlayerStylePage(vm: MusicViewModel, onBack: () -> Unit) {
    val preview = vm.currentSong ?: vm.songs.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        PageHeader("Gaya pemutar", onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(PlayerStyleNames.indices.toList()) { i ->
                val sel = vm.playerStyle == i
                Column(
                    Modifier
                        .cascade(i)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(24.dp), if (sel) 1.8f else 1f)
                        .clickable { vm.applyPlayerStyle(i) }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                        PlayerArt(preview, i, playing = true, size = 118.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(PlayerStyleNames[i], color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        PlayerStyleDescs[i], color = SubText, fontSize = 11.sp,
                        textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (sel) Color.White else Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sel) {
                            Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            if (sel) "Diterapkan" else "Pilih",
                            color = if (sel) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Widget Layar utama
// ---------------------------------------------------------------------

@Composable
private fun WidgetCard(index: Int, title: String, desc: String, onAdd: () -> Unit, preview: @Composable () -> Unit) {
    Column(
        Modifier.cascade(index).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(14.dp),
    ) {
        preview()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(desc, color = SubText, fontSize = 12.sp)
            }
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tambah", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val WidgetBrush = Brush.linearGradient(listOf(Color(0xFF5A4F37), Color(0xFF1F1C13)))

@Composable
private fun MiniCover(song: Song?, modifier: Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    ArtImage(song, modifier, shape, iconSize = 18.dp)
}

@Composable
private fun DiscPreview(song: Song?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF0E0E10)), contentAlignment = Alignment.Center) {
        MiniCover(song, Modifier.size(size * 0.48f), CircleShape)
    }
}

@Composable
private fun DiscControls() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(androidx.compose.material.icons.Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Icon(androidx.compose.material.icons.Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun WidgetsPage(vm: MusicViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.currentSong ?: vm.songs.firstOrNull()
    val recents = vm.songsOf(vm.recents).ifEmpty { vm.songs }.take(4)
    val title = s?.title ?: "Musix"
    val artist = s?.artist ?: "Ketuk untuk memutar musik"

    Column(Modifier.fillMaxSize()) {
        PageHeader("Widget Layar utama", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Pilih widget lalu ketuk Tambah. Jika launcher tidak mendukung, tahan lama layar utama → Widget → Musix.",
                color = SubText, fontSize = 13.sp,
            )

            WidgetCard(0, "Cari dan putar", "Pemutar mini + kolom cari", { pinWidget(ctx, PlayerSearchWidget::class.java) }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(WidgetBrush).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniCover(s, Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.White)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().height(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text("  Cari lagu, daftar putar, dan artis", color = SubText, fontSize = 12.sp) }
                }
            }

            WidgetCard(1, "Lagu terbaru", "Pemutar mini + 4 lagu terakhir", { pinWidget(ctx, RecentWidget::class.java) }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(WidgetBrush).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniCover(s, Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (k in 0 until 4) {
                            MiniCover(recents.getOrNull(k), Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }

            WidgetCard(2, "Piringan persegi", "Kartu kaca dengan tombol putar, jeda, berikutnya", { pinWidget(ctx, RecommendWidget::class.java) }) {
                Column(
                    Modifier.size(width = 170.dp, height = 190.dp).clip(RoundedCornerShape(26.dp))
                        .background(WidgetBrush).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DiscPreview(s, 92.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = SubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    DiscControls()
                }
            }

            WidgetCard(3, "Piringan persegi panjang", "Piringan di kiri, tombol putar di kanan", { pinWidget(ctx, DiscWideWidget::class.java) }) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(WidgetBrush).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiscPreview(s, 82.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, color = SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        DiscControls()
                    }
                }
            }

            WidgetCard(4, "Kapsul", "Kontrol cepat putar/jeda", { pinWidget(ctx, CapsuleWidget::class.java) }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(WidgetBrush)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniCover(s, Modifier.size(40.dp), CircleShape)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, color = SubText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Daftar putar
// ---------------------------------------------------------------------

@Composable
fun PlaylistsPage(
    vm: MusicViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    val names = vm.playlists.keys.toList()
    Column(Modifier.fillMaxSize()) {
        PageHeader("Daftar putar", onBack) {
            GlassIconButton(Icons.Rounded.Add, "Buat daftar putar", onNew)
        }
        if (names.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(84.dp))
                Spacer(Modifier.height(10.dp))
                Text("Belum ada daftar putar", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                GlassButton("Buat daftar putar", Icons.Rounded.Add, onNew)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp)) {
                item {
                    val all = names.flatMap { vm.songsOf(vm.playlists[it].orEmpty()) }.distinctBy { it.uri }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = GroupRadius, topEnd = GroupRadius))
                            .background(Color.Black.copy(alpha = 0.14f))
                            .background(Color.White.copy(alpha = 0.11f))
                            .clickable { vm.shuffleAll(all) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("Acak daftar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                itemsIndexed(names, key = { _, n -> n }) { i, name ->
                    val list = vm.songsOf(vm.playlists[name].orEmpty())
                    Row(
                        Modifier
                            .cascade(i + 1)
                            .fillMaxWidth()
                            .clip(rowShape(i, names.size, headerAbove = true))
                            .background(Color.Black.copy(alpha = 0.14f))
                            .background(Color.White.copy(alpha = 0.11f))
                            .clickable { onOpen(name) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtImage(list.firstOrNull(), Modifier.size(54.dp), RoundedCornerShape(12.dp), seed = name, iconSize = 26.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Total ${list.size} lagu", color = SubText, fontSize = 12.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Pilih banyak lagu
// ---------------------------------------------------------------------

@Composable
fun SelectionScreen(
    vm: MusicViewModel,
    songs: List<Song>,
    initial: Set<String>,
    pickMode: Boolean,
    onClose: () -> Unit,
    onPlayNext: (List<Song>) -> Unit,
    onAddToPlaylist: (List<Song>) -> Unit,
    onDelete: (List<Song>) -> Unit,
    onConfirm: (List<Song>) -> Unit,
) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val selected = remember { mutableStateListOf<String>().also { it.addAll(initial) } }
    var more by remember { mutableStateOf(false) }
    fun chosen() = songs.filter { it.uri in selected }

    ProvideBackdrop {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0C))) {
            GlassBackground()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(Icons.Rounded.Close, "Tutup", onClose)
                    Text(
                        "${selected.size} item terpilih",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    GlassIconButton(
                        if (selected.size == songs.size && songs.isNotEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        "Pilih semua",
                        {
                            if (selected.size == songs.size) selected.clear()
                            else { selected.clear(); selected.addAll(songs.map { it.uri }) }
                        },
                    )
                }

                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                ) {
                    itemsIndexed(songs, key = { _, s -> s.uri }) { i, s ->
                        val on = s.uri in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(rowShape(i, songs.size, headerAbove = false))
                                .background(Color.Black.copy(alpha = 0.14f))
                                .background(Color.White.copy(alpha = if (on) 0.22f else 0.11f))
                                .clickable { if (on) selected.remove(s.uri) else selected.add(s.uri) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${i + 1}",
                                color = SubText,
                                fontSize = 13.sp,
                                modifier = Modifier.width(30.dp),
                                textAlign = TextAlign.Center,
                            )
                            ArtImage(s, Modifier.size(46.dp), RoundedCornerShape(12.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist, color = SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                null,
                                tint = if (on) Accent else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 6.dp).size(26.dp),
                            )
                        }
                    }
                }

                // Bilah aksi bawah
                if (pickMode) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GlassButton(
                            "Tambahkan (${selected.size})",
                            Icons.Rounded.Check,
                            { onConfirm(chosen()) },
                        )
                    }
                } else {
                    val none = selected.isEmpty()
                    Row(
                        Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(32.dp), 1.2f)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        @Composable
                        fun Act(icon: ImageVector, label: String, onClick: () -> Unit) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable(enabled = !none, onClick = onClick),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(icon, label, tint = Color.White.copy(alpha = if (none) 0.35f else 1f))
                                Text(
                                    label,
                                    color = Color.White.copy(alpha = if (none) 0.35f else 0.9f),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                        }
                        Act(Icons.Rounded.PlaylistPlay, "Putar nanti") { onPlayNext(chosen()) }
                        Act(Icons.Rounded.PlaylistAdd, "Tambah ke daftar putar") { onAddToPlaylist(chosen()) }
                        Act(Icons.Rounded.Delete, "Hapus") { onDelete(chosen()) }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.clickable(enabled = !none) { more = true },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Rounded.MoreHoriz, "Lainnya", tint = Color.White.copy(alpha = if (none) 0.35f else 1f))
                                Text("Lainnya", color = Color.White.copy(alpha = if (none) 0.35f else 0.9f), fontSize = 10.sp)
                            }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(
                                    text = { Text("Favorit") },
                                    leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
                                    onClick = { more = false; vm.addFavorites(chosen()); onClose() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Berbagi") },
                                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                    onClick = { more = false; shareSongs(ctx, chosen()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Dialog (dibuka dari menu samping)
// ---------------------------------------------------------------------

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    MusixDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF26241B),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Kebijakan Privasi") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Musix adalah pemutar musik offline.\n\n" +
                        "• Aplikasi tidak mengumpulkan, menyimpan di server, atau membagikan data pribadi Anda.\n" +
                        "• Izin audio/penyimpanan hanya dipakai untuk membaca dan memutar file musik di perangkat.\n" +
                        "• Izin notifikasi dipakai untuk kontrol pemutar di bilah notifikasi dan layar kunci.\n" +
                        "• Izin tampil di atas aplikasi lain (opsional) dipakai untuk layar kunci kaca.\n" +
                        "• Favorit, daftar putar, dan pengaturan hanya tersimpan di perangkat Anda.\n" +
                        "• Tidak ada iklan dan tidak ada pelacak.\n\n" +
                        "Pertanyaan? Hubungi $CONTACT_EMAIL",
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = Accent) } },
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    MusixDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF26241B),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(APP_NAME) },
        text = {
            Text(
                "Pemutar musik offline untuk semua format audio (MP3, M4A, FLAC, WAV, OGG, OPUS, dll). " +
                    "Berjalan di latar belakang dan di layar kunci.\n\nVersi $APP_VERSION"
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Accent) } },
    )
}
