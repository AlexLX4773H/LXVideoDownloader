package com.example.lxvideodownloader.core.download

import android.content.Context
import android.content.Intent
import com.example.lxvideodownloader.core.m3u8.M3U8Downloader
import com.example.lxvideodownloader.core.m3u8.M3U8Parser
import com.example.lxvideodownloader.core.model.DownloadStatus
import com.example.lxvideodownloader.core.model.DownloadTask
import com.example.lxvideodownloader.core.model.DownloadType
import com.example.lxvideodownloader.core.model.HlsPlaylist
import com.example.lxvideodownloader.core.model.StreamVariant
import com.example.lxvideodownloader.core.storage.CompletedVideo
import com.example.lxvideodownloader.core.storage.VideoStorageHelper
import com.example.lxvideodownloader.service.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hlsDownloader = M3U8Downloader()
    private val directDownloader = DirectDownloader()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _completedVideos = MutableStateFlow<List<CompletedVideo>>(emptyList())
    val completedVideos: StateFlow<List<CompletedVideo>> = _completedVideos.asStateFlow()

    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val cancellationTokens = ConcurrentHashMap<String, Boolean>()

    fun refreshCompletedVideos(context: Context) {
        scope.launch {
            val list = VideoStorageHelper.listCompletedVideos(context)
            _completedVideos.value = list
        }
    }

    suspend fun inspectUrl(url: String): HlsPlaylist {
        val playlistText = hlsDownloader.fetchPlaylist(url)
        return M3U8Parser.parse(playlistText, url)
    }

    /**
     * Start an HLS/M3U8 download (existing behavior).
     */
    fun startDownload(
        context: Context,
        url: String,
        title: String,
        selectedVariant: StreamVariant? = null
    ): String {
        val taskId = UUID.randomUUID().toString()
        val effectiveTitle = title.ifBlank { "video_${System.currentTimeMillis()}" }
        val outputFile = VideoStorageHelper.generateOutputFile(context, effectiveTitle)

        val task = DownloadTask(
            id = taskId,
            url = selectedVariant?.url ?: url,
            title = effectiveTitle,
            outputFilePath = outputFile.absolutePath,
            downloadType = DownloadType.HLS,
            status = DownloadStatus.QUEUED
        )

        _tasks.update { it + task }
        cancellationTokens[taskId] = false

        startForegroundService(context, taskId)

        val job = scope.launch {
            executeHlsDownload(context, task, outputFile)
        }
        runningJobs[taskId] = job

        return taskId
    }

    /**
     * Start a direct video file download (MP4, WEBM, etc.).
     */
    fun startDirectDownload(
        context: Context,
        url: String,
        title: String
    ): String {
        val taskId = UUID.randomUUID().toString()
        val effectiveTitle = title.ifBlank { "video_${System.currentTimeMillis()}" }

        // Determine file extension from URL
        val extension = guessExtension(url)
        val outputFile = VideoStorageHelper.generateOutputFile(context, effectiveTitle, extension)

        val task = DownloadTask(
            id = taskId,
            url = url,
            title = effectiveTitle,
            outputFilePath = outputFile.absolutePath,
            downloadType = DownloadType.DIRECT,
            status = DownloadStatus.QUEUED
        )

        _tasks.update { it + task }
        cancellationTokens[taskId] = false

        startForegroundService(context, taskId)

        val job = scope.launch {
            executeDirectDownload(context, task, outputFile)
        }
        runningJobs[taskId] = job

        return taskId
    }

    private fun startForegroundService(context: Context, taskId: String) {
        val serviceIntent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Service startup fallback
        }
    }

    private suspend fun executeHlsDownload(context: Context, initialTask: DownloadTask, outputFile: File) {
        val taskId = initialTask.id
        try {
            updateTask(taskId) { it.copy(status = DownloadStatus.DOWNLOADING) }

            val streamUrl = initialTask.url
            val playlistText = hlsDownloader.fetchPlaylist(streamUrl)
            val parsed = M3U8Parser.parse(playlistText, streamUrl)

            val mediaPlaylist = when (parsed) {
                is HlsPlaylist.Media -> parsed
                is HlsPlaylist.Master -> {
                    val bestVariant = parsed.variants.firstOrNull()
                        ?: throw IllegalStateException("No stream variant found in playlist")
                    val subText = hlsDownloader.fetchPlaylist(bestVariant.url)
                    val subParsed = M3U8Parser.parse(subText, bestVariant.url)
                    if (subParsed is HlsPlaylist.Media) subParsed else throw IllegalStateException("Invalid media playlist")
                }
            }

            val segments = mediaPlaylist.segments
            if (segments.isEmpty()) {
                throw IllegalStateException("No media segments found in stream")
            }

            updateTask(taskId) {
                it.copy(totalSegments = segments.size)
            }

            hlsDownloader.downloadSegments(
                context = context,
                taskId = taskId,
                segments = segments,
                outputFile = outputFile,
                concurrency = 3,
                isCancelled = { cancellationTokens[taskId] == true },
                onProgress = { downloaded, total, bytes, speed ->
                    updateTask(taskId) {
                        val progress = if (total > 0) downloaded.toFloat() / total else 0f
                        it.copy(
                            downloadedSegments = downloaded,
                            totalSegments = total,
                            progress = progress,
                            bytesDownloaded = bytes,
                            speedBytesPerSec = speed
                        )
                    }
                }
            )

            updateTask(taskId) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    downloadedSegments = it.totalSegments,
                    speedBytesPerSec = 0
                )
            }

            VideoStorageHelper.exportToGallery(context, outputFile)
            refreshCompletedVideos(context)

        } catch (e: CancellationException) {
            updateTask(taskId) {
                it.copy(status = DownloadStatus.CANCELLED, speedBytesPerSec = 0)
            }
            if (outputFile.exists()) outputFile.delete()
        } catch (e: Exception) {
            updateTask(taskId) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Download failed",
                    speedBytesPerSec = 0
                )
            }
            if (outputFile.exists()) outputFile.delete()
        } finally {
            runningJobs.remove(taskId)
            cancellationTokens.remove(taskId)
        }
    }

    private suspend fun executeDirectDownload(context: Context, initialTask: DownloadTask, outputFile: File) {
        val taskId = initialTask.id
        try {
            updateTask(taskId) { it.copy(status = DownloadStatus.DOWNLOADING) }

            directDownloader.download(
                url = initialTask.url,
                outputFile = outputFile,
                isCancelled = { cancellationTokens[taskId] == true },
                onProgress = { bytesDownloaded, totalBytes, speed ->
                    val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
                    updateTask(taskId) {
                        it.copy(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = if (totalBytes > 0) totalBytes else 0L,
                            progress = progress.coerceIn(0f, 1f),
                            speedBytesPerSec = speed
                        )
                    }
                }
            )

            updateTask(taskId) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    speedBytesPerSec = 0
                )
            }

            VideoStorageHelper.exportToGallery(context, outputFile)
            refreshCompletedVideos(context)

        } catch (e: CancellationException) {
            updateTask(taskId) {
                it.copy(status = DownloadStatus.CANCELLED, speedBytesPerSec = 0)
            }
            if (outputFile.exists()) outputFile.delete()
        } catch (e: Exception) {
            updateTask(taskId) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Download failed",
                    speedBytesPerSec = 0
                )
            }
            if (outputFile.exists()) outputFile.delete()
        } finally {
            runningJobs.remove(taskId)
            cancellationTokens.remove(taskId)
        }
    }

    fun cancelDownload(taskId: String) {
        cancellationTokens[taskId] = true
        runningJobs[taskId]?.cancel()
        updateTask(taskId) {
            it.copy(status = DownloadStatus.CANCELLED, speedBytesPerSec = 0)
        }
    }

    fun removeTask(taskId: String) {
        cancelDownload(taskId)
        _tasks.update { list -> list.filterNot { it.id == taskId } }
    }

    fun deleteVideo(context: Context, video: CompletedVideo) {
        VideoStorageHelper.deleteVideo(video.file)
        refreshCompletedVideos(context)
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }

    private fun guessExtension(url: String): String {
        val cleanUrl = url.substringBefore("?").substringBefore("#").lowercase()
        return when {
            cleanUrl.endsWith(".webm") -> "webm"
            cleanUrl.endsWith(".mkv") -> "mkv"
            cleanUrl.endsWith(".avi") -> "avi"
            cleanUrl.endsWith(".mov") -> "mov"
            cleanUrl.endsWith(".flv") -> "flv"
            cleanUrl.endsWith(".ts") -> "ts"
            cleanUrl.endsWith(".3gp") -> "3gp"
            else -> "mp4" // Default to mp4
        }
    }
}
