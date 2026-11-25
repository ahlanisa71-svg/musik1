package com.glassplayer.app.lyrics

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import kotlin.math.abs

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class LyricLine(
    val startTimeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String
)

object LyricsEngine {
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, LyricsDocument>()
    private val LRC_LINE = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\](.*)")

    suspend fun getLyrics(
        cacheKey: String,
        rawTitle: String,
        rawArtist: String,
        durationMs: Long
    ): LyricsDocument? = withContext(Dispatchers.IO) {
        cache[cacheKey]?.let { return@withContext it }

        val cleanTitle = cleanSongTitle(rawTitle)
        val cleanArtist = cleanArtistName(rawArtist)
        if (cleanTitle.isBlank()) return@withContext null

        val result = coroutineScope {
            val lrcLibJob = async { fetchLrcLib(cleanTitle, cleanArtist, durationMs) }
            val netEaseJob = async { fetchNetEase(cleanTitle, cleanArtist, durationMs) }
            val kuGouJob = async { fetchKuGou(cleanTitle, cleanArtist, durationMs) }

            val candidates = listOfNotNull(
                lrcLibJob.await(),
                netEaseJob.await(),
                kuGouJob.await()
            )

            candidates.maxByOrNull { doc ->
                when {
                    doc.lines.any { it.words.isNotEmpty() } -> 3
                    doc.synced -> 2
                    else -> 1
                }
            }
        }

        if (result != null) {
            cache[cacheKey] = result
        }
        result
    }

    private fun cleanSongTitle(title: String): String {
        return title
            .replace(Regex("\\(.*?(official|video|audio|lyrics|mv|hd|4k|remaster|feat|ft).*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?(official|video|audio|lyrics|mv|hd|4k|remaster|feat|ft).*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+-\\s+.*"), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("VEVO$", RegexOption.IGNORE_CASE), "")
            .split(",", "&", "feat.", "ft.")
            .firstOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun fetchLrcLib(title: String, artist: String, durationMs: Long): LyricsDocument? {
        val q = URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val body = httpGet("https://lrclib.net/api/search?q=$q") ?: return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        if (arr.length() == 0) return null

        val targetSec = durationMs / 1000.0
        var bestSynced: LyricsDocument? = null
        var bestPlain: LyricsDocument? = null
        var minDiff = Double.MAX_VALUE

        for (i in 0 until arr.length().coerceAtMost(8)) {
            val item = arr.optJSONObject(i) ?: continue
            val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() }
            val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() }
            val dur = item.optDouble("duration", 0.0)
            val diff = if (targetSec > 0 && dur > 0) abs(dur - targetSec) else 0.0

            if (synced != null && (diff <= 5.0 || bestSynced == null)) {
                if (diff < minDiff || bestSynced == null) {
                    minDiff = diff
                    parseLrc(synced, "LRCLIB")?.let { bestSynced = it }
                }
            } else if (plain != null && bestPlain == null) {
                val lines = plain.lines().filter { it.isNotBlank() }.map { LyricLine(null, it.trim()) }
                if (lines.isNotEmpty()) bestPlain = LyricsDocument(lines, synced = false, source = "LRCLIB")
            }
        }
        return bestSynced ?: bestPlain
    }

    private fun fetchNetEase(title: String, artist: String, durationMs: Long): LyricsDocument? {
        val q = URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val headers = mapOf(
            "Referer" to "https://music.163.com",
            "Cookie" to "os=pc; NMTID=00OAVK3xqDG726ITU6jopU6jF2yMk0AAAGCO8l1BA;"
        )
        val searchBody = httpGet("https://music.163.com/api/search/pc?s=$q&type=1&offset=0&limit=5", headers) ?: return null
        val songs = runCatching {
            JSONObject(searchBody).optJSONObject("result")?.optJSONArray("songs")
        }.getOrNull() ?: return null

        var bestId = -1L
        var minDiff = Long.MAX_VALUE
        for (i in 0 until songs.length().coerceAtMost(6)) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optLong("id", -1L)
            val dur = song.optLong("duration", 0L)
            val diff = if (durationMs > 0 && dur > 0) abs(dur - durationMs) else 0L
            if (id > 0 && diff < minDiff) {
                minDiff = diff
                bestId = id
            }
        }
        if (bestId <= 0 || (durationMs > 0 && minDiff > 8000L)) return null

        val lyricBody = httpGet("https://music.163.com/api/song/lyric?id=$bestId&lv=1&kv=1&tv=-1", headers) ?: return null
        val rawLrc = runCatching {
            JSONObject(lyricBody).optJSONObject("lrc")?.optString("lyric")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        return parseLrc(rawLrc, "NetEase")
    }

    private fun fetchKuGou(title: String, artist: String, durationMs: Long): LyricsDocument? {
        val keyword = URLEncoder.encode("$artist - $title".trim(), "UTF-8")
        val durParam = if (durationMs > 0) "&duration=$durationMs" else ""
        val searchBody = httpGet("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=&keyword=$keyword$durParam")
            ?: return null
        val candidates = runCatching { JSONObject(searchBody).optJSONArray("candidates") }.getOrNull() ?: return null

        val best = (0 until candidates.length())
            .mapNotNull { candidates.optJSONObject(it) }
            .filter { it.optString("id").isNotEmpty() && it.optString("accesskey").isNotEmpty() }
            .filter { durationMs <= 0 || abs(it.optLong("duration") - durationMs) <= 5000L }
            .minByOrNull { if (durationMs > 0) abs(it.optLong("duration") - durationMs) else 0L }
            ?: return null

        val id = URLEncoder.encode(best.optString("id"), "UTF-8")
        val key = URLEncoder.encode(best.optString("accesskey"), "UTF-8")
        val dlBody = httpGet("https://lyrics.kugou.com/download?ver=1&client=pc&fmt=krc&charset=utf8&id=$id&accesskey=$key")
            ?: return null
        val content = runCatching { JSONObject(dlBody).optString("content") }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: return null

        val decrypted = decryptKrc(Base64.decode(content, Base64.DEFAULT)) ?: return null
        return parseKrc(decrypted, title)
    }

    private fun decryptKrc(raw: ByteArray): String? {
        if (raw.size <= 4 || String(raw, 0, 4, Charsets.US_ASCII) != "krc1") return null
        val krcKey = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val body = ByteArray(raw.size - 4) { i -> (raw[i + 4].toInt() xor krcKey[i % krcKey.size]).toByte() }
        val inflater = Inflater()
        inflater.setInput(body)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toString("UTF-8")
    }

    private fun parseKrc(krc: String, title: String): LyricsDocument? {
        val lineRegex = Regex("^\\[(\\d+),(\\d+)]")
        val wordRegex = Regex("<(\\d+),(\\d+),\\d+>([^<]*)")

        val lines = krc.lineSequence().mapNotNull { line ->
            val head = lineRegex.find(line) ?: return@mapNotNull null
            val start = head.groupValues[1].toLong()
            val pieces = wordRegex.findAll(line.substring(head.range.last + 1)).toList()
            val text = pieces.joinToString("") { it.groupValues[3] }.trim()
            val words = pieces.mapNotNull { w ->
                val offset = w.groupValues[1].toLong()
                val len = w.groupValues[2].toLong()
                w.groupValues[3].trim().takeIf { it.isNotEmpty() }?.let {
                    LyricWord(start + offset, start + offset + len, it)
                }
            }
            if (text.isEmpty()) null else LyricLine(start, text, words)
        }.toList()

        val firstSung = lines.indexOfFirst { l ->
            !(l.text.contains('：') || l.text.contains(':') ||
              (l.text.startsWith(title, ignoreCase = true) && l.text.contains(" - ")))
        }
        val sung = if (firstSung < 0) emptyList() else lines.drop(firstSung)
        return sung.takeIf { it.isNotEmpty() }?.let { LyricsDocument(it, synced = true, source = "KuGou") }
    }

    private fun parseLrc(raw: String, source: String): LyricsDocument? {
        val lines = raw.lines().mapNotNull { line ->
            val match = LRC_LINE.find(line) ?: return@mapNotNull null
            val (min, sec, frac, text) = match.destructured
            val startMs = min.toLong() * 60_000 +
                sec.toLong() * 1_000 +
                frac.padEnd(3, '0').take(3).toLong()
            val clean = text.trim()
            if (clean.startsWith("作词") || clean.startsWith("作曲") || clean.startsWith("编曲")) {
                return@mapNotNull null
            }
            LyricLine(startMs, clean)
        }
        return lines.takeIf { it.isNotEmpty() }?.let { LyricsDocument(it, synced = true, source = source) }
    }

    private fun httpGet(url: String, extraHeaders: Map<String, String> = emptyMap()): String? = runCatching {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        http.newCall(builder.build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()
}
