package com.glassplayer.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.glassplayer.app.data.MediaRepository
import com.glassplayer.app.data.Song
import com.glassplayer.app.data.Store
import com.glassplayer.app.data.UNKNOWN_ARTIST
import com.glassplayer.app.data.toMediaItem
import com.glassplayer.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed interface DetailKey {
    data object Recent : DetailKey
    data object Newest : DetailKey
    data object All : DetailKey
    data object Favorites : DetailKey
    data class Playlist(val name: String) : DetailKey
    data class Album(val name: String) : DetailKey
    data class Artist(val name: String) : DetailKey
    data class Folder(val name: String) : DetailKey
}

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    // ---- Library state ----
    var allSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    private var byUri by mutableStateOf<Map<String, Song>>(emptyMap())
    private val external = LinkedHashMap<String, Song>()

    var favorites by mutableStateOf(store.loadList("fav"))
        private set
    var recents by mutableStateOf(store.loadList("recent"))
        private set
    var playlists by mutableStateOf(store.loadPlaylists())
        private set
    var sortMode by mutableIntStateOf(store.sortMode)
        private set
    var loading by mutableStateOf(false)
        private set

    val sortLabel: String get() = when (sortMode) { 0 -> "A–Z"; 1 -> "Terbaru"; else -> "Artis" }

    // ---- Player state ----
    var isPlaying by mutableStateOf(false)
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var sleepEndsAt by mutableLongStateOf(0L)
        private set

    // Antrian putar
    var queueIds by mutableStateOf<List<String>>(emptyList())
        private set
    var queueIndex by mutableIntStateOf(0)
        private set

    // Gaya pemutar (0 Bawaan, 1 Vinyl modern, 2 Vinyl klasik, 3 CD, 4 Kaset)
    var playerStyle by mutableIntStateOf(store.playerStyle)
        private set

    // Permintaan dari widget / notifikasi
    var pendingTab by mutableStateOf<Int?>(null)
    var pendingDelete by mutableStateOf<List<Song>?>(null)
        private set

    // Cadangan info lagu dari metadata MediaItem (dipakai layar kunci tanpa memindai ulang pustaka).
    private var metaSong by mutableStateOf<Song?>(null)
    var lockScreenEnabled by mutableStateOf(store.lockScreenEnabled)
        private set

    val currentSong: Song? get() = currentId?.let { byUri[it] } ?: metaSong
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun songFor(uri: String): Song? = byUri[uri] ?: metaSong?.takeIf { it.uri == uri }

    fun setLockScreen(on: Boolean) {
        lockScreenEnabled = on
        store.lockScreenEnabled = on
    }

    var playbackSpeed by mutableFloatStateOf(store.speed)
    var crossfadeSec by mutableIntStateOf(store.crossfadeSec)

    fun setSpeed(v: Float) {
        playbackSpeed = v
        store.speed = v
        controller?.setPlaybackSpeed(v)
    }

    fun setCrossfade(sec: Int) {
        crossfadeSec = sec
        store.crossfadeSec = sec
    }

    fun pauseIfPlaying() { controller?.pause() }

    fun applyPlayerStyle(i: Int) {
        playerStyle = i
        store.playerStyle = i
    }

    private var controller: MediaController? = null
    private var pendingExternal: Uri? = null
    private var pendingPlayUri: String? = null
    private var wantShuffleAll = false
    private var loadJob: Job? = null
    private var loadedOnce = false

    init {
        connect()
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    positionMs = c.currentPosition.coerceAtLeast(0L)
                    val d = c.duration
                    durationMs = if (d == C.TIME_UNSET || d <= 0) (currentSong?.durationMs ?: 0L) else d
                }
                if (sleepEndsAt != 0L && System.currentTimeMillis() > sleepEndsAt) sleepEndsAt = 0L
                delay(400)
            }
        }
    }

    // ---------- Koneksi ke PlaybackService ----------
    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = try { future.get() } catch (_: Exception) { return@addListener }
            controller = c
            c.setPlaybackSpeed(playbackSpeed)
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) =
                    sync(c, events.contains(Player.EVENT_TIMELINE_CHANGED))

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { markRecent(it) }
                }
            })
            sync(c, true)
            flushPending()
            flushActions()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(c: MediaController, timelineChanged: Boolean) {
        isPlaying = c.isPlaying
        val item = c.currentMediaItem
        currentId = item?.mediaId?.takeIf { it.isNotEmpty() }
        metaSong = item?.takeIf { it.mediaId.isNotEmpty() }?.let {
            Song(
                id = 0,
                uri = it.mediaId,
                title = it.mediaMetadata.title?.toString() ?: "Tanpa judul",
                artist = it.mediaMetadata.artist?.toString() ?: UNKNOWN_ARTIST,
                album = it.mediaMetadata.albumTitle?.toString() ?: "",
                albumId = 0,
                durationMs = 0,
                dateAdded = 0,
            )
        }
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        queueIndex = c.currentMediaItemIndex.coerceAtLeast(0)
        if (timelineChanged) queueIds = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
    }

    private fun markRecent(id: String) {
        recents = (listOf(id) + recents.filter { it != id }).take(30)
        store.saveList("recent", recents)
        viewModelScope.launch { WidgetUpdater.refresh(getApplication()) }
    }

    // ---------- Memuat lagu ----------
    /** Pindai hanya jika belum pernah/sedang dipindai (aman dipanggil berkali-kali). */
    fun ensureLoaded() {
        if (!loadedOnce && loadJob?.isActive != true) loadSongs()
    }

    /** Lagu yang baru dihapus: disembunyikan dari hasil pindai walau indeks Android belum diperbarui. */
    private val deletedUris = HashSet<String>()

    fun loadSongs() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            val scanned = MediaRepository.scan(getApplication()).filter { it.uri !in deletedUris }
            val known = scanned.mapTo(HashSet()) { it.uri }
            val extra = store.loadExtra().filter { it.uri !in known }
            allSongs = scanned + extra
            applySort()
            loading = false
            loadedOnce = true
            flushActions()
        }
    }

    private fun applySort() {
        songs = when (sortMode) {
            1 -> allSongs.sortedByDescending { it.dateAdded }
            2 -> allSongs.sortedBy { it.artist.lowercase() }
            else -> allSongs.sortedBy { it.title.lowercase() }
        }
        byUri = allSongs.associateBy { it.uri } + external
    }

    fun cycleSort() {
        sortMode = (sortMode + 1) % 3
        store.sortMode = sortMode
        applySort()
    }

    /** Tambah file audio manual (dari pemilih file) — untuk file yang tidak terdeteksi otomatis. */
    fun addFiles(uris: List<Uri>) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    try {
                        app.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {
                    }
                    MediaRepository.readFromUri(app, u)
                }
            }
            val current = store.loadExtra()
            val have = current.mapTo(HashSet()) { it.uri }
            store.saveExtra(current + added.filter { it.uri !in have })
            toast("${added.size} file ditambahkan")
            loadSongs()
        }
    }

    fun songsOf(uris: List<String>): List<Song> = uris.mapNotNull { byUri[it] }

    fun listFor(key: DetailKey): List<Song> = when (key) {
        DetailKey.Recent -> songsOf(recents)
        DetailKey.Newest -> allSongs.sortedByDescending { it.dateAdded }.take(100)
        DetailKey.All -> songs
        DetailKey.Favorites -> songsOf(favorites)
        is DetailKey.Playlist -> songsOf(playlists[key.name].orEmpty())
        is DetailKey.Album -> allSongs.filter { it.album == key.name }.sortedBy { it.title.lowercase() }
        is DetailKey.Artist -> allSongs.filter { it.artist == key.name }.sortedBy { it.title.lowercase() }
        is DetailKey.Folder -> allSongs.filter { it.folder == key.name }.sortedBy { it.title.lowercase() }
    }

    // ---------- Favorit & playlist ----------
    fun toggleFavorite(song: Song) {
        favorites = if (song.uri in favorites) favorites - song.uri else listOf(song.uri) + favorites
        store.saveList("fav", favorites)
    }

    fun addFavorites(list: List<Song>) {
        val add = list.map { it.uri }.filter { it !in favorites }
        favorites = add + favorites
        store.saveList("fav", favorites)
        toast("${list.size} lagu ditambahkan ke favorit")
    }

    fun createPlaylist(name: String): Boolean {
        val n = name.trim()
        if (n.isBlank() || playlists.containsKey(n)) return false
        playlists = playlists + (n to emptyList())
        store.savePlaylists(playlists)
        return true
    }

    fun addToPlaylist(name: String, song: Song) {
        val list = playlists[name] ?: return
        if (song.uri in list) { toast("Sudah ada di \"$name\""); return }
        playlists = playlists + (name to (list + song.uri))
        store.savePlaylists(playlists)
        toast("Ditambahkan ke \"$name\"")
    }

    fun addManyToPlaylist(name: String, list: List<Song>) {
        val cur = playlists[name] ?: return
        val add = list.map { it.uri }.filter { it !in cur }.distinct()
        playlists = playlists + (name to (cur + add))
        store.savePlaylists(playlists)
        toast(if (add.isEmpty()) "Sudah ada di \"$name\"" else "${add.size} lagu ditambahkan ke \"$name\"")
    }

    fun removeFromPlaylist(name: String, song: Song) {
        val list = playlists[name] ?: return
        playlists = playlists + (name to list.filter { it != song.uri })
        store.savePlaylists(playlists)
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        store.savePlaylists(playlists)
    }

    // ---------- Hapus file ----------
    /** Lokasi file asli, dicatat sebelum dihapus supaya bisa dipastikan benar-benar hilang. */
    private val pathOf = HashMap<String, String>()

    fun requestDelete(list: List<Song>) {
        if (list.isEmpty()) return
        val cr = getApplication<Application>().contentResolver
        list.forEach { s ->
            try {
                @Suppress("DEPRECATION")
                cr.query(Uri.parse(s.uri), arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0)?.let { p -> pathOf[s.uri] = p }
                }
            } catch (_: Exception) {
            }
        }
        pendingDelete = list
    }

    fun clearPendingDelete() {
        pendingDelete = null
    }

    /** Android 10 ke bawah: hapus langsung. */
    fun deleteDirect(list: List<Song>) {
        val cr = getApplication<Application>().contentResolver
        var failed = false
        list.forEach {
            try { cr.delete(Uri.parse(it.uri), null, null) } catch (_: Exception) { failed = true }
        }
        if (failed) toast("Sebagian lagu tidak bisa dihapus")
        onDeleteDone(list)
    }

    /** Dipanggil setelah file benar-benar terhapus (atau dikeluarkan dari pustaka). */
    fun onDeleteDone(list: List<Song>) {
        val gone = list.mapTo(HashSet()) { it.uri }
        deletedUris.addAll(gone)
        favorites = favorites.filter { it !in gone }
        store.saveList("fav", favorites)
        recents = recents.filter { it !in gone }
        store.saveList("recent", recents)
        playlists = playlists.mapValues { (_, v) -> v.filter { it !in gone } }
        store.savePlaylists(playlists)
        store.saveExtra(store.loadExtra().filter { it.uri !in gone })
        external.keys.removeAll(gone)
        allSongs = allSongs.filter { it.uri !in gone }
        applySort()
        controller?.let { c ->
            for (i in c.mediaItemCount - 1 downTo 0) {
                if (c.getMediaItemAt(i).mediaId in gone) c.removeMediaItem(i)
            }
        }
        // Pastikan file asli benar-benar hilang dari penyimpanan (bukan hanya dari indeks).
        val paths = gone.mapNotNull { pathOf.remove(it) }
        viewModelScope.launch {
            val left = withContext(Dispatchers.IO) {
                var n = 0
                paths.forEach { p ->
                    val f = java.io.File(p)
                    if (f.exists() && !f.delete()) n++
                }
                if (paths.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
                }
                n
            }
            if (left > 0) toast("$left file belum terhapus dari penyimpanan, hapus lewat pengelola file")
        }
        toast("${list.size} lagu dihapus")
        loadSongs()
        // Indeks Android kadang telat memperbarui diri; pindai sekali lagi setelah jeda singkat.
        viewModelScope.launch {
            kotlinx.coroutines.delay(2500)
            loadSongs()
        }
    }

    // ---------- Kontrol pemutar ----------
    fun playList(list: List<Song>, start: Int, shuffleMode: Boolean? = null) {
        if (list.isEmpty()) return
        val c = controller ?: run { toast("Pemutar belum siap, coba lagi"); return }
        shuffleMode?.let { c.shuffleModeEnabled = it }
        c.setMediaItems(list.map { it.toMediaItem() }, start.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        playList(list, Random.nextInt(list.size), true)
    }

    fun playNext(song: Song) = playNextMany(listOf(song))

    fun playNextMany(list: List<Song>) {
        if (list.isEmpty()) return
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playList(list, 0)
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, list.map { it.toMediaItem() })
            toast("Diputar berikutnya")
        }
    }

    fun addToQueue(list: List<Song>) {
        if (list.isEmpty()) return
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playList(list, 0)
        } else {
            c.addMediaItems(list.map { it.toMediaItem() })
            toast("Ditambahkan ke antrian")
        }
    }

    fun removeCurrentFromQueue() {
        val c = controller ?: return
        if (c.mediaItemCount > 0) c.removeMediaItem(c.currentMediaItemIndex)
    }

    fun removeFromQueueAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---------- Permintaan dari luar (file manager / widget) ----------
    /** Buka file audio dari aplikasi lain (file manager, WhatsApp, dll). */
    fun playExternal(uri: Uri) {
        pendingExternal = uri
        flushPending()
    }

    private fun flushPending() {
        if (controller == null) return
        val u = pendingExternal ?: return
        pendingExternal = null
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { MediaRepository.readFromUri(getApplication(), u) } ?: return@launch
            external[s.uri] = s
            byUri = byUri + (s.uri to s)
            playList(listOf(s), 0)
        }
    }

    /** Dari widget: putar lagu ini di dalam pustaka (antrian = seluruh pustaka). */
    fun playUriInLibrary(uri: String) {
        pendingPlayUri = uri
        flushActions()
    }

    /** Dari widget "Rekomendasi": acak seluruh pustaka. */
    fun requestShuffleAll() {
        wantShuffleAll = true
        flushActions()
    }

    /** Dari widget "Cari dan putar": buka tab cari. */
    fun requestSearchTab() {
        pendingTab = 1
    }

    private fun flushActions() {
        if (controller == null) return
        if (wantShuffleAll && songs.isNotEmpty()) {
            wantShuffleAll = false
            shuffleAll(songs)
        }
        val u = pendingPlayUri
        if (u != null && allSongs.isNotEmpty()) {
            pendingPlayUri = null
            val idx = songs.indexOfFirst { it.uri == u }
            if (idx >= 0) playList(songs, idx) else playExternal(Uri.parse(u))
        }
    }

    // ---------- Timer tidur ----------
    fun setSleepTimer(minutes: Int) {
        val c = controller ?: return
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        c.sendCustomCommand(
            SessionCommand(CMD_SLEEP, Bundle.EMPTY),
            Bundle().apply { putInt("min", minutes) },
        )
        toast(if (minutes > 0) "Timer tidur: $minutes menit" else "Timer tidur dimatikan")
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
