package com.glassplayer.app

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
                val localFile = YouTubeDownloader.getDownloadedFile(videoId)
                if (localFile != null && localFile.exists()) {
                    return@Factory dataSpec.withUri(Uri.fromFile(localFile))
                }

                val resolved = runBlocking { YouTubeStreamEngine.resolve(videoId) }
                val targetUrl = if (YouTubeStreamEngine.videoMode.value && !resolved.videoUrl.isNullOrBlank()) {
                    resolved.videoUrl
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

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
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
