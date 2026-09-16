package com.example.lxvideodownloader.data

import android.content.Context
import com.example.lxvideodownloader.core.download.DownloadManager
import com.example.lxvideodownloader.core.model.DownloadTask
import com.example.lxvideodownloader.core.model.HlsPlaylist
import com.example.lxvideodownloader.core.model.StreamVariant
import com.example.lxvideodownloader.core.storage.CompletedVideo
import kotlinx.coroutines.flow.StateFlow

interface DataRepository {
    val tasks: StateFlow<List<DownloadTask>>
    val completedVideos: StateFlow<List<CompletedVideo>>
    fun refreshVideos(context: Context)
    suspend fun inspectUrl(url: String): HlsPlaylist
    fun startDownload(context: Context, url: String, title: String, variant: StreamVariant?): String
    fun cancelDownload(taskId: String)
    fun removeTask(taskId: String)
    fun deleteVideo(context: Context, video: CompletedVideo)
}

class DefaultDataRepository : DataRepository {
    override val tasks: StateFlow<List<DownloadTask>> = DownloadManager.tasks
    override val completedVideos: StateFlow<List<CompletedVideo>> = DownloadManager.completedVideos

    override fun refreshVideos(context: Context) {
        DownloadManager.refreshCompletedVideos(context)
    }

    override suspend fun inspectUrl(url: String): HlsPlaylist {
        return DownloadManager.inspectUrl(url)
    }

    override fun startDownload(
        context: Context,
        url: String,
        title: String,
        variant: StreamVariant?
    ): String {
        return DownloadManager.startDownload(context, url, title, variant)
    }

    override fun cancelDownload(taskId: String) {
        DownloadManager.cancelDownload(taskId)
    }

    override fun removeTask(taskId: String) {
        DownloadManager.removeTask(taskId)
    }

    override fun deleteVideo(context: Context, video: CompletedVideo) {
        DownloadManager.deleteVideo(context, video)
    }
}
