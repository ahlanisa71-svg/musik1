package com.glassplayer.app.data

import android.content.Context
import android.text.Html
import com.glassplayer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YtVideo(val id: String, val title: String, val channel: String, val thumb: String)

/**
 * Daftar video musik dari YouTube Data API v3 yang RESMI (hanya data pencarian).
 * Video tidak diputar di dalam Musix: ketuk akan membuka aplikasi YouTube.
 * Hasil disimpan 6 jam supaya kuota harian (10.000 unit; 1 pencarian = 100 unit) hemat.
 */
object YouTubeApi {
    private const val TTL_MS = 6 * 60 * 60 * 1000L

    val hasKey: Boolean get() = BuildConfig.YT_API_KEY.isNotBlank()

    suspend fun musicVideos(ctx: Context): Result<List<YtVideo>> = withContext(Dispatchers.IO) {
        val sp = ctx.getSharedPreferences("yt_cache", Context.MODE_PRIVATE)
        val cached = sp.getString("json", null)
        val at = sp.getLong("at", 0L)
        if (cached != null && System.currentTimeMillis() - at < TTL_MS) {
            return@withContext runCatching { parse(cached) }
        }
        try {
            val q = URLEncoder.encode("official music video", "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&videoCategoryId=10&maxResults=12&regionCode=ID" +
                "&q=$q&key=${BuildConfig.YT_API_KEY}"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                if (conn.responseCode != 200) {
                    val why = when (conn.responseCode) {
                        403 -> "Kuota atau izin kunci API ditolak (403)"
                        400 -> "Kunci API tidak valid (400)"
                        else -> "Gagal memuat (${conn.responseCode})"
                    }
                    return@withContext cached?.let { runCatching { parse(it) } } ?: Result.failure(Exception(why))
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val list = parse(body)
                sp.edit().putString("json", body).putLong("at", System.currentTimeMillis()).apply()
                Result.success(list)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            cached?.let { runCatching { parse(it) } } ?: Result.failure(Exception("Tidak ada koneksi internet"))
        }
    }

    private fun parse(json: String): List<YtVideo> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<YtVideo>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val id = o.optJSONObject("id")?.optString("videoId").orEmpty()
            val sn = o.optJSONObject("snippet") ?: continue
            if (id.isEmpty()) continue
            val thumbs = sn.optJSONObject("thumbnails")
            val thumb = thumbs?.optJSONObject("medium")?.optString("url")
                ?: thumbs?.optJSONObject("default")?.optString("url").orEmpty()
            out += YtVideo(
                id = id,
                title = Html.fromHtml(sn.optString("title"), Html.FROM_HTML_MODE_LEGACY).toString(),
                channel = Html.fromHtml(sn.optString("channelTitle"), Html.FROM_HTML_MODE_LEGACY).toString(),
                thumb = thumb,
            )
        }
        return out
    }
}
