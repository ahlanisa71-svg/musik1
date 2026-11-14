package com.glassplayer.app.data

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Equalizer + Bass Boost yang dipasang ke sesi audio pemutar.
 * Service dan UI berada di satu proses, jadi UI bisa mengubahnya langsung.
 */
object AudioFx {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var store: Store? = null
    private var loaded = false

    var attached by mutableStateOf(false)
    var enabled by mutableStateOf(false)
    var bassStrength by mutableIntStateOf(0)           // 0..1000
    var bandCount by mutableIntStateOf(5)
    var minLevel by mutableIntStateOf(-1500)           // milli-dB
    var maxLevel by mutableIntStateOf(1500)
    var freqs by mutableStateOf(listOf(60000, 230000, 910000, 3600000, 14000000)) // milli-Hz
    var presets by mutableStateOf<List<String>>(emptyList())
    val levels = mutableStateListOf(0, 0, 0, 0, 0)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val st = Store(ctx.applicationContext)
        store = st
        enabled = st.eqEnabled
        bassStrength = st.bassStrength
        val saved = st.eqLevels.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (saved.isNotEmpty()) {
            levels.clear()
            levels.addAll(saved)
            bandCount = saved.size
        }
    }

    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        try {
            val e = Equalizer(0, sessionId)
            val r = e.bandLevelRange
            minLevel = r[0].toInt()
            maxLevel = r[1].toInt()
            val n = e.numberOfBands.toInt()
            bandCount = n
            freqs = List(n) { e.getCenterFreq(it.toShort()) }
            presets = List(e.numberOfPresets.toInt()) { e.getPresetName(it.toShort()) }
            while (levels.size < n) levels.add(0)
            while (levels.size > n) levels.removeAt(levels.lastIndex)
            eq = e
        } catch (_: Throwable) {
            eq = null
        }
        try {
            val b = BassBoost(0, sessionId)
            if (b.strengthSupported) bass = b else b.release()
        } catch (_: Throwable) {
            bass = null
        }
        attached = eq != null
        apply()
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        eq = null
        bass = null
        attached = false
    }

    private fun apply() {
        eq?.let { e ->
            try {
                e.enabled = enabled
                for (i in 0 until minOf(bandCount, levels.size)) {
                    e.setBandLevel(i.toShort(), levels[i].coerceIn(minLevel, maxLevel).toShort())
                }
            } catch (_: Throwable) {}
        }
        bass?.let { b ->
            try {
                b.setStrength(bassStrength.coerceIn(0, 1000).toShort())
                b.enabled = enabled && bassStrength > 0
            } catch (_: Throwable) {}
        }
    }

    private fun save() {
        store?.let {
            it.eqEnabled = enabled
            it.bassStrength = bassStrength
            it.eqLevels = levels.joinToString(",")
        }
    }

    fun setEnabled(on: Boolean) { enabled = on; apply(); save() }

    fun setLevel(band: Int, value: Int) {
        if (band !in levels.indices) return
        levels[band] = value.coerceIn(minLevel, maxLevel)
        apply(); save()
    }

    fun setBass(v: Int) { bassStrength = v.coerceIn(0, 1000); apply(); save() }

    fun usePreset(index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
            for (i in 0 until minOf(bandCount, levels.size)) levels[i] = e.getBandLevel(i.toShort()).toInt()
            enabled = true
            apply(); save()
        } catch (_: Throwable) {}
    }

    fun reset() {
        for (i in levels.indices) levels[i] = 0
        bassStrength = 0
        apply(); save()
    }
}
