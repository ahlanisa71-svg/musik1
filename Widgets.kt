package com.glassplayer.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Dasar semua widget: setiap kali diperbarui sistem, gambar ulang dari status terakhir. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Cari dan putar: pemutar mini + kolom cari. */
class PlayerSearchWidget : BaseWidget()

/** Lagu terbaru: pemutar mini + 4 cover lagu terakhir diputar. */
class RecentWidget : BaseWidget()

/** Piringan persegi: kartu kaca dengan piringan hitam + tombol putar. */
class RecommendWidget : BaseWidget()

/** Piringan persegi panjang: piringan di kiri, judul dan tombol putar di kanan. */
class DiscWideWidget : BaseWidget()

/** Widget pemutar kapsul: kontrol cepat putar/jeda. */
class CapsuleWidget : BaseWidget()

/** Minta Android menaruh widget di layar utama (jika launcher mendukung). */
fun pinWidget(ctx: Context, cls: Class<*>) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val ok = Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported
    if (ok) {
        mgr.requestPinAppWidget(ComponentName(ctx, cls), null, null)
    } else {
        Toast.makeText(
            ctx,
            "Tahan lama layar utama → Widget → Musix",
            Toast.LENGTH_LONG,
        ).show()
    }
}
