package com.glassplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore.Audio.Media as A
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val UNKNOWN_ARTIST = "Artis tidak dikenal"

object MediaRepository {

    private fun canOpen(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.close()
        true
    } catch (_: java.io.FileNotFoundException) {
        false
    } catch (_: Exception) {
        true
    }

    /** Memindai SEMUA file audio yang diindeks Android (mp3, m4a, flac, wav, ogg, opus, dll). */
    suspend fun scan(ctx: Context): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val folderCol = if (Build.VERSION.SDK_INT >= 29) A.RELATIVE_PATH else A.DATA
        val projection = arrayOf(
            A._ID, A.TITLE, A.ARTIST, A.ALBUM, A.ALBUM_ID, A.DURATION, A.DATE_ADDED,
            A.DISPLAY_NAME, A.IS_RINGTONE, A.IS_NOTIFICATION, A.IS_ALARM, folderCol, A.DATA,
        )
        try {
            ctx.contentResolver.query(A.EXTERNAL_CONTENT_URI, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(A._ID)
                val iTitle = c.getColumnIndexOrThrow(A.TITLE)
                val iArtist = c.getColumnIndexOrThrow(A.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(A.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(A.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(A.DURATION)
                val iAdded = c.getColumnIndexOrThrow(A.DATE_ADDED)
                val iName = c.getColumnIndexOrThrow(A.DISPLAY_NAME)
                val iRing = c.getColumnIndexOrThrow(A.IS_RINGTONE)
                val iNotif = c.getColumnIndexOrThrow(A.IS_NOTIFICATION)
                val iAlarm = c.getColumnIndexOrThrow(A.IS_ALARM)
                val iFolder = c.getColumnIndexOrThrow(folderCol)
                val iData = c.getColumnIndexOrThrow(A.DATA)
                while (c.moveToNext()) {
                    if (c.getInt(iRing) == 1 || c.getInt(iNotif) == 1 || c.getInt(iAlarm) == 1) continue
                    val dur = c.getLong(iDur)
                    if (dur in 1..4999) continue
                    val id = c.getLong(iId)
                    // Lewati entri "hantu": tercatat di indeks Android tetapi filenya sudah tidak ada / tidak bisa dibuka.
                    if (!canOpen(ctx, ContentUris.withAppendedId(A.EXTERNAL_CONTENT_URI, id))) continue
                    // Lewati entri "hantu": ada di indeks Android tapi filenya sudah tidak ada / kosong.
                    if (!playable(ctx, id, c.getString(iData).orEmpty())) continue
                    val name = c.getString(iName).orEmpty().substringBeforeLast('.')
                    val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.ifBlank { "Tanpa judul" }
                    val artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ARTIST
                    val album = c.getString(iAlbum)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
                    val rawFolder = c.getString(iFolder).orEmpty()
                    val folder = if (Build.VERSION.SDK_INT >= 29) {
                        rawFolder.trimEnd('/').ifBlank { "Penyimpanan" }
                    } else {
                        rawFolder.substringBeforeLast('/', "").substringAfterLast('/').ifBlank { "Penyimpanan" }
                    }
                    out += Song(
                        id = id,
                        uri = ContentUris.withAppendedId(A.EXTERNAL_CONTENT_URI, id).toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = c.getLong(iAlbumId),
                        durationMs = dur,
                        dateAdded = c.getLong(iAdded),
                        folder = folder,
                        path = c.getString(iData).orEmpty(),
                    )
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        out
    }

    private fun playable(ctx: Context, id: Long, path: String): Boolean {
        if (path.isNotEmpty()) {
            val f = java.io.File(path)
            if (f.exists() && f.length() > 0) return true
        }
        return try {
            ctx.contentResolver.openAssetFileDescriptor(ContentUris.withAppendedId(A.EXTERNAL_CONTENT_URI, id), "r")
                ?.use { it.length != 0L } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Membaca satu file (dari pemilih file atau dibuka dari file manager). */
    fun readFromUri(ctx: Context, uri: Uri): Song? {
        var name: String? = null
        try {
            if (uri.scheme == "content") {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0)
                }
            } else {
                name = uri.lastPathSegment
            }
        } catch (_: Exception) {
        }
        val fallback = (name ?: "Tanpa judul").substringBeforeLast('.')
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var dur = 0L
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        val s = uri.toString()
        return Song(
            id = s.hashCode().toLong(),
            uri = s,
            title = title?.takeIf { it.isNotBlank() } ?: fallback,
            artist = artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST,
            album = album ?: "",
            albumId = 0,
            durationMs = dur,
            dateAdded = System.currentTimeMillis() / 1000,
            folder = "File manual",
        )
    }
}
