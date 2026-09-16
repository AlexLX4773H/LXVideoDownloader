package com.example.lxvideodownloader.ui.main

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.lxvideodownloader.core.model.DownloadStatus
import com.example.lxvideodownloader.core.model.DownloadTask
import com.example.lxvideodownloader.core.model.DownloadType
import com.example.lxvideodownloader.core.model.StreamVariant
import com.example.lxvideodownloader.core.storage.CompletedVideo
import com.example.lxvideodownloader.ui.player.VideoPlayerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    onNavigateToWebBrowser: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle()
    val completedVideos by viewModel.completedVideos.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        viewModel.refreshVideos(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    // Navigate to WebView browser when ViewModel requests it
    LaunchedEffect(uiState.pendingWebBrowserUrl) {
        uiState.pendingWebBrowserUrl?.let { url ->
            viewModel.consumeWebBrowserNavigation()
            onNavigateToWebBrowser(url)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "LX Video Downloader",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Video Downloader",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val downloadingCount = activeTasks.count {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }

            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Download") },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Active") },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (downloadingCount > 0) {
                                    Badge { Text("$downloadingCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Downloading, contentDescription = null)
                        }
                    }
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = {
                        viewModel.selectTab(2)
                        viewModel.refreshVideos(context)
                    },
                    text = { Text("Saved") },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (completedVideos.isNotEmpty()) {
                                    Badge { Text("${completedVideos.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        }
                    }
                )
            }

            when (uiState.selectedTab) {
                0 -> DownloadInputTab(
                    uiState = uiState,
                    onUrlChange = viewModel::onUrlChanged,
                    onTitleChange = viewModel::onTitleChanged,
                    onStartDownload = { viewModel.inspectAndDownload(context) }
                )
                1 -> ActiveTasksTab(
                    tasks = activeTasks,
                    onCancel = viewModel::cancelDownload,
                    onRemove = viewModel::removeTask
                )
                2 -> CompletedVideosTab(
                    videos = completedVideos,
                    onPlay = viewModel::setPlayingVideo,
                    onShare = { video -> viewModel.shareVideo(context, video) },
                    onDelete = { video -> viewModel.deleteVideo(context, video) }
                )
            }
        }
    }

    // Quality Selection Dialog
    uiState.detectedVariants?.let { variants ->
        QualitySelectDialog(
            variants = variants,
            onSelect = { variant -> viewModel.startDownload(context, variant) },
            onDismiss = viewModel::dismissVariantDialog
        )
    }

    // In-App Video Player Dialog
    uiState.playingVideo?.let { video ->
        VideoPlayerDialog(
            video = video,
            onDismiss = { viewModel.setPlayingVideo(null) }
        )
    }
}

@Composable
private fun DownloadInputTab(
    uiState: MainUiState,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onStartDownload: () -> Unit
) {
    val context = LocalContext.current

    // Determine if the URL is a direct M3U8 link
    val urlLower = uiState.urlInput.trim().substringBefore("?").substringBefore("#").lowercase()
    val isM3u8 = urlLower.endsWith(".m3u8")
    val isDirectVideo = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".flv", ".ts", ".3gp")
        .any { urlLower.endsWith(it) }
    val isWebPage = uiState.urlInput.isNotBlank() && !isM3u8 && !isDirectVideo

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Grab Video",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = uiState.urlInput,
                        onValueChange = onUrlChange,
                        label = { Text("Video URL or Web Page") },
                        placeholder = { Text("https://example.com/video.mp4 or web page") },
                        leadingIcon = {
                            Icon(
                                if (isWebPage) Icons.Default.Language else Icons.Default.Link,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            Row {
                                if (uiState.urlInput.isNotEmpty()) {
                                    IconButton(onClick = { onUrlChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clipData = clipboard.primaryClip
                                        if (clipData != null && clipData.itemCount > 0) {
                                            val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                                            if (pastedText.isNotBlank()) {
                                                onUrlChange(pastedText.trim())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Show contextual hint based on URL type
                    if (uiState.urlInput.isNotBlank()) {
                        val hintText = when {
                            isM3u8 -> "🎯 Direct M3U8 stream detected — will parse immediately"
                            isDirectVideo -> "📥 Direct video file — will download immediately"
                            isWebPage -> "🌐 Web page — will open browser to find videos"
                            else -> null
                        }
                        hintText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.titleInput,
                        onValueChange = onTitleChange,
                        label = { Text("Video Title (Optional)") },
                        placeholder = { Text("My_Favorite_Video") },
                        leadingIcon = {
                            Icon(Icons.Default.Title, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onStartDownload,
                        enabled = !uiState.isInspecting && uiState.urlInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isInspecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Analyzing Playlist...")
                        } else {
                            Icon(
                                if (isWebPage) Icons.Default.Language else Icons.Default.CloudDownload,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isWebPage -> "Open & Find Videos"
                                    isDirectVideo -> "Download Video"
                                    else -> "Download Stream"
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Supported Features",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• Paste any URL — web page, M3U8, MP4, or direct video link\n" +
                               "• Web page video extraction via built-in browser\n" +
                               "• HLS Master & Media Playlists (.m3u8)\n" +
                               "• Automatic multi-quality resolution selector\n" +
                               "• High-speed concurrent TS chunk downloads\n" +
                               "• Direct MP4/WEBM/MKV file downloads with progress\n" +
                               "• Standard AES-128 stream decryption\n" +
                               "• Background downloading with persistent notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveTasksTab(
    tasks: List<DownloadTask>,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Downloading,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "No active downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Start a download from the 'Download' tab",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(task = task, onCancel = { onCancel(task.id) }, onRemove = { onRemove(task.id) })
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = task.status)
            }

            when (task.status) {
                DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
                DownloadStatus.QUEUED -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
                else -> {}
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == DownloadStatus.DOWNLOADING) {
                    if (task.downloadType == DownloadType.HLS) {
                        // HLS: show segment progress
                        Text(
                            text = "${task.downloadedSegments} / ${task.totalSegments} parts",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        // Direct: show bytes progress
                        Text(
                            text = "${formatBytes(task.bytesDownloaded)} / ${if (task.totalBytes > 0) formatBytes(task.totalBytes) else "???"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = formatSpeed(task.speedBytesPerSec),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(task.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                } else if (task.status == DownloadStatus.QUEUED) {
                    Text(
                        text = if (task.downloadType == DownloadType.HLS) "Preparing stream chunks..." else "Starting download...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (task.status == DownloadStatus.FAILED) {
                    Text(
                        text = task.errorMessage ?: "Failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = task.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                        TextButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel")
                        }
                    } else {
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Clear, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    val (color, text) = when (status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary to "Downloading"
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.tertiary to "Queued"
        DownloadStatus.COMPLETED -> Color(0xFF2E7D32) to "Completed"
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error to "Failed"
        DownloadStatus.CANCELLED -> Color.Gray to "Cancelled"
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary to "Paused"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun CompletedVideosTab(
    videos: List<CompletedVideo>,
    onPlay: (CompletedVideo) -> Unit,
    onShare: (CompletedVideo) -> Unit,
    onDelete: (CompletedVideo) -> Unit
) {
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "No downloaded videos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Completed downloads will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.file.absolutePath }) { video ->
                VideoCard(video = video, onPlay = { onPlay(video) }, onShare = { onShare(video) }, onDelete = { onDelete(video) })
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: CompletedVideo,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(video.lastModified) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(video.lastModified))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${video.formattedSize} • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
            }

            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun QualitySelectDialog(
    variants: List<StreamVariant>,
    onSelect: (StreamVariant) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HighQuality, contentDescription = null) },
        title = { Text("Select Video Quality") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(variants) { variant ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(variant) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = variant.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!variant.codecs.isNullOrBlank()) {
                                    Text(
                                        text = "Codecs: ${variant.codecs}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024))
        bytesPerSec >= 1024 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
