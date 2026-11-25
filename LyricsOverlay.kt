package com.glassplayer.app.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun LyricsOverlay(
    lyrics: LyricsDocument?,
    isLoading: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Mencari lirik sinkron...", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }
            lyrics == null || lyrics.lines.isEmpty() -> {
                Text(
                    text = "Lirik tidak tersedia untuk lagu ini",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                SynchronizedLyricsList(
                    lyrics = lyrics,
                    positionMs = positionMs,
                    onSeek = onSeek
                )
            }
        }
    }
}

@Composable
private fun SynchronizedLyricsList(
    lyrics: LyricsDocument,
    positionMs: Long,
    onSeek: (Long) -> Unit
) {
    val listState = remember(lyrics) { LazyListState() }

    val activeLine by remember(lyrics, positionMs) {
        derivedStateOf {
            if (!lyrics.synced) -1
            else lyrics.lines.indexOfLast { (it.startTimeMs ?: 0L) <= positionMs }
        }
    }

    LaunchedEffect(activeLine) {
        if (activeLine < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val viewport = layout.viewportSize.height
        if (viewport <= 0) return@LaunchedEffect

        val item = layout.visibleItemsInfo.find { it.index == activeLine }
        if (item == null) {
            listState.animateScrollToItem(activeLine, -viewport / 3)
        } else {
            val delta = (item.offset + item.size / 2) - (viewport / 2)
            if (abs(delta) > 6) {
                listState.animateScrollBy(
                    value = delta.toFloat(),
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Sumber: ${lyrics.source}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 48.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.14f to Color.Black,
                            0.86f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isActive = !lyrics.synced || index == activeLine
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.04f else 0.95f,
                    animationSpec = tween(260),
                    label = "lyricScale"
                )
                val inkColor by animateColorAsState(
                    targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.36f),
                    animationSpec = tween(260),
                    label = "lyricColor"
                )

                val wordTimed = lyrics.synced && index == activeLine && line.words.isNotEmpty()
                val styledText = if (wordTimed) {
                    buildWordProgressText(line, positionMs, Color.White, Color.White.copy(alpha = 0.4f))
                } else {
                    AnnotatedString(line.text.ifBlank { "♪" })
                }

                Text(
                    text = styledText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (wordTimed) Color.White.copy(alpha = 0.4f) else inkColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clickable(enabled = line.startTimeMs != null) {
                            line.startTimeMs?.let(onSeek)
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun buildWordProgressText(
    line: LyricLine,
    positionMs: Long,
    sungColor: Color,
    quietColor: Color
): AnnotatedString = buildAnnotatedString {
    append(line.text)
    var cursor = 0
    for (word in line.words) {
        val start = line.text.indexOf(word.text, cursor)
        if (start < 0) continue
        val end = start + word.text.length
        cursor = end

        when {
            positionMs >= word.endMs -> {
                addStyle(SpanStyle(color = sungColor), start, end)
            }
            positionMs in word.startMs until word.endMs -> {
                val frac = ((positionMs - word.startMs).toFloat() /
                        (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                addStyle(SpanStyle(color = lerp(quietColor, sungColor, frac)), start, end)
            }
        }
    }
}
