package com.glassplayer.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.DetailKey
import com.glassplayer.app.MusicViewModel
import com.glassplayer.app.data.Song
import kotlinx.coroutines.delay

private val Gold = Color(0xFFE3A93A)
/** Tepi kaca: terang di sudut, redup di tengah. */
private val GlassRim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.40f)))
private val GoldDark = Color(0xFF8F6A1B)
private val GoldLight = Color(0xFFC9962B)

private class HeroSlide(val title: String, val sub: String, val songs: List<Song>, val shuffle: Boolean)
private class MixCard(val title: String, val songs: List<Song>, val key: DetailKey, val shuffle: Boolean = false)

/** Beranda baru: "Nikmati musik favoritmu", kartu spesial, "Untuk Kamu", "Lagu Populer". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: MusicViewModel,
    actions: SongActions,
    onOpenDrawer: () -> Unit,
    onTab: (Int) -> Unit,
    onDetail: (DetailKey) -> Unit,
) {
    val ctx = LocalContext.current
    val isOnline by rememberNetworkConnected(ctx)
    // Font tulisan tangan: pakai res/font/script_font.ttf bila ada, kalau tidak pakai cursive bawaan.
    val scriptFont = remember {
        val id = ctx.resources.getIdentifier("script_font", "font", ctx.packageName)
        if (id != 0) FontFamily(Font(id)) else FontFamily.Cursive
    }
    val favorites = vm.favorites.toList()
    val recents = vm.recents.toList()
    val all = vm.allSongs.toList()
    val favSet = remember(favorites) { favorites.toSet() }
    val favs = remember(favorites, all) { vm.songsOf(favorites) }
    val newest = remember(all) { vm.listFor(DetailKey.Newest) }
    val recent = remember(recents, all) { vm.songsOf(recents) }
    val mid = remember(all) { vm.listFor(DetailKey.Mix("mid")) }
    val popular = remember(all, vm.playCountsVersion) { vm.popular(8) }
    val onlineTracks by vm.homeOnlineTracks.collectAsState()
    val isLoadingOnline by vm.isSearchingHomeOnline.collectAsState()

    LaunchedEffect(isOnline) {
        if (isOnline) vm.loadHomeOnlineTracks() else vm.clearHomeOnlineTracks()
    }

    val slides = remember(all, favs, newest, mid) {
        listOf(
            HeroSlide("Malam yang\nLebih Tenang", "Kumpulan lagu pilihan untuk\ntemani malam harimu.", mid.ifEmpty { all }, true),
            HeroSlide("Favorit\nKamu", "Lagu-lagu yang paling\nkamu sukai.", favs, false),
            HeroSlide("Baru\nDitambahkan", "Lagu terbaru yang masuk\nke perangkatmu.", newest.take(30), false),
            HeroSlide("Acak\nSemua", "Semua lagumu diputar\nacak untukmu.", all, true),
        ).filter { it.songs.isNotEmpty() }
    }
    val mixes = remember(all, favs, recent, newest, mid) {
        listOf(
            MixCard("Favorit Kamu", favs, DetailKey.Favorites),
            MixCard("Terkini", recent, DetailKey.Recent),
            MixCard("Baru Ditambah", newest, DetailKey.Newest),
            MixCard("Santai Sejenak", mid, DetailKey.Mix("mid"), shuffle = true),
        ).filter { it.songs.isNotEmpty() }
    }

    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 230.dp)) {
        // Bilah atas: tidak diubah (menu, "Play Musix", ikon musik)
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Rounded.Menu, "Menu", onOpenDrawer)
                Spacer(Modifier.width(14.dp))
                Text("Play Musix", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.padding(end = 10.dp).size(28.dp))
            }
        }
        // Judul
        item {
            Column(Modifier.padding(start = 22.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
                Text("Nikmati musik", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
                Text(
                    "favoritmu",
                    color = Gold,
                    fontSize = 40.sp,
                    fontFamily = scriptFont,
                    lineHeight = 40.sp,
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(56.dp).height(2.dp).background(Gold))
            }
        }
        if (isOnline) {
            item { SectionHead("Musik Online", "Cari lainnya") { onTab(4) } }
            if (onlineTracks.isEmpty()) {
                item {
                    Text(
                        if (isLoadingOnline) "Memuat lagu online…" else "Lagu online belum tersedia. Coba lagi nanti.",
                        color = SubText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(onlineTracks, key = { track -> track.id }) { track ->
                            Column(
                                Modifier
                                    .width(148.dp)
                                    .clickable { vm.playItem(track, onlineTracks) },
                            ) {
                                RemoteImage(
                                    track.artworkUri?.toString().orEmpty(),
                                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }
        } else {
        // Kartu spesial
        if (slides.isNotEmpty()) item {
            val pager = rememberPagerState { slides.size }
            LaunchedEffect(slides.size) {
                while (slides.size > 1) {
                    delay(5000)
                    pager.animateScrollToPage((pager.currentPage + 1) % slides.size)
                }
            }
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(220.dp)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { p ->
                    val s = slides[p]
                    val art = s.songs.firstOrNull { it.uri.isNotEmpty() }?.let { s.songs[kotlin.math.abs(s.title.hashCode()) % s.songs.size] }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF14110C))
                            .border(BorderStroke(1.3.dp, GlassRim), RoundedCornerShape(26.dp)),
                    ) {
                        ArtImage(art, Modifier.fillMaxSize(), RectangleShape, seed = s.title, iconSize = 1.dp)
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.55f), Color.Transparent))
                            )
                        )
                        Column(Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 120.dp)) {
                            Text("PLAYLIST SPESIAL", color = Gold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(s.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(s.sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp)
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(GoldLight, GoldDark)))
                                    .clickable { if (s.shuffle) vm.shuffleAll(s.songs) else vm.playList(s.songs, 0) }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Putar Sekarang", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(slides.size) { i ->
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) Gold else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        // Untuk Kamu
        if (mixes.isNotEmpty()) {
            item { SectionHead("Untuk Kamu", "Lihat semua") { onTab(2) } }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(mixes, key = { _, m -> "mix:" + m.title }) { _, m ->
                        val art = m.songs[kotlin.math.abs(m.title.hashCode()) % m.songs.size]
                        val shape = RoundedCornerShape(22.dp)
                        Box(
                            Modifier
                                .width(128.dp)
                                .height(196.dp)
                                .clip(shape)
                                .background(Color(0xFF14110C))
                                .border(BorderStroke(1.3.dp, GlassRim), shape)
                                .clickable { onDetail(m.key) },
                        ) {
                            ArtImage(art, Modifier.fillMaxSize(), RectangleShape, seed = m.title)
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0.45f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.88f),
                                    )
                                )
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(34.dp)
                                    .glass(CircleShape, 1.4f)
                                    .clickable { if (m.shuffle) vm.shuffleAll(m.songs) else vm.playList(m.songs, 0) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.PlayArrow, "Putar", tint = Color.White, modifier = Modifier.size(20.dp)) }
                            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 12.dp)) {
                                Text(m.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${m.songs.size} lagu", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        // Lagu Populer
        if (popular.isNotEmpty()) {
            item { SectionHead("Lagu Populer", "Lihat semua") { onDetail(DetailKey.All) } }
            itemsIndexed(popular, key = { _, s -> "pop:" + s.uri }) { i, s ->
                var menu by remember { mutableStateOf(false) }
                val fav = s.uri in favSet
                val current = vm.currentId == s.uri
                Column(Modifier.clickable { vm.playList(popular, i) }) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtImage(s, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = if (current) Gold else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, color = SubText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { actions.onToggleFav(s) }) {
                            Icon(
                                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                "Favorit",
                                tint = if (fav) Gold else Color.White.copy(alpha = 0.6f),
                            )
                        }
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    if (i < popular.lastIndex) {
                        Box(Modifier.padding(start = 86.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    }
                }
                if (menu) SongMenuDialog(s, fav, actions) { menu = false }
            }
        } else if (all.isEmpty()) {
            item {
                Text(
                    "Belum ada lagu. Tambahkan lewat Setelan → Pindai ulang musik.",
                    color = SubText,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().padding(24.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun rememberNetworkConnected(context: Context): State<Boolean> {
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val connected = remember(connectivityManager) {
        mutableStateOf(hasValidatedInternet(connectivityManager))
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connected.value = hasValidatedInternet(connectivityManager)
            }

            override fun onLost(network: Network) {
                connected.value = hasValidatedInternet(connectivityManager)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                connected.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return connected
}

private fun hasValidatedInternet(connectivityManager: ConnectivityManager): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun SectionHead(title: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.padding(start = 22.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action, color = Gold, fontSize = 14.sp)
            Icon(Icons.Rounded.ChevronRight, null, tint = Gold, modifier = Modifier.size(20.dp))
        }
    }
}
