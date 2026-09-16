package com.example.lxvideodownloader.core.model

import java.io.Serializable

data class StreamVariant(
    val bandwidth: Long = 0L,
    val resolution: String? = null,
    val codecs: String? = null,
    val url: String,
    val displayName: String = ""
) : Serializable

data class ByteRange(
    val length: Long,
    val offset: Long? = null
) : Serializable

data class MediaSegment(
    val sequenceNumber: Long,
    val url: String,
    val duration: Double,
    val keyUrl: String? = null,
    val ivHex: String? = null,
    val byteRange: ByteRange? = null
) : Serializable

sealed interface HlsPlaylist {
    data class Master(val variants: List<StreamVariant>) : HlsPlaylist
    data class Media(
        val targetDuration: Double,
        val segments: List<MediaSegment>,
        val isVod: Boolean = true
    ) : HlsPlaylist
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class DownloadType {
    HLS,
    DIRECT
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val outputFilePath: String,
    val downloadType: DownloadType = DownloadType.HLS,
    val totalSegments: Int = 0,
    val downloadedSegments: Int = 0,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable
