package com.glassplayer.app.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ResolvedYouTubeMedia(
    val videoId: String,
    val title: String,
    val author: String,
    val durationMs: Long,
    val thumbnailUrl: String,
    val audioUrl: String,
    val audioExtension: String,
    val audioBitrateKbps: Int,
    val videoUrl: String?,
    val videoResolution: String?
)

class OkHttpNewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val url = request.url()
        val dataToSend = request.dataToSend()
        val requestBody = dataToSend?.toRequestBody(null)

        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(request.httpMethod(), requestBody)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )

        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("Rate limited by YouTube (HTTP 429)", url)
            }
            val bodyString = resp.body?.string().orEmpty()
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                bodyString,
                resp.request.url.toString()
            )
        }
    }
}

object YouTubeStreamEngine {
    const val SCHEME = "ytmusic"
    const val WATCH_PREFIX = "https://www.youtube.com/watch?v="

    @Volatile
    private var initialized = false

    private val cache = ConcurrentHashMap<String, Pair<Long, ResolvedYouTubeMedia>>()
    private const val CACHE_TTL_MS = 45 * 60 * 1000L

    private val _videoMode = MutableStateFlow(false)
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    fun setVideoMode(enabled: Boolean) {
        _videoMode.value = enabled
    }

    fun toggleVideoMode(): Boolean {
        val next = !_videoMode.value
        _videoMode.value = next
        return next
    }

    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        NewPipe.init(
            OkHttpNewPipeDownloader(),
            Localization("id", "ID"),
            ContentCountry("ID")
        )
        initialized = true
    }

    fun buildUriString(videoId: String, isVideo: Boolean = false): String {
        val kind = if (isVideo) "video" else "track"
        return "$SCHEME://$kind/$videoId"
    }

    fun extractVideoId(uriString: String): String? {
        if (!uriString.startsWith("$SCHEME://")) return null
        return uriString.substringAfterLast("/").substringBefore("?").takeIf { it.isNotBlank() }
    }

    suspend fun search(query: String, videosOnly: Boolean = false): List<StreamInfoItem> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val filter = if (videosOnly) listOf("videos") else listOf("music_songs", "videos")
            val extractor = ServiceList.YouTube.getSearchExtractor(query, filter, "")
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
        }

    suspend fun resolve(videoId: String, forceRefresh: Boolean = false): ResolvedYouTubeMedia =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                cache[videoId]?.let { (savedAt, media) ->
                    if (now - savedAt < CACHE_TTL_MS) return@withContext media
                }
            }

            ensureInitialized()
            val info = StreamInfo.getInfo(ServiceList.YouTube, "$WATCH_PREFIX$videoId")

            val bestAudio = info.audioStreams
                .filter { !it.content.isNullOrEmpty() }
                .maxByOrNull { it.averageBitrate }
                ?: error("No playable audio stream found for $videoId")

            val bestVideo = info.videoStreams
                .filter { !it.content.isNullOrEmpty() }
                .sortedByDescending { it.height }
                .firstOrNull { it.height <= 1080 }
                ?: info.videoOnlyStreams
                    .filter { !it.content.isNullOrEmpty() }
                    .sortedByDescending { it.height }
                    .firstOrNull { it.height <= 1080 }

            val thumb = info.thumbnails.maxByOrNull { it.width * it.height }?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val resolved = ResolvedYouTubeMedia(
                videoId = videoId,
                title = info.name.orEmpty(),
                author = info.uploaderName.orEmpty(),
                durationMs = (info.duration * 1000L).coerceAtLeast(0L),
                thumbnailUrl = thumb,
                audioUrl = bestAudio.content,
                audioExtension = bestAudio.format?.suffix ?: "m4a",
                audioBitrateKbps = bestAudio.averageBitrate.coerceAtLeast(128),
                videoUrl = bestVideo?.content,
                videoResolution = bestVideo?.resolution
            )

            cache[videoId] = now to resolved
            resolved
        }
}
