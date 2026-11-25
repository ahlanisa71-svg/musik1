package com.glassplayer.app.data

data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val dateAdded: Long = 0L,
    val folder: String = "",
    val path: String = "",
)
