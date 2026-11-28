package com.glassplayer.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.glassplayer.app.BuildConfig
import com.glassplayer.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/** Pembaruan manual lewat GitHub Releases (rilis terbaru milik repositori yang membangun APK ini). */
object Updater {

    private val tagRe = Regex("""^v?([0-9][0-9.]*)-c(\d+)""")

    suspend fun check(ctx: Context): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Musix-Updater")
            val code = conn.responseCode
            if (code == 404) {
                markSuccessfulCheck(ctx)
                return@withContext UpdateCheck.Failed("Belum ada rilis di GitHub (atau repositori bersifat privat).")
            }
            if (code != 200) return@withContext UpdateCheck.Failed("GitHub membalas kode $code. Coba lagi nanti.")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(body)
            val tag = j.optString("tag_name")
            val m = tagRe.find(tag)
                ?: return@withContext UpdateCheck.Failed("Rilis terbaru ($tag) belum memuat nomor versi.")
            val remoteName = m.groupValues[1]
            val remoteCode = m.groupValues[2].toIntOrNull() ?: 0
            val current = currentCode(ctx)
            if (remoteCode <= current) {
                markSuccessfulCheck(ctx)
                return@withContext UpdateCheck.UpToDate
            }

            val assets = j.optJSONArray("assets")
            var url = ""
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size", 0L)
                        break
                    }
                }
            }
            if (url.isEmpty()) return@withContext UpdateCheck.Failed("Rilis terbaru tidak memuat file APK.")
            markSuccessfulCheck(ctx)
            UpdateCheck.Available(UpdateInfo(remoteName, remoteCode, j.optString("body").trim(), url, size))
        } catch (e: Exception) {
            UpdateCheck.Failed("Tidak bisa terhubung ke GitHub. Periksa koneksi internet.")
        }
    }

    /** Pemeriksaan otomatis saat aplikasi dibuka: paling sering sekali tiap 6 jam. */
    fun shouldAutoCheck(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences("musix_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last_check", 0L) < 6 * 60 * 60 * 1000L) return false
        return true
    }

    fun notifyUpdateAvailable(ctx: Context, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val notifications = NotificationManagerCompat.from(ctx)
        if (!notifications.areNotificationsEnabled()) return

        val preferences = ctx.getSharedPreferences("musix_update", Context.MODE_PRIVATE)
        if (info.versionCode <= preferences.getInt("last_notified_version", 0)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotifications = ctx.getSystemService(NotificationManager::class.java)
            val channel = systemNotifications.getNotificationChannel(UPDATE_CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return
            if (channel == null) {
                systemNotifications.createNotificationChannel(
                    NotificationChannel(
                        UPDATE_CHANNEL_ID,
                        "Pembaruan aplikasi",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Pemberitahuan saat pembaruan Musix tersedia."
                    }
                )
            }
        }

        val openUpdater = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_UPDATER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = android.app.PendingIntent.getActivity(
            ctx,
            UPDATE_PENDING_INTENT_ID,
            openUpdater,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Pembaruan Musix tersedia")
            .setContentText("Versi ${info.versionName} tersedia. Ketuk untuk mengunduh.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notifications.notify(UPDATE_NOTIFICATION_ID, notification)
            preferences.edit().putInt("last_notified_version", info.versionCode).apply()
        } catch (e: SecurityException) {
            android.util.Log.w("Updater", "Tidak dapat menampilkan notifikasi pembaruan.", e)
        }
    }

    private fun markSuccessfulCheck(ctx: Context) {
        ctx.getSharedPreferences("musix_update", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_check", System.currentTimeMillis())
            .apply()
    }

    @Suppress("DEPRECATION")
    fun currentCode(ctx: Context): Int = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (_: Exception) {
        0
    }

    /** Unduh APK ke cache aplikasi; [onProgress] 0..100 (atau -1 bila ukuran tidak diketahui). */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "Musix-${info.versionCode}.apk")
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Musix-Updater")
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) throw IllegalStateException("kode ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var done = 0L
            var last = -2
            conn.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
                        if (pct != last) {
                            last = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            if (info.sizeBytes > 0 && out.length() != info.sizeBytes) {
                out.delete()
                throw IOException("Ukuran APK tidak sesuai; unduhan mungkin terputus. Coba lagi.")
            }
            out
        }

    /**
     * Buka pemasang paket Android. Mengembalikan false bila izin "pasang aplikasi dari sumber ini"
     * belum diberikan (pengaturannya dibuka; pengguna lalu menekan tombol lagi).
     */
    fun install(ctx: Context, apk: File): Boolean {
        validateUpdateApk(ctx, apk)
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    @Suppress("DEPRECATION")
    private fun validateUpdateApk(ctx: Context, apk: File) {
        if (!apk.isFile || apk.length() == 0L) {
            throw IOException("File APK tidak ditemukan atau kosong.")
        }

        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IOException("APK unduhan rusak atau tidak dapat dibaca.")
        if (candidate.packageName != ctx.packageName) {
            throw IOException("APK ini bukan pembaruan untuk aplikasi yang terpasang.")
        }
        val candidateVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidate.longVersionCode
        } else {
            candidate.versionCode.toLong()
        }
        if (candidateVersion <= currentCode(ctx).toLong()) {
            throw IOException("Versi APK tidak lebih baru dari aplikasi yang terpasang.")
        }

        val installed = pm.getPackageInfo(ctx.packageName, flags)
        val candidateSigners = candidate.signerCertificates()
        val installedSigners = installed.signerCertificates()
        if (candidateSigners.isEmpty() || candidateSigners != installedSigners) {
            throw IOException("Tanda tangan APK berbeda. Pembaruan harus dibuat dengan kunci aplikasi yang sama.")
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerCertificates(): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        } else {
            signatures.orEmpty().map { it.toCharsString() }.toSet()
        }

    private const val UPDATE_CHANNEL_ID = "musix_updates"
    private const val UPDATE_NOTIFICATION_ID = 2043
    private const val UPDATE_PENDING_INTENT_ID = 2043
}
