package com.glassplayer.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Menggambar piringan hitam + cover + jarum menjadi satu gambar, supaya ikut membesar/mengecil rapi di widget. */
object DiscArt {

    fun render(cover: Bitmap?, size: Int = 480): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = size / 480f
        c.scale(k, k)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = 220f
        val cy = 262f
        val r = 208f

        // piringan
        p.style = Paint.Style.FILL
        p.color = 0xFF0E0E10.toInt()
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0x33FFFFFF
        c.drawCircle(cx, cy, r, p)
        p.strokeWidth = 2f
        for ((rr, a) in listOf(188f to 0x22, 168f to 0x1A, 148f to 0x22, 128f to 0x1A)) {
            p.color = (a shl 24) or 0xFFFFFF
            c.drawCircle(cx, cy, rr, p)
        }

        // cover di tengah
        val cr = 96f
        if (cover != null) {
            val clip = Path().apply { addCircle(cx, cy, cr, Path.Direction.CW) }
            c.save()
            c.clipPath(clip)
            c.drawBitmap(cover, null, RectF(cx - cr, cy - cr, cx + cr, cy + cr), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            p.style = Paint.Style.FILL
            p.color = 0xFF2E2C26.toInt()
            c.drawCircle(cx, cy, cr, p)
        }
        // lubang tengah
        p.style = Paint.Style.FILL
        p.color = 0xFF0E0E10.toInt()
        c.drawCircle(cx, cy, 12f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = 0x55FFFFFF
        c.drawCircle(cx, cy, 12f, p)

        // jarum
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 10f
        p.color = 0xFFE4E4EC.toInt()
        c.drawLine(428f, 52f, 340f, 206f, p)
        p.strokeWidth = 22f
        p.color = 0xFFC9C9D4.toInt()
        c.drawLine(340f, 206f, 326f, 232f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF4A4458.toInt()
        c.drawCircle(428f, 52f, 26f, p)
        p.color = 0xFFE4E4EC.toInt()
        c.drawCircle(428f, 52f, 10f, p)
        return bmp
    }
}
