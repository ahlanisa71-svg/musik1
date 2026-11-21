package com.glassplayer.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.glassplayer.app.DetailKey
import com.glassplayer.app.MusicViewModel
import com.glassplayer.app.data.Song
import kotlinx.coroutines.launch

private data class Nav(val detail: DetailKey?, val page: Page?, val tab: Int)

private data class Sel(
    val songs: List<Song>,
    val initial: Set<String>,
    val pick: Boolean = false,
    val playlist: String? = null,
)

private fun defaultPlaylistName(vm: MusicViewModel): String {
    var n = 1
    while ("Daftar putar baru $n" in vm.playlists.keys) n++
    return "Daftar putar baru $n"
}

private fun LazyListScope.songItems(
    songs: List<Song>,
    vm: MusicViewModel,
    favSet: Set<String>,
    actions: SongActions,
    headerAbove: Boolean,
    pad: Dp,
    onSelect: (List<Song>, Song) -> Unit,
    base: Int = 0,
) {
    itemsIndexed(songs, key = { _, s -> s.uri }) { i, s ->
        SongRow(
            song = s,
            current = vm.currentId == s.uri,
            playing = vm.isPlaying,
            favorite = s.uri in favSet,
            shape = rowShape(i, songs.size, headerAbove),
            actions = actions,
            modifier = Modifier.cascade(base + i).padding(horizontal = pad),
            onLongClick = { onSelect(songs, s) },
        ) { vm.playList(songs, i) }
    }
}

// =====================================================================
//  Root
// =====================================================================

@Composable
fun AppRoot(vm: MusicViewModel, revealed: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAudio() = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasAudio()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val now = hasAudio()
        if (now && !granted) vm.loadSongs()
        granted = now
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    val pickFiles = { picker.launch(arrayOf("audio/*", "application/ogg")) }

    // ---- Hapus lagu di Android 10 ke bawah (butuh izin per file di Android 10) ----
    var legacyQueue by remember { mutableStateOf<List<Song>>(emptyList()) }
    var legacyDone by remember { mutableStateOf<List<Song>>(emptyList()) }
    var legacyFailed by remember { mutableStateOf(0) }
    var consent by remember { mutableStateOf<Int?>(null) }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        consent = if (res.resultCode == Activity.RESULT_OK) 1 else 2
    }

    fun finishLegacy() {
        if (legacyDone.isNotEmpty()) vm.onDeleteDone(legacyDone)
        Toast.makeText(
            ctx,
            if (legacyFailed > 0) "$legacyFailed lagu tidak bisa dihapus" else "Lagu dihapus",
            Toast.LENGTH_SHORT,
        ).show()
        legacyQueue = emptyList()
        legacyDone = emptyList()
        legacyFailed = 0
    }

    fun legacyStep() {
        var q = legacyQueue
        while (q.isNotEmpty()) {
            val s = q.first()
            try {
                ctx.contentResolver.delete(Uri.parse(s.uri), null, null)
                legacyDone = legacyDone + s
                q = q.drop(1)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException) {
                    legacyQueue = q
                    legacyLauncher.launch(
                        IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                    )
                    return
                }
                legacyFailed++
                q = q.drop(1)
            } catch (e: Exception) {
                legacyFailed++
                q = q.drop(1)
            }
        }
        legacyQueue = emptyList()
        finishLegacy()
    }

    val writePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        legacyStep()
    }

    fun startLegacyDelete(list: List<Song>) {
        legacyQueue = list
        legacyDone = emptyList()
        legacyFailed = 0
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            legacyStep()
        }
    }

    LaunchedEffect(consent) {
        when (consent) {
            1 -> { consent = null; legacyStep() }
            2 -> { consent = null; legacyFailed++; legacyQueue = legacyQueue.drop(1); legacyStep() }
            else -> {}
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val list = vm.pendingDelete
        if (res.resultCode == Activity.RESULT_OK && list != null) vm.onDeleteDone(list)
        vm.clearPendingDelete()
    }
    var pendingEdit by remember { mutableStateOf<EditReq?>(null) }
    val editLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val r = pendingEdit
        pendingEdit = null
        if (res.resultCode == Activity.RESULT_OK && r != null) {
            val ok = applySongEdit(ctx, r)
            Toast.makeText(ctx, if (ok) "Info lagu diperbarui" else "Gagal memperbarui info lagu", Toast.LENGTH_SHORT).show()
            if (ok) vm.loadSongs()
        }
    }
    val saveEdit: (EditReq) -> Unit = { r ->
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val sender = MediaStore.createWriteRequest(ctx.contentResolver, listOf(Uri.parse(r.song.uri))).intentSender
                pendingEdit = r
                editLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                Toast.makeText(ctx, "Lagu ini tidak bisa diedit", Toast.LENGTH_SHORT).show()
            }
        } else {
            val ok = applySongEdit(ctx, r)
            Toast.makeText(ctx, if (ok) "Info lagu diperbarui" else "Gagal memperbarui info lagu", Toast.LENGTH_SHORT).show()
            if (ok) vm.loadSongs()
        }
    }
    val pendingDel = vm.pendingDelete
    LaunchedEffect(pendingDel) {
        if (pendingDel != null && Build.VERSION.SDK_INT >= 30) {
            try {
                val sender = MediaStore.createDeleteRequest(
                    ctx.contentResolver, pendingDel.map { Uri.parse(it.uri) }
                ).intentSender
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                Toast.makeText(ctx, "Lagu ini tidak bisa dihapus dari sini", Toast.LENGTH_SHORT).show()
                vm.clearPendingDelete()
            }
        }
    }

    LaunchedEffect(Unit) { if (granted) vm.ensureLoaded() }
    // Dialog izin baru muncul SETELAH animasi pembuka selesai, supaya animasi tidak terganggu.
    LaunchedEffect(revealed) {
        if (!revealed) return@LaunchedEffect
        val need = buildList {
            if (!granted) add(audioPerm)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }

    if (!granted) {
        Box(Modifier.fillMaxSize()) {
            GlassBackground()
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .glass()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Izinkan akses musik", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Musix perlu izin untuk membaca file musik di perangkat Anda.",
                    color = SubText, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                GlassButton("Izinkan", onClick = { permLauncher.launch(arrayOf(audioPerm)) })
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    )
                }) { Text("Buka pengaturan aplikasi", color = Accent) }
            }
        }
        return
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(tab) { if (tab == 4) runCatching { searchFocus.requestFocus() } }
    var detail by remember { mutableStateOf<DetailKey?>(null) }
    var page by remember { mutableStateOf<Page?>(null) }
    var pageFrom by remember { mutableStateOf<Page?>(null) }
    var showNow by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf<Sel?>(null) }
    var addTargets by remember { mutableStateOf<List<Song>?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var afterCreate by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var showSleep by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var detailSong by remember { mutableStateOf<Song?>(null) }
    var editSong by remember { mutableStateOf<Song?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    LaunchedEffect(vm.pendingTab) {
        vm.pendingTab?.let {
            tab = it; detail = null; page = null; pageFrom = null; showNow = false; selection = null
            vm.pendingTab = null
        }
    }

    val actions = remember(vm) {
        SongActions(
            onPlayNext = { vm.playNext(it) },
            onAddToQueue = { vm.addToQueue(listOf(it)) },
            onToggleFav = { vm.toggleFavorite(it) },
            onAddToPlaylist = { addTargets = listOf(it) },
            onDelete = { vm.requestDelete(listOf(it)) },
            onDetails = { detailSong = it },
            onEdit = { editSong = it },
        )
    }
    val onSelect: (List<Song>, Song) -> Unit = { list, s -> selection = Sel(list, setOf(s.uri)) }
    val toggleLock: (Boolean) -> Unit = { on ->
        vm.setLockScreen(on)
        if (on && !Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
    }

    BackHandler(enabled = detail == null && page == null && tab != 0) { tab = 0 }
    val pageBack: () -> Unit = { page = pageFrom; pageFrom = null }
    BackHandler(enabled = page != null && detail == null) { pageBack() }
    BackHandler(enabled = detail != null) { detail = null }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xF01C1B15)) {
                DrawerContent(
                    songCount = vm.allSongs.size,
                    onSettings = { detail = null; pageFrom = null; page = Page.Settings; scope.launch { drawerState.close() } },
                    onStyle = { detail = null; pageFrom = null; page = Page.Style; scope.launch { drawerState.close() } },
                    onWidgets = { detail = null; pageFrom = null; page = Page.Widgets; scope.launch { drawerState.close() } },
                    onPrivacy = { showPrivacy = true; scope.launch { drawerState.close() } },
                    onBug = { sendBugReport(ctx); scope.launch { drawerState.close() } },
                    onAbout = { showAbout = true; scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackground()

            Box(Modifier.fillMaxSize().statusBarsPadding()) {
              AnimatedContent(
                targetState = Nav(detail, page, tab),
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                },
                label = "nav",
              ) { st ->
               val born = remember { android.os.SystemClock.uptimeMillis() }
               CompositionLocalProvider(LocalScreenBorn provides born, LocalScreenReady provides revealed) {
                val d = st.detail
                val pg = st.page
                when {
                    d != null -> {
                        val list = vm.listFor(d)
                        val name = (d as? DetailKey.Playlist)?.name
                        DetailScreen(
                            title = detailTitle(d),
                            songs = list,
                            vm = vm,
                            playlist = name,
                            actions = if (name != null)
                                SongActions(
                                    actions.onPlayNext, actions.onAddToQueue, actions.onToggleFav,
                                    actions.onAddToPlaylist, actions.onDelete,
                                    onRemoveFromPlaylist = { s -> vm.removeFromPlaylist(name, s) },
                                    onDetails = actions.onDetails,
                                    onEdit = actions.onEdit,
                                ) else actions,
                            onBack = { detail = null },
                            onDelete = if (name != null) ({ vm.deletePlaylist(name); detail = null }) else null,
                            onAddSongs = if (name != null) ({
                                selection = Sel(vm.songs, list.map { it.uri }.toSet(), pick = true, playlist = name)
                            }) else null,
                            onSelect = onSelect,
                        )
                    }
                    pg != null -> when (pg) {
                        Page.Settings -> SettingsPage(
                            vm = vm,
                            onBack = { pageBack() },
                            onToggleLock = toggleLock,
                            onSleep = { showSleep = true },
                            onRescan = { vm.loadSongs() },
                            onPick = { pickFiles() },
                            onPage = { pageFrom = Page.Settings; page = it },
                        )
                        Page.Style -> PlayerStylePage(vm) { pageBack() }
                        Page.Widgets -> WidgetsPage(vm) { pageBack() }
                        Page.Equalizer -> EqualizerPage { pageBack() }
                        Page.Theme -> ThemePage { pageBack() }
                        Page.DownloadsStorage -> DownloadsStoragePage(vm) { pageBack() }
                        Page.App -> AppSettingsPage(onBack = { pageBack() }, onTheme = { pageFrom = Page.App; page = Page.Theme })
                        Page.Playlists -> PlaylistsPage(
                            vm = vm,
                            onBack = { pageBack() },
                            onOpen = { detail = DetailKey.Playlist(it) },
                            onNew = { afterCreate = null; showNewPlaylist = true },
                        )
                    }
                    st.tab == 1 -> NewMusicScreen(vm) { list, title -> if (list.isNotEmpty()) vm.playList(list, 0) }
                    st.tab == 2 -> RadioMoodScreen(vm) { list, title -> if (list.isNotEmpty()) vm.shuffleAll(list) }
                    st.tab == 3 -> LibraryTabScreen(
                        vm = vm,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onDetail = { detail = it },
                        onNewPlaylist = { afterCreate = null; showNewPlaylist = true },
                    )
                    st.tab == 4 -> SearchScreen(vm, actions, query, { scope.launch { drawerState.open() } }, onSelect)
                    st.tab == 0 -> LibraryScreen(
                        vm = vm, actions = actions,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { tab = 4 }, onDetail = { detail = it },
                        onPlaylists = { pageFrom = null; page = Page.Playlists },
                        onNewPlaylist = { afterCreate = null; showNewPlaylist = true },
                        onPickFiles = { pickFiles() }, onSelect = onSelect,
                        onSelectMode = { list -> selection = Sel(list, emptySet()) },
                    )
                    else -> DetailScreen(
                        title = "Favorit",
                        songs = vm.listFor(DetailKey.Favorites),
                        vm = vm,
                        playlist = null,
                        actions = actions,
                        onBack = null,
                        onDelete = null,
                        onAddSongs = null,
                        onSelect = onSelect,
                    )
                }
               }
              }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .then(if (tab == 4 && detail == null && page == null) Modifier.imePadding() else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                MiniPlayer(vm) { showNow = true }
                if (vm.currentSong != null) Spacer(Modifier.height(10.dp))
                if (tab == 4 && detail == null && page == null) {
                    SearchBottomBar(
                        query = query,
                        onQuery = { query = it },
                        focus = searchFocus,
                        onHome = { tab = 0; detail = null; page = null; pageFrom = null },
                    )
                } else {
                    BottomBar(selected = tab) { tab = it; detail = null; page = null; pageFrom = null }
                }
            }

            selection?.let { s ->
                SelectionScreen(
                    vm = vm,
                    songs = s.songs,
                    initial = s.initial,
                    pickMode = s.pick,
                    onClose = { selection = null },
                    onPlayNext = { vm.playNextMany(it); selection = null },
                    onAddToPlaylist = { addTargets = it; selection = null },
                    onDelete = { vm.requestDelete(it); selection = null },
                    onConfirm = { picked ->
                        s.playlist?.let { vm.addManyToPlaylist(it, picked) }
                        selection = null
                    },
                )
            }

            AnimatedVisibility(
                visible = showNow,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NowPlayingScreen(
                    vm,
                    onClose = { showNow = false },
                    onSleep = { showSleep = true },
                    onStyle = { showNow = false; detail = null; pageFrom = null; page = Page.Style },
                    onAddToPlaylist = { addTargets = listOf(it) },
                    onAlbum = { showNow = false; page = null; detail = DetailKey.Album(it) },
                    onArtist = { showNow = false; page = null; detail = DetailKey.Artist(it) },
                    onWidgets = { showNow = false; detail = null; pageFrom = null; page = Page.Widgets },
                    onEqualizer = { showNow = false; detail = null; pageFrom = null; page = Page.Equalizer },
                    onDetails = { detailSong = it },
                )
            }
        }
    }

    // ---- Dialog ----
    addTargets?.let { list ->
        MusixDialog(
            onDismissRequest = { addTargets = null },
            containerColor = Color(0xFF26241B),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Tambah ke daftar putar") },
            text = {
                Column {
                    vm.playlists.keys.forEach { name ->
                        TextButton(onClick = { vm.addManyToPlaylist(name, list); addTargets = null }) {
                            Text(name, color = Color.White)
                        }
                    }
                    TextButton(onClick = {
                        afterCreate = { n -> vm.addManyToPlaylist(n, list) }
                        showNewPlaylist = true
                        addTargets = null
                    }) {
                        Text("+ Daftar putar baru", color = Accent)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addTargets = null }) { Text("Tutup", color = Accent) } },
        )
    }

    if (showNewPlaylist) {
        var name by remember { mutableStateOf(defaultPlaylistName(vm)) }
        MusixDialog(
            onDismissRequest = { showNewPlaylist = false },
            containerColor = Color(0xFF26241B),
            titleContentColor = Color.White,
            title = { Text("Nama daftar putar") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (vm.createPlaylist(n)) afterCreate?.invoke(n)
                    else Toast.makeText(ctx, "Nama kosong atau sudah dipakai", Toast.LENGTH_SHORT).show()
                    afterCreate = null
                    showNewPlaylist = false
                }) { Text("Oke", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { afterCreate = null; showNewPlaylist = false }) { Text("Batal", color = Color.White) }
            },
        )
    }

    if (showSleep) {
        MusixDialog(
            onDismissRequest = { showSleep = false },
            containerColor = Color(0xFF26241B),
            titleContentColor = Color.White,
            title = { Text("Timer tidur") },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        TextButton(onClick = { vm.setSleepTimer(m); showSleep = false }) {
                            Text("$m menit", color = Color.White)
                        }
                    }
                    if (vm.sleepEndsAt > 0) {
                        TextButton(onClick = { vm.setSleepTimer(0); showSleep = false }) {
                            Text("Matikan timer", color = Accent)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleep = false }) { Text("Tutup", color = Accent) } },
        )
    }

    detailSong?.let { SongDetailsDialog(it) { detailSong = null } }
    editSong?.let { EditSongDialog(it, { editSong = null }, saveEdit) }
    if (showPrivacy) PrivacyDialog { showPrivacy = false }
    if (showAbout) AboutDialog { showAbout = false }

    // Pemeriksaan pembaruan otomatis sekali saat aplikasi dibuka (hanya memberi tahu; memasang tetap manual).
    var autoUpdate by remember { mutableStateOf<com.glassplayer.app.data.UpdateInfo?>(null) }
    var showUpdateDlg by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) {
            kotlinx.coroutines.delay(2500)
            if (com.glassplayer.app.data.Updater.shouldAutoCheck(ctx)) {
                val r = com.glassplayer.app.data.Updater.check(ctx)
                if (r is com.glassplayer.app.data.UpdateCheck.Available) autoUpdate = r.info
            }
        }
    }
    autoUpdate?.let { u ->
        MusixDialog(
            onDismissRequest = { autoUpdate = null },
            title = { Text("Pembaruan tersedia") },
            text = { Text("Musix ${u.versionName} sudah tersedia. Perbarui sekarang untuk fitur dan perbaikan terbaru.", color = SubText) },
            dismissButton = { TextButton(onClick = { autoUpdate = null }) { Text("Nanti", color = SubText) } },
            confirmButton = {
                TextButton(onClick = { autoUpdate = null; showUpdateDlg = true }) { Text("Perbarui", color = Accent) }
            },
        )
    }
    if (showUpdateDlg) UpdateDialog(APP_VERSION) { showUpdateDlg = false }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.retryLeftovers()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // File tidak bisa dihapus oleh sistem (mis. HP Xiaomi): minta izin "akses semua file"
    if (vm.needAllFiles) {
        MusixDialog(
            onDismissRequest = { vm.needAllFiles = false },
            containerColor = Color(0xFF26241B),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Izinkan akses semua file?") },
            text = {
                Text("Sistem menolak menghapus file lagu ini dari penyimpanan. Aktifkan \"Akses semua file\" untuk Musix, lalu kembali ke aplikasi: file akan dihapus otomatis. Lagu ini sudah disembunyikan dari daftar.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.needAllFiles = false
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                        )
                    } catch (_: Exception) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    }
                }) { Text("Buka pengaturan", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { vm.needAllFiles = false }) { Text("Nanti", color = Color.White) } },
        )
    }

    // Android 10 ke bawah: konfirmasi sendiri sebelum hapus (Android 11+ memakai dialog sistem)
    val pd = vm.pendingDelete
    if (pd != null && Build.VERSION.SDK_INT < 30) {
        MusixDialog(
            onDismissRequest = { vm.clearPendingDelete() },
            containerColor = Color(0xFF26241B),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Hapus ${pd.size} lagu?") },
            text = { Text("File akan dihapus dari perangkat dan tidak bisa dikembalikan.") },
            confirmButton = {
                TextButton(onClick = { vm.clearPendingDelete(); startLegacyDelete(pd) }) {
                    Text("Hapus", color = Color(0xFFFF8A80))
                }
            },
            dismissButton = { TextButton(onClick = { vm.clearPendingDelete() }) { Text("Batal", color = Color.White) } },
        )
    }
}

private fun detailTitle(k: DetailKey) = when (k) {
    DetailKey.Recent -> "Terbaru Diputar"
    DetailKey.Newest -> "Baru Ditambah"
    DetailKey.All -> "Semua Lagu"
    DetailKey.Favorites -> "Favorit"
    is DetailKey.Playlist -> k.name
    is DetailKey.Album -> k.name
    is DetailKey.Artist -> k.name
    is DetailKey.Folder -> k.name.trimEnd('/').substringAfterLast('/').ifBlank { "Folder" }
    DetailKey.Downloads -> "Lagu terunduh"
    is DetailKey.Mix -> when (k.id) {
        "short" -> "Singkat (di bawah 3 menit)"
        "mid" -> "Sedang (3–5 menit)"
        else -> "Panjang (di atas 5 menit)"
    }
}

// =====================================================================
//  Komponen kecil
// =====================================================================

@Composable
fun GlassButton(text: String, icon: ImageVector? = null, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(CircleShape, 1.3f)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun HomeTile(title: String, icon: ImageVector, art: Song?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 160.dp, height = 100.dp)
            .glass(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        if (art != null) {
            ArtImage(art, Modifier.fillMaxSize(), androidx.compose.ui.graphics.RectangleShape, iconSize = 1.dp)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.7f)))
                )
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(30.dp)
                .glass(CircleShape, 1.4f),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

@Composable
private fun CollectionCard(title: String, subtitle: String, art: Song?, onClick: () -> Unit) {
    Column(Modifier.width(128.dp).clickable(onClick = onClick)) {
        ArtImage(art, Modifier.size(128.dp), RoundedCornerShape(22.dp), seed = title, iconSize = 40.dp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = SubText, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    Column(Modifier.width(128.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(128.dp).glass(RoundedCornerShape(22.dp), 1.2f),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(6.dp))
        Text("Daftar putar baru", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(" ", fontSize = 11.sp)
    }
}

// =====================================================================
//  Pustaka (beranda)
// =====================================================================

@Composable
private fun GroupRow(index: Int, title: String, subtitle: String, art: Song?, icon: ImageVector?, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Row(
        Modifier
            .cascade(index)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.14f))
            .background(Color.White.copy(alpha = 0.11f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White) }
        } else {
            ArtImage(art, Modifier.size(50.dp), RoundedCornerShape(12.dp), seed = title)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun LibraryScreen(
    vm: MusicViewModel,
    actions: SongActions,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onDetail: (DetailKey) -> Unit,
    onPlaylists: () -> Unit,
    onNewPlaylist: () -> Unit,
    onPickFiles: () -> Unit,
    onSelect: (List<Song>, Song) -> Unit,
    onSelectMode: (List<Song>) -> Unit,
) {
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }
    val moodKeywords = mapOf(
        "Relax" to listOf("relax", "acoustic", "piano", "ambient"),
        "Sad" to listOf("sad", "galau", "rindu", "sepi", "kecewa", "patah"),
        "Happy" to listOf("happy", "bahagia", "senang", "smile"),
        "Romantic" to listOf("love", "cinta", "romance", "sayang"),
        "Chill" to listOf("chill", "lofi", "jazz", "soft"),
        "Focus" to listOf("focus", "study", "instrumental", "ambient"),
        "Workout" to listOf("workout", "gym", "sport", "run"),
        "Sleep" to listOf("sleep", "lullaby", "piano", "relax"),
        "Commute" to listOf("commute", "road", "drive", "jalanan"),
        "Party" to listOf("party", "dance", "remix", "club"),
    )
    val songs = remember(vm.songs, selectedMood) {
        val keys = selectedMood?.let { moodKeywords[it].orEmpty() }.orEmpty()
        if (keys.isEmpty()) vm.songs else vm.songs.filter { song ->
            val haystack = "${song.title} ${song.artist} ${song.album}".lowercase()
            keys.any { it in haystack }
        }
    }
    var libTab by rememberSaveable { mutableStateOf(0) }
    val favSet = remember(vm.favorites) { vm.favorites.toSet() }
    val albums = remember(vm.allSongs) {
        vm.allSongs
            .filter { it.album.isNotBlank() }
            .groupBy { it.album }
            .map { it.key to it.value }
            .sortedByDescending { it.second.size }
            .take(15)
    }
    val artists = remember(vm.allSongs) {
        vm.allSongs.groupBy { it.artist }.map { it.key to it.value }.sortedBy { it.first.lowercase() }
    }
    val albumGroups = remember(vm.allSongs) {
        vm.allSongs.filter { it.album.isNotBlank() }.groupBy { it.album }.map { it.key to it.value }
            .sortedBy { it.first.lowercase() }
    }
    val folders = remember(vm.allSongs) {
        vm.allSongs.groupBy { it.folder }.map { it.key to it.value }.sortedBy { it.first.lowercase() }
    }
    val tabNames = listOf("Lagu", "Artis", "Album", "Folder")

    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp)) {

        // Bilah atas: menu + kolom cari
        item {
            Row(
                Modifier.cascade(0).padding(horizontal = 16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Rounded.Menu, "Menu", onOpenDrawer)
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Play Musix", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Relax", "Sad", "Happy", "Romantic", "Chill", "Focus", "Workout", "Sleep", "Commute", "Party")) { mood ->
                    val active = selectedMood == mood
                    Box(
                        Modifier.clip(CircleShape)
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.16f))
                            .clickable { selectedMood = if (active) null else mood }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(mood, color = if (active) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (selectedMood != null) {
                Text("Kategori ${selectedMood} • ${songs.size} lagu cocok", color = SubText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Baris kartu kaca teratas
        item {
            LazyRow(
                Modifier.cascade(1),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HomeTile("Favorit", Icons.Rounded.Favorite, vm.songsOf(vm.favorites).firstOrNull()) { onDetail(DetailKey.Favorites) }
                }
                item {
                    HomeTile("Daftar putar", Icons.Rounded.PlaylistPlay, vm.songsOf(vm.playlists.values.firstOrNull().orEmpty()).firstOrNull(), onPlaylists)
                }
                item {
                    HomeTile("Terkini", Icons.Rounded.History, vm.songsOf(vm.recents).firstOrNull()) { onDetail(DetailKey.Recent) }
                }
                item {
                    HomeTile("Baru Ditambah", Icons.Rounded.NewReleases, vm.listFor(DetailKey.Newest).firstOrNull()) { onDetail(DetailKey.Newest) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Panel kaca daftar putar
        item {
            Column(Modifier.cascade(2).padding(horizontal = 16.dp).fillMaxWidth().glass()) {
                Row(
                    Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Daftar putar", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { vm.shuffleAll(songs) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Acak", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        CollectionCard("Semua Lagu", "${songs.size} lagu", songs.firstOrNull()) { onDetail(DetailKey.All) }
                    }
                    items(vm.playlists.keys.toList()) { name ->
                        val list = vm.songsOf(vm.playlists[name].orEmpty())
                        CollectionCard(name, "${list.size} lagu", list.firstOrNull()) { onDetail(DetailKey.Playlist(name)) }
                    }
                    item { NewPlaylistCard(onNewPlaylist) }
                    items(albums) { (name, list) ->
                        CollectionCard(name, "${list.size} lagu", list.first()) { onDetail(DetailKey.Album(name)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Tab: Lagu / Artis / Album / Folder
        item {
            Row(
                Modifier.cascade(3).padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabNames.forEachIndexed { i, n ->
                    val sel = i == libTab
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (sel) Color.White else Color.White.copy(alpha = 0.16f))
                            .clickable { libTab = i }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            n,
                            color = if (sel) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Baris "Acak daftar" + urut + pilih
        item {
            Row(
                Modifier
                    .cascade(4)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(
                        if ((libTab == 0 && songs.isEmpty()) || (libTab != 0 && vm.allSongs.isEmpty())) RoundedCornerShape(GroupRadius)
                        else RoundedCornerShape(topStart = GroupRadius, topEnd = GroupRadius)
                    )
                    .background(Color.Black.copy(alpha = 0.14f))
                    .background(Color.White.copy(alpha = 0.11f))
                    .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { vm.shuffleAll(songs) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shuffle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Acak daftar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("  ${songs.size}", color = SubText, fontSize = 14.sp)
                }
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Row(
                    Modifier.clip(CircleShape).clickable(onClick = vm::cycleSort).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Sort, "Urutkan", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(vm.sortLabel, color = Color.White, fontSize = 12.sp)
                }
                Icon(
                    Icons.Rounded.Checklist, "Pilih lagu", tint = Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onSelectMode(songs) }
                        .padding(8.dp),
                )
            }
        }

        if (vm.allSongs.isEmpty() && !vm.loading) {
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 24.dp).fillMaxWidth().glass().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Belum ada lagu ditemukan", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Salin file musik ke HP, atau pilih file secara manual.",
                        color = SubText, textAlign = TextAlign.Center, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassButton("Tambah file musik", Icons.Rounded.FolderOpen, onPickFiles)
                }
            }
        }

        when (libTab) {
            0 -> songItems(songs, vm, favSet, actions, headerAbove = true, pad = 16.dp, onSelect = onSelect, base = 5)
            1 -> itemsIndexed(artists, key = { _, g -> g.first }) { i, (name, list) ->
                GroupRow(5 + i, name, "${list.size} lagu", list.firstOrNull(), Icons.Rounded.Person, rowShape(i, artists.size, true)) {
                    onDetail(DetailKey.Artist(name))
                }
            }
            2 -> itemsIndexed(albumGroups, key = { _, g -> g.first }) { i, (name, list) ->
                GroupRow(5 + i, name, "${list.first().artist} • ${list.size} lagu", list.firstOrNull(), null, rowShape(i, albumGroups.size, true)) {
                    onDetail(DetailKey.Album(name))
                }
            }
            else -> itemsIndexed(folders, key = { _, g -> g.first }) { i, (name, list) ->
                val label = name.trimEnd('/').substringAfterLast('/').ifBlank { "Penyimpanan" }
                GroupRow(5 + i, label, "${list.size} lagu • ${name.ifBlank { "/" }}", null, Icons.Rounded.Folder, rowShape(i, folders.size, true)) {
                    onDetail(DetailKey.Folder(name))
                }
            }
        }
    }
}

// =====================================================================
//  Detail (daftar lagu: favorit / daftar putar / album / artis / folder)
// =====================================================================

@Composable
fun DetailScreen(
    title: String,
    songs: List<Song>,
    vm: MusicViewModel,
    playlist: String?,
    actions: SongActions,
    onBack: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onAddSongs: (() -> Unit)?,
    onSelect: (List<Song>, Song) -> Unit,
) {
    val favSet = remember(vm.favorites) { vm.favorites.toSet() }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 220.dp)) {
        item {
          Column(Modifier.cascade(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack)
                    Spacer(Modifier.width(12.dp))
                }
                if (playlist != null) {
                    ArtImage(songs.firstOrNull(), Modifier.size(52.dp), RoundedCornerShape(14.dp), seed = title, iconSize = 24.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Total ${songs.size} lagu", color = SubText, fontSize = 13.sp)
                }
                if (onDelete != null) {
                    GlassIconButton(Icons.Rounded.Delete, "Hapus daftar putar", onDelete)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Putar", Icons.Rounded.PlayArrow, { vm.playList(songs, 0, false) }, Modifier.weight(1f))
                GlassButton("Acak daftar", Icons.Rounded.Shuffle, { vm.shuffleAll(songs) }, Modifier.weight(1f))
            }
            if (onAddSongs != null) {
                Spacer(Modifier.height(10.dp))
                GlassButton("Tambah lagu", Icons.Rounded.Add, onAddSongs, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
          }
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    "Belum ada lagu di sini.",
                    color = SubText,
                    modifier = Modifier.fillMaxWidth().glass().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        songItems(songs, vm, favSet, actions, headerAbove = false, pad = 0.dp, onSelect = onSelect, base = 1)
    }
}

// =====================================================================
//  Menu samping (drawer)
// =====================================================================

@Composable
private fun DrawerContent(
    songCount: Int,
    onSettings: () -> Unit,
    onStyle: () -> Unit,
    onWidgets: () -> Unit,
    onPrivacy: () -> Unit,
    onBug: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFB9AC74)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Text("Musix", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text("Pustaka", color = SubText, fontSize = 12.sp)
            Text("$songCount lagu", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        DrawerItem(Icons.Rounded.Settings, "Setelan", onSettings)
        DrawerItem(Icons.Rounded.Palette, "Gaya pemutar", onStyle)
        DrawerItem(Icons.Rounded.Widgets, "Widget Layar utama", onWidgets)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Spacer(Modifier.height(8.dp))
        DrawerItem(Icons.Rounded.PrivacyTip, "Kebijakan Privasi", onPrivacy)
        DrawerItem(Icons.Rounded.BugReport, "Laporan bug", onBug)
        DrawerItem(Icons.Rounded.Info, "Tentang", onAbout)
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}


// =====================================================================
//  Tab baru: Baru, Radio, Koleksi ("Play Musix"), Cari
// =====================================================================

@Composable
private fun SectionTitle(text: String, small: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (small != null) Text(small, color = SubText, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TabHeader(
    title: String,
    subtitle: String?,
    onOpenDrawer: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 14.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = SubText, fontSize = 13.sp)
        }
        trailing()
        Spacer(Modifier.width(8.dp))
        GlassIconButton(Icons.Rounded.Menu, "Menu", onOpenDrawer)
    }
}


private class LibEntry(
    val key: String,
    val title: String,
    val art: Song?,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

/** Library: ubin besar "Local files" / "Downloaded songs", chip, dan kolom cari. */
@Composable
private fun LibraryTabScreen(
    vm: MusicViewModel,
    onOpenDrawer: () -> Unit,
    onDetail: (DetailKey) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    var chip by rememberSaveable { mutableStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    val names = listOf("Everything", "Playlists", "Artists", "Albums")
    val green = Color(0xFF2ECC5B)
    val blue = Color(0xFF30B6E8)
    val pink = Color(0xFFFF6F91)

    val fixed = listOf(
        LibEntry("f:fav", "Favorites", vm.songsOf(vm.favorites).firstOrNull(), Icons.Rounded.Favorite, pink) { onDetail(DetailKey.Favorites) },
        LibEntry("f:local", "Local files", null, Icons.Rounded.FolderOpen, green) { onDetail(DetailKey.All) },
        LibEntry("f:down", "Downloaded songs", null, Icons.Rounded.Download, blue) { onDetail(DetailKey.Downloads) },
    )
    val playlistEntries = vm.playlists.keys.map { name ->
        LibEntry("p:$name", name, vm.songsOf(vm.playlists[name].orEmpty()).firstOrNull(), Icons.Rounded.PlaylistPlay, Color.White) { onDetail(DetailKey.Playlist(name)) }
    }
    val artistEntries = remember(vm.allSongs) {
        vm.allSongs.groupBy { it.artist }.map { it.key to it.value }.sortedBy { it.first.lowercase() }
    }.map { (name, list) ->
        LibEntry("a:$name", name, list.firstOrNull(), Icons.Rounded.Person, Color.White) { onDetail(DetailKey.Artist(name)) }
    }
    val albumEntries = remember(vm.allSongs) {
        vm.allSongs.filter { it.album.isNotBlank() }.groupBy { it.album }.map { it.key to it.value }.sortedBy { it.first.lowercase() }
    }.map { (name, list) ->
        LibEntry("al:$name", name, list.firstOrNull(), Icons.Rounded.Album, Color.White) { onDetail(DetailKey.Album(name)) }
    }

    val needle = q.trim()
    val shown = when (chip) {
        0 -> fixed + playlistEntries
        1 -> listOf(LibEntry("p:new", "New playlist", null, Icons.Rounded.Add, Color.White, onNewPlaylist)) + playlistEntries
        2 -> artistEntries
        else -> albumEntries
    }.filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }

    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 220.dp)) {
        item {
            Row(
                Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 10.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${shown.size} items", color = SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("LIBRARY", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                }
                GlassIconButton(Icons.Rounded.Menu, "Menu", onOpenDrawer)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(names) { i, n ->
                        val sel = i == chip
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (sel) Color.White.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                                .clickable { chip = i }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        ) {
                            Text(n, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Rounded.Sort, "Sort: ${vm.sortLabel}", vm::cycleSort)
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = SubText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (q.isEmpty()) Text("Search your library", color = SubText, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                if (q.isNotEmpty()) {
                    Icon(Icons.Rounded.Close, "Clear", tint = Color.White, modifier = Modifier.size(20.dp).clickable { q = "" })
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        items(shown.chunked(2), key = { row -> row.first().key }) { pair ->
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                pair.forEach { e ->
                    Column(Modifier.weight(1f).clickable(onClick = e.onClick)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color(0xFF1F2F6E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (e.art != null) {
                                ArtImage(e.art, Modifier.fillMaxSize(), androidx.compose.ui.graphics.RectangleShape, seed = e.title)
                            } else {
                                Icon(e.icon, null, tint = e.tint, modifier = Modifier.size(64.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(e.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    "Nothing found.",
                    color = SubText,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Cari: riwayat pencarian saat kolom kosong, hasil saat mengetik. Kolom cari ada di bilah bawah. */
@Composable
fun SearchScreen(
    vm: MusicViewModel,
    actions: SongActions,
    query: String,
    onOpenDrawer: () -> Unit,
    onSelect: (List<Song>, Song) -> Unit,
) {
    val favSet = remember(vm.favorites) { vm.favorites.toSet() }
    val q = query.trim()
    val results = remember(q, vm.songs) {
        if (q.isEmpty()) emptyList()
        else vm.songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }.take(300)
    }
    val history = vm.songsOf(vm.searchHistory)

    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 240.dp)) {
        item { TabHeader("Cari", "Lagu di HP Anda", onOpenDrawer) }
        if (q.isEmpty()) {
            if (history.isNotEmpty()) {
                item {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("DARI PENCARIAN ANDA", color = SubText, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Text(
                            "Hapus semua", color = Accent, fontSize = 12.sp,
                            modifier = Modifier.clip(CircleShape).clickable { vm.clearSearchHistory() }.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                itemsIndexed(history, key = { _, s -> "h:" + s.uri }) { i, s ->
                    SongRow(
                        song = s,
                        current = vm.currentId == s.uri,
                        playing = vm.isPlaying,
                        favorite = s.uri in favSet,
                        shape = rowShape(i, history.size, false),
                        actions = actions,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onLongClick = { onSelect(history, s) },
                    ) { vm.playList(history, i) }
                }
            } else {
                item {
                    Text(
                        "Ketik judul lagu, artis, atau album di kolom bawah.",
                        color = SubText,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass().padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            if (results.isEmpty()) {
                item {
                    Text(
                        "Tidak ada hasil untuk \"$q\"",
                        color = SubText,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass().padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            itemsIndexed(results, key = { _, s -> s.uri }) { i, s ->
                SongRow(
                    song = s,
                    current = vm.currentId == s.uri,
                    playing = vm.isPlaying,
                    favorite = s.uri in favSet,
                    shape = rowShape(i, results.size, headerAbove = false),
                    actions = actions,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onLongClick = { onSelect(results, s) },
                ) {
                    vm.addSearchHistory(s)
                    vm.playList(results, i)
                }
            }
        }
    }
}

/** Bilah bawah di tab Cari: tombol beranda + kolom cari (menggantikan bilah tab). */
@Composable
private fun SearchBottomBar(query: String, onQuery: (String) -> Unit, focus: FocusRequester, onHome: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).glass(CircleShape, 1.2f).clickable(onClick = onHome),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Home, "Beranda", tint = Color.White) }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .glass(CircleShape, 1.2f)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Lagu, artis, album", color = SubText, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close, "Hapus", tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable { onQuery("") },
                )
            }
        }
    }
}
