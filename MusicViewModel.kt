package com.glassplayer.app

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.glassplayer.app.download.DownloadTaskState
import com.glassplayer.app.download.YouTubeDownloader
import com.glassplayer.app.lyrics.LyricsDocument
import com.glassplayer.app.lyrics.LyricsEngine
import com.glassplayer.app.youtube.YouTubeStreamEngine
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private var controller: MediaController? = null

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

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _searchVideosOnly = MutableStateFlow(false)
    val searchVideosOnly: StateFlow<Boolean> = _searchVideosOnly.asStateFlow()

    private val _currentItem = MutableStateFlow<MediaItemModel?>(null)
    val currentItem: StateFlow<MediaItemModel?> = _currentItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _currentLyrics = MutableStateFlow<LyricsDocument?>(null)
    val currentLyrics: StateFlow<LyricsDocument?> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    val videoMode: StateFlow<Boolean> = YouTubeStreamEngine.videoMode
    val activeDownloads: StateFlow<Map<String, DownloadTaskState>> = YouTubeDownloader.activeDownloads

    init {
        connectPlayer()
        refreshLibrary()
        startPositionTicker()
    }

    fun getPlayer(): Player? = controller

    private fun connectPlayer() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val mc = future.get()
            controller = mc
            mc.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val id = mediaItem?.mediaId ?: return
                    val matched = findItemById(id)
                    if (matched != null && matched.id != _currentItem.value?.id) {
                        _currentItem.value = matched
                        loadLyricsFor(matched)
                    }
                }
            })
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                controller?.let { player ->
                    _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    if (dur > 0) _durationMs.value = dur
                }
                delay(250L)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        if (tab == AppTab.DOWNLOADS || tab == AppTab.SONGS || tab == AppTab.VIDEOS) {
            refreshLibrary()
        }
    }

    fun setSearchVideosOnly(videosOnly: Boolean) {
        _searchVideosOnly.value = videosOnly
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearchingOnline.value = true
            val results = YouTubeApi.searchTracks(query, videosOnly = _searchVideosOnly.value)
            _onlineResults.value = results
            _isSearchingOnline.value = false
        }
    }

    fun playItem(item: MediaItemModel, queue: List<MediaItemModel> = listOf(item)) {
        val mc = controller ?: return
        _currentItem.value = item
        loadLyricsFor(item)

        if (item.mediaType == MediaType.LOCAL_VIDEO || item.mediaType == MediaType.YOUTUBE_VIDEO) {
            YouTubeStreamEngine.setVideoMode(true)
        }

        val mediaItems = queue.map { toExoMediaItem(it) }
        val startIndex = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        mc.setMediaItems(mediaItems, startIndex, 0L)
        mc.prepare()
        mc.play()
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        if (mc.isPlaying) mc.pause() else mc.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
        _positionMs.value = ms.coerceAtLeast(0L)
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun toggleLyrics() {
        val next = !_showLyrics.value
        _showLyrics.value = next
        if (next && _currentLyrics.value == null) {
            _currentItem.value?.let { loadLyricsFor(it) }
        }
    }

    fun toggleVideoMode() {
        YouTubeStreamEngine.toggleVideoMode()
        val item = _currentItem.value ?: return
        val mc = controller ?: return
        if (item.isYouTube) {
            val pos = mc.currentPosition.coerceAtLeast(0L)
            val wasPlaying = mc.isPlaying
            val idx = mc.currentMediaItemIndex
            mc.replaceMediaItem(idx, toExoMediaItem(item))
            mc.seekTo(idx, pos)
            mc.prepare()
            if (wasPlaying) mc.play()
        }
    }

    fun downloadCurrentOrItem(item: MediaItemModel, asVideo: Boolean = false) {
        val vid = item.youtubeVideoId ?: return
        YouTubeDownloader.startDownload(getApplication(), vid, item.title, asVideo = asVideo)
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

    private fun toExoMediaItem(item: MediaItemModel): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setArtworkUri(item.artworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(item.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun findItemById(id: String): MediaItemModel? {
        return (_onlineResults.value + _localSongs.value + _localVideos.value + _downloadedItems.value)
            .firstOrNull { it.id == id }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val (songs, videos, downloads) = withContext(Dispatchers.IO) {
                val loadedSongs = queryLocalAudio()
                val loadedVideos = queryLocalVideos()
                val loadedDownloads = (loadedSongs + loadedVideos).filter {
                    it.uri.toString().contains("MusixDownloads") ||
                    it.album.equals("MusixDownloads", ignoreCase = true)
                }
                Triple(loadedSongs, loadedVideos, loadedDownloads)
            }
            _localSongs.value = songs
            _localVideos.value = videos
            _downloadedItems.value = downloads
        }
    }

    private fun queryLocalAudio(): List<MediaItemModel> {
        val context = getApplication<Application>()
        val result = mutableListOf<MediaItemModel>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                result.add(
                    MediaItemModel(
                        id = "audio_$id",
                        title = cursor.getString(titleCol).orEmpty(),
                        artist = cursor.getString(artistCol).orEmpty(),
                        album = cursor.getString(albumCol).orEmpty(),
                        durationMs = cursor.getLong(durCol),
                        uri = contentUri,
                        artworkUri = artUri,
                        mediaType = MediaType.LOCAL_AUDIO
                    )
                )
            }
        }
        return result
    }

    private fun queryLocalVideos(): List<MediaItemModel> {
        val context = getApplication<Application>()
        val result = mutableListOf<MediaItemModel>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                result.add(
                    MediaItemModel(
                        id = "video_$id",
                        title = cursor.getString(titleCol).orEmpty(),
                        artist = cursor.getString(artistCol).orEmpty().ifBlank { "Video Lokal" },
                        album = cursor.getString(bucketCol).orEmpty(),
                        durationMs = cursor.getLong(durCol),
                        uri = contentUri,
                        artworkUri = contentUri,
                        mediaType = MediaType.LOCAL_VIDEO
                    )
                )
            }
        }
        return result
    }
}
