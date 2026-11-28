package com.glassplayer.app

import android.app.Application
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.glassplayer.app.data.AudioFx
import com.glassplayer.app.data.MediaRepository
import com.glassplayer.app.data.Song
import com.glassplayer.app.data.Store
import com.glassplayer.app.download.DownloadTaskState
import com.glassplayer.app.download.YouTubeDownloader
import com.glassplayer.app.lyrics.LyricsDocument
import com.glassplayer.app.lyrics.LyricsEngine
import com.glassplayer.app.ui.AppLang
import com.glassplayer.app.ui.AppTheme
import com.glassplayer.app.youtube.YouTubeStreamEngine
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val appCtx = application.applicationContext
    private val store = Store(appCtx)
    private var controller: MediaController? = null
    private var loadedOnce = false
    private var sleepJob: Job? = null
    private var onlineSearchJob: Job? = null
    private var onlineSearchMoreJob: Job? = null
    private var radioSearchJob: Job? = null
    private var homeSearchJob: Job? = null
    private var onlineSearchSession: YouTubeApiSearch.TrackSearchSession? = null
    private var playbackRequestGeneration = 0
    private val indexedDownloadIds = mutableSetOf<String>()
    private var pendingOnlinePlayback: Pair<MediaItemModel, List<MediaItemModel>>? = null

    private val queueList = mutableStateListOf<Song>()
    private val playCounts = mutableStateMapOf<String, Int>()
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    val allSongs = mutableStateListOf<Song>()
    val songs: List<Song>
        get() = sortedSongs(allSongs)

    val favorites = mutableStateListOf<String>()
    val recents = mutableStateListOf<String>()
    val playlists = mutableStateMapOf<String, List<String>>()
    val searchHistory = mutableStateListOf<String>()

    var loading by mutableStateOf(false)
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var playbackSpeed by mutableStateOf(store.speed)
        private set
    var crossfadeSec by mutableIntStateOf(store.crossfadeSec)
        private set
    var playerStyle by mutableIntStateOf(store.playerStyle)
        private set
    var radioLayout by mutableIntStateOf(store.radioLayout)
        private set
    var lockScreenEnabled by mutableStateOf(store.lockScreenEnabled)
        private set
    var sleepEndsAt by mutableLongStateOf(0L)
        private set
    var pendingDelete by mutableStateOf<List<Song>?>(null)
        private set
    var pendingTab by mutableStateOf<Int?>(null)
    var needAllFiles by mutableStateOf(false)
    var playCountsVersion by mutableIntStateOf(0)
        private set
    private var sortMode by mutableIntStateOf(store.sortMode)

    val sortLabel: String
        get() = when (sortMode) {
            1 -> "Terbaru"
            else -> "A–Z"
        }

    // StateFlow compatibility for NowPlaying / YouTube / Lyrics
    private val _selectedTab = MutableStateFlow(AppTab.SONGS)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _localSongs = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val localSongs: StateFlow<List<MediaItemModel>> = _localSongs.asStateFlow()

    private val _localVideos = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val localVideos: StateFlow<List<MediaItemModel>> = _localVideos.asStateFlow()

    private val _downloadedItems = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val downloadedItems: StateFlow<List<MediaItemModel>> = _downloadedItems.asStateFlow()

    private val _onlineResults = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val onlineResults: StateFlow<List<MediaItemModel>> = _onlineResults.asStateFlow()

    private val _radioResults = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val radioResults: StateFlow<List<MediaItemModel>> = _radioResults.asStateFlow()

    private val _homeOnlineTracks = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val homeOnlineTracks: StateFlow<List<MediaItemModel>> = _homeOnlineTracks.asStateFlow()

    private val _isSearchingHomeOnline = MutableStateFlow(false)
    val isSearchingHomeOnline: StateFlow<Boolean> = _isSearchingHomeOnline.asStateFlow()

    private val _isSearchingRadio = MutableStateFlow(false)
    val isSearchingRadio: StateFlow<Boolean> = _isSearchingRadio.asStateFlow()

    private val _radioSearchError = MutableStateFlow<String?>(null)
    val radioSearchError: StateFlow<String?> = _radioSearchError.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _onlineSearchError = MutableStateFlow<String?>(null)
    val onlineSearchError: StateFlow<String?> = _onlineSearchError.asStateFlow()

    private val _isLoadingMoreOnline = MutableStateFlow(false)
    val isLoadingMoreOnline: StateFlow<Boolean> = _isLoadingMoreOnline.asStateFlow()

    private val _hasMoreOnline = MutableStateFlow(false)
    val hasMoreOnline: StateFlow<Boolean> = _hasMoreOnline.asStateFlow()

    private val _onlineSearchMoreError = MutableStateFlow<String?>(null)
    val onlineSearchMoreError: StateFlow<String?> = _onlineSearchMoreError.asStateFlow()

    private val _searchVideosOnly = MutableStateFlow(false)
    val searchVideosOnly: StateFlow<Boolean> = _searchVideosOnly.asStateFlow()

    private val _currentItem = MutableStateFlow<MediaItemModel?>(null)
    val currentItem: StateFlow<MediaItemModel?> = _currentItem.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _positionFlow = MutableStateFlow(0L)
    val positionFlow: StateFlow<Long> = _positionFlow.asStateFlow()

    private val _durationFlow = MutableStateFlow(0L)
    val durationFlow: StateFlow<Long> = _durationFlow.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _currentLyrics = MutableStateFlow<LyricsDocument?>(null)
    val currentLyrics: StateFlow<LyricsDocument?> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    val videoMode: StateFlow<Boolean> = YouTubeStreamEngine.videoMode
    val activeDownloads: StateFlow<Map<String, DownloadTaskState>> = YouTubeDownloader.activeDownloads

    init {
        AppLang.load(appCtx)
        AppTheme.load(appCtx)
        AudioFx.load(appCtx)

        favorites.addAll(store.loadList("favorites"))
        recents.addAll(store.loadList("recents"))
        searchHistory.addAll(store.loadList("search_history"))
        playlists.putAll(store.loadPlaylists())

        connectPlayer()
        startPositionTicker()
        viewModelScope.launch {
            YouTubeDownloader.activeDownloads.collect { tasks ->
                val hasNewDownloads = tasks.values.any { task ->
                    task.isCompleted && indexedDownloadIds.add(task.videoId)
                }
                if (hasNewDownloads && loadedOnce) loadSongs()
            }
        }
        val audioPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (appCtx.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) {
            ensureLoaded()
        }
    }

    fun getPlayer(): Player? = controller

    private fun connectPlayer() {
        val token = SessionToken(appCtx, ComponentName(appCtx, PlaybackService::class.java))
        val future = MediaController.Builder(appCtx, token).buildAsync()
        future.addListener({
            runCatching {
                val mc = future.get()
                controller = mc
                _player.value = mc
                mc.setPlaybackSpeed(playbackSpeed)
                mc.repeatMode = repeatMode
                isPlaying = mc.isPlaying
                _isPlayingFlow.value = mc.isPlaying
                repeatMode = mc.repeatMode
                mc.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        _isPlayingFlow.value = playing
                        store.widgetPlaying = playing
                    }

                    override fun onRepeatModeChanged(mode: Int) {
                        repeatMode = mode
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val id = mediaItem?.mediaId ?: return
                        val matchedItem = findItemById(id)
                        val matchedSong = (queueList + allSongs).firstOrNull { it.uri == id || it.id.toString() == id }
                        if (matchedSong != null) {
                            syncCurrentSong(matchedSong)
                        } else if (matchedItem?.isYouTube == true) {
                            currentSong = Song(
                                id = matchedItem.id.hashCode().toLong(),
                                uri = matchedItem.uri.toString(),
                                title = matchedItem.title,
                                artist = matchedItem.artist,
                                album = matchedItem.album,
                                durationMs = matchedItem.durationMs,
                                folder = "YouTube",
                            )
                            currentId = matchedItem.uri.toString()
                            durationMs = matchedItem.durationMs
                        }
                        if (matchedItem != null && matchedItem.id != _currentItem.value?.id) {
                            _currentItem.value = matchedItem
                            loadLyricsFor(matchedItem)
                        }
                    }
                })
                pendingOnlinePlayback?.let { pending ->
                    pendingOnlinePlayback = null
                    playItem(pending.first, pending.second)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                controller?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    positionMs = pos
                    _positionFlow.value = pos
                    if (dur > 0L) {
                        durationMs = dur
                        _durationFlow.value = dur
                    }
                    isPlaying = player.isPlaying
                    _isPlayingFlow.value = player.isPlaying
                }
                delay(250L)
            }
        }
    }

    fun ensureLoaded() {
        if (!loadedOnce) {
            loadedOnce = true
            loadSongs()
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            loadedOnce = true
            loading = true
            val scanned = MediaRepository.scan(appCtx)
            val manual = store.loadExtra()
            val downloaded = withContext(Dispatchers.IO) {
                YouTubeDownloader.downloadedAudioFiles(appCtx).mapNotNull { file ->
                    MediaRepository.readFromUri(appCtx, Uri.fromFile(file))?.copy(
                        album = "MusixDownloads",
                        folder = "MusixDownloads",
                    )
                }
            }
            val merged = (scanned + manual + downloaded).distinctBy { it.uri }
            allSongs.clear()
            allSongs.addAll(merged)
            _localSongs.value = merged.map { it.toMediaItemModel() }
            _downloadedItems.value = merged.filter {
                it.uri.contains("MusixDownloads", ignoreCase = true) ||
                    it.folder.contains("MusixDownloads", ignoreCase = true) ||
                    it.album.equals("MusixDownloads", ignoreCase = true)
            }.map { it.toMediaItemModel() }
            if (currentSong == null) {
                val lastUri = store.widgetUri
                val initial = merged.firstOrNull { it.uri == lastUri } ?: merged.firstOrNull()
                if (initial != null) {
                    currentSong = initial
                    currentId = initial.uri
                    durationMs = initial.durationMs
                }
            }
            loading = false
        }
    }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { MediaRepository.readFromUri(appCtx, it) }
            }
            if (added.isNotEmpty()) {
                val currentExtra = store.loadExtra().toMutableList()
                for (s in added) {
                    if (currentExtra.none { it.uri == s.uri }) currentExtra.add(s)
                }
                store.saveExtra(currentExtra)
                loadSongs()
            }
        }
    }

    private fun sortedSongs(list: List<Song>): List<Song> = when (sortMode) {
        1 -> list.sortedByDescending { it.dateAdded }
        else -> list.sortedBy { it.title.lowercase() }
    }

    fun cycleSort() {
        sortMode = (sortMode + 1) % 2
        store.sortMode = sortMode
    }

    fun songsOf(uris: List<String>): List<Song> {
        val map = allSongs.associateBy { it.uri }
        return uris.mapNotNull { map[it] }
    }

    fun listFor(key: DetailKey): List<Song> = when (key) {
        DetailKey.Recent -> songsOf(recents)
        DetailKey.Newest -> allSongs.sortedByDescending { it.dateAdded }
        DetailKey.All -> songs
        DetailKey.Favorites -> songsOf(favorites)
        DetailKey.Downloads -> allSongs.filter {
            it.uri.contains("MusixDownloads", ignoreCase = true) ||
                it.folder.contains("MusixDownloads", ignoreCase = true) ||
                it.album.equals("MusixDownloads", ignoreCase = true)
        }
        is DetailKey.Playlist -> songsOf(playlists[key.name].orEmpty())
        is DetailKey.Album -> allSongs.filter { it.album == key.name }
        is DetailKey.Artist -> allSongs.filter { it.artist == key.name }
        is DetailKey.Folder -> allSongs.filter { it.folder == key.name }
        is DetailKey.Mix -> when (key.id) {
            "short" -> allSongs.filter { it.durationMs in 1 until 180_000L }
            "mid" -> allSongs.filter { it.durationMs in 180_000L..300_000L }
            else -> allSongs.filter { it.durationMs > 300_000L }
        }
    }

    fun popular(limit: Int = 8): List<Song> {
        if (allSongs.isEmpty()) return emptyList()
        return allSongs.sortedByDescending { playCounts[it.uri] ?: 0 }.take(limit)
    }

    private fun syncCurrentSong(song: Song) {
        currentSong = song
        currentId = song.uri
        durationMs = song.durationMs
        positionMs = 0L
        _currentItem.value = song.toMediaItemModel()

        recents.remove(song.uri)
        recents.add(0, song.uri)
        if (recents.size > 50) {
            while (recents.size > 50) recents.removeAt(recents.lastIndex)
        }
        store.saveList("recents", recents.toList())

        playCounts[song.uri] = (playCounts[song.uri] ?: 0) + 1
        playCountsVersion++

        store.widgetTitle = song.title
        store.widgetArtist = song.artist
        store.widgetUri = song.uri

        loadLyricsFor(song.toMediaItemModel())
    }

    fun playList(list: List<Song>, startIndex: Int = 0) {
        if (list.isEmpty()) return
        pendingOnlinePlayback = null
        val idx = startIndex.coerceIn(0, list.lastIndex)
        queueList.clear()
        queueList.addAll(list)
        val target = list[idx]
        syncCurrentSong(target)

        val mc = controller ?: return
        val items = list.map { s ->
            MediaItem.Builder()
                .setMediaId(s.uri)
                .setUri(Uri.parse(s.uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .build()
                )
                .build()
        }
        mc.setMediaItems(items, idx, 0L)
        mc.playbackParameters = mc.playbackParameters.withSpeed(playbackSpeed)
        mc.prepare()
        mc.play()
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        playList(list.shuffled(), 0)
    }

    fun togglePlay() {
        val mc = controller
        if (mc == null) {
            currentSong?.let { playList(listOf(it), 0) }
            return
        }
        if (mc.mediaItemCount == 0) {
            val fallback = currentSong ?: songs.firstOrNull()
            if (fallback != null) playList(songs.ifEmpty { listOf(fallback) }, songs.indexOf(fallback).coerceAtLeast(0))
            return
        }
        if (mc.isPlaying) mc.pause() else mc.play()
    }

    fun pauseIfPlaying() {
        controller?.let { if (it.isPlaying) it.pause() }
    }

    fun next() {
        controller?.let {
            if (it.hasNextMediaItem()) it.seekToNextMediaItem()
            else if (queueList.isNotEmpty()) playList(queueList, 0)
        }
    }

    fun previous() {
        controller?.let {
            if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
            else it.seekTo(0L)
        }
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = repeatMode
    }

    fun seekTo(ms: Long) {
        val clamped = ms.coerceAtLeast(0L)
        controller?.seekTo(clamped)
        positionMs = clamped
        _positionFlow.value = clamped
    }

    fun toggleFavorite(song: Song) {
        if (song.uri in favorites) favorites.remove(song.uri) else favorites.add(0, song.uri)
        store.saveList("favorites", favorites.toList())
    }

    fun addFavorites(list: List<Song>) {
        list.forEach { s ->
            if (s.uri !in favorites) favorites.add(0, s.uri)
        }
        store.saveList("favorites", favorites.toList())
    }

    fun playNext(song: Song) {
        playNextMany(listOf(song))
    }

    fun playNextMany(list: List<Song>) {
        if (list.isEmpty()) return
        val mc = controller ?: return
        val insertIdx = if (mc.mediaItemCount > 0) (mc.currentMediaItemIndex + 1).coerceAtMost(mc.mediaItemCount) else 0
        list.forEachIndexed { offset, s ->
            val mi = MediaItem.Builder()
                .setMediaId(s.uri)
                .setUri(Uri.parse(s.uri))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
                .build()
            mc.addMediaItem(insertIdx + offset, mi)
        }
    }

    fun addToQueue(list: List<Song>) {
        if (list.isEmpty()) return
        queueList.addAll(list)
        val mc = controller ?: return
        list.forEach { s ->
            val mi = MediaItem.Builder()
                .setMediaId(s.uri)
                .setUri(Uri.parse(s.uri))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
                .build()
            mc.addMediaItem(mi)
        }
    }

    fun createPlaylist(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || clean in playlists) return false
        playlists[clean] = emptyList()
        store.savePlaylists(playlists.toMap())
        return true
    }

    fun deletePlaylist(name: String) {
        playlists.remove(name)
        store.savePlaylists(playlists.toMap())
    }

    fun addManyToPlaylist(name: String, list: List<Song>) {
        val existing = playlists[name].orEmpty().toMutableList()
        for (s in list) {
            if (s.uri !in existing) existing.add(s.uri)
        }
        playlists[name] = existing
        store.savePlaylists(playlists.toMap())
    }

    fun removeFromPlaylist(name: String, song: Song) {
        val updated = playlists[name].orEmpty().filterNot { it == song.uri }
        playlists[name] = updated
        store.savePlaylists(playlists.toMap())
    }

    fun requestDelete(list: List<Song>) {
        if (list.isNotEmpty()) pendingDelete = list
    }

    fun clearPendingDelete() {
        pendingDelete = null
    }

    fun onDeleteDone(deleted: List<Song>) {
        val deletedUris = deleted.map { it.uri }.toSet()
        allSongs.removeAll { it.uri in deletedUris }
        favorites.removeAll { it in deletedUris }
        recents.removeAll { it in deletedUris }
        store.saveList("favorites", favorites.toList())
        store.saveList("recents", recents.toList())
        val updatedExtra = store.loadExtra().filterNot { it.uri in deletedUris }
        store.saveExtra(updatedExtra)
        if (currentId in deletedUris) {
            currentSong = allSongs.firstOrNull()
            currentId = currentSong?.uri
        }
    }

    fun retryLeftovers() {
        if (loadedOnce) loadSongs()
    }

    fun setSpeed(spd: Float) {
        playbackSpeed = spd
        store.speed = spd
        controller?.setPlaybackSpeed(spd)
    }

    fun setCrossfade(sec: Int) {
        crossfadeSec = sec
        store.crossfadeSec = sec
    }

    fun applyPlayerStyle(style: Int) {
        playerStyle = style
        store.playerStyle = style
    }

    fun updateRadioLayout(layout: Int) {
        radioLayout = layout.coerceIn(0, 2)
        store.radioLayout = radioLayout
    }

    fun setLockScreen(enabled: Boolean) {
        lockScreenEnabled = enabled
        store.lockScreenEnabled = enabled
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepEndsAt = 0L
            return
        }
        val target = System.currentTimeMillis() + minutes * 60_000L
        sleepEndsAt = target
        sleepJob = viewModelScope.launch {
            while (System.currentTimeMillis() < target) {
                delay(1000L)
            }
            sleepEndsAt = 0L
            pauseIfPlaying()
        }
    }

    fun addSearchHistory(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        searchHistory.remove(clean)
        searchHistory.add(0, clean)
        while (searchHistory.size > 15) searchHistory.removeAt(searchHistory.lastIndex)
        store.saveList("search_history", searchHistory.toList())
    }

    fun clearSearchHistory() {
        searchHistory.clear()
        store.saveList("search_history", emptyList())
    }

    // Online / YouTube / Lyrics / Video helpers
    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setSearchVideosOnly(videosOnly: Boolean) {
        _searchVideosOnly.value = videosOnly
    }

    fun searchYouTube(query: String) {
        onlineSearchJob?.cancel()
        onlineSearchMoreJob?.cancel()
        onlineSearchMoreJob = null
        onlineSearchSession = null
        if (query.isBlank()) {
            clearOnlineSearch()
            return
        }
        _onlineResults.value = emptyList()
        _onlineSearchError.value = null
        _onlineSearchMoreError.value = null
        _hasMoreOnline.value = false
        _isLoadingMoreOnline.value = false
        onlineSearchJob = viewModelScope.launch {
            val searchJob = coroutineContext[Job]
            _isSearchingOnline.value = true
            try {
                val session = YouTubeApiSearch.searchSession(
                    query,
                    videosOnly = _searchVideosOnly.value,
                )
                var initialResults = session.initialResults
                var skippedPages = 0
                while (initialResults.isEmpty() && session.hasMore && skippedPages < 3) {
                    initialResults = session.loadNextPage()
                    skippedPages++
                }
                if (onlineSearchJob !== searchJob) return@launch
                onlineSearchSession = session
                _onlineResults.value = initialResults
                _hasMoreOnline.value = session.hasMore
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _onlineResults.value = emptyList()
                _onlineSearchError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Pencarian YouTube gagal. Periksa koneksi internet."
            } finally {
                if (onlineSearchJob === searchJob) {
                    _isSearchingOnline.value = false
                }
            }
        }
    }

    fun loadMoreYouTubeSearch() {
        val session = onlineSearchSession ?: return
        if (_isSearchingOnline.value || _isLoadingMoreOnline.value || !_hasMoreOnline.value) return
        onlineSearchMoreJob?.cancel()
        onlineSearchMoreJob = viewModelScope.launch {
            val searchJob = coroutineContext[Job]
            _isLoadingMoreOnline.value = true
            _onlineSearchMoreError.value = null
            try {
                var additions = emptyList<MediaItemModel>()
                var attempts = 0
                while (additions.isEmpty() && session.hasMore && attempts < 3) {
                    val nextPage = session.loadNextPage()
                    val existingIds = _onlineResults.value.mapTo(mutableSetOf()) { it.id }
                    additions = nextPage.filter { existingIds.add(it.id) }
                    attempts++
                }
                if (onlineSearchSession !== session) return@launch
                _onlineResults.value = _onlineResults.value + additions
                _hasMoreOnline.value = session.hasMore
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (onlineSearchSession === session) {
                    _onlineSearchMoreError.value = e.message?.takeIf { it.isNotBlank() }
                        ?: "Tidak bisa memuat hasil berikutnya. Coba lagi."
                }
            } finally {
                if (onlineSearchMoreJob === searchJob) {
                    _isLoadingMoreOnline.value = false
                }
            }
        }
    }

    fun clearOnlineSearch() {
        onlineSearchJob?.cancel()
        onlineSearchJob = null
        onlineSearchMoreJob?.cancel()
        onlineSearchMoreJob = null
        onlineSearchSession = null
        _onlineResults.value = emptyList()
        _onlineSearchError.value = null
        _onlineSearchMoreError.value = null
        _hasMoreOnline.value = false
        _isLoadingMoreOnline.value = false
        _isSearchingOnline.value = false
    }

    fun searchRadioMood(mood: String) {
        radioSearchJob?.cancel()
        _radioResults.value = emptyList()
        _radioSearchError.value = null
        _isSearchingRadio.value = true
        radioSearchJob = viewModelScope.launch {
            val searchJob = coroutineContext[Job]
            try {
                _radioResults.value = YouTubeApiSearch.searchTracks("$mood music", videosOnly = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _radioSearchError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Radio online gagal dimuat. Periksa koneksi internet."
            } finally {
                if (radioSearchJob === searchJob) {
                    _isSearchingRadio.value = false
                }
            }
        }
    }

    fun loadHomeOnlineTracks() {
        if (_homeOnlineTracks.value.isNotEmpty() || _isSearchingHomeOnline.value) return
        homeSearchJob?.cancel()
        homeSearchJob = viewModelScope.launch {
            val searchJob = coroutineContext[Job]
            _isSearchingHomeOnline.value = true
            try {
                _homeOnlineTracks.value = YouTubeApiSearch.searchTracks("popular songs music")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MusicViewModel", "Gagal memuat pilihan online beranda.", e)
                _homeOnlineTracks.value = emptyList()
            } finally {
                if (homeSearchJob === searchJob) {
                    _isSearchingHomeOnline.value = false
                }
            }
        }
    }

    fun clearHomeOnlineTracks() {
        homeSearchJob?.cancel()
        homeSearchJob = null
        _homeOnlineTracks.value = emptyList()
        _isSearchingHomeOnline.value = false
    }

    fun playYouTubeVideo(id: String, title: String, artist: String, thumbnail: String) {
        val item = MediaItemModel(
            id = "yt_$id",
            title = title,
            artist = artist,
            uri = Uri.parse(YouTubeStreamEngine.buildUriString(id, isVideo = true)),
            artworkUri = Uri.parse(thumbnail),
            mediaType = MediaType.YOUTUBE_VIDEO,
            youtubeVideoId = id,
        )
        playItem(item)
    }

    fun playItem(item: MediaItemModel, queue: List<MediaItemModel> = listOf(item)) {
        _currentItem.value = item
        loadLyricsFor(item)
        YouTubeStreamEngine.setVideoMode(item.mediaType == MediaType.YOUTUBE_VIDEO)
        if (item.isYouTube) {
            currentSong = Song(
                id = item.id.hashCode().toLong(),
                uri = item.uri.toString(),
                title = item.title,
                artist = item.artist,
                album = item.album,
                durationMs = item.durationMs,
                folder = "YouTube",
            )
            currentId = item.uri.toString()
            durationMs = item.durationMs
        }
        val mc = controller ?: run {
            pendingOnlinePlayback = item to queue
            return
        }
        pendingOnlinePlayback = null
        val startIndex = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        val mediaItems = queue.map { m ->
            MediaItem.Builder()
                .setMediaId(m.id)
                .setUri(m.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(m.title)
                        .setArtist(m.artist)
                        .setArtworkUri(m.artworkUri)
                        .build()
                )
                .build()
        }
        val generation = ++playbackRequestGeneration
        val play = {
            if (generation == playbackRequestGeneration && controller === mc) {
                mc.setMediaItems(mediaItems, startIndex, 0L)
                mc.prepare()
                mc.play()
            }
        }
        if (item.mediaType == MediaType.YOUTUBE_VIDEO) {
            val videoId = item.youtubeVideoId
                ?: YouTubeStreamEngine.extractVideoId(item.uri.toString())
                ?: return
            viewModelScope.launch {
                try {
                    YouTubeStreamEngine.resolve(videoId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (generation == playbackRequestGeneration) {
                        android.widget.Toast.makeText(
                            appCtx,
                            "Gagal memuat stream video: ${e.message ?: "coba lagi"}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@launch
                }
                play()
            }
        } else {
            play()
        }
    }

    fun togglePlayPause() = togglePlay()

    fun skipNext() = next()

    fun skipPrevious() = previous()

    fun toggleLyrics() {
        val next = !_showLyrics.value
        _showLyrics.value = next
        if (next && _currentLyrics.value == null) {
            _currentItem.value?.let { loadLyricsFor(it) }
        }
    }

    fun toggleVideoMode() {
        val item = _currentItem.value ?: return
        val mc = controller ?: return
        if (!item.isYouTube || mc.mediaItemCount == 0) return

        val videoId = item.youtubeVideoId
            ?: YouTubeStreamEngine.extractVideoId(item.uri.toString())
            ?: return
        val videoMode = !YouTubeStreamEngine.videoMode.value
        val generation = ++playbackRequestGeneration

        viewModelScope.launch {
            try {
                if (videoMode) YouTubeStreamEngine.resolve(videoId)
                if (generation != playbackRequestGeneration || controller !== mc) return@launch

                val idx = mc.currentMediaItemIndex
                if (idx !in 0 until mc.mediaItemCount || mc.currentMediaItem?.mediaId != item.id) return@launch
                val pos = mc.currentPosition.coerceAtLeast(0L)
                val wasPlaying = mc.isPlaying
                val mediaItem = MediaItem.Builder()
                    .setMediaId(item.id)
                    .setUri(YouTubeStreamEngine.buildUriString(videoId, isVideo = videoMode))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.artist)
                            .setArtworkUri(item.artworkUri)
                            .build()
                    )
                    .build()

                YouTubeStreamEngine.setVideoMode(videoMode)
                mc.replaceMediaItem(idx, mediaItem)
                mc.seekTo(idx, pos)
                mc.prepare()
                if (wasPlaying) mc.play()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == playbackRequestGeneration) {
                    android.widget.Toast.makeText(
                        appCtx,
                        "Gagal memuat stream video: ${e.message ?: "coba lagi"}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun downloadCurrentOrItem(
        item: MediaItemModel,
        asVideo: Boolean = item.mediaType == MediaType.YOUTUBE_VIDEO,
    ) {
        val vid = item.youtubeVideoId ?: return
        downloadYouTube(vid, item.title, asVideo)
    }

    fun downloadYouTube(videoId: String, title: String, asVideo: Boolean = false) {
        YouTubeDownloader.startDownload(appCtx, videoId, title, asVideo = asVideo)
    }

    private fun loadLyricsFor(item: MediaItemModel) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _currentLyrics.value = null
            val docs = LyricsEngine.getLyrics(
                cacheKey = item.id,
                rawTitle = item.title,
                rawArtist = item.artist,
                durationMs = item.durationMs
            )
            if (_currentItem.value?.id == item.id) {
                _currentLyrics.value = docs
            }
            _isLoadingLyrics.value = false
        }
    }

    private fun findItemById(id: String): MediaItemModel? {
        return (_homeOnlineTracks.value + _radioResults.value + _onlineResults.value + _localSongs.value + _localVideos.value + _downloadedItems.value)
            .firstOrNull { it.id == id }
    }

    private fun Song.toMediaItemModel(): MediaItemModel = MediaItemModel(
        id = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = Uri.parse(uri),
        artworkUri = if (albumId > 0L) {
            Uri.parse("content://media/external/audio/albumart/$albumId")
        } else {
            Uri.parse(uri)
        },
        mediaType = MediaType.LOCAL_AUDIO,
    )

    override fun onCleared() {
        sleepJob?.cancel()
        controller?.release()
        super.onCleared()
    }
}

private fun List<StreamInfoItem>.toTrackResults(videosOnly: Boolean): List<MediaItemModel> =
    mapNotNull { item ->
        val rawUrl = item.url ?: return@mapNotNull null
        val videoId = when {
            "v=" in rawUrl -> rawUrl.substringAfter("v=").substringBefore("&")
            "youtu.be/" in rawUrl -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
            "/shorts/" in rawUrl -> rawUrl.substringAfter("/shorts/").substringBefore("?")
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val thumbUrl = item.thumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val type = if (videosOnly) MediaType.YOUTUBE_VIDEO else MediaType.YOUTUBE_SONG

        MediaItemModel(
            id = "yt_$videoId",
            title = item.name.orEmpty().ifBlank { "Unknown Track" },
            artist = item.uploaderName.orEmpty().ifBlank { "YouTube" },
            album = "YouTube",
            durationMs = (item.duration * 1000L).coerceAtLeast(0L),
            uri = Uri.parse(YouTubeStreamEngine.buildUriString(videoId, isVideo = videosOnly)),
            artworkUri = Uri.parse(thumbUrl),
            mediaType = type,
            youtubeVideoId = videoId,
        )
    }

private object YouTubeApiSearch {
    class TrackSearchSession(
        private val session: YouTubeStreamEngine.SearchSession,
        private val videosOnly: Boolean,
    ) {
        val initialResults: List<MediaItemModel> =
            session.initialItems.toTrackResults(videosOnly)

        val hasMore: Boolean
            get() = session.hasMore

        suspend fun loadNextPage(): List<MediaItemModel> =
            session.loadNextPage().toTrackResults(videosOnly)
    }

    suspend fun searchSession(query: String, videosOnly: Boolean): TrackSearchSession =
        TrackSearchSession(
            YouTubeStreamEngine.searchSession(query.trim(), videosOnly),
            videosOnly,
        )

    suspend fun searchTracks(query: String, videosOnly: Boolean = false): List<MediaItemModel> {
        if (query.isBlank()) return emptyList()
        return YouTubeStreamEngine.search(query.trim(), videosOnly = videosOnly)
            .toTrackResults(videosOnly)
    }
}
