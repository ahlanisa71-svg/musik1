package com.glassplayer.app.data

import com.glassplayer.app.youtube.YouTubeStreamEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class YtVideo(
    val id: String,
    val title: String,
    val channel: String,
    val thumb: String,
)

object YouTubeApi {
    val hasKey: Boolean = true
    private val videoCacheLock = Mutex()
    @Volatile
    private var cachedMusicVideos: List<YtVideo>? = null

    suspend fun musicVideos(): Result<List<YtVideo>> = videoCacheLock.withLock {
        cachedMusicVideos?.let { return@withLock Result.success(it) }
        val result = withContext(Dispatchers.IO) {
            try {
                val items = YouTubeStreamEngine.search("official music video", videosOnly = true)
                val videos = items.mapNotNull { item ->
                    val rawUrl = item.url ?: return@mapNotNull null
                    val videoId = when {
                        "v=" in rawUrl -> rawUrl.substringAfter("v=").substringBefore("&")
                        "youtu.be/" in rawUrl -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
                        "/shorts/" in rawUrl -> rawUrl.substringAfter("/shorts/").substringBefore("?")
                        else -> null
                    }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

                    val thumbUrl = item.thumbnails.maxByOrNull { it.width }?.url
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    YtVideo(
                        id = videoId,
                        title = item.name.orEmpty().ifBlank { "Music Video" },
                        channel = item.uploaderName.orEmpty().ifBlank { "YouTube" },
                        thumb = thumbUrl,
                    )
                }
                Result.success(videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        result.onSuccess { cachedMusicVideos = it }
        result
    }
}
