package com.glassplayer.app

import android.net.Uri
import com.glassplayer.app.youtube.YouTubeStreamEngine

object YouTubeApi {
    suspend fun searchTracks(
        query: String,
        videosOnly: Boolean = false
    ): List<MediaItemModel> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val items = YouTubeStreamEngine.search(query.trim(), videosOnly = videosOnly)
            items.mapNotNull { item ->
                val rawUrl = item.url ?: return@mapNotNull null
                val videoId = extractYoutubeIdFromUrl(rawUrl) ?: return@mapNotNull null
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
                    youtubeVideoId = videoId
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun extractYoutubeIdFromUrl(url: String): String? {
        return when {
            "v=" in url -> url.substringAfter("v=").substringBefore("&")
            "youtu.be/" in url -> url.substringAfter("youtu.be/").substringBefore("?")
            "/shorts/" in url -> url.substringAfter("/shorts/").substringBefore("?")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }
}
