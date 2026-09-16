package com.example.lxvideodownloader.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lxvideodownloader.core.sniffer.DetectedVideo
import com.example.lxvideodownloader.core.sniffer.SourceType
import com.example.lxvideodownloader.core.sniffer.VideoUrlMatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewBrowserScreen(
    initialUrl: String,
    onVideoSelected: (DetectedVideo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebViewBrowserViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPanel by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.pageTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.pageUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Toggle panel visibility
                        BadgedBox(
                            badge = {
                                if (uiState.detectedVideos.isNotEmpty()) {
                                    Badge { Text("${uiState.detectedVideos.size}") }
                                }
                            }
                        ) {
                            IconButton(onClick = { showPanel = !showPanel }) {
                                Icon(
                                    if (showPanel) Icons.Default.Videocam else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle video panel"
                                )
                            }
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Loading progress indicator
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        progress = { uiState.loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // WebView
            WebViewContent(
                initialUrl = initialUrl,
                onWebViewCreated = { webViewRef = it },
                onPageTitleChanged = viewModel::updatePageTitle,
                onPageUrlChanged = viewModel::updatePageUrl,
                onLoadingChanged = viewModel::updateLoadingState,
                onProgressChanged = viewModel::updateLoadProgress,
                onVideoDetected = { url, mimeType, pageTitle, pageUrl ->
                    val sourceType = VideoUrlMatcher.classifySource(url, mimeType)
                    val video = DetectedVideo(
                        url = url,
                        mimeType = mimeType,
                        sourceType = sourceType,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl
                    )
                    viewModel.addDetectedVideo(video)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating detected videos panel
            AnimatedVisibility(
                visible = showPanel && uiState.detectedVideos.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                DetectedVideosPanel(
                    videos = uiState.detectedVideos,
                    onDownload = onVideoSelected
                )
            }

            // Empty hint when no videos detected yet
            if (uiState.detectedVideos.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Browsing... Play a video on the page to detect it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContent(
    initialUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onPageTitleChanged: (String) -> Unit,
    onPageUrlChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onVideoDetected: (url: String, mimeType: String?, pageTitle: String, pageUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Enable JavaScript (required for most video players)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.allowContentAccess = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                // Enable cookies
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // JavaScript interface for DOM video scanning
                addJavascriptInterface(
                    VideoJsInterface { videoUrl ->
                        if (VideoUrlMatcher.isVideo(videoUrl)) {
                            onVideoDetected(videoUrl, null, title ?: "", url ?: "")
                        }
                    },
                    "VideoDetector"
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        val mimeType = request.requestHeaders?.get("Accept")

                        // Check if this request is for a video resource
                        if (VideoUrlMatcher.isVideo(reqUrl, mimeType)) {
                            view?.post {
                                onVideoDetected(
                                    reqUrl,
                                    mimeType,
                                    view.title ?: "",
                                    view.url ?: ""
                                )
                            }
                        }

                        // Return null to let the request proceed normally
                        return null
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingChanged(true)
                        url?.let { onPageUrlChanged(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingChanged(false)
                        url?.let { onPageUrlChanged(it) }
                        view?.title?.let { onPageTitleChanged(it) }

                        // Inject JS to scan for <video> and <source> elements
                        view?.evaluateJavascript(DOM_VIDEO_SCANNER_JS, null)
                    }

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        title?.let { onPageTitleChanged(it) }
                    }

                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChanged(newProgress)
                    }
                }

                onWebViewCreated(this)
                loadUrl(initialUrl)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun DetectedVideosPanel(
    videos: List<DetectedVideo>,
    onDownload: (DetectedVideo) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Detected Videos (${videos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((videos.size.coerceAtMost(3) * 72).dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(videos, key = { it.url }) { video ->
                    DetectedVideoItem(video = video, onDownload = { onDownload(video) })
                }
            }
        }
    }
}

@Composable
private fun DetectedVideoItem(
    video: DetectedVideo,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source type badge
            SourceTypeBadge(sourceType = video.sourceType)

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = VideoUrlMatcher.extractFileName(video.url),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Grab", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SourceTypeBadge(sourceType: SourceType) {
    val (color, text) = when (sourceType) {
        SourceType.M3U8 -> MaterialTheme.colorScheme.primary to "M3U8"
        SourceType.MP4 -> MaterialTheme.colorScheme.tertiary to "MP4"
        SourceType.WEBM -> MaterialTheme.colorScheme.secondary to "WEBM"
        SourceType.TS -> MaterialTheme.colorScheme.error to "TS"
        SourceType.FLV -> MaterialTheme.colorScheme.inversePrimary to "FLV"
        SourceType.MKV -> MaterialTheme.colorScheme.outline to "MKV"
        SourceType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant to "VIDEO"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * JavaScript interface that receives video URLs from DOM scanning.
 */
private class VideoJsInterface(
    private val onVideoFound: (String) -> Unit
) {
    @JavascriptInterface
    fun onVideoSrcFound(url: String) {
        if (url.isNotBlank()) {
            onVideoFound(url)
        }
    }
}

/**
 * JavaScript snippet injected into pages to scan for <video> and <source> elements.
 * Results are sent back via the VideoDetector JavaScript interface.
 */
private const val DOM_VIDEO_SCANNER_JS = """
(function() {
    try {
        // Scan <video> elements with src attribute
        document.querySelectorAll('video[src]').forEach(function(v) {
            if (v.src) VideoDetector.onVideoSrcFound(v.src);
        });
        // Scan <source> elements inside <video>
        document.querySelectorAll('video source[src]').forEach(function(s) {
            if (s.src) VideoDetector.onVideoSrcFound(s.src);
        });
        // Scan <video> elements with currentSrc (may differ from src after playback)
        document.querySelectorAll('video').forEach(function(v) {
            if (v.currentSrc) VideoDetector.onVideoSrcFound(v.currentSrc);
        });
        // Scan <iframe> sources that might be video embeds
        document.querySelectorAll('iframe[src]').forEach(function(iframe) {
            var src = iframe.src || '';
            if (src.indexOf('.m3u8') !== -1 || src.indexOf('.mp4') !== -1) {
                VideoDetector.onVideoSrcFound(src);
            }
        });
    } catch(e) {}
})();
"""
