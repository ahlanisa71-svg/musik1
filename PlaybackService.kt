package com.glassplayer.app

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.glassplayer.app.download.YouTubeDownloader
import com.glassplayer.app.youtube.YouTubeStreamEngine
import kotlinx.coroutines.runBlocking

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(YouTubeStreamEngine.USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                )
            )
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec: DataSpec ->
            val rawUri = dataSpec.uri.toString()
            val videoId = YouTubeStreamEngine.extractVideoId(rawUri)
            if (videoId != null) {
                val isVideo = YouTubeStreamEngine.isVideoUri(rawUri)
                val localFile = YouTubeDownloader.getDownloadedFile(this@PlaybackService, videoId, asVideo = isVideo)
                if (localFile != null && localFile.exists()) {
                    return@Factory dataSpec.withUri(Uri.fromFile(localFile))
                }

                val resolved = runBlocking { YouTubeStreamEngine.resolve(videoId) }
                val targetUrl = if (isVideo) {
                    resolved.videoUrl
                        ?: throw IllegalStateException("Stream video tidak tersedia untuk video ini.")
                } else {
                    resolved.audioUrl
                }
                dataSpec.withUri(Uri.parse(targetUrl))
            } else {
                dataSpec
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val defaultMediaSourceFactory = DefaultMediaSourceFactory(resolvingFactory)
        val mediaSourceFactory = object : MediaSource.Factory {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val uri = mediaItem.localConfiguration?.uri ?: return defaultMediaSourceFactory.createMediaSource(mediaItem)
                val rawUri = uri.toString()
                val videoId = YouTubeStreamEngine.extractVideoId(rawUri)
                if (videoId == null || !YouTubeStreamEngine.isVideoUri(rawUri)) {
                    return defaultMediaSourceFactory.createMediaSource(mediaItem)
                }

                val localFile = YouTubeDownloader.getDownloadedFile(
                    this@PlaybackService,
                    videoId,
                    asVideo = true
                )
                if (localFile != null && localFile.exists()) {
                    return defaultMediaSourceFactory.createMediaSource(mediaItem)
                }

                val resolved = YouTubeStreamEngine.cached(videoId)
                    ?: runBlocking { YouTubeStreamEngine.resolve(videoId) }
                val videoUrl = resolved.videoUrl
                    ?: throw IllegalStateException("Stream video tidak tersedia untuk video ini.")
                val videoItem = mediaItem.buildUpon().setUri(Uri.parse(videoUrl)).build()
                val videoSource = defaultMediaSourceFactory.createMediaSource(videoItem)
                if (!resolved.videoOnly) return videoSource

                val audioItem = mediaItem.buildUpon().setUri(Uri.parse(resolved.audioUrl)).build()
                val audioSource = defaultMediaSourceFactory.createMediaSource(audioItem)
                return MergingMediaSource(true, true, videoSource, audioSource)
            }

            override fun getSupportedTypes(): IntArray = defaultMediaSourceFactory.supportedTypes

            override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: DrmSessionManagerProvider
            ): MediaSource.Factory {
                defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: LoadErrorHandlingPolicy
            ): MediaSource.Factory {
                defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                return this
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
