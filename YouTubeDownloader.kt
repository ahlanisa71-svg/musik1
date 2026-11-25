package com.glassplayer.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.glassplayer.app.youtube.YouTubeStreamEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadTaskState(
    val videoId: String,
    val title: String,
    val progress: Float,
    val isCompleted: Boolean = false,
    val error: String? = null
)

object YouTubeDownloader {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val CHUNK_BYTES = 8L * 1024 * 1024
    private const val BUFFER_BYTES = 64 * 1024

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadTaskState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadTaskState>> = _activeDownloads.asStateFlow()

    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()

    fun downloadDirectory(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(musicDir, "MusixDownloads").apply { mkdirs() }
    }

    fun isDownloaded(videoId: String): Boolean {
        val dir = downloadDirectory()
        return dir.listFiles()?.any { it.name.contains("[$videoId]") && !it.name.endsWith(".part") } == true
    }

    fun getDownloadedFile(videoId: String): File? {
        val dir = downloadDirectory()
        return dir.listFiles()?.firstOrNull { it.name.contains("[$videoId]") && !it.name.endsWith(".part") }
    }

    fun startDownload(context: Context, videoId: String, title: String, asVideo: Boolean = false) {
        val intent = Intent(context, YouTubeDownloadService::class.java).apply {
            putExtra(YouTubeDownloadService.EXTRA_VIDEO_ID, videoId)
            putExtra(YouTubeDownloadService.EXTRA_TITLE, title)
            putExtra(YouTubeDownloadService.EXTRA_AS_VIDEO, asVideo)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancel(videoId: String) {
        cancelledIds.add(videoId)
        updateTask(videoId) { null }
    }

    suspend fun executeDownload(
        context: Context,
        videoId: String,
        fallbackTitle: String,
        asVideo: Boolean,
        onProgressNotify: (String, Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        cancelledIds.remove(videoId)
        val resolved = YouTubeStreamEngine.resolve(videoId, forceRefresh = true)
        val safeTitle = (resolved.title.ifBlank { fallbackTitle })
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(60)
            .trim()

        val streamUrl = if (asVideo && !resolved.videoUrl.isNullOrBlank()) {
            resolved.videoUrl
        } else {
            resolved.audioUrl
        }
        val ext = if (asVideo && !resolved.videoUrl.isNullOrBlank()) "mp4" else resolved.audioExtension

        val dir = downloadDirectory()
        val partFile = File(dir, "$safeTitle [$videoId].$ext.part")
        val finalFile = File(dir, "$safeTitle [$videoId].$ext")

        updateTask(videoId) { DownloadTaskState(videoId, safeTitle, 0f) }

        var offset = 0L
        var total = -1L

        try {
            FileOutputStream(partFile, false).use { out ->
                while (total < 0 || offset < total) {
                    if (videoId in cancelledIds) {
                        throw CancellationException("Download cancelled")
                    }

                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}")
                        .build()

                    http.newCall(request).execute().use { response ->
                        if (response.code != 200 && response.code != 206) {
                            throw IOException("HTTP ${response.code}")
                        }
                        if (total < 0) {
                            total = response.header("Content-Range")
                                ?.substringAfterLast('/')
                                ?.toLongOrNull()
                                ?: -1L
                        }
                        val body = response.body ?: throw IOException("Empty body")
                        val before = offset
                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                offset += read
                            }
                        }
                        if (response.code == 200 || (total < 0 && offset - before < CHUNK_BYTES)) {
                            total = offset
                        }
                    }

                    val progress = if (total > 0) (offset.toFloat() / total).coerceIn(0f, 1f) else 0.5f
                    updateTask(videoId) { DownloadTaskState(videoId, safeTitle, progress) }
                    onProgressNotify(safeTitle, (progress * 100).toInt())
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                throw IOException("Failed to finalize downloaded file")
            }

            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(finalFile.absolutePath),
                null,
                null
            )

            updateTask(videoId) { DownloadTaskState(videoId, safeTitle, 1f, isCompleted = true) }
            finalFile
        } catch (t: Throwable) {
            partFile.delete()
            updateTask(videoId) {
                DownloadTaskState(videoId, safeTitle, 0f, error = t.message ?: "Download gagal")
            }
            throw t
        }
    }

    private fun updateTask(videoId: String, transform: (DownloadTaskState?) -> DownloadTaskState?) {
        val current = _activeDownloads.value.toMutableMap()
        val updated = transform(current[videoId])
        if (updated == null) current.remove(videoId) else current[videoId] = updated
        _activeDownloads.value = current
    }
}

class YouTubeDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "YouTube Track"
        val asVideo = intent.getBooleanExtra(EXTRA_AS_VIDEO, false)

        startForegroundNotification(title, 0)
        activeCount++

        scope.launch {
            runCatching {
                YouTubeDownloader.executeDownload(
                    context = this@YouTubeDownloadService,
                    videoId = videoId,
                    fallbackTitle = title,
                    asVideo = asVideo
                ) { currentTitle, percent ->
                    notifyProgress(currentTitle, percent)
                }
            }
            activeCount--
            if (activeCount <= 0) {
                ServiceCompat.stopForeground(this@YouTubeDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification(title: String, percent: Int) {
        val notification = buildNotification(title, percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notifyProgress(title: String, percent: Int) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTIF_ID, buildNotification(title, percent)) }
    }

    private fun buildNotification(title: String, percent: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Mengunduh: $title")
            .setContentText("$percent%")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Unduhan Musik & Video", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AS_VIDEO = "extra_as_video"
        private const val CHANNEL_ID = "musix_downloads"
        private const val NOTIF_ID = 2042
    }
}
