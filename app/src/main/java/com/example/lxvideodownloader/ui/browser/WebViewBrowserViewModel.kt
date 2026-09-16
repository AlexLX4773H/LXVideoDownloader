package com.example.lxvideodownloader.ui.browser

import androidx.lifecycle.ViewModel
import com.example.lxvideodownloader.core.sniffer.DetectedVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WebBrowserUiState(
    val pageTitle: String = "Loading...",
    val pageUrl: String = "",
    val isLoading: Boolean = true,
    val loadProgress: Int = 0,
    val detectedVideos: List<DetectedVideo> = emptyList()
)

class WebViewBrowserViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WebBrowserUiState())
    val uiState: StateFlow<WebBrowserUiState> = _uiState.asStateFlow()

    private val seenUrls = mutableSetOf<String>()

    /**
     * Adds a detected video to the list, deduplicating by URL.
     */
    fun addDetectedVideo(video: DetectedVideo) {
        val normalizedUrl = video.url.trim()
        if (normalizedUrl.isBlank()) return

        synchronized(seenUrls) {
            if (seenUrls.contains(normalizedUrl)) return
            seenUrls.add(normalizedUrl)
        }

        _uiState.update { state ->
            state.copy(detectedVideos = state.detectedVideos + video)
        }
    }

    fun updatePageTitle(title: String) {
        _uiState.update { it.copy(pageTitle = title) }
    }

    fun updatePageUrl(url: String) {
        _uiState.update { it.copy(pageUrl = url) }
    }

    fun updateLoadingState(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    fun updateLoadProgress(progress: Int) {
        _uiState.update { it.copy(loadProgress = progress) }
    }

    fun clearDetectedVideos() {
        synchronized(seenUrls) {
            seenUrls.clear()
        }
        _uiState.update { it.copy(detectedVideos = emptyList()) }
    }
}
