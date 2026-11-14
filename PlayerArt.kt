package com.glassplayer.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.data.Song

val PlayerStyleNames = listOf("Bawaan", "Vinyl modern", "Vinyl klasik", "CD", "Kaset")
val PlayerStyleDescs = listOf(
    "Cover album persegi membulat",
    "Piringan hitam berputar",
    "Piringan hitam dengan jarum",
    "Cakram CD mengilap",
    "Kaset dengan roda berputar",
)

/** Sudut putar yang terus bertambah selama [playing]; dibaca di fase gambar agar tidak merekomposisi. */
@Composable
fun rememberSpin(playing: Boolean, degPerSec: Float = 40f): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * degPerSec) % 360f
                last = now
            }
        }
    }
    return angle
}

@Composable
fun PlayerArt(song: Song?, style: Int, playing: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val spin = rememberSpin(playing)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (style) {
            1 -> Disc(song, spin, size * 0.94f, 0.36f, Color(0xFF111111), Color(0x33FFFFFF))
            2 -> VinylClassic(song, spin, playing, size)
            3 -> CdDisc(song, spin, size * 0.94f)
            4 -> Cassette(song, spin, size)
            else -> ArtImage(
                song,
                Modifier.size(size).shadow(24.dp, RoundedCornerShape(32.dp)),
                RoundedCornerShape(32.dp),
                iconSize = 72.dp,
            )
        }
    }
}

@Composable
private fun Disc(
    song: Song?,
    spin: State<Float>,
    d: Dp,
    labelFrac: Float,
    base: Color,
    groove: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(d)
            .shadow(20.dp, CircleShape)
            .graphicsLayer { rotationZ = spin.value }
            .clip(CircleShape)
            .background(base),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            var rr = r * 0.97f
            while (rr > r * labelFrac) {
                drawCircle(groove, rr, style = Stroke(1.dp.toPx()))
                rr -= 5.dp.toPx()
            }
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent,
                        Color.White.copy(alpha = 0.14f), Color.Transparent,
                    )
                ),
                r,
            )
        }
        ArtImage(song, Modifier.size(d * labelFrac), CircleShape, iconSize = 24.dp)
        Box(Modifier.size(d * 0.05f).clip(CircleShape).background(base))
    }
}

@Composable
private fun VinylClassic(song: Song?, spin: State<Float>, playing: Boolean, size: Dp) {
    val arm by animateFloatAsState(if (playing) 22f else 0f, tween(600), label = "arm")
    Box(Modifier.size(size)) {
        Disc(
            song, spin, size * 0.86f, 0.34f, Color(0xFF1A1410), Color(0x22FFE9A8),
            Modifier.align(Alignment.BottomStart),
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val pivot = Offset(w * 0.94f, h * 0.07f)
            drawCircle(Color(0xFF6B6455), w * 0.045f, pivot)
            drawCircle(Color(0xFFD8D2C0), w * 0.028f, pivot)
            rotate(arm, pivot) {
                val end = Offset(pivot.x, pivot.y + h * 0.70f)
                drawLine(Color(0xFFD8D2C0), pivot, end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawRoundRect(
                    Color(0xFF2B2A26),
                    topLeft = Offset(end.x - 7.dp.toPx(), end.y - 4.dp.toPx()),
                    size = Size(14.dp.toPx(), 22.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun CdDisc(song: Song?, spin: State<Float>, d: Dp) {
    Box(Modifier.size(d).shadow(20.dp, CircleShape), contentAlignment = Alignment.Center) {
        ArtImage(
            song,
            Modifier.fillMaxSize().graphicsLayer { rotationZ = spin.value },
            CircleShape,
            iconSize = 64.dp,
        )
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color(0x55FF5F6D), Color(0x55FFC371), Color(0x5563E6BE),
                        Color(0x555B8DEF), Color(0x55C86DD7), Color(0x55FF5F6D),
                    )
                ),
                r,
            )
            drawCircle(Color.White.copy(alpha = 0.55f), r * 0.25f)
            drawCircle(Color(0xFF0F0F0C), r * 0.10f)
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.98f, style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun Cassette(song: Song?, spin: State<Float>, size: Dp) {
    val h = size * 0.68f
    Box(Modifier.size(size, h).shadow(18.dp, RoundedCornerShape(16.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val ht = this.size.height
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF3A372E), Color(0xFF1E1C17))),
                cornerRadius = CornerRadius(16.dp.toPx()),
            )
            // label
            drawRoundRect(
                Color(0xFFF1E6C6),
                topLeft = Offset(w * 0.07f, ht * 0.07f),
                size = Size(w * 0.86f, ht * 0.44f),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
            // jendela pita
            drawRoundRect(
                Color(0xFF14130F),
                topLeft = Offset(w * 0.18f, ht * 0.56f),
                size = Size(w * 0.64f, ht * 0.22f),
                cornerRadius = CornerRadius(ht * 0.11f),
            )
            val ry = ht * 0.67f
            for (cx in listOf(w * 0.32f, w * 0.68f)) {
                val c = Offset(cx, ry)
                drawCircle(Color(0xFFE8E2D0), ht * 0.085f, c)
                rotate(spin.value, c) {
                    for (k in 0 until 6) {
                        rotate(k * 60f, c) {
                            drawLine(
                                Color(0xFF14130F),
                                c,
                                Offset(c.x, c.y - ht * 0.075f),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }
                drawCircle(Color(0xFF14130F), ht * 0.03f, c)
            }
            // dasar
            drawRoundRect(
                Color(0xFF2A2822),
                topLeft = Offset(w * 0.22f, ht * 0.82f),
                size = Size(w * 0.56f, ht * 0.15f),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            for (cx in listOf(w * 0.30f, w * 0.70f)) {
                drawCircle(Color(0xFF0F0F0C), ht * 0.03f, Offset(cx, ht * 0.895f))
            }
            for (p in listOf(Offset(w * 0.04f, ht * 0.05f), Offset(w * 0.96f, ht * 0.05f), Offset(w * 0.04f, ht * 0.95f), Offset(w * 0.96f, ht * 0.95f))) {
                drawCircle(Color(0xFF8C8670), 2.5.dp.toPx(), p)
            }
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = h * 0.10f)
                .size(size * 0.78f, h * 0.38f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                song?.title ?: "Musix",
                color = Color(0xFF2B2A26),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                song?.artist ?: "",
                color = Color(0xFF5A5546),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
