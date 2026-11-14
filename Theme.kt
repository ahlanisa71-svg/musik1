package com.glassplayer.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

val Accent = Color(0xFFFFE9A8)
val SubText = Color.White.copy(alpha = 0.78f)

/** Gradasi latar seperti referensi: krem keemasan di atas → gelap di bawah. */
val BgBrush = Brush.verticalGradient(
    0.00f to Color(0xFFB9AC74),
    0.30f to Color(0xFF8C8058),
    0.62f to Color(0xFF3F3A29),
    1.00f to Color(0xFF0F0F0C),
)

/** Lapisan latar yang direkam; panel kaca memburamkan salinannya sebagai "kaca buram". */
val LocalBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

@Composable
fun ProvideBackdrop(content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    CompositionLocalProvider(LocalBackdrop provides layer, content = content)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            surface = Color(0xFF26241B),
            onSurface = Color.White,
            background = Color(0xFF0F0F0C),
            onBackground = Color.White,
        ),
    ) {
        ProvideBackdrop(content)
    }
}

/**
 * Efek kaca ala iOS (frosted glass):
 *  1. latar di belakang panel DIBURAMKAN (Android 12+),
 *  2. ditambah lapisan putih transparan bergradasi,
 *  3. dan garis tepi berkilau.
 * [strength] mengatur seberapa pekat kacanya.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    strength: Float = 1f,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val blurLayer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    remember(blurLayer) {
        blurLayer.renderEffect = BlurEffect(38f, 38f, TileMode.Clamp)
        true
    }

    Modifier
        .clip(shape)
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawWithContent {
            if (backdrop != null && Build.VERSION.SDK_INT >= 31) {
                val w = size.width.toInt()
                val h = size.height.toInt()
                if (w > 0 && h > 0) {
                    blurLayer.record(this, layoutDirection, IntSize(w, h)) {
                        translate(-pos.x, -pos.y) { drawLayer(backdrop) }
                    }
                    drawLayer(blurLayer)
                }
            }
            drawContent()
        }
        .background(Color.Black.copy(alpha = 0.10f * strength))
        .background(
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.28f * strength),
                    Color.White.copy(alpha = 0.08f * strength),
                )
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.70f),
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.35f),
                )
            ),
            shape,
        )
}

/** Isi latar bawaan: gradasi + gumpalan cahaya warna-warni agar efek buram terlihat jelas. */
@Composable
fun DefaultBackdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgBrush)
            .drawBehind {
                fun blob(cx: Float, cy: Float, r: Float, c: Color) {
                    val center = Offset(size.width * cx, size.height * cy)
                    val radius = size.width * r
                    drawCircle(
                        Brush.radialGradient(listOf(c, Color.Transparent), center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
                }
                blob(0.92f, 0.16f, 0.75f, Color(0x99FFE6A6))
                blob(0.04f, 0.38f, 0.65f, Color(0x80FF9F68))
                blob(0.88f, 0.60f, 0.65f, Color(0x70E88AA0))
                blob(0.12f, 0.85f, 0.80f, Color(0x704FB3A0))
            }
    )
}

/** Latar layar penuh yang sekaligus direkam ke [LocalBackdrop] untuk dipakai panel kaca. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = { DefaultBackdrop() },
) {
    val layer = LocalBackdrop.current
    Box(
        modifier
            .fillMaxSize()
            .drawWithContent {
                if (layer != null) {
                    layer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(layer)
                } else {
                    drawContent()
                }
            },
        content = content,
    )
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
