package com.glassplayer.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility as DlgAnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState as dlgAnimateFloat
import androidx.compose.animation.fadeIn as dlgFadeIn
import androidx.compose.animation.scaleIn as dlgScaleIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.MusicViewModel
import com.glassplayer.app.data.ArtLoader
import com.glassplayer.app.data.Song
import kotlin.math.abs

val GroupRadius = 26.dp

/** Waktu (uptime) saat layar saat ini mulai tampil; dipakai agar animasi beruntun hanya jalan saat layar baru dibuka. */
val LocalScreenBorn = compositionLocalOf { 0L }

/** false selama animasi pembuka masih tampil; animasi beruntun menunggu sampai true. */
val LocalScreenReady = compositionLocalOf { true }

/** Muncul beruntun dari atas dengan fade: item ke-[index] tampil sedikit setelah item sebelumnya. */
fun Modifier.cascade(index: Int): Modifier = composed {
    val born = LocalScreenBorn.current
    val ready = LocalScreenReady.current
    val animate = remember { index < 10 && SystemClock.uptimeMillis() - born < 500L }
    val a = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(ready) {
        if (animate && ready) a.animateTo(1f, tween(180, delayMillis = index * 12, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * -12.dp.toPx()
    }
}

/** Bentuk baris dalam satu kelompok daftar (sudut membulat hanya di ujung atas/bawah). */
fun rowShape(index: Int, count: Int, headerAbove: Boolean): Shape {
    val top = if (index == 0 && !headerAbove) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

class SongActions(
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onToggleFav: (Song) -> Unit,
    val onAddToPlaylist: (Song) -> Unit,
    val onDelete: (Song) -> Unit,
    val onRemoveFromPlaylist: ((Song) -> Unit)? = null,
    val onDetails: ((Song) -> Unit)? = null,
    val onEdit: ((Song) -> Unit)? = null,
)

@Composable
fun GlassIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Color.White,
) {
    Box(
        modifier
            .size(size)
            .glass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ArtImage(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    seed: String = song?.title ?: "",
    iconSize: Dp = 24.dp,
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = song?.let { ArtLoader.peek(it.uri) }, song?.uri) {
        value = if (song == null) null else ArtLoader.load(ctx, song)
    }
    val hue = (abs(seed.hashCode()) % 360).toFloat()
    Box(
        modifier
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color.hsv(hue, 0.42f, 0.62f), Color.hsv((hue + 40f) % 360f, 0.55f, 0.36f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun EqualizerBars(playing: Boolean, color: Color = Accent, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, c).forEach { f ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (playing) (16f * f).dp else 4.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SongMenuDialog(
    song: Song,
    favorite: Boolean,
    actions: SongActions,
    onDismiss: () -> Unit,
) {
    @Composable
    fun Item(text: String, color: Color = Color.White, onClick: () -> Unit) {
        Text(
            text,
            color = color,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onDismiss(); onClick() }
                .padding(horizontal = 8.dp, vertical = 13.dp),
        )
    }
    MusixDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF26241B),
        title = {
            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
        },
        text = {
            Column {
                Item("Putar nanti") { actions.onPlayNext(song) }
                Item("Tambah ke antrian") { actions.onAddToQueue(song) }
                Item("Tambah ke daftar putar") { actions.onAddToPlaylist(song) }
                Item(if (favorite) "Hapus dari favorit" else "Tambah ke favorit") { actions.onToggleFav(song) }
                actions.onRemoveFromPlaylist?.let { rm -> Item("Hapus dari daftar putar ini") { rm(song) } }
                actions.onDetails?.let { d -> Item("Detail lagu") { d(song) } }
                actions.onEdit?.let { e -> Item("Edit info lagu") { e(song) } }
                Item("Hapus", Color(0xFFFF8A80)) { actions.onDelete(song) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = Accent) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    current: Boolean,
    playing: Boolean,
    favorite: Boolean,
    shape: Shape,
    actions: SongActions,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.14f))
            .background(Color.White.copy(alpha = if (current) 0.22f else 0.11f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(song, Modifier.size(50.dp), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (current) Accent else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (song.durationMs > 0) "${song.artist} • ${fmtTime(song.durationMs)}" else song.artist,
                color = SubText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) {
            EqualizerBars(playing, modifier = Modifier.padding(horizontal = 6.dp))
        }
        if (favorite) {
            Icon(Icons.Rounded.Favorite, null, tint = Accent, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White.copy(alpha = 0.8f))
        }
    }
    if (menu) SongMenuDialog(song, favorite, actions) { menu = false }
}

@Composable
fun MiniPlayer(vm: MusicViewModel, onClick: () -> Unit) {
    val song = vm.currentSong ?: return
    val currentItem by vm.currentItem.collectAsState()
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(vm.isPlaying, vm.currentId) { if (vm.isPlaying) dismissed = false }
    if (dismissed) return
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dx.value
                translationY = dy.value
                alpha = 1f - (abs(dx.value) / 900f + dy.value / 500f).coerceIn(0f, 0.6f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val x = dx.value
                            val y = dy.value
                            when {
                                y > 110f && y > abs(x) -> {
                                    dy.animateTo(420f, tween(160))
                                    vm.pauseIfPlaying()
                                    dismissed = true
                                    dx.snapTo(0f)
                                    dy.snapTo(0f)
                                }
                                x < -140f -> {
                                    dx.animateTo(-640f, tween(140))
                                    vm.next()
                                    dx.snapTo(640f)
                                    dx.animateTo(0f, tween(220))
                                }
                                x > 140f -> {
                                    dx.animateTo(640f, tween(140))
                                    vm.previous()
                                    dx.snapTo(-640f)
                                    dx.animateTo(0f, tween(220))
                                }
                                else -> {
                                    launch { dx.animateTo(0f, spring()) }
                                    dy.animateTo(0f, spring())
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { dx.animateTo(0f, spring()) }
                            dy.animateTo(0f, spring())
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        scope.launch {
                            dx.snapTo(dx.value + amount.x)
                            dy.snapTo((dy.value + amount.y).coerceAtLeast(0f))
                        }
                    },
                )
            }
            .glass(RoundedCornerShape(24.dp), 1.2f)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val spin = rememberSpin(vm.isPlaying, 24f)
            val artwork = currentItem?.takeIf { it.isYouTube }?.artworkUri
            if (artwork != null) {
                RemoteImage(
                    artwork.toString(),
                    Modifier.size(44.dp).graphicsLayer { rotationZ = spin.value }.clip(CircleShape),
                )
            } else {
                ArtImage(song, Modifier.size(44.dp).graphicsLayer { rotationZ = spin.value }, CircleShape)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = vm::next) {
                Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        LinearProgressIndicator(
            progress = { vm.progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(3.dp)
                .clip(CircleShape),
            color = Accent,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Home", Icons.Rounded.Home, 0),
        Triple("New", Icons.Rounded.GridView, 1),
        Triple("Radio", Icons.Rounded.Radio, 2),
        Triple("Library", Icons.Rounded.MusicNote, 3),
        Triple("Search", Icons.Rounded.Search, 4),
    )
    val pill = RoundedCornerShape(32.dp)
    Row(
        Modifier.fillMaxWidth().clip(pill).background(Color.Black.copy(alpha = 0.55f))
            .border(1.5.dp, Color.White.copy(alpha = 0.30f), pill).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { (label, icon, idx) ->
            val sel = idx == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(26.dp))
                    .background(if (sel) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(idx) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, label, tint = if (sel) Accent else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(21.dp))
                Text(label, fontSize = 10.sp, color = if (sel) Accent else Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/**
 * Pengganti AlertDialog tanpa animasi jendela bawaan sistem (yang tampak "dobel/berhantu").
 * Animasi masuk dibuat sendiri: scrim memudar + kartu membesar halus.
 */
@Composable
fun MusixDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = Color(0xFF26241B),
    titleContentColor: Color = Color.White,
    textContentColor: Color = Color.White,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val w = (view.parent as? DialogWindowProvider)?.window
            w?.setWindowAnimations(-1)
            w?.setDimAmount(0f)
            w?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrim by dlgAnimateFloat(if (shown) 0.10f else 0f, tween(220), label = "scrim")
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest),
            contentAlignment = Alignment.Center,
        ) {
            DlgAnimatedVisibility(
                visible = shown,
                enter = dlgFadeIn(tween(200)) + dlgScaleIn(tween(240), initialScale = 0.92f),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 28.dp)
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(containerColor)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .padding(24.dp),
                ) {
                    if (title != null) {
                        CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp)) { title() }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (text != null) {
                        CompositionLocalProvider(LocalContentColor provides textContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)) { text() }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
