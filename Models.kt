package com.glassplayer.app

import android.net.Uri

enum class MediaType {
    LOCAL_AUDIO,
    LOCAL_VIDEO,
    YOUTUBE_SONG,
    YOUTUBE_VIDEO
}

data class MediaItemModel(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val uri: Uri,
    val artworkUri: Uri? = null,
    val mediaType: MediaType = MediaType.LOCAL_AUDIO,
    val youtubeVideoId: String? = null,
    val isDownloaded: Boolean = false
) {
    val isYouTube: Boolean
        get() = mediaType == MediaType.YOUTUBE_SONG || mediaType == MediaType.YOUTUBE_VIDEO

    val isVideoCapable: Boolean
        get() = mediaType == MediaType.LOCAL_VIDEO ||
                mediaType == MediaType.YOUTUBE_SONG ||
                mediaType == MediaType.YOUTUBE_VIDEO
}

enum class AppTab(val label: String) {
    SONGS("Songs"),
    VIDEOS("Videos"),
    ONLINE("Online"),
    DOWNLOADS("Downloads")
}
