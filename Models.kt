package com.glassplayer.app.data

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val folder: String = "",
)

fun Song.artworkUri(): Uri? =
    if (albumId > 0) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    else null

fun Song.toMediaItem(): MediaItem {
    val u = Uri.parse(uri)
    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(u)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(u).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri())
                .build()
        )
        .build()
}
