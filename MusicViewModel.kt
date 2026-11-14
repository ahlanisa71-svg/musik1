package com.glassplayer.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.glassplayer.app.data.UNKNOWN_ARTIST
import com.glassplayer.app.data.Song
import com.glassplayer.app.data.Store
import com.glassplayer.app.data.toMediaItem
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

    // Cadangan info lagu dari metadata MediaItem (dipakai layar kunci tanpa memindai ulang pustaka).
    private var metaSong by mutableStateOf<Song?>(null)
    var lockScreenEnabled by mutableStateOf(store.lockScreenEnabled)
        private set

    val currentSong: Song? get() = currentId?.let { byUri[it] } ?: metaSong

    fun setLockScreen(on: Boolean) {
        lockScreenEnabled = on
        store.lockScreenEnabled = on
    }
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    private var controller: MediaController? = null
    private var pendingExternal: Uri? = null
    private var loadJob: Job? = null

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
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync(c)
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { markRecent(it) }
                }
            })
            sync(c)
            flushPending()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(c: MediaController) {
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
    }

    private fun markRecent(id: String) {
        recents = (listOf(id) + recents.filter { it != id }).take(30)
        store.saveList("recent", recents)
    }

    // ---------- Memuat lagu ----------
    private var loadedOnce = false

    /** Pindai hanya jika belum pernah/sedang dipindai (aman dipanggil berkali-kali). */
    fun ensureLoaded() {
        if (!loadedOnce && loadJob?.isActive != true) loadSongs()
    }

    fun loadSongs() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            val scanned = MediaRepository.scan(getApplication())
            val known = scanned.mapTo(HashSet()) { it.uri }
            val extra = store.loadExtra().filter { it.uri !in known }
            allSongs = scanned + extra
            applySort()
            loading = false
            loadedOnce = true
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
    }

    // ---------- Favorit & playlist ----------
    fun toggleFavorite(song: Song) {
        favorites = if (song.uri in favorites) favorites - song.uri else listOf(song.uri) + favorites
        store.saveList("fav", favorites)
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

    fun removeFromPlaylist(name: String, song: Song) {
        val list = playlists[name] ?: return
        playlists = playlists + (name to list.filter { it != song.uri })
        store.savePlaylists(playlists)
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        store.savePlaylists(playlists)
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

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playList(listOf(song), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
            toast("Diputar berikutnya")
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
