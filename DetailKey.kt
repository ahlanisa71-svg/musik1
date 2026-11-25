package com.glassplayer.app

sealed interface DetailKey {
    data object Recent : DetailKey
    data object Newest : DetailKey
    data object All : DetailKey
    data object Favorites : DetailKey
    data object Downloads : DetailKey
    data class Playlist(val name: String) : DetailKey
    data class Album(val name: String) : DetailKey
    data class Artist(val name: String) : DetailKey
    data class Folder(val name: String) : DetailKey
    data class Mix(val id: String) : DetailKey
}
