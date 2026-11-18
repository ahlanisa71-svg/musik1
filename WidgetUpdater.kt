package com.glassplayer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver
import com.glassplayer.app.Actions
import com.glassplayer.app.MainActivity
import com.glassplayer.app.R
import com.glassplayer.app.data.ArtLoader
import com.glassplayer.app.data.Song
import com.glassplayer.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Menggambar ulang semua widget layar utama dari status terakhir pemutar. */
object WidgetUpdater {

    suspend fun refresh(context: Context) = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)

        fun idsOf(cls: Class<*>) = mgr.getAppWidgetIds(ComponentName(app, cls))
        val playerIds = idsOf(PlayerSearchWidget::class.java)
        val recentIds = idsOf(RecentWidget::class.java)
        val recommendIds = idsOf(RecommendWidget::class.java)
        val capsuleIds = idsOf(CapsuleWidget::class.java)
        val discWideIds = idsOf(DiscWideWidget::class.java)
        if (playerIds.isEmpty() && recentIds.isEmpty() && recommendIds.isEmpty() &&
            capsuleIds.isEmpty() && discWideIds.isEmpty()
        ) {
            return@withContext
        }

        val st = Store(app)
        val cover = cover(app, st.widgetUri, 200)
        val recentUris = st.loadList("recent").take(4)

        if (playerIds.isNotEmpty()) {
            val rv = RemoteViews(app.packageName, R.layout.widget_player_search)
            bindPlayer(app, rv, st, cover)
            rv.setOnClickPendingIntent(R.id.w_search, openApp(app, Actions.OPEN_SEARCH, null, 11))
            mgr.updateAppWidget(playerIds, rv)
        }

        if (recentIds.isNotEmpty()) {
            val rv = RemoteViews(app.packageName, R.layout.widget_recent)
            bindPlayer(app, rv, st, cover)
            val ids = intArrayOf(R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4)
            for (i in ids.indices) {
                val uri = recentUris.getOrNull(i)
                val bmp = if (uri != null) cover(app, uri, 140) else null
                if (bmp != null) rv.setImageViewBitmap(ids[i], bmp)
                else rv.setImageViewResource(ids[i], R.drawable.ic_w_music)
                rv.setOnClickPendingIntent(
                    ids[i],
                    if (uri != null) openApp(app, Actions.PLAY_URI, Uri.parse(uri), 20 + i)
                    else openApp(app, null, null, 20 + i),
                )
            }
            mgr.updateAppWidget(recentIds, rv)
        }

        if (recommendIds.isNotEmpty()) {
            val rv = RemoteViews(app.packageName, R.layout.widget_recommend)
            bindPlayer(app, rv, st, cover)
            mgr.updateAppWidget(recommendIds, rv)
        }

        if (discWideIds.isNotEmpty()) {
            val rv = RemoteViews(app.packageName, R.layout.widget_disc_wide)
            bindPlayer(app, rv, st, cover)
            mgr.updateAppWidget(discWideIds, rv)
        }

        if (capsuleIds.isNotEmpty()) {
            val rv = RemoteViews(app.packageName, R.layout.widget_capsule)
            bindPlayer(app, rv, st, cover)
            mgr.updateAppWidget(capsuleIds, rv)
        }
    }

    /** Mengisi judul, cover, ikon putar/jeda, dan tombol. Id yang tidak ada di layout diabaikan. */
    private fun bindPlayer(app: Context, rv: RemoteViews, st: Store, cover: Bitmap?) {
        rv.setTextViewText(R.id.w_title, st.widgetTitle.ifBlank { "Musix" })
        rv.setTextViewText(R.id.w_artist, st.widgetArtist.ifBlank { "Ketuk untuk memutar musik" })
        if (cover != null) rv.setImageViewBitmap(R.id.w_cover, cover)
        else rv.setImageViewResource(R.id.w_cover, R.drawable.ic_w_music)
        rv.setImageViewResource(R.id.w_play, if (st.widgetPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play)

        rv.setOnClickPendingIntent(R.id.w_prev, mediaKey(app, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        // Belum ada lagu yang pernah diputar: tombol putar membuka aplikasi dan memutar acak.
        rv.setOnClickPendingIntent(
            R.id.w_play,
            if (st.widgetTitle.isBlank()) openApp(app, Actions.SHUFFLE_ALL, null, 31)
            else mediaKey(app, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        )
        rv.setOnClickPendingIntent(R.id.w_next, mediaKey(app, KeyEvent.KEYCODE_MEDIA_NEXT))
        rv.setOnClickPendingIntent(R.id.w_root, openApp(app, null, null, 10))
    }

    /** Tombol media -> MediaButtonReceiver Media3 -> PlaybackService (jalan tanpa membuka aplikasi). */
    private fun mediaKey(app: Context, keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(app, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return PendingIntent.getBroadcast(
            app, keyCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openApp(app: Context, action: String?, data: Uri?, req: Int): PendingIntent {
        val intent = Intent(app, MainActivity::class.java).apply {
            if (action != null) this.action = action
            if (data != null) this.data = data
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            app, req, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private suspend fun cover(app: Context, uri: String, px: Int): Bitmap? {
        if (uri.isEmpty()) return null
        val src = ArtLoader.load(app, Song(0, uri, "", "", "", 0, 0, 0)) ?: return null
        return try {
            val s = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
            Bitmap.createScaledBitmap(square, px, px, true)
        } catch (_: Exception) {
            null
        }
    }
}
