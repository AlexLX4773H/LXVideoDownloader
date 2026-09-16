package com.example.lxvideodownloader.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lxvideodownloader.core.model.DownloadTask
import com.example.lxvideodownloader.core.model.HlsPlaylist
import com.example.lxvideodownloader.core.model.StreamVariant
import com.example.lxvideodownloader.core.storage.CompletedVideo
import com.example.lxvideodownloader.data.DataRepository
import com.example.lxvideodownloader.data.DefaultDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val urlInput: String = "",
    val titleInput: String = "",
    val selectedTab: Int = 0,
    val isInspecting: Boolean = false,
    val errorMessage: String? = null,
    val detectedVariants: List<StreamVariant>? = null,
    val playingVideo: CompletedVideo? = null
)

class MainScreenViewModel(
    private val dataRepository: DataRepository = DefaultDataRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val activeTasks: StateFlow<List<DownloadTask>> = dataRepository.tasks
    val completedVideos: StateFlow<List<CompletedVideo>> = dataRepository.completedVideos

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl, errorMessage = null) }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(titleInput = newTitle) }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissVariantDialog() {
        _uiState.update { it.copy(detectedVariants = null) }
    }

    fun setPlayingVideo(video: CompletedVideo?) {
        _uiState.update { it.copy(playingVideo = video) }
    }

    fun refreshVideos(context: Context) {
        dataRepository.refreshVideos(context)
    }

    fun inspectAndDownload(context: Context) {
        val url = _uiState.value.urlInput.trim()
        if (url.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter an M3U8 video URL") }
            return
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _uiState.update { it.copy(errorMessage = "URL must start with http:// or https://") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isInspecting = true, errorMessage = null) }
            try {
                val playlist = dataRepository.inspectUrl(url)
                when (playlist) {
                    is HlsPlaylist.Master -> {
                        if (playlist.variants.size > 1) {
                            // Let user pick quality
                            _uiState.update {
                                it.copy(
                                    isInspecting = false,
                                    detectedVariants = playlist.variants
                                )
                            }
                        } else {
                            // Single variant or default
                            val variant = playlist.variants.firstOrNull()
                            startDownload(context, variant)
                        }
                    }
                    is HlsPlaylist.Media -> {
                        startDownload(context, null)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        errorMessage = "Could not parse M3U8 stream: ${e.message}"
                    )
                }
            }
        }
    }

    fun startDownload(context: Context, variant: StreamVariant?) {
        val url = variant?.url ?: _uiState.value.urlInput.trim()
        val title = _uiState.value.titleInput.trim().ifBlank {
            "video_${System.currentTimeMillis()}"
        }

        dataRepository.startDownload(context, url, title, variant)

        _uiState.update {
            it.copy(
                urlInput = "",
                titleInput = "",
                isInspecting = false,
                detectedVariants = null,
                selectedTab = 1 // Switch to Active Downloads tab
            )
        }
    }

    fun cancelDownload(taskId: String) {
        dataRepository.cancelDownload(taskId)
    }

    fun removeTask(taskId: String) {
        dataRepository.removeTask(taskId)
    }

    fun deleteVideo(context: Context, video: CompletedVideo) {
        dataRepository.deleteVideo(context, video)
    }

    fun shareVideo(context: Context, video: CompletedVideo) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                video.file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share Video").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to share video") }
        }
    }
}
