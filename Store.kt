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
