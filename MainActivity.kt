package com.glassplayer.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.glassplayer.app.download.YouTubeDownloader

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MusixAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.videoMode.value && viewModel.isPlaying.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }
}

@Composable
fun MusixAppRoot(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val localSongs by viewModel.localSongs.collectAsState()
    val localVideos by viewModel.localVideos.collectAsState()
    val downloadedItems by viewModel.downloadedItems.collectAsState()
    val onlineResults by viewModel.onlineResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
    val searchVideosOnly by viewModel.searchVideosOnly.collectAsState()
    val currentItem by viewModel.currentItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshLibrary()
    }

    LaunchedEffect(Unit) {
        val missing = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permLauncher.launch(permissions)
        } else {
            viewModel.refreshLibrary()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF141824), Color(0xFF0B0E14))
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    if (currentItem != null) {
                        MiniPlayerBar(
                            item = currentItem!!,
                            isPlaying = isPlaying,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onExpand = { isPlayerExpanded = true }
                        )
                    }
                    NavigationBar(containerColor = Color(0xFF111520)) {
                        AppTab.entries.forEach { tab ->
                            val icon = when (tab) {
                                AppTab.SONGS -> Icons.Rounded.MusicNote
                                AppTab.VIDEOS -> Icons.Rounded.VideoLibrary
                                AppTab.ONLINE -> Icons.Rounded.Search
                                AppTab.DOWNLOADS -> Icons.Rounded.DownloadDone
                            }
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                icon = { Icon(icon, contentDescription = tab.label) },
                                label = { Text(tab.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Musix1",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                if (selectedTab == AppTab.ONLINE) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari lagu atau video YouTube...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    viewModel.searchYouTube(searchQuery)
                                }) {
                                    Icon(Icons.Rounded.ArrowForward, contentDescription = "Cari")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            viewModel.searchYouTube(searchQuery)
                        }),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !searchVideosOnly,
                            onClick = {
                                viewModel.setSearchVideosOnly(false)
                                if (searchQuery.isNotBlank()) viewModel.searchYouTube(searchQuery)
                            },
                            label = { Text("Lagu / Musik") }
                        )
                        FilterChip(
                            selected = searchVideosOnly,
                            onClick = {
                                viewModel.setSearchVideosOnly(true)
                                if (searchQuery.isNotBlank()) viewModel.searchYouTube(searchQuery)
                            },
                            label = { Text("Video") }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }

                val listItems = when (selectedTab) {
                    AppTab.SONGS -> localSongs
                    AppTab.VIDEOS -> localVideos
                    AppTab.ONLINE -> onlineResults
                    AppTab.DOWNLOADS -> downloadedItems
                }

                if (selectedTab == AppTab.ONLINE && isSearchingOnline) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (listItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedTab == AppTab.ONLINE) "Ketik judul lagu atau artis untuk mencari"
                                   else "Belum ada media di kategori ini",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(listItems, key = { it.id }) { item ->
                            MediaRowCard(
                                item = item,
                                downloadState = item.youtubeVideoId?.let { activeDownloads[it] },
                                onPlay = {
                                    viewModel.playItem(item, listItems)
                                    isPlayerExpanded = true
                                },
                                onDownload = {
                                    viewModel.downloadCurrentOrItem(
                                        item,
                                        asVideo = item.mediaType == MediaType.YOUTUBE_VIDEO
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isPlayerExpanded && currentItem != null) {
            NowPlayingScreen(
                viewModel = viewModel,
                onCollapse = { isPlayerExpanded = false }
            )
        }
    }
}

@Composable
private fun MediaRowCard(
    item: MediaItemModel,
    downloadState: com.glassplayer.app.download.DownloadTaskState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val isDone = item.youtubeVideoId?.let { YouTubeDownloader.isDownloaded(it) } == true ||
            downloadState?.isCompleted == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.artworkUri,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.artist,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (item.isYouTube) {
            IconButton(onClick = { if (!isDone && downloadState == null) onDownload() }) {
                when {
                    downloadState != null && !downloadState.isCompleted && downloadState.error == null -> {
                        CircularProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    isDone -> {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Tersimpan", tint = Color(0xFF4ADE80))
                    }
                    else -> {
                        Icon(Icons.Rounded.Download, contentDescription = "Unduh", tint = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    item: MediaItemModel,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1F2638))
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.artworkUri,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.artist,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Putar / Jeda",
                tint = Color.White
            )
        }
    }
}
