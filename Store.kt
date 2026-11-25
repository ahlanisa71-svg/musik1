package com.glassplayer.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Penyimpanan sederhana: favorit, terbaru, playlist, file tambahan manual, urutan. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("glass_store", Context.MODE_PRIVATE)

    var sortMode: Int
        get() = sp.getInt("sort", 0)
        set(v) { sp.edit().putInt("sort", v).apply() }

    var lockScreenEnabled: Boolean
        get() = sp.getBoolean("lock_screen", false)
        set(v) { sp.edit().putBoolean("lock_screen", v).apply() }

    var playerStyle: Int
        get() = sp.getInt("player_style", 0)
        set(v) { sp.edit().putInt("player_style", v).apply() }

    var eqEnabled: Boolean
        get() = sp.getBoolean("eq_on", false)
        set(v) { sp.edit().putBoolean("eq_on", v).apply() }
    var eqLevels: String
        get() = sp.getString("eq_levels", "") ?: ""
        set(v) { sp.edit().putString("eq_levels", v).apply() }
    var bassStrength: Int
        get() = sp.getInt("bass", 0)
        set(v) { sp.edit().putInt("bass", v).apply() }
    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) { sp.edit().putFloat("speed", v).apply() }
    var crossfadeSec: Int
        get() = sp.getInt("crossfade", 0)
        set(v) { sp.edit().putInt("crossfade", v).apply() }
    var lang: String
        get() = sp.getString("lang", "id") ?: "id"
        set(v) { sp.edit().putString("lang", v).apply() }
    var pureBlack: Boolean
        get() = sp.getBoolean("pure_black", false)
        set(v) { sp.edit().putBoolean("pure_black", v).apply() }
    var themeIndex: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }

    // Status terakhir untuk widget layar utama (ditulis oleh PlaybackService)
    var widgetTitle: String
        get() = sp.getString("w_title", "") ?: ""
        set(v) { sp.edit().putString("w_title", v).apply() }
    var widgetArtist: String
        get() = sp.getString("w_artist", "") ?: ""
        set(v) { sp.edit().putString("w_artist", v).apply() }
    var widgetUri: String
        get() = sp.getString("w_uri", "") ?: ""
        set(v) { sp.edit().putString("w_uri", v).apply() }
    var widgetPlaying: Boolean
        get() = sp.getBoolean("w_playing", false)
        set(v) { sp.edit().putBoolean("w_playing", v).apply() }

    fun loadList(key: String): List<String> = try {
        val a = JSONArray(sp.getString(key, "[]") ?: "[]")
        List(a.length()) { a.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveList(key: String, list: List<String>) {
        sp.edit().putString(key, JSONArray(list).toString()).apply()
    }

    fun loadPlaylists(): Map<String, List<String>> {
        val m = LinkedHashMap<String, List<String>>()
        try {
            val o = JSONObject(sp.getString("playlists", "{}") ?: "{}")
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = o.getJSONArray(k)
                m[k] = List(a.length()) { a.getString(it) }
            }
        } catch (_: Exception) {
        }
        return m
    }

    fun savePlaylists(m: Map<String, List<String>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        sp.edit().putString("playlists", o.toString()).apply()
    }

    fun loadExtra(): List<Song> = try {
        val a = JSONArray(sp.getString("extra", "[]") ?: "[]")
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Song(
                id = o.getLong("id"),
                uri = o.getString("uri"),
                title = o.getString("title"),
                artist = o.getString("artist"),
                album = o.optString("album", ""),
                albumId = 0,
                durationMs = o.optLong("dur", 0),
                dateAdded = o.optLong("added", 0),
                folder = "File manual",
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveExtra(list: List<Song>) {
        val a = JSONArray()
        list.forEach { s ->
            a.put(
                JSONObject()
                    .put("id", s.id).put("uri", s.uri).put("title", s.title)
                    .put("artist", s.artist).put("album", s.album)
                    .put("dur", s.durationMs).put("added", s.dateAdded)
            )
        }
        sp.edit().putString("extra", a.toString()).apply()
    }
}
