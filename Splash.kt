package com.glassplayer.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Animasi pembuka ±2 detik: cakram kaca → lingkaran emas melingkar → nada musik naik & berdenyut. */
@Composable
fun SplashScreen() {
    val disc = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val note = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { disc.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 180f)) }
        launch { ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        launch { delay(350); note.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch { delay(900); title.animateTo(1f, tween(600)) }
    }

    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseValue",
    )

    Box(Modifier.fillMaxSize()) {
        DefaultBackdrop()

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        val s = disc.value.coerceAtLeast(0f)
                        scaleX = s
                        scaleY = s
                        alpha = disc.value.coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // cakram kaca
                    drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f))))
                    drawCircle(Color.White.copy(alpha = 0.18f), style = Stroke(1.5.dp.toPx()))
                    // lingkaran emas yang menyapu
                    val w = 3.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.White.copy(alpha = 0.95f), Accent, Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.95f))
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * ring.value,
                        useCenter = false,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFFFF3CF),
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            alpha = note.value
                            translationY = (1f - note.value) * 28.dp.toPx()
                            scaleX = pulse.value
                            scaleY = pulse.value
                        },
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "PLAY MUSIX",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 12.dp.toPx()
                },
            )
        }
    }
}
