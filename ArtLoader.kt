package com.glassplayer.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memuat cover album (dari MediaStore atau tag ID3 yang tertanam di file). */
object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = HashSet<String>()

    fun peek(key: String): Bitmap? = cache.get(key)

    suspend fun load(ctx: Context, song: Song): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(song.uri)?.let { return@withContext it }
        if (synchronized(missing) { song.uri in missing }) return@withContext null

        val uri = Uri.parse(song.uri)
        var bmp: Bitmap? = null

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                bmp = ctx.contentResolver.loadThumbnail(uri, Size(480, 480), null)
            } catch (_: Exception) {
            }
        }
        if (bmp == null) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                val bytes = r.embeddedPicture
                if (bytes != null) bmp = decode(bytes)
            } catch (_: Exception) {
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        }

        if (bmp != null) cache.put(song.uri, bmp) else synchronized(missing) { missing.add(song.uri) }
        bmp
    }

    private fun decode(b: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 800) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(b, 0, b.size, opts)
    }
}
